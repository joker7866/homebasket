package com.homebasket.feature.products

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.homebasket.R
import com.homebasket.core.data.CatalogRepository
import com.homebasket.core.data.Clock
import com.homebasket.core.data.InventoryRepository
import com.homebasket.core.database.HomeBasketDatabase
import com.homebasket.core.database.dao.LotRow
import com.homebasket.core.database.entity.ProductEntity
import com.homebasket.core.database.entity.ProductVariantEntity
import com.homebasket.core.database.entity.PurchaseFrequency
import com.homebasket.core.database.entity.RecurringPurchaseRuleEntity
import com.homebasket.core.designsystem.DropdownField
import com.homebasket.core.designsystem.EmptyState
import com.homebasket.core.designsystem.HbCard
import com.homebasket.core.designsystem.HbScaffold
import com.homebasket.core.designsystem.LocalMessenger
import com.homebasket.core.designsystem.MoneyInput
import com.homebasket.core.designsystem.QuantityInput
import com.homebasket.core.designsystem.SearchField
import com.homebasket.core.designsystem.SectionTitle
import com.homebasket.core.designsystem.formatDate
import com.homebasket.core.designsystem.qty
import com.homebasket.core.domain.model.InputUnit
import com.homebasket.core.domain.model.Money
import com.homebasket.core.domain.model.Quantities
import com.homebasket.core.domain.model.UnitKind
import com.homebasket.core.ui.conditionLabel
import com.homebasket.core.ui.frequencyLabel
import com.homebasket.core.ui.txTypeLabel
import com.homebasket.feature.inventory.LotActionSheet
import com.homebasket.feature.shopping.AddToListDialog
import com.homebasket.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductListViewModel @Inject constructor(catalog: CatalogRepository) : ViewModel() {
    val products = catalog.products.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun ProductListScreen(go: (String) -> Unit, back: () -> Unit, vm: ProductListViewModel = hiltViewModel()) {
    val products by vm.products.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    HbScaffold(
        title = stringResource(R.string.products), onBack = back,
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { go(Routes.productEdit()) }, icon = { Icon(Icons.Default.Add, null) }, text = { Text(stringResource(R.string.new_product)) }) },
    ) { padding ->
        val filtered = remember(products, query) {
            products.orEmpty().filter { query.isBlank() || it.product.name.contains(query, true) || it.product.localName?.contains(query, true) == true || it.categoryName.contains(query, true) }
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp)) {
            item { SearchField(query, { query = it }, stringResource(R.string.search_products)) }
            filtered.groupBy { it.categoryName }.forEach { (category, rows) ->
                item(key = "c_$category") { SectionTitle(category) }
                items(rows, key = { it.product.id }) { row ->
                    ListItem(
                        headlineContent = { Text(row.product.name) },
                        supportingContent = {
                            Text(listOfNotNull(row.product.localName, row.product.brand, qty(row.product.packSize, row.product.unitKind, row.product.countLabel),
                                row.product.lastPricePaise?.let { Money.format(it) }).joinToString(" • "))
                        },
                        modifier = Modifier.clickable { go(Routes.productDetail(row.product.id)) },
                    )
                }
            }
        }
    }
}

@HiltViewModel
class ProductEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val catalog: CatalogRepository,
    inventory: InventoryRepository,
    private val db: HomeBasketDatabase,
    private val clock: Clock,
) : ViewModel() {
    val id: Long = savedState["id"] ?: 0L
    val initialBarcode: String = savedState.get<String>("barcode").orEmpty()
    val initialName: String = savedState.get<String>("name").orEmpty()
    val categories = catalog.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val locations = inventory.locations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val variants = (if (id != 0L) catalog.observeVariants(id) else emptyFlow()).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun load(): ProductEntity? = if (id == 0L) null else catalog.getProduct(id)
    suspend fun loadRule(): RecurringPurchaseRuleEntity? = if (id == 0L) null else db.recurringDao().getForProduct(id)
    suspend fun importImage(uri: android.net.Uri) = catalog.importImage(uri)

    fun save(p: ProductEntity, rule: Pair<Double, Int>?, onResult: (Result<Long>) -> Unit) = viewModelScope.launch {
        val result = runCatching {
            val savedId = catalog.saveProduct(p)
            val existing = db.recurringDao().getForProduct(savedId)
            if (rule != null) {
                db.recurringDao().upsert(
                    RecurringPurchaseRuleEntity(id = existing?.id ?: 0, productId = savedId, quantity = rule.first, intervalDays = rule.second,
                        nextDueEpochDay = existing?.nextDueEpochDay ?: clock.today().plusDays(1).toEpochDay())
                )
            } else existing?.let { db.recurringDao().delete(it) }
            savedId
        }
        onResult(result)
    }

    fun archive(onDone: () -> Unit) = viewModelScope.launch { catalog.archiveProduct(id); onDone() }
    fun addVariant(v: ProductVariantEntity) = viewModelScope.launch { catalog.addVariant(v.copy(productId = id)) }
    fun deleteVariant(v: ProductVariantEntity) = viewModelScope.launch { catalog.deleteVariant(v) }
}

