package com.homebasket.feature.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.homebasket.R
import com.homebasket.core.data.CatalogRepository
import com.homebasket.core.data.Clock
import com.homebasket.core.data.DirectPurchaseLine
import com.homebasket.core.data.HouseholdRepository
import com.homebasket.core.data.ShoppingRepository
import com.homebasket.core.database.dao.ProductRow
import com.homebasket.core.database.entity.BudgetCategory
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
import com.homebasket.core.designsystem.Tone
import com.homebasket.core.designsystem.qty
import com.homebasket.core.domain.engine.BillLine
import com.homebasket.core.domain.engine.BillMatcher
import com.homebasket.core.domain.engine.BillParser
import com.homebasket.core.domain.engine.OcrLine
import com.homebasket.core.domain.engine.ParsedBill
import com.homebasket.core.domain.model.InputUnit
import com.homebasket.core.domain.model.Money
import com.homebasket.core.domain.model.Quantities
import com.homebasket.core.domain.model.UnitKind
import com.homebasket.core.ui.budgetCategoryLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One receipt line as the user will save it. A null [product] means "expense only" (not tracked as stock). */
data class ReviewLine(
    val id: Int,
    val bill: BillLine,
    val product: ProductRow?,
    val quantity: Double,
    val amountPaise: Long,
    val category: BudgetCategory,
    val include: Boolean,
) {
    val kind: UnitKind get() = product?.product?.unitKind ?: UnitKind.COUNT
    val countLabel: String get() = product?.product?.countLabel ?: "pcs"
    val needsCheck: Boolean get() = product == null || !bill.consistent
}

/** Reads text from a photo on the device (ML Kit, no internet needed). */
private suspend fun recognize(context: Context, uri: Uri): List<OcrLine> = withContext(Dispatchers.Default) {
    val image = InputImage.fromFilePath(context, uri)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    try {
        val text = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        text.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line -> line.boundingBox?.let { OcrLine(line.text, it.left, it.top, it.bottom) } }
        }
    } finally {
        recognizer.close()
    }
}

