package com.homebasket.feature.inventory

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.homebasket.R
import com.homebasket.core.data.Clock
import com.homebasket.core.data.InventoryRepository
import com.homebasket.core.database.dao.LotRow
import com.homebasket.core.database.entity.FoodCondition
import com.homebasket.core.designsystem.EmptyState
import com.homebasket.core.designsystem.HbCard
import com.homebasket.core.designsystem.HbScaffold
import com.homebasket.core.designsystem.LocalStatusColors
import com.homebasket.core.designsystem.SearchField
import com.homebasket.core.designsystem.qty
import com.homebasket.core.ui.conditionLabel
import com.homebasket.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class InventoryViewModel @Inject constructor(inventory: InventoryRepository, clock: Clock) : ViewModel() {
    val today = clock.today().toEpochDay()
    val lots = inventory.lots.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val locations = inventory.locations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Inventory tab: everything at home, grouped by product, with per-place breakdown. */
@Composable
fun InventoryScreen(go: (String) -> Unit, vm: InventoryViewModel = hiltViewModel()) {
    val lots by vm.lots.collectAsStateWithLifecycle()
    val locations by vm.locations.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var locationId by rememberSaveable { mutableStateOf<Long?>(null) }

    HbScaffold(
        title = stringResource(R.string.tab_inventory),
        actions = {
            IconButton(onClick = { go(Routes.scan("inventory")) }) { Icon(Icons.Default.QrCodeScanner, stringResource(R.string.action_scan)) }
            IconButton(onClick = { go(Routes.PRODUCTS) }) { Icon(Icons.Default.ListAlt, stringResource(R.string.products)) }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { go(Routes.addStock(locationId = locationId ?: 0)) }, icon = { Icon(Icons.Default.Add, null) }, text = { Text(stringResource(R.string.add_stock)) })
        },
    ) { padding ->
        val all = lots
        val filtered = remember(all, query, locationId) {
            all.orEmpty().filter { l ->
                (locationId == null || l.lot.locationId == locationId) &&
                    (query.isBlank() || l.productName.contains(query, true) || l.productLocalName?.contains(query, true) == true || l.categoryName.contains(query, true))
            }
        }
        val grouped = remember(filtered) { filtered.groupBy { it.lot.productId }.values.sortedBy { it.first().productName.lowercase() } }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { go(Routes.FRIDGE) }, label = { Text(stringResource(R.string.fridge)) }, leadingIcon = { Icon(Icons.Default.Kitchen, null) })
                    AssistChip(onClick = { go(Routes.VEGETABLES) }, label = { Text(stringResource(R.string.vegetables)) }, leadingIcon = { Icon(Icons.Default.Eco, null) })
                    AssistChip(onClick = { go(Routes.EXPIRING) }, label = { Text(stringResource(R.string.expiring_soon)) }, leadingIcon = { Icon(Icons.Default.Timer, null) })
                    AssistChip(onClick = { go(Routes.WASTE) }, label = { Text(stringResource(R.string.waste_log)) }, leadingIcon = { Icon(Icons.Default.DeleteSweep, null) })
                    AssistChip(onClick = { go(Routes.HISTORY) }, label = { Text(stringResource(R.string.stock_history)) }, leadingIcon = { Icon(Icons.Default.History, null) })
                }
            }
            item { SearchField(query, { query = it }, stringResource(R.string.search_stock)) }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = locationId == null, onClick = { locationId = null }, label = { Text(stringResource(R.string.all_places)) })
                    locations.forEach { l -> FilterChip(selected = locationId == l.id, onClick = { locationId = l.id }, label = { Text(l.name) }) }
                }
            }
            if (all != null && grouped.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Default.Inventory2, stringResource(R.string.empty_inventory_title),
                        stringResource(if (query.isBlank()) R.string.empty_inventory else R.string.no_results),
                        actionLabel = stringResource(R.string.add_opening_stock), onAction = { go(Routes.addStock(mode = "opening")) },
                    )
                }
            }
            items(grouped, key = { it.first().lot.productId }) { productLots ->
                ProductStockCard(productLots, vm.today) { go(Routes.productDetail(productLots.first().lot.productId)) }
            }
        }
    }
}

@Composable
fun ProductStockCard(lots: List<LotRow>, today: Long, onClick: () -> Unit) {
    val first = lots.first()
    val usable = lots.filter { it.lot.condition != FoodCondition.SPOILED && (it.lot.expiryEpochDay == null || it.lot.expiryEpochDay >= today) }
    val total = usable.sumOf { it.lot.quantity }
    val flagged = lots.any { it.lot.condition == FoodCondition.SPOILED || it.lot.condition == FoodCondition.USE_SOON || (it.lot.expiryEpochDay != null && it.lot.expiryEpochDay < today + 2) }
    HbCard(onClick = onClick) {
        Row {
            Column(Modifier.weight(1f)) {
                Text(first.productName, style = MaterialTheme.typography.titleMedium)
                first.productLocalName?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(qty(total, first.unitKind, first.countLabel), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        lots.groupBy { it.locationName }.forEach { (place, rows) ->
            Text(
                "$place: " + qty(rows.sumOf { it.lot.quantity }, first.unitKind, first.countLabel) +
                    rows.firstOrNull { it.lot.condition != FoodCondition.FRESH }?.let { " • " + stringResource(conditionLabel(it.lot.condition)) }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (flagged) LocalStatusColors.current.warning else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
