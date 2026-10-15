package com.homebasket.feature.inventory

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.homebasket.R
import com.homebasket.core.data.InventoryRepository
import com.homebasket.core.database.dao.LotRow
import com.homebasket.core.database.entity.ExpiryType
import com.homebasket.core.database.entity.FoodCondition
import com.homebasket.core.database.entity.StorageLocationEntity
import com.homebasket.core.database.entity.WasteReason
import com.homebasket.core.designsystem.DateField
import com.homebasket.core.designsystem.DropdownField
import com.homebasket.core.designsystem.LocalMessenger
import com.homebasket.core.designsystem.Messenger
import com.homebasket.core.designsystem.QuantityInput
import com.homebasket.core.designsystem.formatDate
import com.homebasket.core.designsystem.qty
import com.homebasket.core.domain.model.InputUnit
import com.homebasket.core.domain.model.Quantities
import com.homebasket.core.ui.conditionLabel
import com.homebasket.core.ui.expiryTypeLabel
import com.homebasket.core.ui.wasteReasonLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StockActionsViewModel @Inject constructor(private val inventory: InventoryRepository) : ViewModel() {
    val locations = inventory.locations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun run(m: Messenger, ok: String, block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onSuccess { m.show(ok) }.onFailure { m.show(it.message ?: "Error") }
    }

    fun use(c: Context, m: Messenger, lot: LotRow, q: Double) =
        run(m, c.getString(R.string.msg_used, qty(q, lot.unitKind, lot.countLabel), lot.productName)) { inventory.consumeFromLot(lot.lot.id, q) }

    fun move(c: Context, m: Messenger, lot: LotRow, to: StorageLocationEntity, q: Double) =
        run(m, c.getString(R.string.msg_moved, qty(q, lot.unitKind, lot.countLabel), lot.productName, to.name)) { inventory.transfer(lot.lot.id, to.id, q) }

    fun waste(c: Context, m: Messenger, lot: LotRow, q: Double, reason: WasteReason) =
        run(m, c.getString(R.string.msg_wasted, qty(q, lot.unitKind, lot.countLabel), lot.productName)) { inventory.recordWaste(lot.lot.id, q, reason) }

    fun correct(c: Context, m: Messenger, lot: LotRow, q: Double) {
        val before = lot.lot.quantity
        viewModelScope.launch {
            runCatching { inventory.correctQuantity(lot.lot.id, q, c.getString(R.string.note_stock_check)) }
                .onSuccess {
                    m.show(c.getString(R.string.msg_corrected, lot.productName, qty(q, lot.unitKind, lot.countLabel)), c.getString(R.string.undo)) {
                        viewModelScope.launch { inventory.correctQuantity(lot.lot.id, before, c.getString(R.string.undo)) }
                    }
                }
        }
    }

    fun condition(c: Context, m: Messenger, lot: LotRow, condition: FoodCondition) =
        run(m, c.getString(R.string.msg_marked, lot.productName, c.getString(conditionLabel(condition)))) { inventory.setCondition(lot.lot.id, condition) }

    fun dates(c: Context, m: Messenger, lot: LotRow, expiry: Long?, type: ExpiryType, opened: Long?) =
        run(m, c.getString(R.string.saved)) { inventory.updateLotDetails(lot.lot.id, expiry, type, opened, lot.lot.notes) }
}

private enum class LotAction { USE, MOVE, WASTE, CORRECT, DATES }