@HiltViewModel
class BillScanViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val shopping: ShoppingRepository,
    household: HouseholdRepository,
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) : ViewModel() {
    val products = catalog.products.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val stores = household.household.map { h -> h.preferredStores.split(',').map { it.trim() }.filter { it.isNotEmpty() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val photos = mutableListOf<ParsedBill>()
    val photoCount = MutableStateFlow(0)
    val lines = MutableStateFlow<List<ReviewLine>>(emptyList())
    val billTotal = MutableStateFlow<Long?>(null)
    val store = MutableStateFlow("")
    val day = MutableStateFlow(clock.today().toEpochDay())
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<Int?>(null)

    fun newPhotoUri(): Uri {
        val dir = File(context.cacheDir, "bills").apply { mkdirs() }
        val file = File(dir, "bill_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    fun addPhoto(uri: Uri) = viewModelScope.launch {
        busy.value = true
        error.value = null
        val parsed = runCatching { BillParser.parse(BillParser.rows(recognize(context, uri))) }.getOrNull()
        if (parsed == null || parsed.lines.isEmpty()) {
            error.value = R.string.bill_no_lines
        } else {
            photos += parsed
            photoCount.value = photos.size
            rebuild()
        }
        busy.value = false
    }

    private suspend fun rebuild() {
        val merged = BillParser.merge(photos)
        val catalogRows = catalog.getProducts()
        val byName = catalogRows.associateBy { it.product.name }
        val names = byName.keys
        lines.value = merged.lines.mapIndexed { i, line ->
            val product = BillMatcher.suggest(line.name, names)?.let { byName[it] }
            ReviewLine(
                id = i,
                bill = line,
                product = product,
                quantity = product?.let { BillParser.baseQuantity(line, it.product.unitKind, it.product.packSize) } ?: line.qty,
                amountPaise = line.amountPaise,
                category = product?.budgetCategory ?: BudgetCategory.HOUSEHOLD,
                include = true,
            )
        }
        billTotal.value = merged.billTotalPaise
        merged.date?.let { day.value = it.toEpochDay() }
        if (store.value.isBlank()) store.value = merged.store.orEmpty()
    }

    fun update(line: ReviewLine) { lines.value = lines.value.map { if (it.id == line.id) line else it } }

    fun reset() {
        photos.clear(); photoCount.value = 0; lines.value = emptyList(); billTotal.value = null; error.value = null
    }

    /** Records every ticked line as one purchase; matched products also go into stock. */
    fun save(onDone: (Int, Long) -> Unit) = viewModelScope.launch {
        val chosen = lines.value.filter { it.include && it.amountPaise > 0 }
        val result = shopping.recordDirectPurchase(
            store.value.ifBlank { null }, day.value, context.getString(R.string.bill_note),
            chosen.map { l ->
                val p = l.product
                if (p != null) DirectPurchaseLine(p.product.id, p.product.name, l.quantity, p.product.unitKind, p.product.countLabel, l.amountPaise, p.budgetCategory, null, null)
                else DirectPurchaseLine(null, l.bill.name, l.quantity, UnitKind.COUNT, "pcs", l.amountPaise, l.category, null, null)
            },
        )
        onDone(result?.lineCount ?: 0, result?.totalPaise ?: 0)
        if (result != null) reset()
    }
}

/**
 * Scan a shop bill (receipt photo -> purchase). Text is read on the phone; every line is shown for review,
 * and nothing is saved until the user taps Save.
 */
@Composable
fun BillScanScreen(back: () -> Unit, vm: BillScanViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val lines by vm.lines.collectAsStateWithLifecycle()
    val photoCount by vm.photoCount.collectAsStateWithLifecycle()
    val billTotal by vm.billTotal.collectAsStateWithLifecycle()
    val store by vm.store.collectAsStateWithLifecycle()
    val day by vm.day.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val stores by vm.stores.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ReviewLine?>(null) }
    var pendingUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pendingUri?.let { vm.addPhoto(it) }
    }
    fun launchCamera() {
        val uri = vm.newPhotoUri()
        pendingUri = uri
        takePicture.launch(uri)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) launchCamera() }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.addPhoto(uri) }
    val onCamera = {
        // The app declares CAMERA, so Android requires it to be granted before using the camera app.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }
    val onGallery = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    val included = lines.filter { it.include }
    val linesTotal = included.sumOf { it.amountPaise }

    HbScaffold(
        title = stringResource(R.string.scan_bill), onBack = back,
        bottomBar = {
            if (lines.isNotEmpty()) {
                Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                    Button(
                        onClick = {
                            vm.save { n, total ->
                                if (n > 0) { messenger.show(context.getString(R.string.msg_purchase_recorded, n, Money.format(total))); back() }
                            }
                        },
                        enabled = included.isNotEmpty() && !busy,
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
                    ) { Text(stringResource(R.string.bill_save, included.size, Money.format(linesTotal))) }
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (lines.isEmpty()) {
                item {
                    EmptyState(Icons.Default.ReceiptLong, stringResource(R.string.scan_bill_title), stringResource(R.string.scan_bill_intro))
                }
                item { InfoBanner(stringResource(R.string.scan_bill_tips)) }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onCamera, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(6.dp))
                        Text(stringResource(if (photoCount == 0) R.string.take_photo else R.string.add_photo), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    FilledTonalButton(onClick = onGallery, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.from_gallery), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (busy) item {
                Column { Text(stringResource(R.string.reading_bill)); Spacer(Modifier.height(6.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            error?.let { item { InfoBanner(stringResource(it), Tone.WARNING) } }
            if (lines.isNotEmpty()) {
                item {
                    HbCard {
                        Text(stringResource(R.string.bill_photos_read, photoCount, lines.size), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        DropdownField(stringResource(R.string.store), (stores + store).filter { it.isNotBlank() }.distinct() + listOf(""), store, { it.ifEmpty { "—" } }, { vm.store.value = it })
                        Spacer(Modifier.height(8.dp))
                        DateField(stringResource(R.string.purchase_date), day, { it?.let { d -> vm.day.value = d } }, allowClear = false)
                    }
                }
                item {
                    val bt = billTotal
                    when {
                        bt == null -> InfoBanner(stringResource(R.string.bill_total_unknown, Money.format(linesTotal)), Tone.INFO)
                        kotlin.math.abs(bt - linesTotal) <= 100 -> InfoBanner(stringResource(R.string.bill_total_ok, Money.format(bt)), Tone.OK)
                        else -> InfoBanner(stringResource(R.string.bill_total_mismatch, Money.format(linesTotal), Money.format(bt)), Tone.WARNING)
                    }
                }
                val toCheck = lines.count { it.include && it.needsCheck }
                if (toCheck > 0) item { Text(stringResource(R.string.bill_lines_to_check, toCheck), style = MaterialTheme.typography.bodyMedium, color = LocalStatusColors.current.warning) }
                items(lines, key = { it.id }) { line ->
                    ListItem(
                        leadingContent = { Checkbox(line.include, { vm.update(line.copy(include = it)) }) },
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (line.needsCheck) { Icon(Icons.Default.WarningAmber, null, tint = LocalStatusColors.current.warning, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)) }
                                Text(line.product?.product?.name ?: stringResource(R.string.bill_expense_only_name, line.bill.name), fontStyle = if (line.product == null) FontStyle.Italic else null)
                            }
                        },
                        supportingContent = { Text(line.bill.name + " • " + stringResource(budgetCategoryLabel(line.category)), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Money.format(line.amountPaise), style = MaterialTheme.typography.titleSmall)
                                Text(qty(line.quantity, line.kind, line.countLabel), style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        modifier = Modifier.clickable { editing = line },
                    )
                    HorizontalDivider()
                }
                item {
                    TextButton(onClick = { vm.reset() }) { Text(stringResource(R.string.bill_start_over)) }
                }
            }
        }
    }

    editing?.let { line -> EditBillLineDialog(line, products, onDismiss = { editing = null }) { vm.update(it); editing = null } }
}

@Composable
private fun EditBillLineDialog(line: ReviewLine, products: List<ProductRow>, onDismiss: () -> Unit, onSave: (ReviewLine) -> Unit) {
    var product by remember { mutableStateOf(line.product) }
    var picking by remember { mutableStateOf(false) }
    var unit by remember { mutableStateOf(InputUnit.bestFor(line.kind, line.quantity)) }
    var text by remember { mutableStateOf(Quantities.formatNumber(line.quantity / unit.factor)) }
    var amount by remember { mutableStateOf(Money.toEditable(line.amountPaise)) }
    var category by remember { mutableStateOf(line.category) }
    val kind = product?.product?.unitKind ?: UnitKind.COUNT
    val value = Quantities.parse(text)?.let { unit.toBase(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(line.bill.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(line.bill.raw, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!line.bill.consistent) {
                    Text(stringResource(R.string.bill_math_warning), style = MaterialTheme.typography.bodySmall, color = LocalStatusColors.current.warning)
                    line.bill.ratePaise?.let { rate ->
                        val computed = kotlin.math.round(rate * line.bill.qty).toLong()
                        TextButton(onClick = { amount = Money.toEditable(computed) }) { Text(stringResource(R.string.bill_use_computed, Money.format(computed))) }
                    }
                }
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(product?.product?.name ?: stringResource(R.string.bill_choose_product))
                }
                if (product != null) TextButton(onClick = { product = null; unit = InputUnit.PCS; text = Quantities.formatNumber(line.bill.qty) }) {
                    Text(stringResource(R.string.bill_make_expense_only))
                }
                QuantityInput(text, { text = it }, unit, { unit = it }, kind, product?.product?.countLabel ?: "pcs")
                MoneyInput(amount, { amount = it }, stringResource(R.string.price_paid))
                if (product == null) DropdownField(stringResource(R.string.budget_category), BudgetCategory.entries, category, { stringResource(budgetCategoryLabel(it)) }, { category = it })
            }
        },
        confirmButton = {
            TextButton(enabled = value != null && value > 0, onClick = {
                onSave(line.copy(product = product, quantity = value!!, amountPaise = Money.fromRupees(amount) ?: line.amountPaise, category = product?.budgetCategory ?: category))
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (picking) {
        ProductPickerDialog(products, onPick = { p ->
            product = p
            val q = BillParser.baseQuantity(line.bill, p.product.unitKind, p.product.packSize)
            unit = InputUnit.bestFor(p.product.unitKind, q)
            text = Quantities.formatNumber(q / unit.factor)
            picking = false
        }, onDismiss = { picking = false })
    }
}
