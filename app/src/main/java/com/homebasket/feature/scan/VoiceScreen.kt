package com.homebasket.feature.scan

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.homebasket.R
import com.homebasket.core.data.CatalogRepository
import com.homebasket.core.data.Clock
import com.homebasket.core.data.HouseholdRepository
import com.homebasket.core.data.InventoryRepository
import com.homebasket.core.data.MealRepository
import com.homebasket.core.data.RecipeRepository
import com.homebasket.core.data.ShoppingRepository
import com.homebasket.core.database.entity.ItemSource
import com.homebasket.core.database.entity.ListType
import com.homebasket.core.database.entity.LocationKind
import com.homebasket.core.database.entity.ProductEntity
import com.homebasket.core.database.entity.RecipeEntity
import com.homebasket.core.database.entity.StorageLocationEntity
import com.homebasket.core.database.entity.TxType
import com.homebasket.core.designsystem.HbCard
import com.homebasket.core.designsystem.HbScaffold
import com.homebasket.core.designsystem.InfoBanner
import com.homebasket.core.designsystem.LocalMessenger
import com.homebasket.core.designsystem.Tone
import com.homebasket.core.designsystem.formatDate
import com.homebasket.core.designsystem.qty
import com.homebasket.core.domain.engine.VoiceCommand
import com.homebasket.core.domain.engine.VoiceCommandParser
import com.homebasket.core.domain.model.InputUnit
import com.homebasket.core.domain.model.UnitKind
import com.homebasket.core.ui.slotLabel
import com.homebasket.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

/** A parsed command with its words resolved to real records, ready to confirm. */
sealed interface ResolvedCommand {
    data class AddStock(val product: ProductEntity, val quantity: Double, val location: StorageLocationEntity) : ResolvedCommand
    data class AddToList(val product: ProductEntity, val quantity: Double) : ResolvedCommand
    data class Plan(val recipe: RecipeEntity?, val dishText: String, val day: Long, val slot: com.homebasket.core.domain.engine.MealSlotKind, val servings: Int) : ResolvedCommand
    data object ShowTomorrow : ResolvedCommand
    data class NotUnderstood(val text: String, val reason: Int) : ResolvedCommand
}

@HiltViewModel
class VoiceViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val recipes: RecipeRepository,
    private val inventory: InventoryRepository,
    private val shopping: ShoppingRepository,
    private val meals: MealRepository,
    private val household: HouseholdRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val clock: Clock,
) : ViewModel() {
    val resolved = MutableStateFlow<ResolvedCommand?>(null)

    private fun quantityFor(p: ProductEntity, amount: Double, unit: InputUnit?): Double = when {
        unit != null && unit.kind == p.unitKind -> unit.toBase(amount)
        // "2 tomatoes" for a weight product: treat the number as packs to avoid guessing a weight per piece.
        unit == null || unit.kind == UnitKind.COUNT -> amount * (if (p.unitKind == UnitKind.COUNT) 1.0 else p.packSize)
        else -> amount * p.packSize
    }

    fun interpret(text: String) = viewModelScope.launch {
        resolved.value = when (val cmd = VoiceCommandParser.parse(text)) {
            is VoiceCommand.AddStock -> {
                val p = catalog.matchProduct(cmd.productText) ?: return@launch run { resolved.value = ResolvedCommand.NotUnderstood(text, R.string.voice_unknown_product) }
                val locations = inventory.getLocations()
                val t = cmd.locationText.lowercase()
                val loc = locations.firstOrNull { t.isNotBlank() && (it.name.lowercase().contains(t) || t.contains(it.name.lowercase().substringBefore(' '))) }
                    ?: locations.firstOrNull {
                        when {
                            t.contains("fridge") || t.contains("refrigerator") || t.contains("फ्रिज") || t.contains("ஃப்ரிட்ஜ்") -> it.kind == LocationKind.FRIDGE
                            t.contains("freezer") -> it.kind == LocationKind.FREEZER
                            t.contains("kitchen") || t.contains("pantry") || t.contains("रसोई") || t.contains("சமையலறை") -> it.kind == LocationKind.PANTRY
                            t.contains("basket") -> it.kind == LocationKind.BASKET
                            t.contains("store") -> it.kind == LocationKind.STOREROOM
                            else -> false
                        }
                    }
                    ?: locations.firstOrNull { it.id == p.defaultLocationId } ?: locations.first()
                ResolvedCommand.AddStock(p, quantityFor(p, cmd.amount, cmd.unit), loc)
            }
            is VoiceCommand.AddToShopping -> {
                val p = catalog.matchProduct(cmd.productText) ?: return@launch run { resolved.value = ResolvedCommand.NotUnderstood(text, R.string.voice_unknown_product) }
                ResolvedCommand.AddToList(p, quantityFor(p, cmd.amount, cmd.unit))
            }
            is VoiceCommand.PlanMeal -> ResolvedCommand.Plan(
                recipes.findByName(cmd.dishText), cmd.dishText, clock.today().toEpochDay() + cmd.dayOffset, cmd.slot, household.getHousehold()?.defaultServings ?: 4,
            )
            VoiceCommand.ShowTomorrowShopping -> ResolvedCommand.ShowTomorrow
            is VoiceCommand.Unknown -> ResolvedCommand.NotUnderstood(text, R.string.voice_not_understood)
        }
    }

    fun execute(c: ResolvedCommand, onDone: (String) -> Unit) = viewModelScope.launch {
        when (c) {
            is ResolvedCommand.AddStock -> {
                inventory.addStock(c.product.id, c.location.id, c.quantity, TxType.ADJUST, purchaseDay = clock.today().toEpochDay(),
                    expiryDay = inventory.estimatedExpiry(c.product.id, clock.today().toEpochDay()), note = context.getString(R.string.note_voice))
                onDone(context.getString(R.string.msg_added, qty(c.quantity, c.product.unitKind, c.product.countLabel), c.product.name, c.location.name))
            }
            is ResolvedCommand.AddToList -> {
                val tomorrow = clock.today().plusDays(1)
                val id = shopping.getOrCreateList(ListType.TOMORROW, tomorrow.toEpochDay(), context.getString(R.string.list_name_tomorrow, tomorrow.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))))
                shopping.addProduct(id, c.product.id, c.quantity, ItemSource.VOICE)
                onDone(context.getString(R.string.msg_added_to_list, c.product.name))
            }
            is ResolvedCommand.Plan -> {
                meals.addMeal(c.day, c.slot, c.recipe?.id, if (c.recipe == null) c.dishText.replaceFirstChar(Char::uppercase) else null, c.servings)
                onDone(context.getString(R.string.msg_planned, c.recipe?.name ?: c.dishText))
            }
            else -> Unit
        }
        resolved.value = null
    }
}

