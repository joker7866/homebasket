package com.homebasket.core.data

import android.content.Context
import androidx.room.withTransaction
import com.homebasket.R
import com.homebasket.core.database.HomeBasketDatabase
import com.homebasket.core.database.dao.PlannedNeedRow
import com.homebasket.core.database.dao.ProductRow
import com.homebasket.core.database.entity.FoodCondition
import com.homebasket.core.database.entity.ItemSource
import com.homebasket.core.database.entity.ItemState
import com.homebasket.core.database.entity.ListType
import com.homebasket.core.database.entity.Priority
import com.homebasket.core.database.entity.RecipeEntity
import com.homebasket.core.domain.engine.EngineProduct
import com.homebasket.core.domain.engine.MealIngredientNeed
import com.homebasket.core.domain.engine.MonthlyBasis
import com.homebasket.core.domain.engine.MonthlyInput
import com.homebasket.core.domain.engine.MonthlyPlanner
import com.homebasket.core.domain.engine.MonthlySuggestion
import com.homebasket.core.domain.engine.ShortageEngine
import com.homebasket.core.domain.engine.ShortageLine
import com.homebasket.core.domain.model.QUANTITY_EPSILON
import com.homebasket.core.domain.model.Quantities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

fun ProductRow.toEngine() = EngineProduct(
    id = product.id, name = product.name, unitKind = product.unitKind, countLabel = product.countLabel, packSize = product.packSize,
    packPricePaise = product.lastPricePaise, minStock = product.minStock, desiredStock = product.desiredStock, preferredStore = product.preferredStore,
)

fun PlannedNeedRow.toEngine() = MealIngredientNeed(epochDay, slot.order, label, productId, quantity)

/** A monthly suggestion the user may have edited before saving it to a list. */
data class MonthlyChoice(val productId: Long, val quantity: Double, val reason: String)

/** Turns engine reasons into plain sentences in the app language. */
@Singleton
class ReasonFormatter @Inject constructor(@ApplicationContext private val context: Context) {

    private fun q(p: EngineProduct, v: Double) = Quantities.format(v, p.unitKind, p.countLabel)

    fun shortage(line: ShortageLine): String {
        val p = line.product
        val r = line.reason
        val parts = mutableListOf<String>()
        if (r.requiredForMeals > QUANTITY_EPSILON) {
            parts += context.getString(R.string.reason_meals_need, r.mealLabels.joinToString(", "), q(p, r.requiredForMeals))
            parts += context.getString(R.string.reason_you_have, q(p, r.usableStock))
        } else {
            parts += context.getString(R.string.reason_low_stock, q(p, r.usableStock), q(p, p.minStock))
        }
        if (r.reservedForEarlierMeals > QUANTITY_EPSILON) parts += context.getString(R.string.reason_reserved, q(p, r.reservedForEarlierMeals))
        if (r.reserveTopUp > QUANTITY_EPSILON && r.requiredForMeals > QUANTITY_EPSILON) parts += context.getString(R.string.reason_reserve, q(p, r.reserveTopUp))
        parts += context.getString(R.string.reason_add, q(p, line.suggestedPurchase))
        if (line.suggestedPurchase - line.shortage > QUANTITY_EPSILON) parts += context.getString(R.string.reason_rounded, q(p, line.shortage))
        return parts.joinToString(" ")
    }

    fun monthly(s: MonthlySuggestion): String {
        val p = s.product
        val basis = when (s.basis) {
            MonthlyBasis.USER_SET -> context.getString(R.string.basis_user)
            MonthlyBasis.HISTORY -> context.getString(R.string.basis_history)
            MonthlyBasis.LAST_MONTH -> context.getString(R.string.basis_last_month)
            MonthlyBasis.MEAL_PLAN -> context.getString(R.string.basis_meal_plan)
        }
        return context.getString(R.string.reason_monthly, q(p, s.monthlyRequirement), basis, q(p, s.usableStock), q(p, s.suggestedPurchase))
    }

    fun recurring(intervalDays: Int) = context.getString(R.string.reason_recurring, intervalDays)

