package com.homebasket.feature.inventory

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.homebasket.R
import com.homebasket.core.data.CatalogRepository
import com.homebasket.core.data.Clock
import com.homebasket.core.data.HouseholdRepository
import com.homebasket.core.data.InventoryRepository
import com.homebasket.core.data.MealRepository
import com.homebasket.core.data.PlanningRepository
import com.homebasket.core.data.ReportRepository
import com.homebasket.core.data.ShoppingRepository
import com.homebasket.core.data.endDay
import com.homebasket.core.data.startDay
import com.homebasket.core.database.dao.LotRow
import com.homebasket.core.database.entity.BudgetCategory
import com.homebasket.core.database.entity.FoodCondition
import com.homebasket.core.database.entity.LeftoverStatus
import com.homebasket.core.database.entity.LocationKind
import com.homebasket.core.database.entity.PreparedFoodLeftoverEntity
import com.homebasket.core.database.entity.TxReference
import com.homebasket.core.database.entity.TxType
import com.homebasket.core.designsystem.DayNavigator
import com.homebasket.core.designsystem.EmptyState
import com.homebasket.core.designsystem.HbCard
import com.homebasket.core.designsystem.HbScaffold
import com.homebasket.core.designsystem.InfoBanner
import com.homebasket.core.designsystem.LocalMessenger
import com.homebasket.core.designsystem.LocalStatusColors
import com.homebasket.core.designsystem.SearchField
import com.homebasket.core.designsystem.SectionTitle
import com.homebasket.core.designsystem.Tone
import com.homebasket.core.designsystem.formatDate
import com.homebasket.core.designsystem.qty
import com.homebasket.core.domain.model.Money
import com.homebasket.core.domain.model.QUANTITY_EPSILON
import com.homebasket.core.pdf.DocumentService
import com.homebasket.core.ui.conditionLabel
import com.homebasket.core.ui.expiryTypeLabel
import com.homebasket.core.ui.txTypeLabel
import com.homebasket.core.ui.wasteReasonLabel
import com.homebasket.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class KitchenViewModel @Inject constructor(
    private val inventory: InventoryRepository,
    private val meals: MealRepository,
    private val planning: PlanningRepository,
    private val documents: DocumentService,
    private val reports: ReportRepository,
    household: HouseholdRepository,
    private val clock: Clock,
) : ViewModel() {
    val today = clock.today().toEpochDay()
    val lots = inventory.lots.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val locations = inventory.locations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val leftovers = meals.leftovers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = household.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val transactions = inventory.transactions(null, 400).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val wasteMonth = MutableStateFlow(YearMonth.from(clock.today()))
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val waste = wasteMonth.flatMapLatest { reports.observeWaste(it.startDay(), it.endDay()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun useSoonRecipes() = planning.useSoonRecipes()

    fun setLeftover(l: PreparedFoodLeftoverEntity, status: LeftoverStatus) = viewModelScope.launch { meals.setLeftoverStatus(l, status) }
    fun addLeftover(name: String, portions: Double, locationId: Long?) = viewModelScope.launch { meals.addLeftover(name, portions, locationId, today + 1) }

    fun printFridge(activity: android.content.Context, title: String) = viewModelScope.launch {
        val file = documents.stockRegister(setOf(LocationKind.FRIDGE, LocationKind.FREEZER), title)
        documents.print(activity, file, title)
    }

    fun quickUse(productId: Long, quantity: Double) = viewModelScope.launch {
        inventory.consume(productId, quantity, TxReference.MANUAL)
    }
}

@Composable
private fun LotRowItem(lot: LotRow, today: Long, onClick: () -> Unit) {
    val warn = LocalStatusColors.current.warning
    val expired = lot.lot.expiryEpochDay != null && lot.lot.expiryEpochDay < today
    ListItem(
        headlineContent = { Text(lot.productName, fontWeight = FontWeight.Medium) },
        supportingContent = {
            val parts = buildList {
                add(lot.locationName)
                lot.lot.expiryEpochDay?.let { add(stringResource(expiryTypeLabel(lot.lot.expiryType)) + ": " + formatDate(it)) }
                if (lot.lot.condition != FoodCondition.FRESH) add(stringResource(conditionLabel(lot.lot.condition)))
                if (expired) add(stringResource(R.string.past_date_check))
            }
            Text(parts.joinToString(" • "), color = if (expired || lot.lot.condition == FoodCondition.SPOILED) MaterialTheme.colorScheme.error else if (lot.lot.condition == FoodCondition.USE_SOON) warn else MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = { Text(qty(lot.lot.quantity, lot.unitKind, lot.countLabel), style = MaterialTheme.typography.titleMedium) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Refrigerator dashboard (Part 8). */
@Composable
fun FridgeScreen(go: (String) -> Unit, back: () -> Unit, vm: KitchenViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val lots by vm.lots.collectAsStateWithLifecycle()
    val locations by vm.locations.collectAsStateWithLifecycle()
    val leftovers by vm.leftovers.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<LotRow?>(null) }
    val fridge = lots.orEmpty().filter { it.locationKind == LocationKind.FRIDGE || it.locationKind == LocationKind.FREEZER }
    val fridgeId = locations.firstOrNull { it.kind == LocationKind.FRIDGE }?.id ?: 0L
    val warnDays = settings?.expiryWarningDays ?: 2
    val title = stringResource(R.string.fridge)

    HbScaffold(
        title = title, onBack = back,
        actions = { IconButton(onClick = { vm.printFridge(context, context.getString(R.string.pdf_fridge_report)) }) { Icon(Icons.Default.Print, stringResource(R.string.print)) } },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { go(Routes.addStock(locationId = fridgeId)) }, icon = { Icon(Icons.Default.Add, null) }, text = { Text(stringResource(R.string.add_to_fridge)) }) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { InfoBanner(stringResource(R.string.food_safety_note)) }
            val soon = fridge.filter { it.lot.condition == FoodCondition.USE_SOON || it.lot.condition == FoodCondition.SPOILED || (it.lot.expiryEpochDay != null && it.lot.expiryEpochDay <= vm.today + warnDays) }
            if (soon.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.use_first)) }
                items(soon, key = { "s${it.lot.id}" }) { LotRowItem(it, vm.today) { selected = it } }
            }
            val groups = listOf(
                R.string.vegetables to { l: LotRow -> l.budgetCategory == BudgetCategory.VEGETABLES },
                R.string.fruits to { l: LotRow -> l.budgetCategory == BudgetCategory.FRUITS },
                R.string.dairy to { l: LotRow -> l.budgetCategory == BudgetCategory.DAIRY },
                R.string.frozen to { l: LotRow -> l.locationKind == LocationKind.FREEZER },
                R.string.packaged_other to { _: LotRow -> true },
            )
            val shown = mutableSetOf<Long>()
            groups.forEach { (label, predicate) ->
                val rows = fridge.filter { it.lot.id !in shown && predicate(it) }
                shown += rows.map { it.lot.id }
                if (rows.isNotEmpty()) {
                    item { SectionTitle(stringResource(label)) }
                    items(rows, key = { "g${it.lot.id}" }) { LotRowItem(it, vm.today) { selected = it } }
                }
            }
            item { SectionTitle(stringResource(R.string.leftovers)) }
            val fridgeLeftovers = leftovers
            if (fridgeLeftovers.isEmpty()) item { Text(stringResource(R.string.no_leftovers), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(fridgeLeftovers, key = { "l${it.id}" }) { LeftoverRow(it, vm.today, vm) }
            if (fridge.isEmpty()) {
                item { EmptyState(Icons.Default.AcUnit, stringResource(R.string.fridge_empty_title), stringResource(R.string.fridge_empty)) }
            }
        }
    }
    selected?.let { LotActionSheet(it, onDismiss = { selected = null }) }
}

@Composable
private fun LeftoverRow(l: PreparedFoodLeftoverEntity, today: Long, vm: KitchenViewModel) {
    ListItem(
        headlineContent = { Text(l.name) },
        supportingContent = {
            Text(
                stringResource(R.string.leftover_line, com.homebasket.core.domain.model.Quantities.formatNumber(l.portions), formatDate(l.useByEpochDay)),
                color = if (l.useByEpochDay < today) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Row {
                TextButton(onClick = { vm.setLeftover(l, LeftoverStatus.EATEN) }) { Text(stringResource(R.string.eaten)) }
                TextButton(onClick = { vm.setLeftover(l, LeftoverStatus.DISCARDED) }) { Text(stringResource(R.string.discard)) }
            }
        },
    )
}

/** Expiring soon, leftovers and use-soon recipe ideas (Part 19). */
@Composable
fun ExpiringScreen(go: (String) -> Unit, back: () -> Unit, vm: KitchenViewModel = hiltViewModel()) {
    val lots by vm.lots.collectAsStateWithLifecycle()
    val leftovers by vm.leftovers.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val locations by vm.locations.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<LotRow?>(null) }
    var addLeftover by remember { mutableStateOf(false) }
    val warnDays = settings?.expiryWarningDays ?: 2
    val all = lots.orEmpty()
    val spoiled = all.filter { it.lot.condition == FoodCondition.SPOILED }
    val past = all.filter { it.lot.condition != FoodCondition.SPOILED && it.lot.expiryEpochDay != null && it.lot.expiryEpochDay < vm.today }
    val soon = all.filter { it !in spoiled && it !in past && (it.lot.condition == FoodCondition.USE_SOON || (it.lot.expiryEpochDay != null && it.lot.expiryEpochDay <= vm.today + warnDays)) }
    val ideas by produceState(emptyList<Pair<com.homebasket.core.database.entity.RecipeEntity, List<String>>>(), lots) { value = vm.useSoonRecipes() }

    HbScaffold(title = stringResource(R.string.expiring_soon), onBack = back, floatingActionButton = {
        ExtendedFloatingActionButton(onClick = { addLeftover = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text(stringResource(R.string.add_leftover)) })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { InfoBanner(stringResource(R.string.expiry_disclaimer)) }
            if (spoiled.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.spoiled_discard)) }
                items(spoiled, key = { "sp${it.lot.id}" }) { LotRowItem(it, vm.today) { selected = it } }
            }
            if (past.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.past_date)) }
                item { InfoBanner(stringResource(R.string.past_date_hint), Tone.WARNING) }
                items(past, key = { "pd${it.lot.id}" }) { LotRowItem(it, vm.today) { selected = it } }
            }
            item { SectionTitle(stringResource(R.string.use_soon_items)) }
            if (soon.isEmpty()) item { Text(stringResource(R.string.nothing_expiring), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(soon, key = { "so${it.lot.id}" }) { LotRowItem(it, vm.today) { selected = it } }

            item { SectionTitle(stringResource(R.string.leftovers)) }
            if (leftovers.isEmpty()) item { Text(stringResource(R.string.no_leftovers), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(leftovers, key = { "lo${it.id}" }) { LeftoverRow(it, vm.today, vm) }

            if (ideas.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.use_soon_ideas)) }
                items(ideas.take(6), key = { "r${it.first.id}" }) { (recipe, hits) ->
                    HbCard(onClick = { go(Routes.recipeDetail(recipe.id)) }) {
                        Text(recipe.name, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.use_soon_idea_line, hits.joinToString(", ")), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    selected?.let { LotActionSheet(it, onDismiss = { selected = null }) }
    if (addLeftover) {
        var name by remember { mutableStateOf("") }
        var portions by remember { mutableStateOf("2") }
        AlertDialog(
            onDismissRequest = { addLeftover = false },
            title = { Text(stringResource(R.string.add_leftover)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.dish_name)) }, singleLine = true)
                    OutlinedTextField(portions, { portions = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text(stringResource(R.string.portions)) }, singleLine = true)
                    Text(stringResource(R.string.leftover_hint), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && (portions.toDoubleOrNull() ?: 0.0) > 0, onClick = {
                    vm.addLeftover(name, portions.toDouble(), locations.firstOrNull { it.kind == LocationKind.FRIDGE }?.id); addLeftover = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { addLeftover = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Food waste log with estimated cost (Part 19). */
@Composable
fun WasteScreen(back: () -> Unit, vm: KitchenViewModel = hiltViewModel()) {
    val month by vm.wasteMonth.collectAsStateWithLifecycle()
    val waste by vm.waste.collectAsStateWithLifecycle()
    HbScaffold(title = stringResource(R.string.waste_log), onBack = back) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                DayNavigator(
                    month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + month.year,
                    onPrev = { vm.wasteMonth.value = month.minusMonths(1) }, onNext = { vm.wasteMonth.value = month.plusMonths(1) },
                )
            }
            item {
                HbCard {
                    Text(stringResource(R.string.waste_total_estimated), style = MaterialTheme.typography.bodyMedium)
                    Text(Money.format(waste.sumOf { it.estimatedCostPaise ?: 0L }), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.waste_count, waste.size), style = MaterialTheme.typography.bodyMedium)
                }
            }
            item { InfoBanner(stringResource(R.string.waste_how_to)) }
            if (waste.isEmpty()) item { EmptyState(Icons.Default.DeleteSweep, stringResource(R.string.no_waste_title), stringResource(R.string.no_waste)) }
            items(waste, key = { it.id }) { w ->
                ListItem(
                    headlineContent = { Text(w.name) },
                    supportingContent = { Text(formatDate(w.epochDay) + " • " + stringResource(wasteReasonLabel(w.reason)) + (w.note?.let { " • $it" } ?: "")) },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(qty(w.quantity, w.unitKind, w.countLabel))
                            w.estimatedCostPaise?.let { Text("≈ " + Money.format(it), style = MaterialTheme.typography.bodySmall) }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

/** Daily vegetable tracking (Part 9): bought, used, remaining and tomorrow's need per vegetable. */
@Composable
fun VegetablesScreen(go: (String) -> Unit, back: () -> Unit, vm: VegetablesViewModel = hiltViewModel()) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    var useFor by remember { mutableStateOf<VegRow?>(null) }
    HbScaffold(
        title = stringResource(R.string.vegetables), onBack = back,
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { go(Routes.addStock(mode = "vegetable")) }, icon = { Icon(Icons.Default.Add, null) }, text = { Text(stringResource(R.string.action_add_vegetable)) }) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(stringResource(R.string.veg_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (rows.isEmpty()) item { EmptyState(Icons.Default.Eco, stringResource(R.string.no_veg_title), stringResource(R.string.no_veg)) }
            items(rows, key = { it.productId }) { r ->
                HbCard(onClick = { go(Routes.productDetail(r.productId)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(r.fmt(r.remaining), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    r.lastPurchase?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Text(stringResource(R.string.veg_used, r.fmt(r.usedYesterday), r.fmt(r.usedToday)), style = MaterialTheme.typography.bodyMedium)
                    if (r.tomorrowNeed > QUANTITY_EPSILON) {
                        Text(
                            if (r.shortage > QUANTITY_EPSILON) stringResource(R.string.veg_tomorrow_short, r.fmt(r.tomorrowNeed), r.fmt(r.shortage))
                            else stringResource(R.string.veg_tomorrow_ok, r.fmt(r.tomorrowNeed)),
                            color = if (r.shortage > QUANTITY_EPSILON) MaterialTheme.colorScheme.error else LocalStatusColors.current.ok,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        FilledTonalIconButton(onClick = { useFor = r }, enabled = r.remaining > QUANTITY_EPSILON) { Icon(Icons.Default.Remove, stringResource(R.string.action_use)) }
                        FilledTonalIconButton(onClick = { go(Routes.addStock(productId = r.productId, mode = "vegetable")) }) { Icon(Icons.Default.Add, stringResource(R.string.bought)) }
                    }
                }
            }
        }
    }
    useFor?.let { r ->
        var text by remember { mutableStateOf("") }
        var unit by remember { mutableStateOf(com.homebasket.core.domain.model.InputUnit.bestFor(r.kind, 100.0)) }
        val value = com.homebasket.core.domain.model.Quantities.parse(text)?.let { unit.toBase(it) }
        AlertDialog(
            onDismissRequest = { useFor = null },
            title = { Text(stringResource(R.string.used_today_title, r.name)) },
            text = {
                Column {
                    Text(stringResource(R.string.available_now, r.fmt(r.remaining)))
                    com.homebasket.core.designsystem.QuantityInput(text, { text = it }, unit, { unit = it }, r.kind, r.countLabel)
                }
            },
            confirmButton = {
                TextButton(enabled = value != null && value > 0 && value <= r.remaining + 1e-6, onClick = { vm.use(r.productId, value!!); useFor = null }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { useFor = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

data class VegRow(
    val productId: Long,
    val name: String,
    val kind: com.homebasket.core.domain.model.UnitKind,
    val countLabel: String,
    val remaining: Double,
    val lastPurchase: String?,
    val usedToday: Double,
    val usedYesterday: Double,
    val tomorrowNeed: Double,
    val shortage: Double,
) {
    fun fmt(v: Double) = qty(v, kind, countLabel)
}

@HiltViewModel
class VegetablesViewModel @Inject constructor(
    private val inventory: InventoryRepository,
    catalog: CatalogRepository,
    planning: PlanningRepository,
    shopping: ShoppingRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    clock: Clock,
) : ViewModel() {
    private val today = clock.today().toEpochDay()

    val rows = combine(
        catalog.products,
        inventory.lots,
        inventory.transactions(null, 1000),
        planning.observeShortages(today + 1, today + 1, includeLowStock = false),
        shopping.observePurchases(200),
    ) { products, lots, txs, shortages, _ ->
        val veg = products.filter { it.budgetCategory == BudgetCategory.VEGETABLES || it.budgetCategory == BudgetCategory.FRUITS }
        val needs = shortages.associateBy { it.product.id }
        veg.mapNotNull { p ->
            val id = p.product.id
            val productLots = lots.filter { it.lot.productId == id && it.lot.condition != FoodCondition.SPOILED }
            val productTx = txs.filter { it.tx.productId == id }
            val used = { day: Long -> -productTx.filter { it.tx.epochDay == day && it.tx.type == TxType.CONSUME }.sumOf { it.tx.quantityDelta } }
            val lastBuy = productTx.firstOrNull { it.tx.type == TxType.PURCHASE }
            val need = needs[id]
            if (productLots.isEmpty() && lastBuy == null && need == null) return@mapNotNull null
            VegRow(
                productId = id, name = p.product.name, kind = p.product.unitKind, countLabel = p.product.countLabel,
                remaining = productLots.sumOf { it.lot.quantity },
                lastPurchase = lastBuy?.let {
                    context.getString(
                        R.string.veg_bought, qty(it.tx.quantityDelta, p.product.unitKind, p.product.countLabel),
                        Instant.ofEpochMilli(it.tx.timestamp).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                    ) + (p.product.lastPricePaise?.let { price -> " • " + context.getString(R.string.last_price_pack, Money.format(price), qty(p.product.packSize, p.product.unitKind, p.product.countLabel)) } ?: "")
                },
                usedToday = used(today), usedYesterday = used(today - 1),
                tomorrowNeed = need?.reason?.requiredForMeals ?: 0.0,
                shortage = need?.shortage ?: 0.0,
            )
        }.sortedWith(compareByDescending<VegRow> { it.shortage }.thenBy { it.name })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun use(productId: Long, quantity: Double) = viewModelScope.launch { inventory.consume(productId, quantity, TxReference.MANUAL) }
}

/** Full audit trail of stock changes. */
@Composable
fun HistoryScreen(back: () -> Unit, vm: KitchenViewModel = hiltViewModel()) {
    val txs by vm.transactions.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    HbScaffold(title = stringResource(R.string.stock_history), onBack = back) { padding ->
        val filtered = remember(txs, query) { txs.filter { query.isBlank() || it.productName.contains(query, true) } }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item { SearchField(query, { query = it }, stringResource(R.string.search_products)) }
            if (filtered.isEmpty()) item { EmptyState(Icons.Default.History, stringResource(R.string.no_history_title), stringResource(R.string.no_history)) }
            items(filtered, key = { it.tx.id }) { t ->
                val time = Instant.ofEpochMilli(t.tx.timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
                ListItem(
                    headlineContent = { Text(t.productName) },
                    supportingContent = { Text(listOfNotNull(stringResource(txTypeLabel(t.tx.type)), t.locationName, time, t.tx.note).joinToString(" • ")) },
                    trailingContent = {
                        val sign = if (t.tx.quantityDelta >= 0) "+" else "−"
                        Text(sign + qty(kotlin.math.abs(t.tx.quantityDelta), t.unitKind, t.countLabel), color = if (t.tx.quantityDelta >= 0) LocalStatusColors.current.ok else MaterialTheme.colorScheme.error)
                    },
                )
                HorizontalDivider()
            }
        }
    }
}
