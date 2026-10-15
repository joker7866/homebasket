package com.homebasket.core.data

import androidx.room.withTransaction
import com.homebasket.core.database.HomeBasketDatabase
import com.homebasket.core.database.dao.ShoppingListSummary
import com.homebasket.core.database.entity.BudgetCategory
import com.homebasket.core.database.entity.ExpiryType
import com.homebasket.core.database.entity.ItemSource
import com.homebasket.core.database.entity.ItemState
import com.homebasket.core.database.entity.ListStatus
import com.homebasket.core.database.entity.ListType
import com.homebasket.core.database.entity.Priority
import com.homebasket.core.database.entity.ProductEntity
import com.homebasket.core.database.entity.PurchaseEntity
import com.homebasket.core.database.entity.PurchaseLineEntity
import com.homebasket.core.database.entity.ShoppingListEntity
import com.homebasket.core.database.entity.ShoppingListItemEntity
import com.homebasket.core.database.entity.TxReference
import com.homebasket.core.database.entity.TxType
import com.homebasket.core.domain.model.Money
import com.homebasket.core.domain.model.QUANTITY_EPSILON
import com.homebasket.core.domain.model.UnitKind
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.roundToLong

data class PurchaseResult(val purchaseId: Long, val lineCount: Int, val totalPaise: Long)

/** A line typed directly on the "Record Purchase" screen (no shopping list involved). */
data class DirectPurchaseLine(
    val productId: Long?,
    val name: String,
    val quantity: Double,
    val unitKind: UnitKind,
    val countLabel: String,
    val amountPaise: Long,
    val budgetCategory: BudgetCategory,
    val locationId: Long?,
    val expiryDay: Long?,
)

/**
 * Shopping lists are plans, not stock (Rule 3). Ticking an item in Shopping Mode is provisional; only
 * [confirmPurchase] writes purchase lines, expenses and inventory - once per item, inside one transaction (Rules 2, 8).
 */
