package com.homebasket.core.data

import androidx.room.withTransaction
import com.homebasket.core.database.HomeBasketDatabase
import com.homebasket.core.database.dao.LotRow
import com.homebasket.core.database.entity.ExpiryType
import com.homebasket.core.database.entity.FoodCondition
import com.homebasket.core.database.entity.FoodWasteRecordEntity
import com.homebasket.core.database.entity.InventoryLotEntity
import com.homebasket.core.database.entity.InventoryTransactionEntity
import com.homebasket.core.database.entity.LocationKind
import com.homebasket.core.database.entity.StockTransferEntity
import com.homebasket.core.database.entity.StorageLocationEntity
import com.homebasket.core.database.entity.TxReference
import com.homebasket.core.database.entity.TxType
import com.homebasket.core.database.entity.WasteReason
import com.homebasket.core.domain.model.Money
import com.homebasket.core.domain.model.QUANTITY_EPSILON
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * All stock changes go through here. Each operation runs in one Room transaction and writes an
 * [InventoryTransactionEntity] for every lot it touches, so history is complete and auditable.
 *
 * Rules enforced here:
 *  - Stock is never reset by date; lots persist until used, wasted or corrected.
 *  - A transfer moves quantity between locations; household total is unchanged.
 *  - Spoiled, discarded or past-date lots are never offered to cooking (see usable queries).
 */
