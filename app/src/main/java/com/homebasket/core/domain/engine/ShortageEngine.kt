package com.homebasket.core.domain.engine

import com.homebasket.core.domain.model.Money
import com.homebasket.core.domain.model.QUANTITY_EPSILON
import com.homebasket.core.domain.model.Quantities
import com.homebasket.core.domain.model.UnitKind
import kotlin.math.max

/** What the engine needs to know about a product. Quantities are in base units. */
data class EngineProduct(
    val id: Long,
    val name: String,
    val unitKind: UnitKind,
    val countLabel: String,
    val packSize: Double,
    val packPricePaise: Long?,
    val minStock: Double,
    val desiredStock: Double,
    val preferredStore: String? = null,
)

/** One ingredient of one planned (not yet cooked or skipped) meal, already scaled to its servings. */
data class MealIngredientNeed(
    val epochDay: Long,
    val slotOrder: Int,
    val mealLabel: String,
    val productId: Long,
    val quantity: Double,
)

/** Why a quantity is recommended. Every number is in base units; the UI turns this into a sentence. */
data class ShortageReason(
    /** Needed by meals inside the planning window. */
    val requiredForMeals: Double,
    /** Usable stock at home (spoiled / expired lots already excluded). */
    val usableStock: Double,
    /** Stock already promised to planned meals that come before the window. */
    val reservedForEarlierMeals: Double,
    /** Extra kept back so the product does not fall below its minimum. */
    val reserveTopUp: Double,
    /** Quantity already confirmed as arriving (not just planned on a list). */
    val confirmedIncoming: Double,
    val mealLabels: List<String>,
)

data class ShortageLine(
    val product: EngineProduct,
    /** MAX(0, required + reserve - available - incoming), before pack rounding. */
    val shortage: Double,
    /** Shortage rounded up to whole packs; this is what goes on the list. */
    val suggestedPurchase: Double,
    val estimatedCostPaise: Long?,
    val reason: ShortageReason,
) {
    val isLowStockOnly: Boolean get() = reason.requiredForMeals <= QUANTITY_EPSILON
}

/**
 * Deterministic shortage rules engine (Part 13).
 *
 * Required Purchase = MAX(0, Required Ingredients + Desired Reserve - Usable Available Stock - Confirmed Incoming)
 *
 * - Meals are allocated stock in date/slot order, so meals planned *before* the window (e.g. today's dinner when
 *   planning tomorrow) reserve stock first and that stock is not offered again to the window.
 * - Each ingredient is aggregated per product across every meal, so a product used by several recipes is
 *   counted once with the summed quantity, and its stock is subtracted once.
 * - Items sitting on a shopping list are NOT incoming; only [confirmedIncoming] is subtracted.
 */
object ShortageEngine {

    fun compute(
        products: Map<Long, EngineProduct>,
        usableStock: Map<Long, Double>,
        plannedNeeds: List<MealIngredientNeed>,
        windowStartDay: Long,
        windowEndDay: Long,
        includeLowStockReserve: Boolean,
        confirmedIncoming: Map<Long, Double> = emptyMap(),
        roundToPacks: Boolean = true,
    ): List<ShortageLine> {
        val before = plannedNeeds.filter { it.epochDay < windowStartDay }
            .groupBy { it.productId }.mapValues { (_, v) -> v.sumOf { it.quantity } }
        val inWindow = plannedNeeds.filter { it.epochDay in windowStartDay..windowEndDay }
            .sortedWith(compareBy({ it.epochDay }, { it.slotOrder }))
        val windowByProduct = inWindow.groupBy { it.productId }

        val candidateIds = buildSet {
            addAll(windowByProduct.keys)
            if (includeLowStockReserve) products.values.filter { it.minStock > QUANTITY_EPSILON }.forEach { add(it.id) }
        }

        return candidateIds.mapNotNull { id ->
            val product = products[id] ?: return@mapNotNull null
            val needs = windowByProduct[id].orEmpty()
            val required = needs.sumOf { it.quantity }
            val usable = usableStock[id] ?: 0.0
            val reserved = before[id] ?: 0.0
            val incoming = confirmedIncoming[id] ?: 0.0
            val available = max(0.0, usable - reserved)

            val leftAfterMeals = available + incoming - required
            val reserve = if (includeLowStockReserve && product.minStock > QUANTITY_EPSILON && leftAfterMeals < product.minStock - QUANTITY_EPSILON) {
                max(product.desiredStock, product.minStock)
            } else 0.0

            val shortage = max(0.0, required + reserve - available - incoming)
            if (shortage <= QUANTITY_EPSILON) return@mapNotNull null

            val purchase = if (roundToPacks) Quantities.roundUpToPack(shortage, product.packSize) else shortage
            ShortageLine(
                product = product,
                shortage = shortage,
                suggestedPurchase = purchase,
                estimatedCostPaise = Money.costFor(purchase, product.packSize, product.packPricePaise),
                reason = ShortageReason(
                    requiredForMeals = required,
                    usableStock = usable,
                    reservedForEarlierMeals = reserved,
                    reserveTopUp = reserve,
                    confirmedIncoming = incoming,
                    mealLabels = needs.map { it.mealLabel }.distinct(),
                ),
            )
        }.sortedWith(compareBy({ it.isLowStockOnly }, { it.product.name.lowercase() }))
    }
}
