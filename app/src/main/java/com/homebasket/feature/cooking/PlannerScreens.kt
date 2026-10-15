package com.homebasket.feature.cooking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.homebasket.R
import com.homebasket.core.data.Clock
import com.homebasket.core.data.ConfirmedIngredient
import com.homebasket.core.data.HouseholdRepository
import com.homebasket.core.data.InventoryRepository
import com.homebasket.core.data.MealRepository
import com.homebasket.core.data.PlanningRepository
import com.homebasket.core.data.RecipeRepository
import com.homebasket.core.database.dao.MealSlotRow
import com.homebasket.core.database.dao.RecipeIngredientRow
import com.homebasket.core.database.entity.ListType
import com.homebasket.core.database.entity.LocationKind
import com.homebasket.core.database.entity.MealStatus
import com.homebasket.core.designsystem.ActionTile
import com.homebasket.core.designsystem.DayNavigator
import com.homebasket.core.designsystem.HbCard
import com.homebasket.core.designsystem.HbScaffold
import com.homebasket.core.designsystem.InfoBanner
import com.homebasket.core.designsystem.LocalMessenger
import com.homebasket.core.designsystem.LocalStatusColors
import com.homebasket.core.designsystem.SectionTitle
import com.homebasket.core.designsystem.Tone
import com.homebasket.core.designsystem.TwoColumn
import com.homebasket.core.designsystem.formatDate
import com.homebasket.core.designsystem.qty
import com.homebasket.core.domain.engine.MealSlotKind
import com.homebasket.core.domain.engine.ShortageLine
import com.homebasket.core.domain.model.Money
import com.homebasket.core.pdf.DocumentService
import com.homebasket.core.ui.slotLabel
import com.homebasket.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlannerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val meals: MealRepository,
    private val recipes: RecipeRepository,
    private val planning: PlanningRepository,
    private val inventory: InventoryRepository,
    private val documents: DocumentService,
    household: HouseholdRepository,
    private val clock: Clock,
) : ViewModel() {
    val today = clock.today().toEpochDay()
    val day = MutableStateFlow(savedState.get<Long>("day") ?: today)
    val weekStart = MutableStateFlow(clock.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))

    val recipeList = recipes.recipes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val ingredients = recipes.allIngredients.map { list -> list.groupBy { it.ingredient.recipeId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val household = household.household.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val usable = inventory.observeUsableTotals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val daySlots = day.flatMapLatest { meals.observeSlots(it, it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val dayPlan = day.flatMapLatest { d -> meals.observePlans(d, d).map { it.firstOrNull() } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val dayShortages = day.flatMapLatest { planning.observeShortages(it, it, includeLowStock = false) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val weekSlots = weekStart.flatMapLatest { meals.observeSlots(it.toEpochDay(), it.toEpochDay() + 6) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val weekPlans = weekStart.flatMapLatest { meals.observePlans(it.toEpochDay(), it.toEpochDay() + 6) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val weekShortages = weekStart.flatMapLatest { planning.observeShortages(it.toEpochDay(), it.toEpochDay() + 6, includeLowStock = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addMeal(slot: MealSlotKind, recipeId: Long?, custom: String?, servings: Int, onDay: Long = day.value) =
        viewModelScope.launch { meals.addMeal(onDay, slot, recipeId, custom, servings) }

    fun changeServings(row: MealSlotRow, delta: Int) = viewModelScope.launch {
        meals.updateMeal(row.slot.copy(servings = (row.slot.servings + delta).coerceIn(1, 99)))
    }

    fun changeDish(row: MealSlotRow, recipeId: Long?, custom: String?) = viewModelScope.launch {
        meals.updateMeal(row.slot.copy(recipeId = recipeId, customName = custom))
    }

    fun setNotes(row: MealSlotRow, notes: String) = viewModelScope.launch { meals.updateMeal(row.slot.copy(notes = notes.trim().ifEmpty { null })) }
    fun skip(row: MealSlotRow, skipped: Boolean) = viewModelScope.launch { meals.setSkipped(row.slot, skipped) }

    fun delete(row: MealSlotRow, onDeleted: () -> Unit) = viewModelScope.launch { meals.deleteMeal(row.slot); onDeleted() }
    fun restore(row: MealSlotRow) = viewModelScope.launch { meals.restoreMeal(row.slot) }

    fun setOccasion(occasion: String, notes: String) = viewModelScope.launch { meals.setDayNote(day.value, occasion, notes) }

    fun copyDay(from: Long, onDone: (Int) -> Unit) = viewModelScope.launch { onDone(meals.copyRange(from, from, day.value, replaceExisting = false)) }

    fun copyWeek(fromStart: LocalDate, toStart: LocalDate, onDone: (Int) -> Unit) = viewModelScope.launch {
        onDone(meals.copyRange(fromStart.toEpochDay(), fromStart.toEpochDay() + 6, toStart.toEpochDay(), replaceExisting = false))
    }

    fun cook(row: MealSlotRow, servings: Int, used: List<Pair<Long, Double>>, leftovers: Double, note: String?, onDone: (Int) -> Unit) = viewModelScope.launch {
        val fridge = inventory.getLocations().firstOrNull { it.kind == LocationKind.FRIDGE }?.id
        val result = meals.markCooked(row.slot.id, servings, used.map { ConfirmedIngredient(it.first, it.second) }, leftovers, fridge, note)
        onDone(result.shortfalls.size)
    }

    fun shoppingForDay(name: String, onDone: (Long) -> Unit) = viewModelScope.launch {
        val d = day.value
        onDone(if (d == today + 1) planning.generateTomorrowList() else planning.generatePeriodList(d, d, ListType.DAILY, name))
    }

    fun shoppingForWeek(name: String, onDone: (Long) -> Unit) = viewModelScope.launch {
        val s = weekStart.value.toEpochDay()
        onDone(planning.generatePeriodList(s, s + 6, ListType.WEEKLY, name))
    }

    fun printWeek(activity: android.content.Context, title: String) = viewModelScope.launch {
        documents.print(activity, documents.weeklyMenu(weekStart.value), title)
    }
}

private fun dayTitle(epochDay: Long, today: Long): String {
    val d = LocalDate.ofEpochDay(epochDay)
    val name = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    return "$name, " + d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}

/** Cooking tab: today and tomorrow at a glance plus entry points (Part 10-12). */
@Composable
fun CookingScreen(go: (String) -> Unit, vm: PlannerViewModel = hiltViewModel()) {
    val week by vm.weekSlots.collectAsStateWithLifecycle()
    val weekShort by vm.weekShortages.collectAsStateWithLifecycle()
    HbScaffold(title = stringResource(R.string.tab_cooking)) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                TwoColumn(
                    listOf(
                        { m -> ActionTile(stringResource(R.string.plan_today), Icons.Default.Today, { go(Routes.dayPlan(vm.today)) }, m) },
                        { m -> ActionTile(stringResource(R.string.action_plan_tomorrow), Icons.Default.EventNote, { go(Routes.dayPlan(vm.today + 1)) }, m) },
                        { m -> ActionTile(stringResource(R.string.weekly_planner), Icons.Default.CalendarViewWeek, { go(Routes.WEEK) }, m) },
                        { m -> ActionTile(stringResource(R.string.recipes), Icons.Default.MenuBook, { go(Routes.RECIPES) }, m) },
                    )
                )
            }
            listOf(vm.today, vm.today + 1).forEach { d ->
                item(key = "d$d") {
                    SectionTitle(stringResource(if (d == vm.today) R.string.today else R.string.tomorrow))
                    val slots = week.filter { it.slot.epochDay == d }
                    HbCard(onClick = { go(Routes.dayPlan(d)) }) {
                        if (slots.isEmpty()) Text(stringResource(R.string.nothing_planned_tap), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MealSlotKind.entries.forEach { kind ->
                            val dishes = slots.filter { it.slot.slot == kind }
                            if (dishes.isNotEmpty()) {
                                Row(Modifier.padding(vertical = 3.dp)) {
                                    Text(stringResource(slotLabel(kind)), style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(120.dp))
                                    Text(dishes.joinToString(", ") { (it.recipeName ?: it.slot.customName.orEmpty()) + if (it.slot.status == MealStatus.COOKED) " ✓" else "" })
                                }
                            }
                        }
                    }
                }
            }
            item {
                SectionTitle(stringResource(R.string.this_week))
                InfoBanner(
                    if (weekShort.isEmpty()) stringResource(R.string.week_all_available, week.size)
                    else stringResource(R.string.week_missing, week.size, weekShort.size),
                    if (weekShort.isEmpty()) Tone.OK else Tone.WARNING,
                    onClick = { go(Routes.WEEK) },
                )
            }
        }
    }
}

@Composable
fun DayPlanScreen(go: (String) -> Unit, back: () -> Unit, vm: PlannerViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val day by vm.day.collectAsStateWithLifecycle()
    val slots by vm.daySlots.collectAsStateWithLifecycle()
    val plan by vm.dayPlan.collectAsStateWithLifecycle()
    val recipes by vm.recipeList.collectAsStateWithLifecycle()
    val ingredients by vm.ingredients.collectAsStateWithLifecycle()
    val shortages by vm.dayShortages.collectAsStateWithLifecycle()
    val household by vm.household.collectAsStateWithLifecycle()
    val defaultServings = household?.defaultServings ?: 4

    var pickFor by remember { mutableStateOf<MealSlotKind?>(null) }
    var changeFor by remember { mutableStateOf<MealSlotRow?>(null) }
    var cookFor by remember { mutableStateOf<MealSlotRow?>(null) }
    var notesFor by remember { mutableStateOf<MealSlotRow?>(null) }
    var editOccasion by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val shortIds = shortages.map { it.product.id }.toSet()

    HbScaffold(
        title = stringResource(R.string.daily_menu), onBack = back,
        actions = {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more)) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.repeat_yesterday)) }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = {
                    menu = false
                    vm.copyDay(day - 1) { n -> messenger.show(context.getString(R.string.msg_copied_meals, n)) }
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.copy_last_week_same_day)) }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = {
                    menu = false
                    vm.copyDay(day - 7) { n -> messenger.show(context.getString(R.string.msg_copied_meals, n)) }
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.occasion_notes)) }, leadingIcon = { Icon(Icons.Default.EventNote, null) }, onClick = { menu = false; editOccasion = true })
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                DayNavigator(
                    when (day) { vm.today -> stringResource(R.string.today); vm.today + 1 -> stringResource(R.string.tomorrow); else -> dayTitle(day, vm.today) },
                    onPrev = { vm.day.value = day - 1 }, onNext = { vm.day.value = day + 1 },
                )
                if (day == vm.today || day == vm.today + 1) Text(dayTitle(day, vm.today), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            plan?.let { p ->
                if (p.occasion != null || p.notes != null) item { InfoBanner(listOfNotNull(p.occasion, p.notes).joinToString(" — "), Tone.OK, onClick = { editOccasion = true }) }
            }
            MealSlotKind.entries.forEach { kind ->
                item(key = "slot_$kind") {
                    SectionTitle(stringResource(slotLabel(kind)), action = {
                        TextButton(onClick = { pickFor = kind }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.add_dish)) }
                    })
                    val dishes = slots.filter { it.slot.slot == kind }
                    if (dishes.isEmpty()) Text(stringResource(R.string.no_dish_planned), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        dishes.forEach { row ->
                            MealCard(
                                row = row,
                                ingredients = row.slot.recipeId?.let { ingredients[it] }.orEmpty(),
                                shortIds = shortIds,
                                onServings = { vm.changeServings(row, it) },
                                onChange = { changeFor = row },
                                onSkip = { vm.skip(row, row.slot.status != MealStatus.SKIPPED) },
                                onCook = { cookFor = row },
                                onNotes = { notesFor = row },
                                onDelete = {
                                    vm.delete(row) {
                                        messenger.show(context.getString(R.string.msg_meal_removed), context.getString(R.string.undo)) { vm.restore(row) }
                                    }
                                },
                                onRecipe = { row.slot.recipeId?.let { go(Routes.recipeDetail(it)) } },
                            )
                        }
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.ingredients_for_day)) }
            item { IngredientSummary(shortages, slots.isEmpty()) }
            if (slots.any { it.slot.status == MealStatus.PLANNED }) {
                item {
                    Button(onClick = {
                        vm.shoppingForDay(context.getString(R.string.list_name_day, formatDate(day))) { id -> go(Routes.listDetail(id)) }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.ShoppingCart, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.make_shopping_list))
                    }
                }
            }
        }
    }

    pickFor?.let { slot ->
        RecipePickerDialog(recipes, slot,
            onPick = { vm.addMeal(slot, it.id, null, defaultServings); pickFor = null; messenger.show(context.getString(R.string.msg_planned, it.name)) },
            onCustom = { vm.addMeal(slot, null, it, defaultServings); pickFor = null },
            onDismiss = { pickFor = null })
    }
    changeFor?.let { row ->
        RecipePickerDialog(recipes, row.slot.slot,
            onPick = { vm.changeDish(row, it.id, null); changeFor = null },
            onCustom = { vm.changeDish(row, null, it); changeFor = null },
            onDismiss = { changeFor = null })
    }
    cookFor?.let { row ->
        CookDialog(
            row.recipeName ?: row.slot.customName.orEmpty(), row.slot.servings, row.slot.recipeId?.let { ingredients[it] }.orEmpty(),
            onConfirm = { servings, used, leftovers, note ->
                vm.cook(row, servings, used, leftovers, note) { shortCount ->
                    messenger.show(if (shortCount == 0) context.getString(R.string.msg_cooked) else context.getString(R.string.msg_cooked_short, shortCount))
                }
                cookFor = null
            },
            onDismiss = { cookFor = null },
        )
    }
    notesFor?.let { row ->
        var text by remember { mutableStateOf(row.slot.notes.orEmpty()) }
        AlertDialog(
            onDismissRequest = { notesFor = null },
            title = { Text(stringResource(R.string.cooking_notes)) },
            text = { OutlinedTextField(text, { text = it }, minLines = 3, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { vm.setNotes(row, text); notesFor = null }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { notesFor = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (editOccasion) {
        var occasion by remember { mutableStateOf(plan?.occasion.orEmpty()) }
        var notes by remember { mutableStateOf(plan?.notes.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editOccasion = false },
            title = { Text(stringResource(R.string.occasion_notes)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(occasion, { occasion = it }, label = { Text(stringResource(R.string.occasion_hint)) }, singleLine = true)
                    OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.notes)) }, minLines = 2)
                }
            },
            confirmButton = { TextButton(onClick = { vm.setOccasion(occasion, notes); editOccasion = false }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { editOccasion = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun MealCard(
    row: MealSlotRow,
    ingredients: List<RecipeIngredientRow>,
    shortIds: Set<Long>,
    onServings: (Int) -> Unit,
    onChange: () -> Unit,
    onSkip: () -> Unit,
    onCook: () -> Unit,
    onNotes: () -> Unit,
    onDelete: () -> Unit,
    onRecipe: () -> Unit,
) {
    val s = row.slot
    val cooked = s.status == MealStatus.COOKED
    val skipped = s.status == MealStatus.SKIPPED
    var menu by remember { mutableStateOf(false) }
    HbCard(onClick = if (s.recipeId != null) onRecipe else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    (row.recipeName ?: s.customName.orEmpty()) + when { cooked -> " ✓"; skipped -> ""; else -> "" },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (skipped) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (skipped) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                )
                val status = when { cooked -> stringResource(R.string.status_cooked); skipped -> stringResource(R.string.status_skipped); else -> null }
                val missing = ingredients.filter { it.ingredient.productId in shortIds && !it.ingredient.optional }.map { it.productName }
                val cost = if (ingredients.isNotEmpty()) estimateCost(ingredients, s.servings) else null
                Text(
                    listOfNotNull(
                        status,
                        cost?.takeIf { it.first > 0 }?.let { stringResource(R.string.est_cost, Money.format(it.first)) + if (it.second) "+" else "" },
                    ).joinToString(" • ").ifEmpty { if (s.recipeId == null) stringResource(R.string.custom_dish_no_ingredients) else "" },
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!cooked && !skipped && s.recipeId != null) {
                    if (missing.isEmpty()) Text(stringResource(R.string.all_ingredients_available), color = LocalStatusColors.current.ok, style = MaterialTheme.typography.bodySmall)
                    else Text(stringResource(R.string.missing_list, missing.joinToString(", ")), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                s.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more)) }
            DropdownMenu(menu, { menu = false }) {
                if (!cooked) DropdownMenuItem(text = { Text(stringResource(R.string.change_dish)) }, onClick = { menu = false; onChange() })
                if (!cooked) DropdownMenuItem(text = { Text(stringResource(if (skipped) R.string.unskip else R.string.skip_meal)) }, onClick = { menu = false; onSkip() })
                DropdownMenuItem(text = { Text(stringResource(R.string.cooking_notes)) }, onClick = { menu = false; onNotes() })
                if (!cooked) DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; onDelete() })
            }
        }
        if (!cooked && !skipped) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                IconButton(onClick = { onServings(-1) }) { Icon(Icons.Default.Remove, stringResource(R.string.fewer_servings)) }
                Text(stringResource(R.string.servings_n, s.servings), style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { onServings(1) }) { Icon(Icons.Default.Add, stringResource(R.string.more_servings)) }
                Spacer(Modifier.weight(1f))
                FilledTonalButton(onClick = onCook) { Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.mark_cooked)) }
            }
        }
    }
}