@Singleton
class ShoppingRepository @Inject constructor(
    private val db: HomeBasketDatabase,
    private val inventory: InventoryRepository,
    private val clock: Clock,
) {
    private val dao = db.shoppingDao()
    private val purchases = db.purchaseDao()
    private val catalog = db.catalogDao()

    val listSummaries: Flow<List<ShoppingListSummary>> = dao.observeListSummaries()
    fun observeList(id: Long) = dao.observeList(id)
    fun observeItems(listId: Long) = dao.observeItems(listId)
    fun observeOpenTomorrowCount() = dao.observeOpenTomorrowCount(clock.today().plusDays(1).toEpochDay())
    suspend fun getList(id: Long) = dao.getList(id)
    suspend fun getItems(listId: Long) = dao.getItems(listId)

    suspend fun createList(name: String, type: ListType, targetDay: Long?, store: String? = null): Long {
        val now = clock.nowMillis()
        return dao.insertList(ShoppingListEntity(name = name.trim(), type = type, targetEpochDay = targetDay, store = store, createdAt = now, updatedAt = now))
    }

    suspend fun getOrCreateList(type: ListType, targetDay: Long?, name: String): Long =
        dao.findActiveList(type, targetDay)?.id ?: createList(name, type, targetDay)

    suspend fun renameList(list: ShoppingListEntity, name: String, store: String?) =
        dao.updateList(list.copy(name = name.trim(), store = store?.trim()?.ifEmpty { null }, updatedAt = clock.nowMillis()))

    suspend fun deleteList(list: ShoppingListEntity) = dao.deleteList(list)

    fun estimate(product: ProductEntity, quantity: Double): Long? = Money.costFor(quantity, product.packSize, product.lastPricePaise)

    /** Adds a catalog product. A pending line for the same product is increased instead of duplicated. */
    suspend fun addProduct(
        listId: Long,
        productId: Long,
        quantity: Double,
        source: ItemSource,
        reason: String? = null,
        priority: Priority = Priority.NORMAL,
        notes: String? = null,
    ): Long = db.withTransaction {
        val row = requireNotNull(catalog.getProductRow(productId))
        val p = row.product
        val existing = dao.findPendingItem(listId, productId)
        val id = if (existing != null) {
            val q = existing.quantity + quantity
            dao.updateItem(existing.copy(quantity = q, estimatedPricePaise = estimate(p, q), notes = notes ?: existing.notes))
            existing.id
        } else {
            dao.insertItem(
                ShoppingListItemEntity(
                    listId = listId, productId = p.id, name = p.name, categoryName = row.categoryName, budgetCategory = row.budgetCategory,
                    unitKind = p.unitKind, countLabel = p.countLabel, quantity = quantity, estimatedPricePaise = estimate(p, quantity),
                    store = p.preferredStore, priority = priority, source = source, reason = reason, notes = notes, locationId = p.defaultLocationId,
                )
            )
        }
        dao.touch(listId, clock.nowMillis())
        id
    }

    /** A free-text item that is not in the catalog (e.g. a gift). It is recorded as expense only. */
    suspend fun addCustom(listId: Long, name: String, quantity: Double, unitKind: UnitKind, budgetCategory: BudgetCategory, estimatePaise: Long?, notes: String?): Long {
        val id = dao.insertItem(
            ShoppingListItemEntity(
                listId = listId, productId = null, name = name.trim(), categoryName = "Other", budgetCategory = budgetCategory, unitKind = unitKind,
                quantity = quantity, estimatedPricePaise = estimatePaise, notes = notes, source = ItemSource.MANUAL,
            )
        )
        dao.touch(listId, clock.nowMillis())
        return id
    }

    suspend fun insertItems(items: List<ShoppingListItemEntity>) = dao.insertItems(items)

    suspend fun updateItem(item: ShoppingListItemEntity) {
        val current = dao.getItem(item.id) ?: return
        if (current.state == ItemState.PURCHASED) return // confirmed lines are corrected via returns, never edited
        dao.updateItem(item)
        dao.touch(item.listId, clock.nowMillis())
    }

    suspend fun deleteItem(item: ShoppingListItemEntity): Boolean {
        val current = dao.getItem(item.id) ?: return false
        if (current.state == ItemState.PURCHASED) return false
        dao.deleteItem(current)
        return true
    }

    suspend fun restoreItem(item: ShoppingListItemEntity) = dao.insertItem(item.copy(id = 0))

    suspend fun setState(item: ShoppingListItemEntity, state: ItemState) {
        require(state != ItemState.PURCHASED) { "Use confirmPurchase" }
        updateItem(item.copy(state = state))
    }

    /**
     * Turns every PICKED item of the list into a purchase line, adds tracked products to stock and updates
     * last price. Items already PURCHASED are skipped, so calling this twice cannot double count.
     */
    suspend fun confirmPurchase(listId: Long, store: String?): PurchaseResult? = db.withTransaction {
        val list = requireNotNull(dao.getList(listId))
        val picked = dao.getItems(listId).filter { it.state == ItemState.PICKED && it.purchaseLineId == null }
        if (picked.isEmpty()) return@withTransaction null
        val today = clock.today().toEpochDay()
        val purchaseId = purchases.insertPurchase(
            PurchaseEntity(epochDay = today, timestamp = clock.nowMillis(), store = store ?: list.store, shoppingListId = listId, note = list.name)
        )
        var total = 0L
        picked.forEach { item ->
            val qty = item.actualQuantity ?: item.quantity
            val amount = item.actualPricePaise ?: item.estimatedPricePaise ?: 0L
            val lineId = writeLine(
                purchaseId, today,
                DirectPurchaseLine(item.productId, item.substituteName ?: item.name, qty, item.unitKind, item.countLabel, amount, item.budgetCategory, item.locationId, null),
            )
            total += amount
            dao.updateItem(item.copy(state = ItemState.PURCHASED, purchaseLineId = lineId, actualQuantity = qty, actualPricePaise = amount))
        }
        val remaining = dao.getItems(listId).count { it.state == ItemState.PENDING || it.state == ItemState.PICKED }
        dao.updateList(list.copy(status = if (remaining == 0) ListStatus.COMPLETED else ListStatus.ACTIVE, updatedAt = clock.nowMillis()))
        PurchaseResult(purchaseId, picked.size, total)
    }

    suspend fun recordDirectPurchase(store: String?, day: Long, note: String?, lines: List<DirectPurchaseLine>): PurchaseResult? = db.withTransaction {
        val valid = lines.filter { it.quantity > QUANTITY_EPSILON && it.name.isNotBlank() }
        if (valid.isEmpty()) return@withTransaction null
        val purchaseId = purchases.insertPurchase(PurchaseEntity(epochDay = day, timestamp = clock.nowMillis(), store = store?.trim()?.ifEmpty { null }, note = note))
        valid.forEach { writeLine(purchaseId, day, it) }
        PurchaseResult(purchaseId, valid.size, valid.sumOf { it.amountPaise })
    }

    private suspend fun writeLine(purchaseId: Long, day: Long, l: DirectPurchaseLine): Long {
        val product = l.productId?.let { catalog.getProduct(it) }
        val tracksStock = product != null && l.budgetCategory != BudgetCategory.CLOTHING
        val lineId = purchases.insertLine(
            PurchaseLineEntity(
                purchaseId = purchaseId, productId = product?.id, name = l.name, unitKind = l.unitKind, countLabel = l.countLabel,
                quantity = l.quantity, amountPaise = l.amountPaise, budgetCategory = l.budgetCategory,
                locationId = if (tracksStock) l.locationId ?: inventory.defaultLocationFor(product!!.id) else null,
                addedToInventory = tracksStock,
            )
        )
        if (product != null) {
            if (tracksStock) {
                val location = l.locationId ?: inventory.defaultLocationFor(product.id)
                val expiry = l.expiryDay ?: inventory.estimatedExpiry(product.id, day)
                inventory.addStock(
                    product.id, location, l.quantity, TxType.PURCHASE, purchaseDay = day, expiryDay = expiry,
                    expiryType = if (l.expiryDay != null) ExpiryType.USE_BY else ExpiryType.ESTIMATE,
                    reference = TxReference.PURCHASE_LINE, referenceId = lineId, purchaseLineId = lineId,
                )
            }
            if (l.amountPaise > 0 && product.packSize > QUANTITY_EPSILON) {
                catalog.recordPrice(product.id, (l.amountPaise * product.packSize / l.quantity).roundToLong(), day)
            } else catalog.recordPurchaseDay(product.id, day)
            db.recurringDao().getForProduct(product.id)?.let { rule ->
                db.recurringDao().upsert(rule.copy(nextDueEpochDay = day + rule.intervalDays))
            }
        }
        return lineId
    }

    /**
     * Records a return as a negative line in a new purchase dated today. When [removeFromStock] is set the
     * quantity is taken back out of inventory (from that purchase's lot first) with a RETURN transaction.
     */
    suspend fun returnLine(lineId: Long, quantity: Double, refundPaise: Long, removeFromStock: Boolean): Boolean = db.withTransaction {
        val line = purchases.getLine(lineId) ?: return@withTransaction false
        val returnable = line.quantity - purchases.returnedQuantity(lineId)
        val qty = min(quantity, returnable)
        if (qty <= QUANTITY_EPSILON || line.returnOfLineId != null) return@withTransaction false
        val original = purchases.getPurchase(line.purchaseId)
        val today = clock.today().toEpochDay()
        val purchaseId = purchases.insertPurchase(PurchaseEntity(epochDay = today, timestamp = clock.nowMillis(), store = original?.store, note = "Return"))
        val returnId = purchases.insertLine(
            line.copy(id = 0, purchaseId = purchaseId, quantity = -qty, amountPaise = -refundPaise, returnOfLineId = lineId, addedToInventory = removeFromStock && line.addedToInventory)
        )
        if (removeFromStock && line.addedToInventory && line.productId != null) {
            var remaining = qty
            db.inventoryDao().getLotsForPurchaseLine(lineId).forEach { lot ->
                if (remaining <= QUANTITY_EPSILON) return@forEach
                val take = min(lot.quantity, remaining)
                inventory.changeLot(lot, -take, TxType.RETURN, TxReference.PURCHASE_LINE, returnId, null)
                remaining -= take
            }
            if (remaining > QUANTITY_EPSILON) inventory.consume(line.productId, remaining, TxReference.PURCHASE_LINE, returnId, type = TxType.RETURN)
        }
        true
    }

    /** Fixes a wrongly entered amount with a separate correction line, keeping the original for audit. */
    suspend fun correctAmount(lineId: Long, correctAmountPaise: Long) = db.withTransaction {
        val line = purchases.getLine(lineId) ?: return@withTransaction
        val delta = correctAmountPaise - line.amountPaise
        if (delta == 0L) return@withTransaction
        purchases.insertLine(line.copy(id = 0, name = "${line.name} (correction)", quantity = 0.0, amountPaise = delta, returnOfLineId = lineId, addedToInventory = false))
    }

    fun observePurchases(limit: Int = 100) = purchases.observePurchaseSummaries(limit)
    fun observePurchaseLines(purchaseId: Long) = purchases.observeLines(purchaseId)
    suspend fun returnedQuantity(lineId: Long) = purchases.returnedQuantity(lineId)
}