@Singleton
class InventoryRepository @Inject constructor(
    private val db: HomeBasketDatabase,
    private val clock: Clock,
) {
    private val dao = db.inventoryDao()
    private val catalog = db.catalogDao()

    val lots: Flow<List<LotRow>> = dao.observeLotRows()
    val locations: Flow<List<StorageLocationEntity>> = dao.observeLocations()

    fun lotsForProduct(productId: Long) = dao.observeLotRowsForProduct(productId)
    fun transactions(productId: Long?, limit: Int = 200) = dao.observeTransactions(productId, limit)

    fun observeUsableTotals(): Flow<Map<Long, Double>> =
        dao.observeUsableTotals(clock.today().toEpochDay()).map { list -> list.associate { it.productId to it.quantity } }

    suspend fun usableTotals(): Map<Long, Double> =
        dao.getUsableTotals(clock.today().toEpochDay()).associate { it.productId to it.quantity }

    suspend fun getLocations() = dao.getLocations()

    suspend fun defaultLocationFor(productId: Long): Long {
        val product = catalog.getProduct(productId)
        product?.defaultLocationId?.let { return it }
        val locations = dao.getLocations()
        return (locations.firstOrNull { it.kind == LocationKind.PANTRY } ?: locations.first()).id
    }

    /** Estimated expiry from the product's typical shelf life, or null when unknown. */
    suspend fun estimatedExpiry(productId: Long, fromDay: Long): Long? =
        catalog.getProduct(productId)?.shelfLifeDays?.let { fromDay + it }

    // ----------------------------------------------------------------------------- adding stock

    suspend fun addStock(
        productId: Long,
        locationId: Long,
        quantity: Double,
        type: TxType,
        purchaseDay: Long? = null,
        expiryDay: Long? = null,
        expiryType: ExpiryType = ExpiryType.ESTIMATE,
        reference: TxReference = TxReference.MANUAL,
        referenceId: Long? = null,
        purchaseLineId: Long? = null,
        note: String? = null,
    ): Long {
        require(quantity > QUANTITY_EPSILON) { "quantity" }
        return db.withTransaction {
            val now = clock.nowMillis()
            val lotId = dao.insertLot(
                InventoryLotEntity(
                    productId = productId,
                    locationId = locationId,
                    quantity = quantity,
                    addedAt = now,
                    purchaseEpochDay = purchaseDay,
                    expiryEpochDay = expiryDay,
                    expiryType = expiryType,
                    purchaseLineId = purchaseLineId,
                    notes = note,
                )
            )
            dao.insertTransaction(
                InventoryTransactionEntity(
                    productId = productId, lotId = lotId, locationId = locationId, type = type, quantityDelta = quantity,
                    timestamp = now, epochDay = clock.today().toEpochDay(), referenceType = reference, referenceId = referenceId, note = note,
                )
            )
            lotId
        }
    }

    // ----------------------------------------------------------------------------- using stock

    /**
     * Uses [quantity] of a product first-expiring-first-out across usable lots.
     * Returns the part that could not be covered by stock (0 when fully covered).
     */
    suspend fun consume(
        productId: Long,
        quantity: Double,
        reference: TxReference,
        referenceId: Long? = null,
        note: String? = null,
        type: TxType = TxType.CONSUME,
    ): Double = db.withTransaction {
        var remaining = quantity
        val today = clock.today().toEpochDay()
        for (lot in dao.getUsableLotsFefo(productId, today)) {
            if (remaining <= QUANTITY_EPSILON) break
            val take = min(lot.quantity, remaining)
            changeLot(lot, -take, type, reference, referenceId, note)
            remaining -= take
        }
        max(0.0, remaining)
    }

    suspend fun consumeFromLot(lotId: Long, quantity: Double, note: String? = null) = db.withTransaction {
        val lot = requireNotNull(dao.getLot(lotId))
        changeLot(lot, -min(quantity, lot.quantity), TxType.CONSUME, TxReference.MANUAL, null, note)
    }

    /** Moves part or all of a lot to another place. Total household quantity does not change. */
    suspend fun transfer(lotId: Long, toLocationId: Long, quantity: Double) = db.withTransaction {
        val lot = requireNotNull(dao.getLot(lotId))
        require(quantity > QUANTITY_EPSILON && quantity <= lot.quantity + QUANTITY_EPSILON) { "quantity" }
        if (lot.locationId == toLocationId) return@withTransaction
        val moved = min(quantity, lot.quantity)
        val now = clock.nowMillis()
        val transferId = dao.insertTransfer(
            StockTransferEntity(productId = lot.productId, fromLocationId = lot.locationId, toLocationId = toLocationId, quantity = moved, timestamp = now)
        )
        changeLot(lot, -moved, TxType.TRANSFER_OUT, TxReference.TRANSFER, transferId, null)
        val newLotId = dao.insertLot(lot.copy(id = 0, locationId = toLocationId, quantity = moved, addedAt = now))
        dao.insertTransaction(
            InventoryTransactionEntity(
                productId = lot.productId, lotId = newLotId, locationId = toLocationId, type = TxType.TRANSFER_IN, quantityDelta = moved,
                timestamp = now, epochDay = clock.today().toEpochDay(), referenceType = TxReference.TRANSFER, referenceId = transferId,
            )
        )
    }

    /** Records waste from a lot and logs it with an estimated cost (from the last price paid). */
    suspend fun recordWaste(lotId: Long, quantity: Double, reason: WasteReason, note: String? = null) = db.withTransaction {
        val lot = requireNotNull(dao.getLot(lotId))
        val product = requireNotNull(catalog.getProduct(lot.productId))
        val wasted = min(quantity, lot.quantity)
        if (wasted <= QUANTITY_EPSILON) return@withTransaction
        val wasteId = db.wasteDao().insert(
            FoodWasteRecordEntity(
                productId = product.id, name = product.name, quantity = wasted, unitKind = product.unitKind, countLabel = product.countLabel,
                reason = reason, epochDay = clock.today().toEpochDay(),
                estimatedCostPaise = Money.costFor(wasted, product.packSize, product.lastPricePaise), lotId = lot.id, note = note,
            )
        )
        changeLot(lot, -wasted, TxType.WASTE, TxReference.WASTE, wasteId, note)
    }

    /** Sets a lot to the quantity actually found at home. */
    suspend fun correctQuantity(lotId: Long, newQuantity: Double, note: String? = null) = db.withTransaction {
        val lot = requireNotNull(dao.getLot(lotId))
        val delta = max(0.0, newQuantity) - lot.quantity
        if (kotlin.math.abs(delta) > QUANTITY_EPSILON) changeLot(lot, delta, TxType.ADJUST, TxReference.MANUAL, null, note)
    }

    /** DISCARDED logs the remaining quantity as waste; SPOILED keeps it visible but unusable. */
    suspend fun setCondition(lotId: Long, condition: FoodCondition) = db.withTransaction {
        val lot = requireNotNull(dao.getLot(lotId))
        if (condition == FoodCondition.DISCARDED && lot.quantity > QUANTITY_EPSILON) {
            val reason = if (lot.condition == FoodCondition.SPOILED) WasteReason.SPOILED else WasteReason.OTHER
            recordWaste(lotId, lot.quantity, reason)
        }
        dao.getLot(lotId)?.let { dao.updateLot(it.copy(condition = condition)) }
    }

    suspend fun updateLotDetails(lotId: Long, expiryDay: Long?, expiryType: ExpiryType, openedDay: Long?, notes: String?) {
        dao.getLot(lotId)?.let { dao.updateLot(it.copy(expiryEpochDay = expiryDay, expiryType = expiryType, openedEpochDay = openedDay, notes = notes)) }
    }

    /** Lot quantity change plus its audit row. Must be called inside a transaction. */
    internal suspend fun changeLot(lot: InventoryLotEntity, delta: Double, type: TxType, reference: TxReference, referenceId: Long?, note: String?) {
        val fresh = dao.getLot(lot.id) ?: lot
        var newQty = fresh.quantity + delta
        if (newQty < QUANTITY_EPSILON) newQty = 0.0
        dao.updateLot(fresh.copy(quantity = newQty))
        dao.insertTransaction(
            InventoryTransactionEntity(
                productId = fresh.productId, lotId = fresh.id, locationId = fresh.locationId, type = type, quantityDelta = newQty - fresh.quantity,
                timestamp = clock.nowMillis(), epochDay = clock.today().toEpochDay(), referenceType = reference, referenceId = referenceId, note = note,
            )
        )
    }

    // ----------------------------------------------------------------------------- locations

    suspend fun saveLocation(l: StorageLocationEntity): Long =
        if (l.id == 0L) dao.insertLocation(l.copy(sortOrder = dao.getLocations().size)) else { dao.updateLocation(l); l.id }

    /** Only empty locations can be removed, so no stock is ever orphaned. */
    suspend fun deleteLocation(l: StorageLocationEntity): Boolean {
        if (dao.countLotsAt(l.id) > 0) return false
        return runCatching { dao.deleteLocation(l) }.isSuccess
    }
}
