package com.homebasket.feature.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.homebasket.R
import com.homebasket.core.data.CatalogRepository
import com.homebasket.core.data.Clock
import com.homebasket.core.data.DirectPurchaseLine
import com.homebasket.core.data.HouseholdRepository
import com.homebasket.core.data.InventoryRepository
import com.homebasket.core.data.ShoppingRepository
import com.homebasket.core.database.dao.ProductRow
import com.homebasket.core.database.entity.BudgetCategory
import com.homebasket.core.database.entity.ExpiryType
import com.homebasket.core.database.entity.TxType
import com.homebasket.core.designsystem.DateField
import com.homebasket.core.designsystem.DropdownField
import com.homebasket.core.designsystem.HbScaffold
import com.homebasket.core.designsystem.InfoBanner
import com.homebasket.core.designsystem.LocalMessenger
import com.homebasket.core.designsystem.MoneyInput
import com.homebasket.core.designsystem.ProductPickerDialog
import com.homebasket.core.designsystem.QuantityInput
import com.homebasket.core.designsystem.qty
import com.homebasket.core.domain.model.InputUnit
import com.homebasket.core.domain.model.Money
import com.homebasket.core.domain.model.Quantities
import com.homebasket.core.ui.expiryTypeLabel
import com.homebasket.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddStockViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val catalog: CatalogRepository,
    private val inventory: InventoryRepository,
    private val shopping: ShoppingRepository,
    household: HouseholdRepository,
    private val clock: Clock,
) : ViewModel() {
    val mode: String = savedState["mode"] ?: "grocery"
    private val initialProduct: Long = savedState["productId"] ?: 0L
    val initialLocation: Long = savedState["locationId"] ?: 0L
    val today = clock.today().toEpochDay()

    val products = catalog.products.map { list ->
        if (mode == "vegetable") list.filter { it.budgetCategory == BudgetCategory.VEGETABLES || it.budgetCategory == BudgetCategory.FRUITS } else list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val locations = inventory.locations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val stores = household.household.map { h -> h.preferredStores.split(',').map { it.trim() }.filter { it.isNotEmpty() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selected = MutableStateFlow<ProductRow?>(null)

    init {
        if (initialProduct != 0L) viewModelScope.launch { selected.value = catalog.getProductRow(initialProduct) }
    }

    suspend fun estimateExpiry(productId: Long, fromDay: Long) = inventory.estimatedExpiry(productId, fromDay)

    /** Saves either as opening/adjusting stock, or as a purchase (expense + stock, recorded once). */
    fun save(
        product: ProductRow, quantity: Double, locationId: Long, isPurchase: Boolean, pricePaise: Long?, store: String?,
        purchaseDay: Long, expiry: Long?, expiryType: ExpiryType, onDone: (Long?) -> Unit,
    ) = viewModelScope.launch {
        if (isPurchase) {
            shopping.recordDirectPurchase(
                store, purchaseDay, null,
                listOf(DirectPurchaseLine(product.product.id, product.product.name, quantity, product.product.unitKind, product.product.countLabel, pricePaise ?: 0, product.budgetCategory, locationId, expiry)),
            )
            onDone(null)
        } else {
            val lotId = inventory.addStock(
                product.product.id, locationId, quantity, if (mode == "opening") TxType.OPENING else TxType.ADJUST,
                purchaseDay = purchaseDay, expiryDay = expiry, expiryType = expiryType,
            )
            onDone(lotId)
        }
    }

    fun undo(lotId: Long, note: String) = viewModelScope.launch { inventory.correctQuantity(lotId, 0.0, note) }
}

@Composable
fun AddStockScreen(go: (String) -> Unit, back: () -> Unit, vm: AddStockViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val products by vm.products.collectAsStateWithLifecycle()
    val locations by vm.locations.collectAsStateWithLifecycle()
    val stores by vm.stores.collectAsStateWithLifecycle()
    val product by vm.selected.collectAsStateWithLifecycle()

    var picking by rememberSaveable { mutableStateOf(false) }
    var amount by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(InputUnit.KG) }
    var locationId by rememberSaveable { mutableStateOf(vm.initialLocation) }
    var isPurchase by rememberSaveable { mutableStateOf(vm.mode != "opening") }
    var price by rememberSaveable { mutableStateOf("") }
    var store by rememberSaveable { mutableStateOf("") }
    var purchaseDay by rememberSaveable { mutableStateOf(vm.today) }
    var expiry by rememberSaveable { mutableStateOf<Long?>(null) }
    var expiryType by rememberSaveable { mutableStateOf(ExpiryType.ESTIMATE) }

    LaunchedEffect(product?.product?.id, purchaseDay) {
        val p = product ?: return@LaunchedEffect
        if (unit.kind != p.product.unitKind) unit = InputUnit.bestFor(p.product.unitKind, p.product.packSize)
        if (locationId == 0L || locations.none { it.id == locationId }) locationId = p.product.defaultLocationId ?: locations.firstOrNull()?.id ?: 0L
        if (expiryType == ExpiryType.ESTIMATE) expiry = vm.estimateExpiry(p.product.id, purchaseDay)
        if (store.isEmpty()) store = p.product.preferredStore.orEmpty()
    }

    val title = stringResource(
        when (vm.mode) {
            "vegetable" -> R.string.action_add_vegetable
            "opening" -> R.string.add_opening_stock
            else -> R.string.action_add_grocery
        }
    )
    val baseQty = Quantities.parse(amount)?.let { unit.toBase(it) }
    val canSave = product != null && baseQty != null && baseQty > 0 && locationId != 0L

    HbScaffold(title = title, onBack = back, actions = {
        IconButton(onClick = { go(Routes.scan("inventory")) }) { Icon(Icons.Default.QrCodeScanner, stringResource(R.string.action_scan)) }
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedCard(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(product?.product?.name ?: stringResource(R.string.choose_product), style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text(product?.categoryName ?: stringResource(R.string.tap_to_choose)) },
                    trailingContent = { Icon(Icons.Default.Edit, null) },
                )
            }
            product?.let { p ->
                QuantityInput(amount, { amount = it }, unit, { unit = it }, p.product.unitKind, p.product.countLabel)
                DropdownField(stringResource(R.string.storage_place), locations, locations.firstOrNull { it.id == locationId }, { it.name }, { locationId = it.id })

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.record_as_purchase), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.record_as_purchase_hint), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = isPurchase, onCheckedChange = { isPurchase = it })
                }
                if (isPurchase) {
                    MoneyInput(price, { price = it }, stringResource(R.string.price_paid))
                    DropdownField(stringResource(R.string.store), stores + listOf(""), store, { it.ifEmpty { "—" } }, { store = it })
                    DateField(stringResource(R.string.purchase_date), purchaseDay, { it?.let { d -> purchaseDay = d } }, allowClear = false)
                }
                DateField(stringResource(R.string.expiry_date), expiry, { expiry = it; if (it != null && expiryType == ExpiryType.ESTIMATE) expiryType = ExpiryType.BEST_BEFORE })
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ExpiryType.entries.forEachIndexed { i, t ->
                        SegmentedButton(selected = expiryType == t, onClick = { expiryType = t }, shape = SegmentedButtonDefaults.itemShape(i, ExpiryType.entries.size)) {
                            Text(stringResource(expiryTypeLabel(t)), maxLines = 1)
                        }
                    }
                }
                InfoBanner(stringResource(R.string.expiry_disclaimer))
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    val p = product ?: return@Button
                    val q = baseQty ?: return@Button
                    vm.save(p, q, locationId, isPurchase, Money.fromRupees(price), store.ifEmpty { null }, purchaseDay, expiry, expiryType) { lotId ->
                        val place = locations.firstOrNull { it.id == locationId }?.name.orEmpty()
                        val msg = context.getString(R.string.msg_added, qty(q, p.product.unitKind, p.product.countLabel), p.product.name, place)
                        if (lotId != null) messenger.show(msg, context.getString(R.string.undo)) { vm.undo(lotId, context.getString(R.string.undo)) }
                        else messenger.show(msg)
                        amount = ""; price = ""
                        vm.selected.value = null
                    }
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(stringResource(R.string.save)) }
        }
    }

    if (picking) {
        ProductPickerDialog(
            products, onPick = { vm.selected.value = it; picking = false }, onDismiss = { picking = false },
            onCreateNew = { name -> picking = false; go(Routes.productEdit(name = name)) },
        )
    }
}