@Composable
private fun IngredientSummary(shortages: List<ShortageLine>, noMeals: Boolean) {
    when {
        noMeals -> Text(stringResource(R.string.plan_meals_to_see_ingredients), color = MaterialTheme.colorScheme.onSurfaceVariant)
        shortages.isEmpty() -> InfoBanner(stringResource(R.string.all_ingredients_available_day), Tone.OK)
        else -> HbCard {
            Text(stringResource(R.string.need_to_buy), style = MaterialTheme.typography.titleSmall)
            shortages.forEach { l ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(l.product.name, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.need_have, qty(l.reason.requiredForMeals, l.product.unitKind, l.product.countLabel), qty(maxOf(0.0, l.reason.usableStock - l.reason.reservedForEarlierMeals), l.product.unitKind, l.product.countLabel)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
fun WeekPlanScreen(go: (String) -> Unit, back: () -> Unit, vm: PlannerViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val weekStart by vm.weekStart.collectAsStateWithLifecycle()
    val slots by vm.weekSlots.collectAsStateWithLifecycle()
    val plans by vm.weekPlans.collectAsStateWithLifecycle()
    val shortages by vm.weekShortages.collectAsStateWithLifecycle()
    val ingredients by vm.ingredients.collectAsStateWithLifecycle()
    var confirmCopy by remember { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }
    val fmt = DateTimeFormatter.ofPattern("d MMM")

    HbScaffold(
        title = stringResource(R.string.weekly_planner), onBack = back,
        actions = { IconButton(onClick = { vm.printWeek(context, context.getString(R.string.pdf_weekly_menu)) }) { Icon(Icons.Default.Print, stringResource(R.string.print)) } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                DayNavigator(weekStart.format(fmt) + " – " + weekStart.plusDays(6).format(fmt),
                    onPrev = { vm.weekStart.value = weekStart.minusWeeks(1) }, onNext = { vm.weekStart.value = weekStart.plusWeeks(1) })
            }
            item {
                val cost = slots.filter { it.slot.status == MealStatus.PLANNED }.sumOf { r -> r.slot.recipeId?.let { estimateCost(ingredients[it].orEmpty(), r.slot.servings).first } ?: 0L }
                InfoBanner(
                    (if (shortages.isEmpty()) stringResource(R.string.week_all_available, slots.size) else stringResource(R.string.week_missing, slots.size, shortages.size)) +
                        if (cost > 0) "\n" + stringResource(R.string.week_est_cost, Money.format(cost)) else "",
                    if (shortages.isEmpty()) Tone.OK else Tone.WARNING,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { confirmCopy = weekStart.minusWeeks(1) to weekStart }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.copy_last_week), maxLines = 2) }
                    OutlinedButton(onClick = { confirmCopy = weekStart to weekStart.plusWeeks(1) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.repeat_next_week), maxLines = 2) }
                }
            }
            (0..6).forEach { offset ->
                val date = weekStart.plusDays(offset.toLong())
                val d = date.toEpochDay()
                item(key = "wd$d") {
                    val daySlots = slots.filter { it.slot.epochDay == d }
                    val occasion = plans.firstOrNull { it.epochDay == d }?.occasion
                    HbCard(onClick = { go(Routes.dayPlan(d)) }, container = if (d == vm.today) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + date.format(fmt), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            occasion?.let { AssistChip(onClick = { go(Routes.dayPlan(d)) }, label = { Text(it) }) }
                        }
                        MealSlotKind.entries.forEach { kind ->
                            val dishes = daySlots.filter { it.slot.slot == kind }
                            Row(Modifier.padding(vertical = 2.dp)) {
                                Text(stringResource(slotLabel(kind)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
                                Text(
                                    dishes.joinToString(", ") { (it.recipeName ?: it.slot.customName.orEmpty()) + when (it.slot.status) { MealStatus.COOKED -> " ✓"; MealStatus.SKIPPED -> " ✗"; else -> "" } }.ifEmpty { "—" },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
            item {
                Button(onClick = {
                    vm.shoppingForWeek(context.getString(R.string.list_name_week, weekStart.format(fmt))) { id -> go(Routes.listDetail(id)) }
                }, modifier = Modifier.fillMaxWidth(), enabled = slots.isNotEmpty()) {
                    Icon(Icons.Default.ShoppingCart, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.make_weekly_list))
                }
            }
        }
    }
    confirmCopy?.let { (from, to) ->
        com.homebasket.core.designsystem.ConfirmDialog(
            stringResource(R.string.copy_week_title), stringResource(R.string.copy_week_text, from.format(fmt), to.format(fmt)), stringResource(R.string.copy),
            onConfirm = { vm.copyWeek(from, to) { n -> messenger.show(context.getString(R.string.msg_copied_meals, n)) } },
            onDismiss = { confirmCopy = null },
        )
    }
}
