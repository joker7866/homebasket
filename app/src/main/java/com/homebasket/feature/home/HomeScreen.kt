package com.homebasket.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.homebasket.core.designsystem.Brand
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import com.homebasket.core.data.MealRepository
import com.homebasket.core.data.PlanningRepository
import com.homebasket.core.data.ReasonFormatter
import com.homebasket.core.data.ShoppingRepository
import com.homebasket.core.database.dao.LotRow
import com.homebasket.core.database.dao.MealSlotRow
import com.homebasket.core.database.entity.FoodCondition
import com.homebasket.core.database.entity.LocationKind
import com.homebasket.core.database.entity.MealStatus
import com.homebasket.core.designsystem.ActionTile
import com.homebasket.core.designsystem.HbCard
import com.homebasket.core.designsystem.HbScaffold
import com.homebasket.core.designsystem.InfoBanner
import com.homebasket.core.designsystem.LocalStatusColors
import com.homebasket.core.designsystem.SectionTitle
import com.homebasket.core.designsystem.StatCard
import com.homebasket.core.designsystem.Tone
import com.homebasket.core.designsystem.TwoColumn
import com.homebasket.core.designsystem.formatDate
import com.homebasket.core.designsystem.qty
import com.homebasket.core.domain.engine.MealSlotKind
import com.homebasket.core.domain.engine.ShortageLine
import com.homebasket.core.domain.model.Money
import com.homebasket.core.ui.slotLabel
import com.homebasket.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalTime
import java.time.YearMonth
import javax.inject.Inject

