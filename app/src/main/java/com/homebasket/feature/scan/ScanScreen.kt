package com.homebasket.feature.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.homebasket.R
import com.homebasket.core.data.BarcodeMatch
import com.homebasket.core.data.CatalogRepository
import com.homebasket.core.database.dao.ProductRow
import com.homebasket.core.database.entity.ItemSource
import com.homebasket.core.designsystem.EmptyState
import com.homebasket.core.designsystem.HbCard
import com.homebasket.core.designsystem.HbScaffold
import com.homebasket.core.designsystem.InfoBanner
import com.homebasket.core.designsystem.LocalMessenger
import com.homebasket.core.designsystem.ProductPickerDialog
import com.homebasket.core.designsystem.qty
import com.homebasket.feature.shopping.AddToListDialog
import com.homebasket.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(savedState: SavedStateHandle, private val catalog: CatalogRepository) : ViewModel() {
    val target: String = savedState["target"] ?: "inventory"
    val match = MutableStateFlow<BarcodeMatch?>(null)
    val matchedRow = MutableStateFlow<ProductRow?>(null)
    val products = catalog.products.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun lookup(code: String) = viewModelScope.launch {
        val m = catalog.findByBarcode(code.trim())
        match.value = m
        matchedRow.value = (m as? BarcodeMatch.Product)?.let { catalog.getProductRow(it.product.id) }
    }

    fun link(productId: Long, code: String) = viewModelScope.launch { catalog.linkBarcode(productId, code); lookup(code) }
    fun reset() { match.value = null; matchedRow.value = null }
}

/**
 * Barcode scanning (Part 22). Codes are matched only against the household's own products: a barcode
 * alone does not tell us a product's name, size or price, so unknown codes are created or linked manually.
 */
@Composable
fun ScanScreen(go: (String) -> Unit, back: () -> Unit, vm: ScanViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val match by vm.match.collectAsStateWithLifecycle()
    val row by vm.matchedRow.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var manual by rememberSaveable { mutableStateOf("") }
    var linking by remember { mutableStateOf(false) }
    var addToList by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    HbScaffold(title = stringResource(R.string.action_scan), onBack = back) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val m = match) {
                null -> {
                    if (granted) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.large)) {
                            CameraScanner(onCode = { vm.lookup(it) })
                        }
                        Text(stringResource(R.string.scan_hint), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        EmptyState(Icons.Default.NoPhotography, stringResource(R.string.camera_needed_title), stringResource(R.string.camera_needed),
                            actionLabel = stringResource(R.string.allow_camera), onAction = { permission.launch(Manifest.permission.CAMERA) })
                    }
                    OutlinedTextField(manual, { manual = it.filter(Char::isLetterOrDigit) }, label = { Text(stringResource(R.string.type_barcode)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Button(onClick = { vm.lookup(manual) }, enabled = manual.length >= 4, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.find_product)) }
                }
                is BarcodeMatch.Product -> {
                    HbCard {
                        Text(stringResource(R.string.found), style = MaterialTheme.typography.bodyMedium)
                        Text(m.product.name, style = MaterialTheme.typography.titleLarge)
                        m.variant?.let { Text(it.name + " • " + qty(it.packSize, m.product.unitKind, m.product.countLabel)) }
                    }
                    Button(onClick = { go(Routes.addStock(productId = m.product.id)) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Inventory2, null); Spacer(Modifier.size(8.dp)); Text(stringResource(R.string.add_to_inventory))
                    }
                    FilledTonalButton(onClick = { addToList = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.AddShoppingCart, null); Spacer(Modifier.size(8.dp)); Text(stringResource(R.string.add_to_shopping_list))
                    }
                    OutlinedButton(onClick = { vm.reset() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.scan_another)) }
                }
                is BarcodeMatch.NotFound -> {
                    InfoBanner(stringResource(R.string.barcode_not_found, m.code))
                    Button(onClick = { go(Routes.productEdit(barcode = m.code)) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PlaylistAdd, null); Spacer(Modifier.size(8.dp)); Text(stringResource(R.string.create_with_barcode))
                    }
                    FilledTonalButton(onClick = { linking = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Link, null); Spacer(Modifier.size(8.dp)); Text(stringResource(R.string.link_existing))
                    }
                    OutlinedButton(onClick = { vm.reset() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.scan_another)) }
                    Text(stringResource(R.string.barcode_no_lookup), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (linking) {
        val code = (match as? BarcodeMatch.NotFound)?.code
        ProductPickerDialog(products, onPick = { p ->
            code?.let { vm.link(p.product.id, it); messenger.show(context.getString(R.string.msg_linked, p.product.name)) }
            linking = false
        }, onDismiss = { linking = false })
    }
    if (addToList) row?.let { AddToListDialog(it, onDismiss = { addToList = false }, source = ItemSource.SCAN) }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraScanner(onCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_CODE_128, Barcode.FORMAT_QR_CODE,
            ).build()
        )
    }
    var delivered by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            scanner.close()
            executor.shutdown()
        }
    }
    AndroidView(
        factory = { ctx ->
            val view = PreviewView(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { proxy ->
                    val media = proxy.image
                    if (media == null || delivered) { proxy.close(); return@setAnalyzer }
                    scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                        .addOnSuccessListener { codes ->
                            codes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue?.let {
                                if (!delivered) { delivered = true; onCode(it) }
                            }
                        }
                        .addOnCompleteListener { proxy.close() }
                }
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }
            }, ContextCompat.getMainExecutor(ctx))
            view
        },
        modifier = Modifier.fillMaxSize(),
    )
}
