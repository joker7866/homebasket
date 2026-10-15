package com.homebasket.feature.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.homebasket.R
import com.homebasket.core.data.BudgetRepository
import com.homebasket.core.data.Clock
import com.homebasket.core.data.MonthlyChoice
import com.homebasket.core.data.PlanningRepository
import com.homebasket.core.data.ReasonFormatter
import com.homebasket.core.data.SettingsRepository
import com.homebasket.core.data.ShoppingRepository
import com.homebasket.core.database.dao.ProductRow
import com.homebasket.core.database.entity.ItemSource
import com.homebasket.core.database.entity.ListStatus
import com.homebasket.core.database.entity.ListType
import com.homebasket.core.designsystem.ActionTile
import com.homebasket.core.designsystem.DropdownField
import com.homebasket.core.designsystem.EmptyState
import com.homebasket.core.designsystem.HbCard
import com.homebasket.core.designsystem.HbScaffold
import com.homebasket.core.designsystem.InfoBanner
import com.homebasket.core.designsystem.LocalMessenger
import com.homebasket.core.designsystem.QuantityInput
import com.homebasket.core.designsystem.SectionTitle
import com.homebasket.core.designsystem.Tone
import com.homebasket.core.designsystem.TwoColumn
import com.homebasket.core.designsystem.formatDate
import com.homebasket.core.designsystem.qty
import com.homebasket.core.domain.engine.MonthlySuggestion
import com.homebasket.core.domain.model.InputUnit
import com.homebasket.core.domain.model.Money
import com.homebasket.core.domain.model.Quantities
import com.homebasket.core.ui.listTypeLabel
import com.homebasket.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ShoppingHomeViewModel @Inject constructor(
    private val shopping: ShoppingRepository,
    private val planning: PlanningRepository,
    private val reasons: ReasonFormatter,
    budget: BudgetRepository,
    private val clock: Clock,
) : ViewModel() {
    val today = clock.today().toEpochDay()
    val lists = shopping.listSummaries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val tomorrowSuggestions = planning.observeShortages(today + 1, today + 1, includeLowStock = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val budget = budget.observe(YearMonth.from(clock.today())).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun reason(line: com.homebasket.core.domain.engine.ShortageLine) = reasons.shortage(line)

    fun createList(name: String, type: ListType, onDone: (Long) -> Unit) = viewModelScope.launch {
        onDone(shopping.createList(name, type, if (type == ListType.DAILY) today else null))
    }

    fun generateTomorrow(onDone: (Long) -> Unit) = viewModelScope.launch { onDone(planning.generateTomorrowList()) }
}

/** Shopping tab. */
@Composable
fun ShoppingScreen(go: (String) -> Unit, vm: ShoppingHomeViewModel = hiltViewModel()) {
    val lists by vm.lists.collectAsStateWithLifecycle()
    var newList by remember { mutableStateOf(false) }
    var showCompleted by remember { mutableStateOf(false) }
    HbScaffold(title = stringResource(R.string.tab_shopping)) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                TwoColumn(
                    listOf(
                        { m -> ActionTile(stringResource(R.string.tomorrows_shopping), Icons.Default.Today, { go(Routes.TOMORROW) }, m) },
                        { m -> ActionTile(stringResource(R.string.action_monthly_list), Icons.Default.ShoppingBasket, { go(Routes.MONTHLY) }, m) },
                        { m -> ActionTile(stringResource(R.string.new_list), Icons.Default.PlaylistAdd, { newList = true }, m) },
                        { m -> ActionTile(stringResource(R.string.action_record_purchase), Icons.Default.ReceiptLong, { go(Routes.RECORD_PURCHASE) }, m) },
                        { m -> ActionTile(stringResource(R.string.scan_bill), Icons.Default.DocumentScanner, { go(Routes.BILL_SCAN) }, m) },
                    )
                )
            }
            item { SectionTitle(stringResource(R.string.active_lists), action = { TextButton(onClick = { go(Routes.PURCHASES) }) { Icon(Icons.Default.History, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.purchase_history)) } }) }
            val active = lists.orEmpty().filter { it.status == ListStatus.ACTIVE }
            val done = lists.orEmpty().filter { it.status == ListStatus.COMPLETED }
            if (lists != null && active.isEmpty()) item { EmptyState(Icons.Default.ShoppingCart, stringResource(R.string.no_lists_title), stringResource(R.string.no_lists)) }
            items(active, key = { it.id }) { l -> ListSummaryCard(l) { go(Routes.listDetail(l.id)) } }
            if (done.isNotEmpty()) {
                item {
                    TextButton(onClick = { showCompleted = !showCompleted }) { Text(stringResource(if (showCompleted) R.string.hide_completed else R.string.show_completed, done.size)) }
                }
                if (showCompleted) items(done.take(20), key = { "d${it.id}" }) { l -> ListSummaryCard(l) { go(Routes.listDetail(l.id)) } }
            }
        }
    }
    if (newList) {
        var name by remember { mutableStateOf("") }
        var type by remember { mutableStateOf(ListType.CUSTOM) }
        AlertDialog(
            onDismissRequest = { newList = false },
            title = { Text(stringResource(R.string.new_list)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.list_name)) }, singleLine = true)
                    DropdownField(stringResource(R.string.list_type), listOf(ListType.DAILY, ListType.WEEKLY, ListType.CUSTOM), type, { stringResource(listTypeLabel(it)) }, { type = it })
                }
            },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { vm.createList(name, type) { go(Routes.listDetail(it)) }; newList = false }) { Text(stringResource(R.string.create)) } },
            dismissButton = { TextButton(onClick = { newList = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ListSummaryCard(l: com.homebasket.core.database.dao.ShoppingListSummary, onClick: () -> Unit) {
    HbCard(onClick = onClick) {
        Text(l.name, style = MaterialTheme.typography.titleMedium)
        Text(
            listOfNotNull(stringResource(listTypeLabel(l.type)), stringResource(R.string.items_done, l.doneCount, l.itemCount), l.estimatedPaise?.takeIf { it > 0 }?.let { stringResource(R.string.est_cost, Money.format(it)) }).joinToString(" • "),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (l.itemCount > 0) LinearProgressIndicator(progress = { l.doneCount.toFloat() / l.itemCount }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

/** Tomorrow's Shopping (Part 14): live suggestions with reasons, saved into an editable list. */
@Composable
fun TomorrowScreen(go: (String) -> Unit, back: () -> Unit, vm: ShoppingHomeViewModel = hiltViewModel()) {
    val suggestions by vm.tomorrowSuggestions.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val existing = lists.orEmpty().firstOrNull { it.type == ListType.TOMORROW && it.status == ListStatus.ACTIVE && it.targetEpochDay == vm.today + 1 }
    HbScaffold(title = stringResource(R.string.tomorrows_shopping), onBack = back) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(stringResource(R.string.tomorrow_intro, formatDate(vm.today + 1)), style = MaterialTheme.typography.bodyLarge) }
            existing?.let { l ->
                item {
                    HbCard(onClick = { go(Routes.listDetail(l.id)) }, container = MaterialTheme.colorScheme.primaryContainer) {
                        Text(stringResource(R.string.saved_list_ready, l.itemCount), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.open_list_hint), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Button(onClick = { vm.generateTomorrow { go(Routes.listDetail(it)) } }, modifier = Modifier.fillMaxWidth()) {
                    Icon(if (existing == null) Icons.Default.Add else Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (existing == null) R.string.save_tomorrow_list else R.string.update_tomorrow_list))
                }
            }
            item { Text(stringResource(R.string.list_never_auto_purchased), style = MaterialTheme.typography.bodySmall) }
            item { SectionTitle(stringResource(R.string.suggestions)) }
            val s = suggestions
            if (s != null && s.isEmpty()) item { InfoBanner(stringResource(R.string.nothing_needed_tomorrow), Tone.OK) }
            items(s.orEmpty(), key = { it.product.id }) { line ->
                HbCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(line.product.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(qty(line.suggestedPurchase, line.product.unitKind, line.product.countLabel), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(vm.reason(line), style = MaterialTheme.typography.bodyMedium)
                    line.estimatedCostPaise?.let { Text(stringResource(R.string.est_cost, Money.format(it)), style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@HiltViewModel
class MonthlyViewModel @Inject constructor(
    private val planning: PlanningRepository,
    private val settings: SettingsRepository,
    budget: BudgetRepository,
    clock: Clock,
) : ViewModel() {
    val month: YearMonth = YearMonth.from(clock.today())
    val suggestions = MutableStateFlow<List<Pair<MonthlySuggestion, ProductRow>>?>(null)
    val includePerishables = MutableStateFlow(false)
    val budget = budget.observe(month).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        includePerishables.value = settings.current().includePerishablesInMonthly
        suggestions.value = planning.monthlySuggestions()
    }

    fun setPerishables(on: Boolean) = viewModelScope.launch { settings.setIncludePerishablesInMonthly(on); refresh() }
    fun reason(s: MonthlySuggestion) = planning.monthlyReason(s)

    fun save(choices: List<MonthlyChoice>, name: String, onDone: (Long) -> Unit) = viewModelScope.launch { onDone(planning.saveMonthlyList(choices, name)) }
}

/** Monthly DMart / supermarket planner (Part 15). Every quantity can be overridden or unticked. */
@Composable
fun MonthlyScreen(go: (String) -> Unit, back: () -> Unit, vm: MonthlyViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val perishables by vm.includePerishables.collectAsStateWithLifecycle()
    val budget by vm.budget.collectAsStateWithLifecycle()
    val excluded = remember { mutableStateMapOf<Long, Boolean>() }
    val overrides = remember { mutableStateMapOf<Long, Double>() }
    var editing by remember { mutableStateOf<Pair<MonthlySuggestion, ProductRow>?>(null) }
    val monthName = vm.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())

    HbScaffold(title = stringResource(R.string.monthly_planner), onBack = back) { padding ->
        val list = suggestions
        val chosen = list.orEmpty().filter { excluded[it.first.product.id] != true }
        val total = chosen.sumOf { (s, _) ->
            val q = overrides[s.product.id] ?: s.suggestedPurchase
            Money.costFor(q, s.product.packSize, s.product.packPricePaise) ?: 0L
        }
        val missingPrices = chosen.count { it.first.product.packPricePaise == null }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(stringResource(R.string.monthly_intro, monthName), style = MaterialTheme.typography.bodyLarge) }
            item {
                HbCard {
                    Text(stringResource(R.string.estimated_total), style = MaterialTheme.typography.bodyMedium)
                    Text(Money.format(total), style = MaterialTheme.typography.headlineSmall)
                    if (missingPrices > 0) Text(stringResource(R.string.prices_missing_n, missingPrices), style = MaterialTheme.typography.bodySmall)
                    budget?.let { b ->
                        if (b.monthlyBudgetPaise > 0) {
                            val after = b.remainingPaise - total
                            Text(stringResource(R.string.budget_after_shopping, Money.format(b.remainingPaise), Money.format(after)),
                                color = if (after < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Text(stringResource(R.string.no_live_prices), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.include_perishables), modifier = Modifier.weight(1f))
                    Switch(perishables, { vm.setPerishables(it) })
                }
            }
            if (list == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else if (list.isEmpty()) item { InfoBanner(stringResource(R.string.monthly_empty), Tone.INFO) }
            list.orEmpty().groupBy { it.second.categoryName }.forEach { (category, rows) ->
                item(key = "c_$category") { SectionTitle(category) }
                items(rows, key = { it.first.product.id }) { pair ->
                    val (s, _) = pair
                    val include = excluded[s.product.id] != true
                    val q = overrides[s.product.id] ?: s.suggestedPurchase
                    HbCard(onClick = { editing = pair }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(include, { excluded[s.product.id] = !it })
                            Text(s.product.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(qty(q, s.product.unitKind, s.product.countLabel), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(vm.reason(s), style = MaterialTheme.typography.bodySmall)
                        Money.costFor(q, s.product.packSize, s.product.packPricePaise)?.let { Text(stringResource(R.string.est_cost, Money.format(it)), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            if (!list.isNullOrEmpty()) {
                item {
                    Button(onClick = {
                        val choices = chosen.map { (s, _) -> MonthlyChoice(s.product.id, overrides[s.product.id] ?: s.suggestedPurchase, vm.reason(s)) }
                        vm.save(choices, context.getString(R.string.list_name_monthly, monthName)) { go(Routes.listDetail(it)) }
                    }, modifier = Modifier.fillMaxWidth(), enabled = chosen.isNotEmpty()) { Text(stringResource(R.string.save_monthly_list, chosen.size)) }
                }
            }
        }
    }
    editing?.let { (s, _) ->
        val start = overrides[s.product.id] ?: s.suggestedPurchase
        var unit by remember { mutableStateOf(InputUnit.bestFor(s.product.unitKind, start)) }
        var text by remember { mutableStateOf(Quantities.formatNumber(start / unit.factor)) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(s.product.name) },
            text = { QuantityInput(text, { text = it }, unit, { unit = it }, s.product.unitKind, s.product.countLabel) },
            confirmButton = {
                TextButton(onClick = {
                    Quantities.parse(text)?.let { overrides[s.product.id] = unit.toBase(it) }
                    editing = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@HiltViewModel
class AddToListViewModel @Inject constructor(
    private val shopping: ShoppingRepository,
    private val clock: Clock,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {
    val lists = shopping.listSummaries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(listId: Long?, product: ProductRow, quantity: Double, source: ItemSource, onDone: () -> Unit) = viewModelScope.launch {
        val id = listId ?: run {
            val tomorrow = clock.today().plusDays(1)
            shopping.getOrCreateList(ListType.TOMORROW, tomorrow.toEpochDay(),
                context.getString(R.string.list_name_tomorrow, tomorrow.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))))
        }
        shopping.addProduct(id, product.product.id, quantity, source)
        onDone()
    }
}

/** Adds one product to a chosen list (Tomorrow's list by default). Adding to a list never changes stock. */
@Composable
fun AddToListDialog(product: ProductRow, onDismiss: () -> Unit, initialQuantity: Double? = null, source: ItemSource = ItemSource.MANUAL, vm: AddToListViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val lists by vm.lists.collectAsStateWithLifecycle()
    val active = lists.filter { it.status == ListStatus.ACTIVE }
    var listId by remember { mutableStateOf<Long?>(null) }
    val start = initialQuantity ?: product.product.packSize
    var unit by remember { mutableStateOf(InputUnit.bestFor(product.product.unitKind, start)) }
    var text by remember { mutableStateOf(Quantities.formatNumber(start / unit.factor)) }
    val value = Quantities.parse(text)?.let { unit.toBase(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_to_list_title, product.product.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QuantityInput(text, { text = it }, unit, { unit = it }, product.product.unitKind, product.product.countLabel)
                val options: List<Long?> = listOf<Long?>(null) + active.map { it.id }
                DropdownField(stringResource(R.string.list), options, listId, { id -> if (id == null) stringResource(R.string.tomorrows_shopping) else active.first { it.id == id }.name }, { listId = it })
            }
        },
        confirmButton = {
            TextButton(enabled = value != null && value > 0, onClick = {
                vm.add(listId, product, value!!, source) { messenger.show(context.getString(R.string.msg_added_to_list, product.product.name)) }
                onDismiss()
            }) { Text(stringResource(R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
