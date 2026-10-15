package com.homebasket.feature.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.homebasket.R
import com.homebasket.core.data.BudgetRepository
import com.homebasket.core.data.Clock
import com.homebasket.core.data.HouseholdRepository
import com.homebasket.core.data.InventoryRepository
import com.homebasket.core.data.PlanningRepository
import com.homebasket.core.data.ReportData
import com.homebasket.core.data.ReportFilter
import com.homebasket.core.data.ReportRepository
import com.homebasket.core.data.ShoppingRepository
import com.homebasket.core.database.entity.BudgetCategory
import com.homebasket.core.database.entity.ListStatus
import com.homebasket.core.database.entity.ListType
import com.homebasket.core.database.entity.LocationKind
import com.homebasket.core.designsystem.DayNavigator
import com.homebasket.core.designsystem.DropdownField
import com.homebasket.core.designsystem.HbCard
import com.homebasket.core.designsystem.HbScaffold
import com.homebasket.core.designsystem.InfoBanner
import com.homebasket.core.designsystem.LocalMessenger
import com.homebasket.core.designsystem.LocalStatusColors
import com.homebasket.core.designsystem.MoneyInput
import com.homebasket.core.designsystem.SectionTitle
import com.homebasket.core.designsystem.StatCard
import com.homebasket.core.designsystem.Tone
import com.homebasket.core.designsystem.TwoColumn
import com.homebasket.core.designsystem.formatDate
import com.homebasket.core.designsystem.qty
import com.homebasket.core.domain.model.Money
import com.homebasket.core.pdf.CsvKind
import com.homebasket.core.pdf.DocumentService
import com.homebasket.core.ui.budgetCategoryLabel
import com.homebasket.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject

enum class Period { DAY, WEEK, MONTH }

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val reports: ReportRepository,
    private val documents: DocumentService,
    private val planning: PlanningRepository,
    private val inventory: InventoryRepository,
    private val clock: Clock,
) : ViewModel() {
    val period = MutableStateFlow(Period.MONTH)
    val anchor = MutableStateFlow(clock.today())
    val filter = MutableStateFlow(ReportFilter())
    val data = MutableStateFlow<ReportData?>(null)
    val extras = MutableStateFlow<Pair<Int, Long?>>(0 to null) // (items in stock, next-month estimate or tomorrow need count)
    val tomorrowNeeds = MutableStateFlow<List<com.homebasket.core.domain.engine.ShortageLine>>(emptyList())

    init {
        viewModelScope.launch {
            combine(period, anchor, filter) { p, a, f -> Triple(p, a, f) }.collect { (p, a, f) -> load(p, a, f) }
        }
    }

    fun range(p: Period = period.value, a: LocalDate = anchor.value): Pair<LocalDate, LocalDate> = when (p) {
        Period.DAY -> a to a
        Period.WEEK -> a.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it to it.plusDays(6) }
        Period.MONTH -> YearMonth.from(a).let { it.atDay(1) to it.atEndOfMonth() }
    }

    private suspend fun load(p: Period, a: LocalDate, f: ReportFilter) {
        val (s, e) = range(p, a)
        data.value = reports.build(s, e, f)
        val inStock = inventory.usableTotals().count { it.value > 0 }
        val nextMonth = if (p == Period.MONTH) planning.monthlySuggestions().sumOf { it.first.estimatedCostPaise ?: 0L } else null
        extras.value = inStock to nextMonth
        if (p == Period.DAY) {
            val t = a.plusDays(1).toEpochDay()
            tomorrowNeeds.value = planning.shortages(t, t, includeLowStock = false)
        }
    }

    fun shift(delta: Long) {
        anchor.value = when (period.value) {
            Period.DAY -> anchor.value.plusDays(delta)
            Period.WEEK -> anchor.value.plusWeeks(delta)
            Period.MONTH -> anchor.value.plusMonths(delta)
        }
    }

    fun exportCsv(uri: android.net.Uri, kind: CsvKind, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val (s, e) = range()
        onDone(runCatching { documents.writeCsv(uri, s, e, kind) }.isSuccess)
    }

    fun printMonthly(activity: android.content.Context, share: Boolean) = viewModelScope.launch {
        val file = documents.monthlyExpenseReport(YearMonth.from(anchor.value))
        if (share) documents.share(activity, file) else documents.print(activity, file, file.nameWithoutExtension)
    }
}