/** Bottom sheet with every action for one stock lot. Each action asks for a quantity before changing anything. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotActionSheet(lot: LotRow, onDismiss: () -> Unit, vm: StockActionsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val locations by vm.locations.collectAsStateWithLifecycle()
    var action by remember { mutableStateOf<LotAction?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(lot.productName, style = MaterialTheme.typography.titleLarge)
            Text(
                "${qty(lot.lot.quantity, lot.unitKind, lot.countLabel)} • ${lot.locationName}" +
                    (lot.lot.expiryEpochDay?.let { " • " + context.getString(expiryTypeLabel(lot.lot.expiryType)) + ": " + formatDate(it) } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(stringResource(R.string.condition), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FoodCondition.entries.forEach { c ->
                    FilterChip(selected = lot.lot.condition == c, onClick = {
                        if (c != lot.lot.condition) { vm.condition(context, messenger, lot, c); onDismiss() }
                    }, label = { Text(stringResource(conditionLabel(c))) })
                }
            }
            if (lot.lot.condition == FoodCondition.SPOILED) {
                Text(stringResource(R.string.spoiled_hint), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = { action = LotAction.USE }, modifier = Modifier.fillMaxWidth(), enabled = lot.lot.condition != FoodCondition.SPOILED) { Text(stringResource(R.string.action_use)) }
            OutlinedButton(onClick = { action = LotAction.MOVE }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_move)) }
            OutlinedButton(onClick = { action = LotAction.CORRECT }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_correct)) }
            OutlinedButton(onClick = { action = LotAction.WASTE }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_waste)) }
            OutlinedButton(onClick = { action = LotAction.DATES }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_dates)) }
        }
    }

    when (action) {
        LotAction.USE -> QuantityDialog(lot, stringResource(R.string.action_use), lot.lot.quantity, onDismiss = { action = null }) { q ->
            vm.use(context, messenger, lot, q); onDismiss()
        }
        LotAction.CORRECT -> QuantityDialog(lot, stringResource(R.string.action_correct), lot.lot.quantity, allowMoreThanLot = true, hint = stringResource(R.string.correct_hint), onDismiss = { action = null }) { q ->
            vm.correct(context, messenger, lot, q); onDismiss()
        }
        LotAction.MOVE -> {
            var target by remember { mutableStateOf(locations.firstOrNull { it.id != lot.lot.locationId }) }
            QuantityDialog(lot, stringResource(R.string.action_move), lot.lot.quantity, initial = lot.lot.quantity, onDismiss = { action = null }, extra = {
                DropdownField(stringResource(R.string.move_to), locations.filter { it.id != lot.lot.locationId }, target, { it.name }, { target = it })
            }) { q ->
                target?.let { vm.move(context, messenger, lot, it, q) }; onDismiss()
            }
        }
        LotAction.WASTE -> {
            var reason by remember { mutableStateOf(if (lot.lot.condition == FoodCondition.SPOILED) WasteReason.SPOILED else WasteReason.EXPIRED) }
            QuantityDialog(lot, stringResource(R.string.action_waste), lot.lot.quantity, initial = lot.lot.quantity, onDismiss = { action = null }, extra = {
                DropdownField(stringResource(R.string.reason), WasteReason.entries, reason, { stringResource(wasteReasonLabel(it)) }, { reason = it })
            }) { q ->
                vm.waste(context, messenger, lot, q, reason); onDismiss()
            }
        }
        LotAction.DATES -> {
            var expiry by remember { mutableStateOf(lot.lot.expiryEpochDay) }
            var type by remember { mutableStateOf(lot.lot.expiryType) }
            var opened by remember { mutableStateOf(lot.lot.openedEpochDay) }
            AlertDialog(
                onDismissRequest = { action = null },
                title = { Text(stringResource(R.string.action_dates)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateField(stringResource(R.string.expiry_date), expiry, { expiry = it })
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            ExpiryType.entries.forEachIndexed { i, t ->
                                SegmentedButton(selected = type == t, onClick = { type = t }, shape = SegmentedButtonDefaults.itemShape(i, ExpiryType.entries.size)) {
                                    Text(stringResource(expiryTypeLabel(t)), maxLines = 1)
                                }
                            }
                        }
                        DateField(stringResource(R.string.opened_on), opened, { opened = it })
                        Text(stringResource(R.string.expiry_disclaimer), style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = { TextButton(onClick = { vm.dates(context, messenger, lot, expiry, type, opened); onDismiss() }) { Text(stringResource(R.string.save)) } },
                dismissButton = { TextButton(onClick = { action = null }) { Text(stringResource(R.string.cancel)) } },
            )
        }
        null -> Unit
    }
}

@Composable
fun QuantityDialog(
    lot: LotRow,
    title: String,
    max: Double,
    onDismiss: () -> Unit,
    initial: Double? = null,
    allowMoreThanLot: Boolean = false,
    hint: String? = null,
    extra: @Composable () -> Unit = {},
    onConfirm: (Double) -> Unit,
) {
    val startUnit = InputUnit.bestFor(lot.unitKind, initial ?: max)
    var unit by remember { mutableStateOf(startUnit) }
    var text by remember { mutableStateOf(initial?.let { Quantities.formatNumber(it / startUnit.factor) } ?: "") }
    val value = Quantities.parse(text)?.let { unit.toBase(it) }
    val valid = value != null && (if (allowMoreThanLot) value >= 0 else value > 0 && value <= max + 1e-6)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.available_now, qty(lot.lot.quantity, lot.unitKind, lot.countLabel)))
                hint?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                QuantityInput(text, { text = it }, unit, { unit = it }, lot.unitKind, lot.countLabel, isError = text.isNotEmpty() && !valid)
                extra()
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onConfirm(value!!) }) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
