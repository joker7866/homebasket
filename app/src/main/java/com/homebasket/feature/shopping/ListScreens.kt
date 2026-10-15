package com.homebasket.feature.shopping

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.homebasket.R
import com.homebasket.core.data.BudgetRepository
import com.homebasket.core.data.CatalogRepository
import com.homebasket.core.data.Clock
import com.homebasket.core.data.DirectPurchaseLine
import com.homebasket.core.data.HouseholdRepository
import com.homebasket.core.data.InventoryRepository
import com.homebasket.core.data.ShoppingRepository
import com.homebasket.core.database.dao.ProductRow
import com.homebasket.core.database.entity.BudgetCategory
import com.homebasket.core.database.entity.ItemSource
import com.homebasket.core.database.entity.ItemState
import com.homebasket.core.database.entity.Priority
import com.homebasket.core.database.entity.PurchaseLineEntity
import com.homebasket.core.database.entity.ShoppingListItemEntity
import com.homebasket.core.designsystem.ConfirmDialog
import com.homebasket.core.designsystem.DateField
import com.homebasket.core.designsystem.DropdownField
import com.homebasket.core.designsystem.EmptyState
import com.homebasket.core.designsystem.HbCard
import com.homebasket.core.designsystem.HbScaffold
import com.homebasket.core.designsystem.InfoBanner
import com.homebasket.core.designsystem.LocalMessenger
import com.homebasket.core.designsystem.LocalStatusColors
import com.homebasket.core.designsystem.MoneyInput
import com.homebasket.core.designsystem.ProductPickerDialog
import com.homebasket.core.designsystem.QuantityInput
import com.homebasket.core.designsystem.SearchField
import com.homebasket.core.designsystem.SectionTitle
import com.homebasket.core.designsystem.Tone
import com.homebasket.core.designsystem.formatDate
import com.homebasket.core.designsystem.qty
import com.homebasket.core.domain.model.InputUnit
import com.homebasket.core.domain.model.Money
import com.homebasket.core.domain.model.Quantities
import com.homebasket.core.domain.model.UnitKind
import com.homebasket.core.pdf.DocumentService
import com.homebasket.core.ui.budgetCategoryLabel
import com.homebasket.core.ui.priorityLabel
import com.homebasket.core.ui.sourceLabel
import com.homebasket.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class ListViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val shopping: ShoppingRepository,
    private val catalog: CatalogRepository,
    private val documents: DocumentService,
    household: HouseholdRepository,
    budget: BudgetRepository,
    clock: Clock,
) : ViewModel() {
    val id: Long = savedState["id"] ?: 0L
    val list = shopping.observeList(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val items = shopping.observeItems(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val products = catalog.products.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val stores = household.household.map { h -> h.preferredStores.split(',').map { it.trim() }.filter { it.isNotEmpty() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val budget = budget.observe(YearMonth.from(clock.today())).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun addProduct(p: ProductRow, quantity: Double) = viewModelScope.launch { shopping.addProduct(id, p.product.id, quantity, ItemSource.MANUAL) }
    fun addCustom(name: String, quantity: Double, kind: UnitKind, category: BudgetCategory, estimate: Long?) =
        viewModelScope.launch { shopping.addCustom(id, name, quantity, kind, category, estimate, null) }

    fun update(item: ShoppingListItemEntity) = viewModelScope.launch { shopping.updateItem(item) }

    /** Ticking only marks an item as provisionally picked; stock/expense change on confirmation. */
    fun toggle(item: ShoppingListItemEntity) = viewModelScope.launch {
        if (item.state == ItemState.PURCHASED) return@launch
        shopping.setState(item, if (item.state == ItemState.PICKED) ItemState.PENDING else ItemState.PICKED)
    }

    fun delete(item: ShoppingListItemEntity, onDeleted: () -> Unit) = viewModelScope.launch { if (shopping.deleteItem(item)) onDeleted() }
    fun restore(item: ShoppingListItemEntity) = viewModelScope.launch { shopping.restoreItem(item) }

    fun rename(name: String, store: String?) = viewModelScope.launch { list.value?.let { shopping.renameList(it, name, store) } }
    fun deleteList(onDone: () -> Unit) = viewModelScope.launch { list.value?.let { shopping.deleteList(it) }; onDone() }

    fun confirmPurchase(store: String?, onDone: (com.homebasket.core.data.PurchaseResult?) -> Unit) = viewModelScope.launch { onDone(shopping.confirmPurchase(id, store)) }

    fun estimate(productId: Long?, quantity: Double, onResult: (Long?) -> Unit) = viewModelScope.launch {
        onResult(productId?.let { catalog.getProduct(it) }?.let { shopping.estimate(it, quantity) })
    }

    fun print(activity: android.content.Context) = viewModelScope.launch {
        val file = documents.shoppingList(id)
        documents.print(activity, file, list.value?.name ?: "HomeBasket")
    }

    fun share(activity: android.content.Context) = viewModelScope.launch { documents.share(activity, documents.shoppingList(id)) }
}

/** Editable list (Part 14/15): edit, save, shopping mode, print, share. */
@Composable
fun ListDetailScreen(go: (String) -> Unit, back: () -> Unit, vm: ListViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val list by vm.list.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val stores by vm.stores.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var addQtyFor by remember { mutableStateOf<ProductRow?>(null) }
    var customName by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ShoppingListItemEntity?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }

    val all = items.orEmpty()
    val picked = all.count { it.state == ItemState.PICKED }
    HbScaffold(
        title = list?.name ?: "", onBack = back,
        actions = {
            IconButton(onClick = { vm.print(context) }) { Icon(Icons.Default.Print, stringResource(R.string.print)) }
            IconButton(onClick = { vm.share(context) }) { Icon(Icons.Default.Share, stringResource(R.string.share_pdf)) }
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more)) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.rename_list)) }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; renaming = true })
                DropdownMenuItem(text = { Text(stringResource(R.string.delete_list)) }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; confirmDelete = true })
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { go(Routes.shoppingMode(vm.id)) }, icon = { Icon(Icons.Default.ShoppingCartCheckout, null) }, text = { Text(stringResource(R.string.shopping_mode)) })
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                val est = all.sumOf { it.estimatedPricePaise ?: 0L }
                val actual = all.filter { it.state == ItemState.PURCHASED }.sumOf { it.actualPricePaise ?: 0L }
                HbCard {
                    Text(stringResource(R.string.items_done, all.count { it.state == ItemState.PURCHASED || it.state == ItemState.UNAVAILABLE }, all.size), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.estimated_total) + ": " + Money.format(est), style = MaterialTheme.typography.bodyLarge)
                    if (actual > 0) Text(stringResource(R.string.actual_spent) + ": " + Money.format(actual), style = MaterialTheme.typography.bodyLarge)
                    list?.store?.let { Text(stringResource(R.string.store) + ": " + it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picking = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.add_item)) }
                    if (picked > 0) Button(onClick = { confirming = true }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.confirm_purchase_n, picked)) }
                }
            }
            if (items != null && all.isEmpty()) item { EmptyState(Icons.Default.ShoppingCartCheckout, stringResource(R.string.empty_list_title), stringResource(R.string.empty_list)) }
            all.groupBy { it.categoryName }.toSortedMap().forEach { (category, rows) ->
                item(key = "c_$category") { SectionTitle(category) }
                items(rows, key = { it.id }) { item ->
                    ItemRow(item, onToggle = { vm.toggle(item) }, onClick = { if (item.state != ItemState.PURCHASED) editing = item })
                }
            }
        }
    }

    if (picking) {
        ProductPickerDialog(products, onPick = { addQtyFor = it; picking = false }, onDismiss = { picking = false }, onCreateNew = { customName = it; picking = false },
            title = stringResource(R.string.add_item))
    }
    addQtyFor?.let { p ->
        QuantityPrompt(p.product.name, p.product.unitKind, p.product.countLabel, p.product.packSize, onDismiss = { addQtyFor = null }) { q ->
            vm.addProduct(p, q); addQtyFor = null
            messenger.show(context.getString(R.string.msg_added_to_list, p.product.name))
        }
    }
    customName?.let { name ->
        var text by remember { mutableStateOf("1") }
        var category by remember { mutableStateOf(BudgetCategory.HOUSEHOLD) }
        var price by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { customName = null },
            title = { Text(stringResource(R.string.custom_item, name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(text, { text = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text(stringResource(R.string.quantity)) }, singleLine = true)
                    DropdownField(stringResource(R.string.budget_category), BudgetCategory.entries, category, { stringResource(budgetCategoryLabel(it)) }, { category = it })
                    MoneyInput(price, { price = it }, stringResource(R.string.estimated_price))
                    Text(stringResource(R.string.custom_item_hint), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = (text.toDoubleOrNull() ?: 0.0) > 0, onClick = {
                    vm.addCustom(name, text.toDouble(), UnitKind.COUNT, category, Money.fromRupees(price)); customName = null
                }) { Text(stringResource(R.string.add)) }
            },
            dismissButton = { TextButton(onClick = { customName = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    editing?.let { item ->
        EditItemDialog(item, stores, vm, onDismiss = { editing = null }, onDelete = {
            vm.delete(item) { messenger.show(context.getString(R.string.msg_item_removed, item.name), context.getString(R.string.undo)) { vm.restore(item) } }
            editing = null
        })
    }
    if (renaming) {
        var name by remember { mutableStateOf(list?.name.orEmpty()) }
        var store by remember { mutableStateOf(list?.store.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.rename_list)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.list_name)) }, singleLine = true)
                    OutlinedTextField(store, { store = it }, label = { Text(stringResource(R.string.store)) }, singleLine = true)
                }
            },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { vm.rename(name, store); renaming = false }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(stringResource(R.string.delete_list), stringResource(R.string.delete_list_text), stringResource(R.string.delete),
            onConfirm = { vm.deleteList(back) }, onDismiss = { confirmDelete = false }, destructive = true)
    }
    if (confirming) ConfirmPurchaseDialog(all, list?.store, stores, onDismiss = { confirming = false }) { store ->
        vm.confirmPurchase(store) { r ->
            r?.let { messenger.show(context.getString(R.string.msg_purchase_recorded, it.lineCount, Money.format(it.totalPaise))) }
        }
        confirming = false
    }
}