/** Reports tab (Part 18). Every figure comes from recorded purchases, stock changes, meals and waste. */
@Composable
fun ReportsScreen(go: (String) -> Unit, vm: ReportsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val period by vm.period.collectAsStateWithLifecycle()
    val anchor by vm.anchor.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val extras by vm.extras.collectAsStateWithLifecycle()
    val tomorrowNeeds by vm.tomorrowNeeds.collectAsStateWithLifecycle()
    var exportMenu by remember { mutableStateOf(false) }
    var csvKind by remember { mutableStateOf(CsvKind.PURCHASES) }
    var productFilter by remember { mutableStateOf(false) }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(uri, csvKind) { ok -> messenger.show(context.getString(if (ok) R.string.msg_exported else R.string.err_generic)) }
    }

    val (start, end) = vm.range(period, anchor)
    val label = when (period) {
        Period.DAY -> formatDate(start.toEpochDay())
        Period.WEEK -> start.format(DateTimeFormatter.ofPattern("d MMM")) + " – " + end.format(DateTimeFormatter.ofPattern("d MMM"))
        Period.MONTH -> start.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + start.year
    }

    HbScaffold(
        title = stringResource(R.string.tab_reports),
        actions = {
            IconButton(onClick = { go(Routes.BUDGET) }) { Icon(Icons.Default.AccountBalanceWallet, stringResource(R.string.budget)) }
            IconButton(onClick = { go(Routes.DOCUMENTS) }) { Icon(Icons.Default.Print, stringResource(R.string.print_center)) }
            IconButton(onClick = { exportMenu = true }) { Icon(Icons.Default.Download, stringResource(R.string.export)) }
            DropdownMenu(exportMenu, { exportMenu = false }) {
                listOf(CsvKind.PURCHASES to R.string.csv_purchases, CsvKind.STOCK_HISTORY to R.string.csv_stock, CsvKind.WASTE to R.string.csv_waste).forEach { (kind, res) ->
                    DropdownMenuItem(text = { Text(stringResource(res)) }, onClick = {
                        exportMenu = false; csvKind = kind
                        csvLauncher.launch("homebasket_${kind.name.lowercase()}_${start}_$end.csv")
                    })
                }
                if (period == Period.MONTH) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.print_monthly_report)) }, leadingIcon = { Icon(Icons.Default.Print, null) }, onClick = { exportMenu = false; vm.printMonthly(context, false) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.share_monthly_report)) }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { exportMenu = false; vm.printMonthly(context, true) })
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(Period.DAY to R.string.daily, Period.WEEK to R.string.weekly, Period.MONTH to R.string.monthly).forEachIndexed { i, (p, res) ->
                        SegmentedButton(selected = period == p, onClick = { vm.period.value = p }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(stringResource(res)) }
                    }
                }
            }
            item { DayNavigator(label, onPrev = { vm.shift(-1) }, onNext = { vm.shift(1) }) }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    var catMenu by remember { mutableStateOf(false) }
                    var storeMenu by remember { mutableStateOf(false) }
                    Box {
                        AssistChip(onClick = { catMenu = true }, label = { Text(filter.category?.let { stringResource(budgetCategoryLabel(it)) } ?: stringResource(R.string.all_categories)) })
                        DropdownMenu(catMenu, { catMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.all_categories)) }, onClick = { vm.filter.value = filter.copy(category = null); catMenu = false })
                            BudgetCategory.entries.forEach { c -> DropdownMenuItem(text = { Text(stringResource(budgetCategoryLabel(c))) }, onClick = { vm.filter.value = filter.copy(category = c); catMenu = false }) }
                        }
                    }
                    Box {
                        AssistChip(onClick = { storeMenu = true }, label = { Text(filter.store ?: stringResource(R.string.all_stores)) })
                        DropdownMenu(storeMenu, { storeMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.all_stores)) }, onClick = { vm.filter.value = filter.copy(store = null); storeMenu = false })
                            data?.stores.orEmpty().forEach { s -> DropdownMenuItem(text = { Text(s) }, onClick = { vm.filter.value = filter.copy(store = s); storeMenu = false }) }
                        }
                    }
                    AssistChip(onClick = { productFilter = true }, label = { Text(filter.productText.ifBlank { stringResource(R.string.all_products) }) })
                }
            }
            val d = data ?: return@LazyColumn
            item {
                val diff = d.totalPaise - d.previousPeriodPaise
                TwoColumn(
                    listOf(
                        { m -> StatCard(stringResource(R.string.total_spent), Money.format(d.totalPaise), Icons.Default.AccountBalanceWallet, m) },
                        { m -> StatCard(stringResource(R.string.vs_previous), (if (diff >= 0) "+" else "") + Money.format(diff), Icons.Default.AccountBalanceWallet, m,
                            tint = if (diff > 0) LocalStatusColors.current.warning else LocalStatusColors.current.ok) },
                        { m -> StatCard(stringResource(R.string.items_purchased), d.purchasedItemCount.toString(), Icons.Default.AccountBalanceWallet, m) },
                        { m -> StatCard(stringResource(R.string.meals_cooked), d.mealsCooked.toString(), Icons.Default.AccountBalanceWallet, m) },
                        { m -> StatCard(stringResource(R.string.waste_cost_est), Money.format(d.wasteCostPaise), Icons.Default.AccountBalanceWallet, m, tint = MaterialTheme.colorScheme.error) },
                        { m -> StatCard(stringResource(R.string.items_in_stock_now), extras.first.toString(), Icons.Default.AccountBalanceWallet, m) },
                    )
                )
            }
            if (period == Period.MONTH) extras.second?.let { est ->
                item { InfoBanner(stringResource(R.string.next_month_estimate, Money.format(est)), Tone.INFO, onClick = { go(Routes.MONTHLY) }) }
            }
            if (period == Period.DAY) {
                item { SectionTitle(stringResource(R.string.tomorrow_required)) }
                if (tomorrowNeeds.isEmpty()) item { Text(stringResource(R.string.nothing_needed_tomorrow), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                tomorrowNeeds.forEach { l ->
                    item(key = "tn${l.product.id}") {
                        ListItem(headlineContent = { Text(l.product.name) }, trailingContent = { Text(qty(l.suggestedPurchase, l.product.unitKind, l.product.countLabel)) })
                    }
                }
            }
            if (d.byCategory.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.report_by_category)) }
                item {
                    HbCard {
                        val rows = d.byCategory.entries.sortedByDescending { it.value }.map { stringResource(budgetCategoryLabel(it.key)) to it.value }
                        HorizontalBars(rows)
                    }
                }
            }
            if (period != Period.DAY && d.byDay.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.spending_by_day)) }
                item { HbCard { DayBars(start, end, d.byDay) } }
            }
            if (d.topProducts.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.most_purchased)) }
                d.topProducts.forEach { (name, count, total) ->
                    item(key = "tp$name") { ListItem(headlineContent = { Text(name) }, supportingContent = { Text(stringResource(R.string.times_bought, count)) }, trailingContent = { Text(Money.format(total)) }) }
                }
            }
            if (d.priceChanges.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.price_changes)) }
                d.priceChanges.forEach { c ->
                    item(key = "pc${c.name}") {
                        ListItem(
                            headlineContent = { Text(c.name) },
                            supportingContent = { Text(stringResource(R.string.price_change_line, Money.format(c.previousPackPaise), Money.format(c.latestPackPaise))) },
                            trailingContent = { Text((if (c.changePercent > 0) "+" else "") + c.changePercent + "%", color = if (c.changePercent > 0) MaterialTheme.colorScheme.error else LocalStatusColors.current.ok) },
                        )
                    }
                }
            }
            if (d.consumed.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.food_consumed)) }
                d.consumed.take(15).forEach { c ->
                    item(key = "fc${c.name}") { ListItem(headlineContent = { Text(c.name) }, trailingContent = { Text(qty(c.quantity, c.unitKind, c.countLabel)) }) }
                }
            }
            if (d.waste.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.waste_log)) }
                d.waste.take(10).forEach { w ->
                    item(key = "w${w.id}") {
                        ListItem(headlineContent = { Text(w.name) }, supportingContent = { Text(formatDate(w.epochDay)) },
                            trailingContent = { Text(qty(w.quantity, w.unitKind, w.countLabel) + (w.estimatedCostPaise?.let { " • ≈" + Money.format(it) } ?: "")) })
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.report_purchases)) }
            if (d.lines.isEmpty()) item { Text(stringResource(R.string.no_purchases_period), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            d.lines.takeLast(40).reversed().forEach { l ->
                item(key = "pl${l.line.id}") {
                    ListItem(
                        headlineContent = { Text(l.line.name) },
                        supportingContent = { Text(listOfNotNull(formatDate(l.epochDay), l.store, qty(l.line.quantity, l.line.unitKind, l.line.countLabel)).joinToString(" • ")) },
                        trailingContent = { Text(Money.format(l.line.amountPaise)) },
                    )
                }
            }
            item { Text(stringResource(R.string.reports_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    if (productFilter) {
        var text by remember { mutableStateOf(filter.productText) }
        AlertDialog(
            onDismissRequest = { productFilter = false },
            title = { Text(stringResource(R.string.filter_product)) },
            text = { OutlinedTextField(text, { text = it }, singleLine = true, label = { Text(stringResource(R.string.product_name)) }) },
            confirmButton = { TextButton(onClick = { vm.filter.value = filter.copy(productText = text.trim()); productFilter = false }) { Text(stringResource(R.string.apply)) } },
            dismissButton = { TextButton(onClick = { vm.filter.value = filter.copy(productText = ""); productFilter = false }) { Text(stringResource(R.string.clear)) } },
        )
    }
}

/** Labelled horizontal bars, values from real records. */
@Composable
fun HorizontalBars(rows: List<Pair<String, Long>>) {
    val max = rows.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { (label, value) ->
            Column(Modifier.semantics { contentDescription = "$label ${Money.format(value)}" }) {
                Row {
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(Money.format(value), style = MaterialTheme.typography.bodyMedium)
                }
                Canvas(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
                    drawRect(track)
                    drawRoundRect(color, size = Size(size.width * (value.coerceAtLeast(0).toFloat() / max), size.height), cornerRadius = CornerRadius(5.dp.toPx()))
                }
            }
        }
    }
}

@Composable
fun DayBars(start: LocalDate, end: LocalDate, byDay: Map<Long, Long>) {
    val days = (start.toEpochDay()..end.toEpochDay()).toList()
    val max = byDay.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val color = MaterialTheme.colorScheme.primary
    val baseline = MaterialTheme.colorScheme.outlineVariant
    Column {
        Text(stringResource(R.string.highest_day, Money.format(max)), style = MaterialTheme.typography.bodySmall)
        Canvas(Modifier.fillMaxWidth().height(120.dp).padding(top = 8.dp).semantics { contentDescription = "Spending by day" }) {
            val slot = size.width / days.size
            val barW = (slot * 0.6f).coerceAtMost(28.dp.toPx())
            days.forEachIndexed { i, d ->
                val v = (byDay[d] ?: 0L).coerceAtLeast(0)
                val h = size.height * (v.toFloat() / max)
                drawRoundRect(color, topLeft = Offset(i * slot + (slot - barW) / 2, size.height - h), size = Size(barW, h), cornerRadius = CornerRadius(3.dp.toPx()))
            }
            drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        }
        Row(Modifier.fillMaxWidth()) {
            Text(start.format(DateTimeFormatter.ofPattern("d MMM")), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(end.format(DateTimeFormatter.ofPattern("d MMM")), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budget: BudgetRepository,
    private val household: HouseholdRepository,
    clock: Clock,
) : ViewModel() {
    val month = MutableStateFlow(YearMonth.from(clock.today()))
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val overview = month.flatMapLatest { budget.observe(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setCategory(category: BudgetCategory, amount: Long) = viewModelScope.launch { budget.setCategoryBudget(month.value, category, amount) }
    fun setMonthly(amount: Long) = viewModelScope.launch {
        val h = household.getHousehold() ?: return@launch
        val s = household.getSettings() ?: return@launch
        household.save(h, s.copy(monthlyBudgetPaise = amount))
    }
    fun carryForward() = viewModelScope.launch { budget.carryForward(month.value) }
}

/** Household budget (Part 17). Warnings only; purchases are never blocked. */
@Composable
fun BudgetScreen(back: () -> Unit, vm: BudgetViewModel = hiltViewModel()) {
    val month by vm.month.collectAsStateWithLifecycle()
    val o by vm.overview.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<BudgetCategory?>(null) }
    var editMonthly by remember { mutableStateOf(false) }
    HbScaffold(title = stringResource(R.string.budget), onBack = back) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                DayNavigator(month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + month.year,
                    onPrev = { vm.month.value = month.minusMonths(1) }, onNext = { vm.month.value = month.plusMonths(1) })
            }
            val b = o ?: return@LazyColumn
            b.crossedThreshold?.let { item { InfoBanner(stringResource(R.string.budget_threshold_banner, it), if (it >= 100) Tone.ERROR else Tone.WARNING) } }
            item {
                HbCard(onClick = { editMonthly = true }) {
                    Text(stringResource(R.string.monthly_budget), style = MaterialTheme.typography.bodyMedium)
                    Text(if (b.monthlyBudgetPaise > 0) Money.format(b.monthlyBudgetPaise) else stringResource(R.string.tap_to_set), style = MaterialTheme.typography.headlineSmall)
                    if (b.monthlyBudgetPaise > 0) LinearProgressIndicator(progress = { (b.usedPercent / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        color = if (b.usedPercent >= 100) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.actual_spent) + ": " + Money.format(b.spentPaise) + " (" + b.usedPercent + "%)")
                    if (b.monthlyBudgetPaise > 0) Text(stringResource(R.string.remaining) + ": " + Money.format(b.remainingPaise), color = if (b.remainingPaise < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.pending_estimate) + ": " + Money.format(b.pendingEstimatePaise), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.previous_month) + ": " + Money.format(b.previousMonthSpentPaise), style = MaterialTheme.typography.bodyMedium)
                }
            }
            item { SectionTitle(stringResource(R.string.category_budgets), action = { TextButton(onClick = { vm.carryForward() }) { Text(stringResource(R.string.copy_last_month)) } }) }
            b.categories.forEach { c ->
                item(key = c.category.name) {
                    HbCard(onClick = { editing = c.category }) {
                        Row {
                            Text(stringResource(budgetCategoryLabel(c.category)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(Money.format(c.spentPaise) + if (c.budgetPaise > 0) " / " + Money.format(c.budgetPaise) else "")
                        }
                        if (c.budgetPaise > 0) {
                            val pct = c.spentPaise.toFloat() / c.budgetPaise
                            LinearProgressIndicator(progress = { pct.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                color = when { pct >= 1f -> MaterialTheme.colorScheme.error; pct >= 0.8f -> LocalStatusColors.current.warning; else -> MaterialTheme.colorScheme.primary })
                        } else Text(stringResource(R.string.no_category_budget), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { Text(stringResource(R.string.budget_note, b.warnPercents.joinToString("% / ") + "%"), style = MaterialTheme.typography.bodySmall) }
        }
    }
    editing?.let { cat ->
        var amount by remember { mutableStateOf(Money.toEditable(o?.categories?.firstOrNull { it.category == cat }?.budgetPaise?.takeIf { it > 0 })) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(budgetCategoryLabel(cat))) },
            text = { MoneyInput(amount, { amount = it }, stringResource(R.string.monthly_budget)) },
            confirmButton = { TextButton(onClick = { vm.setCategory(cat, Money.fromRupees(amount) ?: 0); editing = null }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (editMonthly) {
        var amount by remember { mutableStateOf(Money.toEditable(o?.monthlyBudgetPaise?.takeIf { it > 0 })) }
        AlertDialog(
            onDismissRequest = { editMonthly = false },
            title = { Text(stringResource(R.string.monthly_budget)) },
            text = { MoneyInput(amount, { amount = it }, stringResource(R.string.monthly_budget)) },
            confirmButton = { TextButton(onClick = { vm.setMonthly(Money.fromRupees(amount) ?: 0); editMonthly = false }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { editMonthly = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@HiltViewModel
class DocumentsViewModel @Inject constructor(
    private val documents: DocumentService,
    private val planning: PlanningRepository,
    shopping: ShoppingRepository,
    private val clock: Clock,
) : ViewModel() {
    val lists = shopping.listSummaries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun run(activity: android.content.Context, share: Boolean, title: String, build: suspend DocumentService.() -> File) = viewModelScope.launch {
        val file = documents.build()
        if (share) documents.share(activity, file) else documents.print(activity, file, title)
    }

    fun tomorrow(activity: android.content.Context, share: Boolean, title: String) = run(activity, share, title) { shoppingList(planning.generateTomorrowList()) }
    fun weekStart(): LocalDate = clock.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    fun month(): YearMonth = YearMonth.from(clock.today())
}

/** Print centre (Part 23): A4, black-and-white friendly, via the Android print dialog (which can also save as PDF). */
@Composable
fun DocumentsScreen(back: () -> Unit, vm: DocumentsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val lists by vm.lists.collectAsStateWithLifecycle()
    val active = lists.filter { it.status == ListStatus.ACTIVE }

    @Composable
    fun DocRow(title: String, subtitle: String, onPrint: () -> Unit, onShare: () -> Unit) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle) },
            trailingContent = {
                Row {
                    IconButton(onClick = onPrint) { Icon(Icons.Default.Print, stringResource(R.string.print)) }
                    IconButton(onClick = onShare) { Icon(Icons.Default.Share, stringResource(R.string.share_pdf)) }
                }
            },
        )
        HorizontalDivider()
    }

    HbScaffold(title = stringResource(R.string.print_center), onBack = back) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item { Text(stringResource(R.string.print_center_intro), style = MaterialTheme.typography.bodyMedium) }
            item { SectionTitle(stringResource(R.string.checklists)) }
            item {
                val t = stringResource(R.string.tomorrows_shopping)
                DocRow(t, stringResource(R.string.doc_tomorrow_sub), { vm.tomorrow(context, false, t) }, { vm.tomorrow(context, true, t) })
            }
            active.filter { it.type != ListType.TOMORROW }.forEach { l ->
                item(key = "l${l.id}") {
                    DocRow(l.name, stringResource(R.string.items_done, l.doneCount, l.itemCount),
                        { vm.run(context, false, l.name) { shoppingList(l.id) } }, { vm.run(context, true, l.name) { shoppingList(l.id) } })
                }
            }
            item { SectionTitle(stringResource(R.string.registers_reports)) }
            item {
                val t = stringResource(R.string.pdf_stock_register)
                DocRow(t, stringResource(R.string.doc_stock_sub), { vm.run(context, false, t) { stockRegister(null, t) } }, { vm.run(context, true, t) { stockRegister(null, t) } })
            }
            item {
                val t = stringResource(R.string.pdf_fridge_report)
                val kinds = setOf(LocationKind.FRIDGE, LocationKind.FREEZER)
                DocRow(t, stringResource(R.string.doc_fridge_sub), { vm.run(context, false, t) { stockRegister(kinds, t) } }, { vm.run(context, true, t) { stockRegister(kinds, t) } })
            }
            item {
                val t = stringResource(R.string.pdf_weekly_menu)
                DocRow(t, stringResource(R.string.doc_menu_sub), { vm.run(context, false, t) { weeklyMenu(vm.weekStart()) } }, { vm.run(context, true, t) { weeklyMenu(vm.weekStart()) } })
            }
            item {
                val t = stringResource(R.string.pdf_expense_report)
                DocRow(t, stringResource(R.string.doc_expense_sub), { vm.run(context, false, t) { monthlyExpenseReport(vm.month()) } }, { vm.run(context, true, t) { monthlyExpenseReport(vm.month()) } })
            }
        }
    }
}