/**
 * Voice and text commands (Part 21). Speech is handled by the device's recognition service, which may use the
 * internet; typing works the same way. Every data change is shown first and needs a tap on Confirm.
 */
@Composable
fun VoiceScreen(go: (String) -> Unit, back: () -> Unit, vm: VoiceViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val resolved by vm.resolved.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf("en-IN") }
    var noRecognizer by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { heard ->
                text = heard
                vm.interpret(heard)
            }
        }
    }

    HbScaffold(title = stringResource(R.string.voice_entry), onBack = back) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.voice_intro), style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("en-IN" to "English", "ta-IN" to "தமிழ்", "hi-IN" to "हिन्दी").forEach { (tag, name) ->
                    FilterChip(language == tag, { language = tag }, label = { Text(name) })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                LargeFloatingActionButton(onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.voice_prompt))
                    }
                    try { launcher.launch(intent) } catch (_: ActivityNotFoundException) { noRecognizer = true }
                }) { Icon(Icons.Default.Mic, stringResource(R.string.speak), modifier = Modifier.size(36.dp)) }
            }
            if (noRecognizer) InfoBanner(stringResource(R.string.no_recognizer), Tone.WARNING)
            Text(stringResource(R.string.voice_network_note), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(text, { text = it }, label = { Text(stringResource(R.string.or_type_command)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Button(onClick = { vm.interpret(text) }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.check_command)) }
            Text(stringResource(R.string.examples), style = MaterialTheme.typography.titleSmall)
            listOf("Add 2 kg tomatoes to refrigerator", "Add 5 kg rice to shopping list", "Plan dosa for tomorrow's breakfast", "Show tomorrow's shopping list").forEach { ex ->
                SuggestionChip(onClick = { text = ex; vm.interpret(ex) }, label = { Text(ex) })
            }

            resolved?.let { c ->
                Spacer(Modifier.height(4.dp))
                HbCard(container = MaterialTheme.colorScheme.primaryContainer) {
                    Text(stringResource(R.string.i_understood), style = MaterialTheme.typography.bodyMedium)
                    val summary = when (c) {
                        is ResolvedCommand.AddStock -> stringResource(R.string.voice_add_stock, qty(c.quantity, c.product.unitKind, c.product.countLabel), c.product.name, c.location.name)
                        is ResolvedCommand.AddToList -> stringResource(R.string.voice_add_list, qty(c.quantity, c.product.unitKind, c.product.countLabel), c.product.name)
                        is ResolvedCommand.Plan -> stringResource(R.string.voice_plan, c.recipe?.name ?: c.dishText, stringResource(slotLabel(c.slot)), formatDate(c.day)) +
                            if (c.recipe == null) "\n" + stringResource(R.string.voice_custom_dish) else ""
                        ResolvedCommand.ShowTomorrow -> stringResource(R.string.voice_show_tomorrow)
                        is ResolvedCommand.NotUnderstood -> stringResource(c.reason)
                    }
                    Text(summary, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        when (c) {
                            ResolvedCommand.ShowTomorrow -> Button(onClick = { vm.resolved.value = null; go(Routes.TOMORROW) }) { Text(stringResource(R.string.open)) }
                            is ResolvedCommand.NotUnderstood -> Unit
                            else -> Button(onClick = { vm.execute(c) { msg -> messenger.show(msg) }; text = "" }) { Text(stringResource(R.string.confirm)) }
                        }
                        OutlinedButton(onClick = { vm.resolved.value = null }) { Text(stringResource(R.string.cancel)) }
                    }
                }
            }
        }
    }
}