data class HomeState(
    val householdName: String = "",
    val productsInStock: Int = 0,
    val fridgeItems: Int = 0,
    val lowStock: List<ShortageLine> = emptyList(),
    val expiring: List<LotRow> = emptyList(),
    val tomorrowItems: Int = 0,
    val budgetRemainingPaise: Long? = null,
    val budgetUsedPercent: Int = 0,
    val tomorrowMeals: List<MealSlotRow> = emptyList(),
    val shoppingAlert: String? = null,
    val budgetWarning: Int? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    household: HouseholdRepository,
    inventory: InventoryRepository,
    meals: MealRepository,
    planning: PlanningRepository,
    shopping: ShoppingRepository,
    budget: BudgetRepository,
    reasons: ReasonFormatter,
    clock: Clock,
) : ViewModel() {
    private val today = clock.today().toEpochDay()
    private val tomorrow = today + 1

    val state: StateFlow<HomeState> = combine(
        combine(household.household, household.settings) { h, s -> h to s },
        combine(inventory.lots, inventory.observeUsableTotals()) { lots, usable -> lots to usable },
        meals.observeSlots(tomorrow, tomorrow),
        combine(planning.observeShortages(tomorrow, tomorrow, includeLowStock = true), shopping.observeOpenTomorrowCount()) { a, b -> a to b },
        budget.observe(YearMonth.from(clock.today())),
    ) { (h, s), (lots, usable), tomorrowMeals, (shortages, openTomorrow), overview ->
        val warn = s.expiryWarningDays
        val expiring = lots.filter { l ->
            l.lot.condition == FoodCondition.USE_SOON || l.lot.condition == FoodCondition.SPOILED ||
                (l.lot.expiryEpochDay != null && l.lot.expiryEpochDay <= today + warn)
        }.sortedBy { it.lot.expiryEpochDay ?: Long.MAX_VALUE }
        HomeState(
            householdName = h.name,
            productsInStock = usable.count { it.value > 0 },
            fridgeItems = lots.count { it.locationKind == LocationKind.FRIDGE || it.locationKind == LocationKind.FREEZER },
            lowStock = shortages.filter { it.isLowStockOnly },
            expiring = expiring,
            tomorrowItems = maxOf(openTomorrow, shortages.size),
            budgetRemainingPaise = if (overview.monthlyBudgetPaise > 0) overview.remainingPaise else null,
            budgetUsedPercent = overview.usedPercent,
            tomorrowMeals = tomorrowMeals.filter { it.slot.status != MealStatus.SKIPPED },
            shoppingAlert = reasons.tomorrowAlert(shortages),
            budgetWarning = overview.crossedThreshold,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    val tomorrowDay = tomorrow
}

@Composable
fun HomeScreen(go: (String) -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val greeting = when (LocalTime.now().hour) {
        in 4..11 -> R.string.good_morning
        in 12..16 -> R.string.good_afternoon
        else -> R.string.good_evening
    }
    HbScaffold(
        title = stringResource(R.string.app_name),
        actions = {
            IconButton(onClick = { go(Routes.VOICE) }) { Icon(Icons.Default.Mic, stringResource(R.string.voice_entry)) }
            IconButton(onClick = { go(Routes.SETTINGS) }) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text(stringResource(greeting), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.hi_name, s.householdName), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            item { SearchPill(stringResource(R.string.search_home_hint)) { go(Routes.INVENTORY) } }
            item { HeroBanner(onPlan = { go(Routes.dayPlan(vm.tomorrowDay)) }) }
            item { SectionTitle(stringResource(R.string.shop_by_category)) }
            item { CategoryRow(go) }
            s.shoppingAlert?.let { alert ->
                item { InfoBanner(alert, Tone.WARNING, onClick = { go(Routes.TOMORROW) }) }
            }
            s.budgetWarning?.let { t ->
                item { InfoBanner(stringResource(R.string.budget_threshold_banner, t), if (t >= 100) Tone.ERROR else Tone.WARNING, onClick = { go(Routes.BUDGET) }) }
            }
            item {
                val warning = LocalStatusColors.current.warning
                TwoColumn(
                    listOf(
                        { m -> StatCard(stringResource(R.string.stat_in_stock), s.productsInStock.toString(), Icons.Default.Inventory2, m, onClick = { go(Routes.INVENTORY) }) },
                        { m -> StatCard(stringResource(R.string.stat_fridge), s.fridgeItems.toString(), Icons.Default.AcUnit, m, onClick = { go(Routes.FRIDGE) }) },
                        { m -> StatCard(stringResource(R.string.stat_low_stock), s.lowStock.size.toString(), Icons.Default.TrendingDown, m, tint = warning, onClick = { go(Routes.TOMORROW) }) },
                        { m -> StatCard(stringResource(R.string.stat_expiring), s.expiring.size.toString(), Icons.Default.Timer, m, tint = warning, onClick = { go(Routes.EXPIRING) }) },
                        { m -> StatCard(stringResource(R.string.stat_tomorrow_shopping), s.tomorrowItems.toString(), Icons.Default.ShoppingBasket, m, onClick = { go(Routes.TOMORROW) }) },
                        { m ->
                            StatCard(
                                stringResource(R.string.stat_budget_left),
                                s.budgetRemainingPaise?.let { Money.format(it) } ?: stringResource(R.string.not_set),
                                Icons.Default.AccountBalanceWallet, m,
                                tint = if ((s.budgetRemainingPaise ?: 1) < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                onClick = { go(Routes.BUDGET) },
                            )
                        },
                    )
                )
            }
            item { SectionTitle(stringResource(R.string.quick_actions)) }
            item {
                TwoColumn(
                    listOf(
                        { m -> ActionTile(stringResource(R.string.action_add_grocery), Icons.Default.AddShoppingCart, { go(Routes.addStock(mode = "grocery")) }, m) },
                        { m -> ActionTile(stringResource(R.string.action_add_vegetable), Icons.Default.Eco, { go(Routes.addStock(mode = "vegetable")) }, m) },
                        { m -> ActionTile(stringResource(R.string.action_what_to_buy), Icons.Default.Checklist, { go(Routes.TOMORROW) }, m) },
                        { m -> ActionTile(stringResource(R.string.action_plan_tomorrow), Icons.Default.EventNote, { go(Routes.dayPlan(vm.tomorrowDay)) }, m) },
                        { m -> ActionTile(stringResource(R.string.action_check_fridge), Icons.Default.Kitchen, { go(Routes.FRIDGE) }, m) },
                        { m -> ActionTile(stringResource(R.string.action_monthly_list), Icons.Default.ShoppingBasket, { go(Routes.MONTHLY) }, m) },
                        { m -> ActionTile(stringResource(R.string.action_record_purchase), Icons.Default.ReceiptLong, { go(Routes.RECORD_PURCHASE) }, m) },
                        { m -> ActionTile(stringResource(R.string.scan_bill), Icons.Default.ReceiptLong, { go(Routes.BILL_SCAN) }, m) },
                        { m -> ActionTile(stringResource(R.string.action_scan), Icons.Default.QrCodeScanner, { go(Routes.scan()) }, m) },
                    )
                )
            }
            item {
                SectionTitle(stringResource(R.string.tomorrows_cooking), action = {
                    TextButton(onClick = { go(Routes.dayPlan(vm.tomorrowDay)) }) { Text(stringResource(if (s.tomorrowMeals.isEmpty()) R.string.plan else R.string.edit)) }
                })
            }
            item {
                HbCard(onClick = { go(Routes.dayPlan(vm.tomorrowDay)) }) {
                    if (s.tomorrowMeals.isEmpty()) {
                        Text(stringResource(R.string.no_meals_tomorrow), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        MealSlotKind.entries.forEach { kind ->
                            val dishes = s.tomorrowMeals.filter { it.slot.slot == kind }
                            if (dishes.isNotEmpty()) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text(stringResource(slotLabel(kind)) + ":", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(120.dp))
                                    Text(dishes.joinToString(", ") { it.recipeName ?: it.slot.customName.orEmpty() }, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }
            if (s.lowStock.isNotEmpty() || s.expiring.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.inventory_alerts)) }
                s.expiring.take(4).forEach { lot ->
                    item(key = "exp${lot.lot.id}") {
                        val msg = when {
                            lot.lot.condition == FoodCondition.SPOILED -> stringResource(R.string.alert_spoiled, lot.productName)
                            lot.lot.condition == FoodCondition.USE_SOON -> stringResource(R.string.alert_use_soon, lot.productName, qty(lot.lot.quantity, lot.unitKind, lot.countLabel))
                            else -> stringResource(R.string.alert_expiring, lot.productName, qty(lot.lot.quantity, lot.unitKind, lot.countLabel), formatDate(lot.lot.expiryEpochDay!!))
                        }
                        InfoBanner(msg, if (lot.lot.condition == FoodCondition.SPOILED) Tone.ERROR else Tone.WARNING, onClick = { go(Routes.EXPIRING) })
                    }
                }
                s.lowStock.take(4).forEach { line ->
                    item(key = "low${line.product.id}") {
                        InfoBanner(
                            stringResource(R.string.alert_low, line.product.name, qty(line.reason.usableStock, line.product.unitKind, line.product.countLabel)),
                            Tone.INFO,
                            onClick = { go(Routes.productDetail(line.product.id)) },
                        )
                    }
                }
                if (s.lowStock.size + s.expiring.size > 8) {
                    item { TextButton(onClick = { go(Routes.EXPIRING) }) { Text(stringResource(R.string.see_all)) } }
                }
            }
            item { Spacer(Modifier.size(8.dp)) }
        }
    }
}

@Composable
private fun SearchPill(hint: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(hint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HeroBanner(onPlan: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Brand.Forest, Brand.Sage)))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.hero_title), style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.ExtraBold)
                Text(stringResource(R.string.hero_subtitle), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.88f))
                Button(onClick = onPlan, colors = ButtonDefaults.buttonColors(containerColor = Brand.Amber, contentColor = Brand.Forest)) {
                    Text(stringResource(R.string.action_plan_tomorrow), fontWeight = FontWeight.Bold)
                }
            }
            Text("🧺", fontSize = 64.sp, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun CategoryRow(go: (String) -> Unit) {
    val items = listOf(
        Triple("🥬", R.string.vegetables, Routes.VEGETABLES),
        Triple("🍎", R.string.fruits, Routes.addStock(mode = "vegetable")),
        Triple("🥛", R.string.dairy, Routes.FRIDGE),
        Triple("🌾", R.string.bc_grocery, Routes.INVENTORY),
        Triple("🌶️", R.string.cat_spices, Routes.PRODUCTS),
        Triple("🧴", R.string.bc_cleaning, Routes.PRODUCTS),
    )
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEach { (emoji, label, route) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp).clickable { go(route) }) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp, modifier = Modifier.size(64.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 28.sp) }
                }
                Spacer(Modifier.size(6.dp))
                Text(stringResource(label), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
