package com.homebasket.core.domain.engine

import com.homebasket.core.domain.model.Money
import com.homebasket.core.domain.model.QUANTITY_EPSILON
import com.homebasket.core.domain.model.Quantities
import kotlin.math.max

enum class MonthlyBasis { USER_SET, HISTORY, LAST_MONTH, MEAL_PLAN }

data class MonthlyInput(
    val product: EngineProduct,
    /** Requirement the user typed for this product, 0 when not set. */
    val userMonthlyRequirement: Double,
    /** Average monthly consumption from confirmed cooking/usage records (last 3 months). */
    val historicalMonthlyUse: Double,
    val lastMonthPurchased: Double,
    /** Ingredients needed by meals planned over the next month. */
    val plannedMealNeed: Double,
    val usableStock: Double,
)

data class MonthlySuggestion(
    val product: EngineProduct,
    val basis: MonthlyBasis,
    val monthlyRequirement: Double,
    val usableStock: Double,
    val minStock: Double,
    val suggestedPurchase: Double,
    val estimatedCostPaise: Long?,
    val input: MonthlyInput,
)

/**
 * Monthly (DMart-style) list builder (Part 15).
 * The requirement comes from the most trustworthy source available: what the user set, then real usage history,
 * then last month's purchases. Planned meals raise it if they need more. Remaining stock is never reset;
 * it is subtracted. Suggested = MAX(0, requirement + min stock - usable stock), rounded up to whole packs.
 */
object MonthlyPlanner {

    fun suggest(inputs: List<MonthlyInput>, roundToPacks: Boolean = true): List<MonthlySuggestion> =
        inputs.mapNotNull { input ->
            val (basis, base) = when {
                input.userMonthlyRequirement > QUANTITY_EPSILON -> MonthlyBasis.USER_SET to input.userMonthlyRequirement
                input.historicalMonthlyUse > QUANTITY_EPSILON -> MonthlyBasis.HISTORY to input.historicalMonthlyUse
                input.lastMonthPurchased > QUANTITY_EPSILON -> MonthlyBasis.LAST_MONTH to input.lastMonthPurchased
                input.plannedMealNeed > QUANTITY_EPSILON -> MonthlyBasis.MEAL_PLAN to input.plannedMealNeed
                else -> null to 0.0
            }
            val minStock = input.product.minStock
            if (basis == null && input.usableStock >= minStock - QUANTITY_EPSILON) return@mapNotNull null

            val requirement = max(base, input.plannedMealNeed)
            val raw = max(0.0, requirement + minStock - input.usableStock)
            if (raw <= QUANTITY_EPSILON) return@mapNotNull null
            val purchase = if (roundToPacks) Quantities.roundUpToPack(raw, input.product.packSize) else raw
            MonthlySuggestion(
                product = input.product,
                basis = basis ?: MonthlyBasis.USER_SET,
                monthlyRequirement = requirement,
                usableStock = input.usableStock,
                minStock = minStock,
                suggestedPurchase = purchase,
                estimatedCostPaise = Money.costFor(purchase, input.product.packSize, input.product.packPricePaise),
                input = input,
            )
        }
}