    /** "Tomorrow's meals require 500 g Tomato and 1 L Milk that are not currently available." */
    fun tomorrowAlert(lines: List<ShortageLine>): String? {
        val meal = lines.filter { !it.isLowStockOnly }
        if (meal.isEmpty()) return null
        val items = meal.take(4).joinToString(", ") { "${q(it.product, it.shortage)} ${it.product.name}" }
        val more = if (meal.size > 4) context.getString(R.string.alert_and_more, meal.size - 4) else ""
        return context.getString(R.string.alert_tomorrow_meals, items + more)
    }
}

@Singleton
class PlanningRepository @Inject constructor(
    private val db: HomeBasketDatabase,
    private val catalog: CatalogRepository,
    private val inventory: InventoryRepository,
    private val shopping: ShoppingRepository,
    private val household: HouseholdRepository,
    private val reasons: ReasonFormatter,
    private val settings: SettingsRepository,
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    private suspend fun roundToPacks() = household.getSettings()?.roundToPacks ?: true

    /** Live shortages for a window (Home, Cooking and Tomorrow screens). Meals from today onward reserve stock in order. */
    fun observeShortages(windowStart: Long, windowEnd: Long, includeLowStock: Boolean): Flow<List<ShortageLine>> {
        val today = clock.today().toEpochDay()
        return combine(
            catalog.products,
            inventory.observeUsableTotals(),
            db.mealDao().observePlannedNeeds(today, windowEnd),
            household.settings,
        ) { products, usable, needs, s ->
            ShortageEngine.compute(
                products = products.associate { it.product.id to it.toEngine() },
                usableStock = usable,
                plannedNeeds = needs.map { it.toEngine() },
                windowStartDay = windowStart,
                windowEndDay = windowEnd,
                includeLowStockReserve = includeLowStock,
                roundToPacks = s.roundToPacks,
            )
        }
    }

    suspend fun shortages(windowStart: Long, windowEnd: Long, includeLowStock: Boolean): List<ShortageLine> {
        val today = clock.today().toEpochDay()
        return ShortageEngine.compute(
            products = catalog.getProducts().associate { it.product.id to it.toEngine() },
            usableStock = inventory.usableTotals(),
            plannedNeeds = db.mealDao().getPlannedNeeds(today, windowEnd).map { it.toEngine() },
            windowStartDay = windowStart,
            windowEndDay = windowEnd,
            includeLowStockReserve = includeLowStock,
            roundToPacks = roundToPacks(),
        )
    }

    /**
     * Builds (or refreshes) tomorrow's list from meal shortages, low stock and due recurring purchases.
     * Manual, ticked and purchased lines are kept; only previously generated pending lines are replaced,
     * so pressing the button twice never doubles quantities.
     */
    suspend fun generateTomorrowList(): Long {
        val tomorrow = clock.today().plusDays(1)
        val day = tomorrow.toEpochDay()
        val name = context.getString(R.string.list_name_tomorrow, tomorrow.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
        val listId = shopping.getOrCreateList(ListType.TOMORROW, day, name)
        val lines = shortages(day, day, includeLowStock = true)
        val due = db.recurringDao().getDue(day)
        db.withTransaction {
            db.shoppingDao().clearGeneratedPending(listId)
            val keep = db.shoppingDao().getItems(listId).mapNotNull { it.productId }.toSet()
            val planned = mutableMapOf<Long, Pair<Double, String>>()
            lines.forEach { planned[it.product.id] = it.suggestedPurchase to reasons.shortage(it) }
            val sources = lines.associate { it.product.id to if (it.isLowStockOnly) ItemSource.LOW_STOCK else ItemSource.MEALS }.toMutableMap()
            due.forEach { rule ->
                val current = planned[rule.productId]
                // A routine purchase and a meal shortage for the same product are not added together: take the larger.
                if (current == null || rule.quantity > current.first) {
                    planned[rule.productId] = rule.quantity to reasons.recurring(rule.intervalDays)
                    sources[rule.productId] = ItemSource.RECURRING
                }
            }
            planned.filterKeys { it !in keep }.forEach { (productId, value) ->
                val source = sources[productId] ?: ItemSource.MEALS
                shopping.addProduct(listId, productId, value.first, source, value.second, if (source == ItemSource.MEALS) Priority.HIGH else Priority.NORMAL)
            }
        }
        return listId
    }

    /** Weekly/daily list for any date window, from planned meals plus low stock. */
    suspend fun generatePeriodList(start: Long, end: Long, type: ListType, name: String): Long {
        val listId = shopping.getOrCreateList(type, start, name)
        val lines = shortages(start, end, includeLowStock = true)
        db.withTransaction {
            db.shoppingDao().clearGeneratedPending(listId)
            val keep = db.shoppingDao().getItems(listId).mapNotNull { it.productId }.toSet()
            lines.filter { it.product.id !in keep }.forEach {
                shopping.addProduct(listId, it.product.id, it.suggestedPurchase, if (it.isLowStockOnly) ItemSource.LOW_STOCK else ItemSource.MEALS, reasons.shortage(it))
            }
        }
        return listId
    }

    suspend fun monthlySuggestions(): List<Pair<MonthlySuggestion, ProductRow>> {
        val today = clock.today()
        val todayDay = today.toEpochDay()
        val includePerishables = settings.current().includePerishablesInMonthly
        val products = catalog.getProducts().filter { includePerishables || !it.perishable }
        val usable = inventory.usableTotals()
        val history = db.inventoryDao().getConsumptionBetween(todayDay - 90, todayDay).associate { it.productId to it.quantity / 3.0 }
        val lastMonthStart = today.minusMonths(1).withDayOfMonth(1)
        val lastMonthEnd = today.withDayOfMonth(1).minusDays(1)
        val lastMonth = db.purchaseDao().getPurchasedQuantities(lastMonthStart.toEpochDay(), lastMonthEnd.toEpochDay()).associate { it.productId to it.quantity }
        val planned = db.mealDao().getPlannedNeeds(todayDay, todayDay + 30).groupBy { it.productId }.mapValues { (_, v) -> v.sumOf { it.quantity } }
        val byId = products.associateBy { it.product.id }
        val inputs = products.map { row ->
            val id = row.product.id
            MonthlyInput(
                product = row.toEngine(),
                userMonthlyRequirement = row.product.monthlyRequirement,
                historicalMonthlyUse = history[id] ?: 0.0,
                lastMonthPurchased = max(0.0, lastMonth[id] ?: 0.0),
                plannedMealNeed = planned[id] ?: 0.0,
                usableStock = usable[id] ?: 0.0,
            )
        }
        return MonthlyPlanner.suggest(inputs, roundToPacks()).mapNotNull { s -> byId[s.product.id]?.let { s to it } }
    }

    fun monthlyReason(s: MonthlySuggestion) = reasons.monthly(s)

    suspend fun saveMonthlyList(choices: List<MonthlyChoice>, name: String): Long {
        val month = clock.today().withDayOfMonth(1).toEpochDay()
        val listId = shopping.getOrCreateList(ListType.MONTHLY, month, name)
        db.withTransaction {
            db.shoppingDao().clearGeneratedPending(listId)
            val keep = db.shoppingDao().getItems(listId).filter { it.state != ItemState.PENDING || it.source != ItemSource.MONTHLY }.mapNotNull { it.productId }.toSet()
            choices.filter { it.quantity > QUANTITY_EPSILON && it.productId !in keep }.forEach {
                shopping.addProduct(listId, it.productId, it.quantity, ItemSource.MONTHLY, it.reason)
            }
        }
        return listId
    }

    /** Recipes that use something marked "use soon" or close to its date. Never suggests spoiled stock. */
    suspend fun useSoonRecipes(): List<Pair<RecipeEntity, List<String>>> {
        val today = clock.today().toEpochDay()
        val warnDays = household.getSettings()?.expiryWarningDays ?: 2
        val useSoon = inventory.lots.first().filter { lot ->
            lot.lot.condition == FoodCondition.USE_SOON ||
                (lot.lot.condition == FoodCondition.FRESH && lot.lot.expiryEpochDay != null && lot.lot.expiryEpochDay in today..(today + warnDays))
        }
        if (useSoon.isEmpty()) return emptyList()
        val names = useSoon.associate { it.lot.productId to it.productName }
        val ingredients = db.recipeDao().observeAllIngredients().first().groupBy { it.ingredient.recipeId }
        return db.recipeDao().getRecipes().mapNotNull { r ->
            val hits = ingredients[r.id].orEmpty().mapNotNull { names[it.ingredient.productId] }.distinct()
            if (hits.isEmpty()) null else r to hits
        }.sortedByDescending { it.second.size }
    }
}