/** Shown in a QuantityInput: value in the best display unit for [base]. */
private fun display(base: Double, kind: UnitKind): Pair<String, InputUnit> {
    val u = InputUnit.bestFor(kind, base)
    return (if (base <= 0) "" else Quantities.formatNumber(base / u.factor)) to u
}

@Composable
fun ProductEditScreen(back: () -> Unit, vm: ProductEditViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val categories by vm.categories.collectAsStateWithLifecycle()
    val locations by vm.locations.collectAsStateWithLifecycle()
    val variants by vm.variants.collectAsStateWithLifecycle()
    var loaded by remember { mutableStateOf<ProductEntity?>(null) }
    var ready by remember { mutableStateOf(false) }

    var name by rememberSaveable { mutableStateOf(vm.initialName) }
    var localName by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf(0L) }
    var brand by rememberSaveable { mutableStateOf("") }
    var barcode by rememberSaveable { mutableStateOf(vm.initialBarcode) }
    var kind by rememberSaveable { mutableStateOf(UnitKind.WEIGHT) }
    var countLabel by rememberSaveable { mutableStateOf("pcs") }
    var pack by rememberSaveable { mutableStateOf("1") }
    var packUnit by rememberSaveable { mutableStateOf(InputUnit.KG) }
    var price by rememberSaveable { mutableStateOf("") }
    var store by rememberSaveable { mutableStateOf("") }
    var frequency by rememberSaveable { mutableStateOf(PurchaseFrequency.MONTHLY) }
    var minQty by rememberSaveable { mutableStateOf("") }
    var minUnit by rememberSaveable { mutableStateOf(InputUnit.KG) }
    var desiredQty by rememberSaveable { mutableStateOf("") }
    var desiredUnit by rememberSaveable { mutableStateOf(InputUnit.KG) }
    var monthlyQty by rememberSaveable { mutableStateOf("") }
    var monthlyUnit by rememberSaveable { mutableStateOf(InputUnit.KG) }
    var shelfLife by rememberSaveable { mutableStateOf("") }
    var locationId by rememberSaveable { mutableStateOf(0L) }
    var notes by rememberSaveable { mutableStateOf("") }
    var imagePath by rememberSaveable { mutableStateOf<String?>(null) }
    var recurring by rememberSaveable { mutableStateOf(false) }
    var recurringQty by rememberSaveable { mutableStateOf("") }
    var recurringUnit by rememberSaveable { mutableStateOf(InputUnit.L) }
    var recurringDays by rememberSaveable { mutableStateOf("1") }
    var confirmArchive by remember { mutableStateOf(false) }
    var addVariant by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (ready) return@LaunchedEffect
        vm.load()?.let { p ->
            loaded = p
            name = p.name; localName = p.localName.orEmpty(); categoryId = p.categoryId; brand = p.brand.orEmpty(); barcode = p.barcode.orEmpty()
            kind = p.unitKind; countLabel = p.countLabel
            display(p.packSize, p.unitKind).let { pack = it.first; packUnit = it.second }
            price = Money.toEditable(p.lastPricePaise); store = p.preferredStore.orEmpty(); frequency = p.frequency
            display(p.minStock, p.unitKind).let { minQty = it.first; minUnit = it.second }
            display(p.desiredStock, p.unitKind).let { desiredQty = it.first; desiredUnit = it.second }
            display(p.monthlyRequirement, p.unitKind).let { monthlyQty = it.first; monthlyUnit = it.second }
            shelfLife = p.shelfLifeDays?.toString().orEmpty(); locationId = p.defaultLocationId ?: 0L; notes = p.notes.orEmpty(); imagePath = p.imagePath
        }
        vm.loadRule()?.let { r ->
            recurring = true; recurringDays = r.intervalDays.toString()
            display(r.quantity, kind).let { recurringQty = it.first; recurringUnit = it.second }
        }
        ready = true
    }
    LaunchedEffect(kind) {
        listOf(packUnit, minUnit, desiredUnit, monthlyUnit, recurringUnit).let {
            val fallback = InputUnit.forKind(kind).last()
            if (packUnit.kind != kind) packUnit = fallback
            if (minUnit.kind != kind) minUnit = fallback
            if (desiredUnit.kind != kind) desiredUnit = fallback
            if (monthlyUnit.kind != kind) monthlyUnit = fallback
            if (recurringUnit.kind != kind) recurringUnit = fallback
        }
    }
    LaunchedEffect(categories) { if (categoryId == 0L) categories.firstOrNull()?.let { categoryId = it.id } }

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { vm.importImage(uri)?.let { imagePath = it } }
    }

    fun base(text: String, unit: InputUnit) = Quantities.parse(text)?.let { unit.toBase(it) } ?: 0.0
    val packBase = base(pack, packUnit)
    val canSave = name.isNotBlank() && categoryId != 0L && packBase > 0

    HbScaffold(
        title = stringResource(if (vm.id == 0L) R.string.new_product else R.string.edit_product), onBack = back,
        actions = { if (vm.id != 0L) IconButton(onClick = { confirmArchive = true }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (imagePath != null) {
                    AsyncImage(model = imagePath, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)))
                    Spacer(Modifier.size(12.dp))
                }
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Default.AddPhotoAlternate, null); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.product_photo))
                }
            }
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.product_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(localName, { localName = it }, label = { Text(stringResource(R.string.local_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text(stringResource(R.string.local_name_hint)) })
            DropdownField(stringResource(R.string.category), categories, categories.firstOrNull { it.id == categoryId }, { it.name }, { categoryId = it.id })
            OutlinedTextField(brand, { brand = it }, label = { Text(stringResource(R.string.brand)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(barcode, { barcode = it.filter { c -> c.isLetterOrDigit() } }, label = { Text(stringResource(R.string.barcode)) }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Text(stringResource(R.string.measured_by), style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(UnitKind.WEIGHT to R.string.unit_weight, UnitKind.VOLUME to R.string.unit_volume, UnitKind.COUNT to R.string.unit_count).forEachIndexed { i, (k, label) ->
                    SegmentedButton(selected = kind == k, onClick = { kind = k }, shape = SegmentedButtonDefaults.itemShape(i, 3), enabled = loaded == null) { Text(stringResource(label)) }
                }
            }
            if (loaded != null) Text(stringResource(R.string.unit_locked), style = MaterialTheme.typography.bodySmall)
            if (kind == UnitKind.COUNT) {
                OutlinedTextField(countLabel, { countLabel = it.take(12) }, label = { Text(stringResource(R.string.count_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text(stringResource(R.string.count_label_hint)) })
            }
            QuantityInput(pack, { pack = it }, packUnit, { packUnit = it }, kind, countLabel, label = stringResource(R.string.pack_size))
            MoneyInput(price, { price = it }, stringResource(R.string.last_price_per_pack))
            OutlinedTextField(store, { store = it }, label = { Text(stringResource(R.string.preferred_store)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            DropdownField(stringResource(R.string.buy_how_often), PurchaseFrequency.entries, frequency, { stringResource(frequencyLabel(it)) }, { frequency = it })
            DropdownField(stringResource(R.string.default_place), locations, locations.firstOrNull { it.id == locationId }, { it.name }, { locationId = it.id })

            SectionTitle(stringResource(R.string.stock_targets))
            QuantityInput(minQty, { minQty = it }, minUnit, { minUnit = it }, kind, countLabel, label = stringResource(R.string.min_stock))
            QuantityInput(desiredQty, { desiredQty = it }, desiredUnit, { desiredUnit = it }, kind, countLabel, label = stringResource(R.string.desired_stock))
            QuantityInput(monthlyQty, { monthlyQty = it }, monthlyUnit, { monthlyUnit = it }, kind, countLabel, label = stringResource(R.string.monthly_requirement))
            OutlinedTextField(shelfLife, { shelfLife = it.filter(Char::isDigit).take(4) }, label = { Text(stringResource(R.string.shelf_life_days)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), supportingText = { Text(stringResource(R.string.shelf_life_hint)) })

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.buy_regularly), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.buy_regularly_hint), style = MaterialTheme.typography.bodySmall)
                }
                Switch(recurring, { recurring = it })
            }
            if (recurring) {
                QuantityInput(recurringQty, { recurringQty = it }, recurringUnit, { recurringUnit = it }, kind, countLabel)
                OutlinedTextField(recurringDays, { recurringDays = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.every_n_days)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.notes)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)

            if (vm.id != 0L) {
                SectionTitle(stringResource(R.string.pack_variants), action = { TextButton(onClick = { addVariant = true }) { Text(stringResource(R.string.add)) } })
                if (variants.isEmpty()) Text(stringResource(R.string.variants_hint), style = MaterialTheme.typography.bodySmall)
                variants.forEach { v ->
                    ListItem(
                        headlineContent = { Text(v.name) },
                        supportingContent = { Text(listOfNotNull(qty(v.packSize, kind, countLabel), v.barcode, v.lastPricePaise?.let(Money::format)).joinToString(" • ")) },
                        trailingContent = { IconButton(onClick = { vm.deleteVariant(v) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) } },
                    )
                }
            }

            Button(
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                onClick = {
                    val p = (loaded ?: ProductEntity(name = "", categoryId = 0, unitKind = kind, packSize = 1.0)).copy(
                        name = name, localName = localName, categoryId = categoryId, brand = brand.trim().ifEmpty { null }, barcode = barcode, unitKind = kind,
                        countLabel = countLabel.trim().ifEmpty { "pcs" }, packSize = packBase, lastPricePaise = Money.fromRupees(price),
                        preferredStore = store.trim().ifEmpty { null }, frequency = frequency, minStock = base(minQty, minUnit), desiredStock = base(desiredQty, desiredUnit),
                        monthlyRequirement = base(monthlyQty, monthlyUnit), shelfLifeDays = shelfLife.toIntOrNull(), defaultLocationId = locationId.takeIf { it != 0L },
                        notes = notes.trim().ifEmpty { null }, imagePath = imagePath,
                    )
                    val rule = if (recurring) base(recurringQty, recurringUnit).takeIf { it > 0 }?.let { it to (recurringDays.toIntOrNull() ?: 1).coerceAtLeast(1) } else null
                    vm.save(p, rule) { result ->
                        result.onSuccess { messenger.show(context.getString(R.string.saved)); back() }
                            .onFailure { messenger.show(context.getString(if (it.message == "duplicate") R.string.err_duplicate_product else R.string.err_generic)) }
                    }
                },
            ) { Text(stringResource(R.string.save)) }
        }
    }

    if (confirmArchive) {
        com.homebasket.core.designsystem.ConfirmDialog(
            stringResource(R.string.delete_product_title), stringResource(R.string.delete_product_text), stringResource(R.string.delete),
            onConfirm = { vm.archive { back() } }, onDismiss = { confirmArchive = false }, destructive = true,
        )
    }
    if (addVariant) {
        var vName by remember { mutableStateOf("") }
        var vPack by remember { mutableStateOf("") }
        var vUnit by remember { mutableStateOf(InputUnit.forKind(kind).last()) }
        var vBarcode by remember { mutableStateOf("") }
        var vPrice by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addVariant = false },
            title = { Text(stringResource(R.string.pack_variants)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(vName, { vName = it }, label = { Text(stringResource(R.string.variant_name)) }, singleLine = true)
                    QuantityInput(vPack, { vPack = it }, vUnit, { vUnit = it }, kind, countLabel, label = stringResource(R.string.pack_size))
                    OutlinedTextField(vBarcode, { vBarcode = it }, label = { Text(stringResource(R.string.barcode)) }, singleLine = true)
                    MoneyInput(vPrice, { vPrice = it }, stringResource(R.string.price))
                }
            },
            confirmButton = {
                TextButton(enabled = vName.isNotBlank() && base(vPack, vUnit) > 0, onClick = {
                    vm.addVariant(ProductVariantEntity(productId = vm.id, name = vName.trim(), packSize = base(vPack, vUnit), barcode = vBarcode.trim().ifEmpty { null }, lastPricePaise = Money.fromRupees(vPrice)))
                    addVariant = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { addVariant = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    catalog: CatalogRepository,
    inventory: InventoryRepository,
    clock: Clock,
) : ViewModel() {
    val id: Long = savedState["id"] ?: 0L
    val today = clock.today().toEpochDay()
    val product = catalog.observeProduct(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val lots = inventory.lotsForProduct(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val history = inventory.transactions(id, 50).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun ProductDetailScreen(go: (String) -> Unit, back: () -> Unit, vm: ProductDetailViewModel = hiltViewModel()) {
    val row by vm.product.collectAsStateWithLifecycle()
    val lots by vm.lots.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<LotRow?>(null) }
    var addToList by remember { mutableStateOf(false) }
    val p = row?.product

    HbScaffold(
        title = p?.name ?: "", onBack = back,
        actions = { IconButton(onClick = { go(Routes.productEdit(vm.id)) }) { Icon(Icons.Default.Edit, stringResource(R.string.edit)) } },
    ) { padding ->
        if (p == null) return@HbScaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                HbCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        p.imagePath?.let {
                            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
                            Spacer(Modifier.size(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleLarge)
                            Text(listOfNotNull(p.localName, row!!.categoryName, p.brand).joinToString(" • "), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    val usable = lots.filter { it.lot.condition.name in setOf("FRESH", "USE_SOON") && (it.lot.expiryEpochDay == null || it.lot.expiryEpochDay >= vm.today) }.sumOf { it.lot.quantity }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.usable_stock, qty(usable, p.unitKind, p.countLabel)), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    if (p.minStock > 0) Text(stringResource(R.string.min_desired_line, qty(p.minStock, p.unitKind, p.countLabel), qty(p.desiredStock, p.unitKind, p.countLabel)), style = MaterialTheme.typography.bodyMedium)
                    p.lastPricePaise?.let { Text(stringResource(R.string.last_price_pack, Money.format(it), qty(p.packSize, p.unitKind, p.countLabel)), style = MaterialTheme.typography.bodyMedium) }
                    p.lastPurchaseEpochDay?.let { Text(stringResource(R.string.last_bought_on, formatDate(it)), style = MaterialTheme.typography.bodyMedium) }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { go(Routes.addStock(productId = vm.id)) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Inventory2, null); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.add_stock)) }
                    FilledTonalButton(onClick = { addToList = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.AddShoppingCart, null); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.to_list)) }
                }
            }
            item { SectionTitle(stringResource(R.string.stock_by_place)) }
            if (lots.isEmpty()) item { Text(stringResource(R.string.no_stock_of_product), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(lots, key = { it.lot.id }) { lot ->
                ListItem(
                    headlineContent = { Text(lot.locationName) },
                    supportingContent = {
                        Text(listOfNotNull(
                            stringResource(conditionLabel(lot.lot.condition)),
                            lot.lot.expiryEpochDay?.let { formatDate(it) },
                            lot.lot.purchaseEpochDay?.let { stringResource(R.string.bought_on, formatDate(it)) },
                        ).joinToString(" • "))
                    },
                    trailingContent = { Text(qty(lot.lot.quantity, lot.unitKind, lot.countLabel), style = MaterialTheme.typography.titleMedium) },
                    modifier = Modifier.clickable { selected = lot },
                )
            }
            item { SectionTitle(stringResource(R.string.recent_changes)) }
            items(history, key = { "h${it.tx.id}" }) { t ->
                ListItem(
                    headlineContent = { Text(stringResource(txTypeLabel(t.tx.type))) },
                    supportingContent = { Text(listOfNotNull(t.locationName, formatDate(t.tx.epochDay), t.tx.note).joinToString(" • ")) },
                    trailingContent = { Text((if (t.tx.quantityDelta >= 0) "+" else "−") + qty(kotlin.math.abs(t.tx.quantityDelta), t.unitKind, t.countLabel)) },
                )
                HorizontalDivider()
            }
        }
    }
    selected?.let { LotActionSheet(it, onDismiss = { selected = null }) }
    if (addToList && row != null) AddToListDialog(row!!, onDismiss = { addToList = false })
}