@Composable
private fun ItemRow(item: ShoppingListItemEntity, onToggle: () -> Unit, onClick: () -> Unit) {
    var showReason by remember { mutableStateOf(false) }
    val done = item.state == ItemState.PURCHASED
    ListItem(
        leadingContent = {
            Checkbox(checked = item.state == ItemState.PICKED || done, onCheckedChange = { onToggle() }, enabled = !done && item.state != ItemState.UNAVAILABLE)
        },
        headlineContent = {
            Text(
                item.substituteName?.let { "$it (${item.name})" } ?: item.name,
                textDecoration = if (done || item.state == ItemState.UNAVAILABLE) TextDecoration.LineThrough else null,
                fontWeight = if (item.priority == Priority.HIGH) FontWeight.Bold else FontWeight.Normal,
            )
        },
        supportingContent = {
            Column {
                val status = when (item.state) {
                    ItemState.PICKED -> stringResource(R.string.state_picked)
                    ItemState.UNAVAILABLE -> stringResource(R.string.state_unavailable)
                    ItemState.PURCHASED -> stringResource(R.string.state_purchased)
                    ItemState.PENDING -> null
                }
                Text(listOfNotNull(status, stringResource(sourceLabel(item.source)), item.store, item.notes).joinToString(" • "), style = MaterialTheme.typography.bodySmall)
                item.reason?.let { r ->
                    Text(if (showReason) r else stringResource(R.string.why), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(role = Role.Button) { showReason = !showReason })
                }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(qty(item.actualQuantity ?: item.quantity, item.unitKind, item.countLabel), style = MaterialTheme.typography.titleSmall)
                (if (done) item.actualPricePaise else item.actualPricePaise ?: item.estimatedPricePaise)?.let {
                    Text((if (done || item.actualPricePaise != null) "" else "≈ ") + Money.format(it), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider()
}

@Composable
fun QuantityPrompt(title: String, kind: UnitKind, countLabel: String, start: Double, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var unit by remember { mutableStateOf(InputUnit.bestFor(kind, start)) }
    var text by remember { mutableStateOf(Quantities.formatNumber(start / unit.factor)) }
    val value = Quantities.parse(text)?.let { unit.toBase(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { QuantityInput(text, { text = it }, unit, { unit = it }, kind, countLabel) },
        confirmButton = { TextButton(enabled = value != null && value > 0, onClick = { onConfirm(value!!) }) { Text(stringResource(R.string.add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun EditItemDialog(item: ShoppingListItemEntity, stores: List<String>, vm: ListViewModel, onDismiss: () -> Unit, onDelete: () -> Unit) {
    var unit by remember { mutableStateOf(InputUnit.bestFor(item.unitKind, item.quantity)) }
    var text by remember { mutableStateOf(Quantities.formatNumber(item.quantity / unit.factor)) }
    var price by remember { mutableStateOf(Money.toEditable(item.estimatedPricePaise)) }
    var store by remember { mutableStateOf(item.store.orEmpty()) }
    var priority by remember { mutableStateOf(item.priority) }
    var notes by remember { mutableStateOf(item.notes.orEmpty()) }
    val value = Quantities.parse(text)?.let { unit.toBase(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QuantityInput(text, { v ->
                    text = v
                    Quantities.parse(v)?.let { q -> vm.estimate(item.productId, unit.toBase(q)) { est -> if (est != null) price = Money.toEditable(est) } }
                }, unit, { unit = it }, item.unitKind, item.countLabel)
                MoneyInput(price, { price = it }, stringResource(R.string.estimated_price))
                DropdownField(stringResource(R.string.store), (stores + store).filter { it.isNotBlank() }.distinct() + listOf(""), store, { it.ifEmpty { "—" } }, { store = it })
                DropdownField(stringResource(R.string.priority), Priority.entries, priority, { stringResource(priorityLabel(it)) }, { priority = it })
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.notes)) })
                TextButton(onClick = onDelete) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.remove_from_list), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = value != null && value > 0, onClick = {
                vm.update(item.copy(quantity = value!!, estimatedPricePaise = Money.fromRupees(price), store = store.ifBlank { null }, priority = priority, notes = notes.ifBlank { null }))
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ConfirmPurchaseDialog(items: List<ShoppingListItemEntity>, listStore: String?, stores: List<String>, onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    val picked = items.filter { it.state == ItemState.PICKED }
    var store by remember { mutableStateOf(listStore.orEmpty()) }
    val total = picked.sumOf { it.actualPricePaise ?: it.estimatedPricePaise ?: 0L }
    val noPrice = picked.count { it.actualPricePaise == null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_purchase_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.confirm_purchase_text, picked.size, Money.format(total)))
                if (noPrice > 0) Text(stringResource(R.string.confirm_purchase_estimates, noPrice), style = MaterialTheme.typography.bodySmall, color = LocalStatusColors.current.warning)
                DropdownField(stringResource(R.string.store), (stores + store).filter { it.isNotBlank() }.distinct() + listOf(""), store, { it.ifEmpty { "—" } }, { store = it })
                Text(stringResource(R.string.confirm_purchase_once), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(store.ifBlank { null }) }) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Distraction-free Shopping Mode (Part 16). Progress is saved after every tap, so a trip can be continued later. */
@Composable
fun ShoppingModeScreen(back: () -> Unit, vm: ListViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val list by vm.list.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val stores by vm.stores.collectAsStateWithLifecycle()
    val budget by vm.budget.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var detail by remember { mutableStateOf<ShoppingListItemEntity?>(null) }
    var confirming by remember { mutableStateOf(false) }
    val all = items.orEmpty()
    val running = all.filter { it.state == ItemState.PICKED }.sumOf { it.actualPricePaise ?: it.estimatedPricePaise ?: 0L }
    val remaining = budget?.takeIf { it.monthlyBudgetPaise > 0 }?.let { it.remainingPaise - running }

    HbScaffold(
        title = list?.name ?: stringResource(R.string.shopping_mode), onBack = back,
        bottomBar = {
            BottomAppBar {
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(stringResource(R.string.running_total, Money.format(running)), style = MaterialTheme.typography.titleMedium)
                    remaining?.let { Text(stringResource(R.string.budget_left_after, Money.format(it)), style = MaterialTheme.typography.bodySmall, color = if (it < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Button(onClick = { confirming = true }, enabled = all.any { it.state == ItemState.PICKED }, modifier = Modifier.padding(end = 8.dp)) { Text(stringResource(R.string.finish)) }
            }
        },
    ) { padding ->
        val open = all.filter { it.state != ItemState.PURCHASED && (query.isBlank() || it.name.contains(query, true)) }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SearchField(query, { query = it }, stringResource(R.string.search_list)) }
            item { InfoBanner(stringResource(R.string.shopping_mode_hint)) }
            open.groupBy { it.categoryName }.toSortedMap().forEach { (category, rows) ->
                item(key = "c_$category") { SectionTitle(category) }
                items(rows, key = { it.id }) { item ->
                    val picked = item.state == ItemState.PICKED
                    val unavailable = item.state == ItemState.UNAVAILABLE
                    HbCard(onClick = { detail = item }, container = if (picked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(picked, { vm.toggle(item) }, enabled = !unavailable, modifier = Modifier.size(48.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.substituteName ?: item.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                                    textDecoration = if (unavailable) TextDecoration.LineThrough else null)
                                Text(
                                    listOfNotNull(
                                        qty(item.actualQuantity ?: item.quantity, item.unitKind, item.countLabel),
                                        (item.actualPricePaise ?: item.estimatedPricePaise)?.let { (if (item.actualPricePaise == null) "≈ " else "") + Money.format(it) },
                                        if (unavailable) stringResource(R.string.state_unavailable) else null,
                                    ).joinToString(" • "),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                }
            }
            val purchased = all.filter { it.state == ItemState.PURCHASED }
            if (purchased.isNotEmpty()) item { Text(stringResource(R.string.already_purchased_n, purchased.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    detail?.let { item ->
        var unit by remember { mutableStateOf(InputUnit.bestFor(item.unitKind, item.actualQuantity ?: item.quantity)) }
        var text by remember { mutableStateOf(Quantities.formatNumber((item.actualQuantity ?: item.quantity) / unit.factor)) }
        var price by remember { mutableStateOf(Money.toEditable(item.actualPricePaise)) }
        var unavailable by remember { mutableStateOf(item.state == ItemState.UNAVAILABLE) }
        var substitute by remember { mutableStateOf(item.substituteName.orEmpty()) }
        val value = Quantities.parse(text)?.let { unit.toBase(it) }
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(item.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuantityInput(text, { text = it }, unit, { unit = it }, item.unitKind, item.countLabel, label = stringResource(R.string.actual_quantity))
                    MoneyInput(price, { price = it }, stringResource(R.string.actual_price))
                    OutlinedTextField(substitute, { substitute = it }, label = { Text(stringResource(R.string.substitute)) }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.not_available_in_shop), modifier = Modifier.weight(1f))
                        Switch(unavailable, { unavailable = it })
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = value != null && value > 0, onClick = {
                    vm.update(item.copy(
                        actualQuantity = value, actualPricePaise = Money.fromRupees(price), substituteName = substitute.ifBlank { null },
                        state = if (unavailable) ItemState.UNAVAILABLE else ItemState.PICKED,
                    ))
                    detail = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { detail = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirming) ConfirmPurchaseDialog(all, list?.store, stores, onDismiss = { confirming = false }) { store ->
        vm.confirmPurchase(store) { r ->
            r?.let { messenger.show(context.getString(R.string.msg_purchase_recorded, it.lineCount, Money.format(it.totalPaise))) }
            back()
        }
        confirming = false
    }
}

@HiltViewModel
class PurchaseViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val shopping: ShoppingRepository,
    catalog: CatalogRepository,
    inventory: InventoryRepository,
    household: HouseholdRepository,
    clock: Clock,
) : ViewModel() {
    val id: Long = savedState["id"] ?: 0L
    val today = clock.today().toEpochDay()
    val purchases = shopping.observePurchases(200).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val lines = (if (id != 0L) shopping.observePurchaseLines(id) else emptyFlow()).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val products = catalog.products.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val locations = inventory.locations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val stores = household.household.map { h -> h.preferredStores.split(',').map { it.trim() }.filter { it.isNotEmpty() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun record(store: String?, day: Long, note: String?, lines: List<DirectPurchaseLine>, onDone: (com.homebasket.core.data.PurchaseResult?) -> Unit) =
        viewModelScope.launch { onDone(shopping.recordDirectPurchase(store, day, note, lines)) }

    fun returnLine(line: PurchaseLineEntity, qty: Double, refund: Long, removeFromStock: Boolean, onDone: (Boolean) -> Unit) =
        viewModelScope.launch { onDone(shopping.returnLine(line.id, qty, refund, removeFromStock)) }

    fun correct(line: PurchaseLineEntity, amount: Long) = viewModelScope.launch { shopping.correctAmount(line.id, amount) }
    suspend fun returnable(line: PurchaseLineEntity) = line.quantity - shopping.returnedQuantity(line.id)
}

/** Record a purchase made without a list (Part 16/17). */
@Composable
fun RecordPurchaseScreen(back: () -> Unit, vm: PurchaseViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val products by vm.products.collectAsStateWithLifecycle()
    val stores by vm.stores.collectAsStateWithLifecycle()
    val locations by vm.locations.collectAsStateWithLifecycle()
    var store by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableStateOf(vm.today) }
    val lines = remember { mutableStateListOf<DirectPurchaseLine>() }
    var picking by remember { mutableStateOf(false) }
    var lineFor by remember { mutableStateOf<ProductRow?>(null) }
    var customName by remember { mutableStateOf<String?>(null) }

    HbScaffold(title = stringResource(R.string.action_record_purchase), onBack = back) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DropdownField(stringResource(R.string.store), (stores + store).filter { it.isNotBlank() }.distinct() + listOf(""), store, { it.ifEmpty { "—" } }, { store = it })
            DateField(stringResource(R.string.purchase_date), day, { it?.let { d -> day = d } }, allowClear = false)
            SectionTitle(stringResource(R.string.items_bought), action = { TextButton(onClick = { picking = true }) { Icon(Icons.Default.Add, null); Text(stringResource(R.string.add)) } })
            if (lines.isEmpty()) Text(stringResource(R.string.no_items_added), color = MaterialTheme.colorScheme.onSurfaceVariant)
            lines.forEachIndexed { i, l ->
                ListItem(
                    headlineContent = { Text(l.name) },
                    supportingContent = { Text(qty(l.quantity, l.unitKind, l.countLabel) + " • " + stringResource(budgetCategoryLabel(l.budgetCategory))) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Money.format(l.amountPaise))
                            IconButton(onClick = { lines.removeAt(i) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                        }
                    },
                )
            }
            Text(stringResource(R.string.total) + ": " + Money.format(lines.sumOf { it.amountPaise }), style = MaterialTheme.typography.titleMedium)
            Button(onClick = {
                vm.record(store.ifBlank { null }, day, null, lines.toList()) { r ->
                    r?.let { messenger.show(context.getString(R.string.msg_purchase_recorded, it.lineCount, Money.format(it.totalPaise))) }
                    back()
                }
            }, enabled = lines.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.save_purchase)) }
            Text(stringResource(R.string.record_purchase_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (picking) ProductPickerDialog(products, onPick = { lineFor = it; picking = false }, onDismiss = { picking = false }, onCreateNew = { customName = it; picking = false })
    lineFor?.let { p ->
        var unit by remember { mutableStateOf(InputUnit.bestFor(p.product.unitKind, p.product.packSize)) }
        var text by remember { mutableStateOf(Quantities.formatNumber(p.product.packSize / unit.factor)) }
        var price by remember { mutableStateOf("") }
        var locationId by remember { mutableStateOf(p.product.defaultLocationId ?: locations.firstOrNull()?.id ?: 0L) }
        val value = Quantities.parse(text)?.let { unit.toBase(it) }
        AlertDialog(
            onDismissRequest = { lineFor = null },
            title = { Text(p.product.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuantityInput(text, { text = it }, unit, { unit = it }, p.product.unitKind, p.product.countLabel)
                    MoneyInput(price, { price = it }, stringResource(R.string.price_paid))
                    if (p.budgetCategory != BudgetCategory.CLOTHING) DropdownField(stringResource(R.string.storage_place), locations, locations.firstOrNull { it.id == locationId }, { it.name }, { locationId = it.id })
                }
            },
            confirmButton = {
                TextButton(enabled = value != null && value > 0, onClick = {
                    lines += DirectPurchaseLine(p.product.id, p.product.name, value!!, p.product.unitKind, p.product.countLabel, Money.fromRupees(price) ?: 0, p.budgetCategory, locationId.takeIf { it != 0L }, null)
                    lineFor = null
                }) { Text(stringResource(R.string.add)) }
            },
            dismissButton = { TextButton(onClick = { lineFor = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    customName?.let { name ->
        var text by remember { mutableStateOf("1") }
        var price by remember { mutableStateOf("") }
        var category by remember { mutableStateOf(BudgetCategory.HOUSEHOLD) }
        AlertDialog(
            onDismissRequest = { customName = null },
            title = { Text(stringResource(R.string.custom_item, name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(text, { text = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text(stringResource(R.string.quantity)) }, singleLine = true)
                    MoneyInput(price, { price = it }, stringResource(R.string.price_paid))
                    DropdownField(stringResource(R.string.budget_category), BudgetCategory.entries, category, { stringResource(budgetCategoryLabel(it)) }, { category = it })
                    Text(stringResource(R.string.custom_item_hint), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = (text.toDoubleOrNull() ?: 0.0) > 0, onClick = {
                    lines += DirectPurchaseLine(null, name, text.toDouble(), UnitKind.COUNT, "pcs", Money.fromRupees(price) ?: 0, category, null, null)
                    customName = null
                }) { Text(stringResource(R.string.add)) }
            },
            dismissButton = { TextButton(onClick = { customName = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun PurchasesScreen(go: (String) -> Unit, back: () -> Unit, vm: PurchaseViewModel = hiltViewModel()) {
    val purchases by vm.purchases.collectAsStateWithLifecycle()
    HbScaffold(title = stringResource(R.string.purchase_history), onBack = back) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            if (purchases != null && purchases!!.isEmpty()) item { EmptyState(Icons.Default.ShoppingCartCheckout, stringResource(R.string.no_purchases_title), stringResource(R.string.no_purchases)) }
            items(purchases.orEmpty(), key = { it.id }) { p ->
                ListItem(
                    headlineContent = { Text(listOfNotNull(p.store, p.note).joinToString(" • ").ifEmpty { stringResource(R.string.purchase) }) },
                    supportingContent = { Text(formatDate(p.epochDay) + " • " + stringResource(R.string.n_items, p.lineCount)) },
                    trailingContent = { Text(Money.format(p.totalPaise), style = MaterialTheme.typography.titleSmall, color = if (p.totalPaise < 0) LocalStatusColors.current.ok else MaterialTheme.colorScheme.onSurface) },
                    modifier = Modifier.clickable { go(Routes.purchaseDetail(p.id)) },
                )
                HorizontalDivider()
            }
        }
    }
}

/** Purchase lines with returns and amount corrections (originals are never edited). */
@Composable
fun PurchaseDetailScreen(back: () -> Unit, vm: PurchaseViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val lines by vm.lines.collectAsStateWithLifecycle()
    var returning by remember { mutableStateOf<Pair<PurchaseLineEntity, Double>?>(null) }
    var correcting by remember { mutableStateOf<PurchaseLineEntity?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    HbScaffold(title = stringResource(R.string.purchase), onBack = back) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item { Text(stringResource(R.string.total) + ": " + Money.format(lines.sumOf { it.amountPaise }), style = MaterialTheme.typography.titleLarge) }
            items(lines, key = { it.id }) { l ->
                var menu by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text(l.name) },
                    supportingContent = {
                        Text(listOfNotNull(
                            if (l.quantity != 0.0) qty(l.quantity, l.unitKind, l.countLabel) else null,
                            stringResource(budgetCategoryLabel(l.budgetCategory)),
                            if (l.returnOfLineId != null) stringResource(if (l.quantity < 0) R.string.tx_return else R.string.correction) else null,
                            if (!l.addedToInventory && l.returnOfLineId == null) stringResource(R.string.expense_only) else null,
                        ).joinToString(" • "))
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Money.format(l.amountPaise))
                            if (l.returnOfLineId == null && l.quantity > 0) {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more)) }
                                DropdownMenu(menu, { menu = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.record_return)) }, onClick = {
                                        menu = false
                                        scope.launch { returning = l to vm.returnable(l) }
                                    })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.correct_amount)) }, onClick = { menu = false; correcting = l })
                                }
                            }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
    returning?.let { (line, max) ->
        var unit by remember { mutableStateOf(InputUnit.bestFor(line.unitKind, max)) }
        var text by remember { mutableStateOf(Quantities.formatNumber(max / unit.factor)) }
        var refund by remember { mutableStateOf(Money.toEditable(line.amountPaise)) }
        var fromStock by remember { mutableStateOf(line.addedToInventory) }
        val value = Quantities.parse(text)?.let { unit.toBase(it) }
        AlertDialog(
            onDismissRequest = { returning = null },
            title = { Text(stringResource(R.string.record_return)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.returnable, qty(max, line.unitKind, line.countLabel)))
                    QuantityInput(text, { text = it }, unit, { unit = it }, line.unitKind, line.countLabel)
                    MoneyInput(refund, { refund = it }, stringResource(R.string.refund_amount))
                    if (line.addedToInventory) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.remove_from_stock), modifier = Modifier.weight(1f)); Switch(fromStock, { fromStock = it })
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = value != null && value > 0 && value <= max + 1e-6, onClick = {
                    vm.returnLine(line, value!!, Money.fromRupees(refund) ?: 0, fromStock) { ok -> if (ok) messenger.show(context.getString(R.string.msg_return_recorded)) }
                    returning = null
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { returning = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    correcting?.let { line ->
        var amount by remember { mutableStateOf(Money.toEditable(line.amountPaise)) }
        AlertDialog(
            onDismissRequest = { correcting = null },
            title = { Text(stringResource(R.string.correct_amount)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MoneyInput(amount, { amount = it }, stringResource(R.string.correct_total_paid))
                    Text(stringResource(R.string.correction_hint), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { Money.fromRupees(amount)?.let { vm.correct(line, it) }; correcting = null }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { correcting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
