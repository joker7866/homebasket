package com.homebasket.core.data

import com.homebasket.core.database.HomeBasketDatabase
import com.homebasket.core.database.dao.PurchaseLineRow
import com.homebasket.core.database.dao.TransactionRow
import com.homebasket.core.database.entity.BudgetCategory
import com.homebasket.core.database.entity.BudgetEntity
import com.homebasket.core.database.entity.FoodWasteRecordEntity
import com.homebasket.core.database.entity.ListStatus
import com.homebasket.core.database.entity.TxType
import com.homebasket.core.domain.model.QUANTITY_EPSILON
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

fun YearMonth.key(): Int = year * 100 + monthValue
fun YearMonth.startDay(): Long = atDay(1).toEpochDay()
fun YearMonth.endDay(): Long = atEndOfMonth().toEpochDay()

data class CategoryBudget(val category: BudgetCategory, val budgetPaise: Long, val spentPaise: Long)

data class BudgetOverview(
    val month: YearMonth,
    val monthlyBudgetPaise: Long,
    val spentPaise: Long,
    /** Estimate of everything still pending on active lists. Labelled as an estimate in the UI. */
    val pendingEstimatePaise: Long,
    val previousMonthSpentPaise: Long,
    val categories: List<CategoryBudget>,
    val warnPercents: List<Int>,
) {
    val remainingPaise: Long get() = monthlyBudgetPaise - spentPaise
    val usedPercent: Int get() = if (monthlyBudgetPaise <= 0) 0 else ((spentPaise * 100.0) / monthlyBudgetPaise).roundToLong().toInt()
    /** Highest threshold crossed (e.g. 80, 90, 100) or null. Warns only; purchases are never blocked. */
    val crossedThreshold: Int? get() = warnPercents.filter { monthlyBudgetPaise > 0 && usedPercent >= it }.maxOrNull()
}

@Singleton
class BudgetRepository @Inject constructor(
    private val db: HomeBasketDatabase,
    private val household: HouseholdRepository,
) {
    fun observe(month: YearMonth): Flow<BudgetOverview> {
        val prev = month.minusMonths(1)
        return combine(
            household.settings,
            db.budgetDao().observeBudgets(month.key()),
            db.purchaseDao().observeSpendByCategory(month.startDay(), month.endDay()),
            db.purchaseDao().observeSpendByCategory(prev.startDay(), prev.endDay()),
            db.shoppingDao().observeListSummaries(),
        ) { settings, budgets, spend, prevSpend, lists ->
            val spendMap = spend.associate { it.budgetCategory to it.totalPaise }
            val budgetMap = budgets.associate { it.category to it.amountPaise }
            BudgetOverview(
                month = month,
                monthlyBudgetPaise = settings.monthlyBudgetPaise,
                spentPaise = spend.sumOf { it.totalPaise },
                pendingEstimatePaise = lists.filter { it.status == ListStatus.ACTIVE }.sumOf { it.estimatedPaise ?: 0L },
                previousMonthSpentPaise = prevSpend.sumOf { it.totalPaise },
                categories = BudgetCategory.entries.map { CategoryBudget(it, budgetMap[it] ?: 0L, spendMap[it] ?: 0L) },
                warnPercents = listOf(settings.warnPercent1, settings.warnPercent2, settings.warnPercent3),
            )
        }
    }

    suspend fun current(month: YearMonth): BudgetOverview = observe(month).first()

    suspend fun setCategoryBudget(month: YearMonth, category: BudgetCategory, amountPaise: Long) {
        val existing = db.budgetDao().get(month.key(), category)
        db.budgetDao().upsert(BudgetEntity(id = existing?.id ?: 0, yearMonth = month.key(), category = category, amountPaise = amountPaise))
    }

    /** Copies last month's category budgets into this month when it has none yet. */
    suspend fun carryForward(month: YearMonth) {
        if (db.budgetDao().getBudgets(month.key()).isNotEmpty()) return
        db.budgetDao().getBudgets(month.minusMonths(1).key()).forEach {
            db.budgetDao().upsert(it.copy(id = 0, yearMonth = month.key()))
        }
    }
}

data class PriceChange(val name: String, val previousPackPaise: Long, val latestPackPaise: Long) {
    val changePercent: Int get() = if (previousPackPaise <= 0) 0 else (((latestPackPaise - previousPackPaise) * 100.0) / previousPackPaise).roundToLong().toInt()
}

data class ConsumedItem(val name: String, val quantity: Double, val unitKind: com.homebasket.core.domain.model.UnitKind, val countLabel: String)

data class ReportFilter(val category: BudgetCategory? = null, val store: String? = null, val productText: String = "")

data class ReportData(
    val start: LocalDate,
    val end: LocalDate,
    val lines: List<PurchaseLineRow>,
    val totalPaise: Long,
    val previousPeriodPaise: Long,
    val byCategory: Map<BudgetCategory, Long>,
    val byDay: Map<Long, Long>,
    val consumed: List<ConsumedItem>,
    val waste: List<FoodWasteRecordEntity>,
    val wasteCostPaise: Long,
    val mealsCooked: Int,
    val topProducts: List<Triple<String, Int, Long>>,
    val priceChanges: List<PriceChange>,
    val stores: List<String>,
) {
    val purchasedItemCount: Int get() = lines.count { it.line.quantity > 0 }
}

@Singleton
class ReportRepository @Inject constructor(private val db: HomeBasketDatabase) {

    suspend fun build(start: LocalDate, end: LocalDate, filter: ReportFilter = ReportFilter()): ReportData {
        val s = start.toEpochDay()
        val e = end.toEpochDay()
        val length = e - s + 1
        val all = db.purchaseDao().getLinesBetween(s, e)
        val stores = all.mapNotNull { it.store }.distinct().sorted()
        val lines = all.filter { row ->
            (filter.category == null || row.line.budgetCategory == filter.category) &&
                (filter.store == null || row.store == filter.store) &&
                (filter.productText.isBlank() || row.line.name.contains(filter.productText.trim(), ignoreCase = true))
        }
        val previous = db.purchaseDao().getLinesBetween(s - length, s - 1).filter { row ->
            (filter.category == null || row.line.budgetCategory == filter.category) && (filter.store == null || row.store == filter.store)
        }

        val txs: List<TransactionRow> = db.inventoryDao().getTransactionsBetween(s, e)
        val consumed = txs.filter { it.tx.type == TxType.CONSUME }
            .groupBy { it.tx.productId }
            .map { (_, rows) -> ConsumedItem(rows.first().productName, -rows.sumOf { it.tx.quantityDelta }, rows.first().unitKind, rows.first().countLabel) }
            .filter { it.quantity > QUANTITY_EPSILON }
            .sortedByDescending { it.quantity }

        val waste = db.wasteDao().getBetween(s, e)

        val history = db.purchaseDao().getLinesBetween(s - 365, e)
            .filter { it.line.productId != null && it.line.quantity > QUANTITY_EPSILON && it.line.amountPaise > 0 && it.packSize != null }
        val priceChanges = history.groupBy { it.line.productId }.mapNotNull { (_, rows) ->
            val inRange = rows.filter { it.epochDay >= s }
            val before = rows.filter { it.epochDay < s }
            val latest = inRange.lastOrNull() ?: return@mapNotNull null
            val prior = before.lastOrNull() ?: inRange.dropLast(1).lastOrNull() ?: return@mapNotNull null
            fun pack(r: PurchaseLineRow) = (r.line.amountPaise * r.packSize!! / r.line.quantity).roundToLong()
            val change = PriceChange(latest.line.name, pack(prior), pack(latest))
            if (change.previousPackPaise == change.latestPackPaise) null else change
        }.sortedByDescending { kotlin.math.abs(it.changePercent) }

        return ReportData(
            start = start,
            end = end,
            lines = lines,
            totalPaise = lines.sumOf { it.line.amountPaise },
            previousPeriodPaise = previous.sumOf { it.line.amountPaise },
            byCategory = lines.groupBy { it.line.budgetCategory }.mapValues { (_, v) -> v.sumOf { it.line.amountPaise } },
            byDay = lines.groupBy { it.epochDay }.mapValues { (_, v) -> v.sumOf { it.line.amountPaise } },
            consumed = consumed,
            waste = waste,
            wasteCostPaise = waste.sumOf { it.estimatedCostPaise ?: 0L },
            mealsCooked = db.mealDao().countCooked(s, e),
            topProducts = lines.filter { it.line.quantity > 0 }.groupBy { it.line.name }
                .map { (name, v) -> Triple(name, v.size, v.sumOf { it.line.amountPaise }) }
                .sortedWith(compareByDescending<Triple<String, Int, Long>> { it.second }.thenByDescending { it.third }).take(10),
            priceChanges = priceChanges.take(10),
            stores = stores,
        )
    }

    suspend fun transactionsBetween(start: Long, end: Long) = db.inventoryDao().getTransactionsBetween(start, end)
    suspend fun wasteBetween(start: Long, end: Long) = db.wasteDao().getBetween(start, end)
    fun observeWaste(start: Long, end: Long) = db.wasteDao().observeBetween(start, end)
}
