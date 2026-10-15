package com.homebasket.feature.cooking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
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
import com.homebasket.core.database.dao.ProductRow
import com.homebasket.core.database.entity.Diet
import com.homebasket.core.database.entity.RecipeEntity
import com.homebasket.core.database.entity.RecipeIngredientEntity
import com.homebasket.core.designsystem.ConfirmDialog
import com.homebasket.core.designsystem.EmptyState
import com.homebasket.core.designsystem.HbCard
import com.homebasket.core.designsystem.HbScaffold
import com.homebasket.core.designsystem.LocalMessenger
import com.homebasket.core.designsystem.ProductPickerDialog
import com.homebasket.core.designsystem.SearchField
import com.homebasket.core.designsystem.SectionTitle
import com.homebasket.core.domain.engine.MealSlotKind
import com.homebasket.core.domain.model.InputUnit
import com.homebasket.core.domain.model.Money
import com.homebasket.core.domain.model.Quantities
import com.homebasket.core.domain.model.UnitKind
import com.homebasket.core.ui.dietLabel
import com.homebasket.core.ui.slotLabel
import com.homebasket.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecipesViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val recipes: RecipeRepository,
    private val meals: MealRepository,
    catalog: CatalogRepository,
    inventory: InventoryRepository,
    household: HouseholdRepository,
    clock: Clock,
) : ViewModel() {
    val id: Long = savedState["id"] ?: 0L
    val today = clock.today().toEpochDay()
    val all = recipes.recipes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val recipe = (if (id != 0L) recipes.observeRecipe(id) else emptyFlow()).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val ingredients = (if (id != 0L) recipes.observeIngredients(id) else emptyFlow()).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val products = catalog.products.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val usable = inventory.observeUsableTotals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val household = household.household.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleFavorite(r: RecipeEntity) = viewModelScope.launch { recipes.toggleFavorite(r) }
    fun plan(r: RecipeEntity, day: Long, slot: MealSlotKind, servings: Int) = viewModelScope.launch { meals.addMeal(day, slot, r.id, null, servings) }
    fun delete(r: RecipeEntity, onDone: () -> Unit) = viewModelScope.launch { recipes.delete(r); onDone() }

    suspend fun loadForEdit() = if (id == 0L) null else recipes.getRecipe(id)?.let { it to recipes.getIngredients(id) }

    fun save(r: RecipeEntity, ingredients: List<RecipeIngredientEntity>, onResult: (Result<Long>) -> Unit) = viewModelScope.launch {
        onResult(runCatching { recipes.save(r, ingredients) })
    }
}

@Composable
fun RecipesScreen(go: (String) -> Unit, back: () -> Unit, vm: RecipesViewModel = hiltViewModel()) {
    val recipes by vm.all.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var diet by rememberSaveable { mutableStateOf<Diet?>(null) }
    var slot by rememberSaveable { mutableStateOf<MealSlotKind?>(null) }
    var favourites by rememberSaveable { mutableStateOf(false) }
    HbScaffold(
        title = stringResource(R.string.recipes), onBack = back,
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { go(Routes.recipeEdit()) }, icon = { Icon(Icons.Default.Add, null) }, text = { Text(stringResource(R.string.new_recipe)) }) },
    ) { padding ->
        val filtered = recipes.orEmpty().filter { r ->
            (diet == null || r.diet == diet) && (slot == null || slot in r.slots()) && (!favourites || r.isFavorite) &&
                (query.isBlank() || r.name.contains(query, true) || r.localName?.contains(query, true) == true || r.tags.contains(query, true))
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SearchField(query, { query = it }, stringResource(R.string.search_dishes)) }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(diet == null, { diet = null }, label = { Text(stringResource(R.string.all)) })
                    FilterChip(favourites, { favourites = !favourites }, label = { Text(stringResource(R.string.favourites)) })
                    Diet.entries.forEach { d -> FilterChip(diet == d, { diet = if (diet == d) null else d }, label = { Text(stringResource(dietLabel(d))) }) }
                    MealSlotKind.entries.forEach { s -> FilterChip(slot == s, { slot = if (slot == s) null else s }, label = { Text(stringResource(slotLabel(s))) }) }
                }
            }
            if (recipes != null && filtered.isEmpty()) item { EmptyState(Icons.Default.MenuBook, stringResource(R.string.no_recipes_title), stringResource(R.string.no_results)) }
            items(filtered, key = { it.id }) { r ->
                HbCard(onClick = { go(Routes.recipeDetail(r.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(r.localName, stringResource(dietLabel(r.diet)), stringResource(R.string.minutes_total, r.prepMinutes + r.cookMinutes)).joinToString(" • "),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { vm.toggleFavorite(r) }) {
                            Icon(if (r.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, stringResource(R.string.favourites), tint = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecipeDetailScreen(go: (String) -> Unit, back: () -> Unit, vm: RecipesViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val recipe by vm.recipe.collectAsStateWithLifecycle()
    val ingredients by vm.ingredients.collectAsStateWithLifecycle()
    val usable by vm.usable.collectAsStateWithLifecycle()
    val r = recipe
    var servings by rememberSaveable { mutableStateOf(0) }
    var planning by remember { mutableStateOf(false) }
    LaunchedEffect(r?.id) { if (r != null && servings == 0) servings = r.defaultServings }

    HbScaffold(
        title = r?.name ?: "", onBack = back,
        actions = {
            if (r != null) {
                IconButton(onClick = { vm.toggleFavorite(r) }) { Icon(if (r.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, stringResource(R.string.favourites)) }
                IconButton(onClick = { go(Routes.recipeEdit(r.id)) }) { Icon(Icons.Default.Edit, stringResource(R.string.edit)) }
            }
        },
    ) { padding ->
        if (r == null) return@HbScaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(listOfNotNull(r.localName, stringResource(dietLabel(r.diet)), r.slots().joinToString(", ")).joinToString(" • "), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.prep_cook_line, r.prepMinutes, r.cookMinutes), style = MaterialTheme.typography.bodyMedium)
                if (r.tags.isNotBlank()) Text("#" + r.tags.split(',').joinToString(" #") { it.trim() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.servings), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { servings = (servings - 1).coerceAtLeast(1) }) { Icon(Icons.Default.Remove, stringResource(R.string.fewer_servings)) }
                    Text(servings.toString(), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { servings = (servings + 1).coerceAtMost(99) }) { Icon(Icons.Default.Add, stringResource(R.string.more_servings)) }
                }
                val (cost, partial) = estimateCost(ingredients, servings)
                if (cost > 0) Text(stringResource(R.string.est_cost, Money.format(cost)) + if (partial) " " + stringResource(R.string.some_prices_missing) else "", style = MaterialTheme.typography.bodyMedium)
            }
            item { SectionTitle(stringResource(R.string.ingredients)) }
            if (ingredients.isEmpty()) item { Text(stringResource(R.string.no_ingredients_yet), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(ingredients, key = { it.ingredient.id }) { IngredientLine(it, servings, usable[it.ingredient.productId] ?: 0.0) }
            item { SectionTitle(stringResource(R.string.instructions)) }
            item { Text(r.instructions.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge) }
            item {
                Spacer(Modifier.height(8.dp))
                Button(onClick = { planning = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.EventAvailable, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.plan_this))
                }
            }
        }
    }
    if (planning && r != null) {
        PlanDishDialog(vm.today, servings.coerceAtLeast(1), onConfirm = { day, slot, n ->
            vm.plan(r, day, slot, n); planning = false
            messenger.show(context.getString(R.string.msg_planned, r.name))
        }, onDismiss = { planning = false })
    }
}

private data class IngredientDraft(val product: ProductRow, val text: String, val unit: InputUnit, val optional: Boolean)

@Composable
fun RecipeEditScreen(back: () -> Unit, vm: RecipesViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val products by vm.products.collectAsStateWithLifecycle()
    val household by vm.household.collectAsStateWithLifecycle()
    var original by remember { mutableStateOf<RecipeEntity?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var localName by rememberSaveable { mutableStateOf("") }
    var slots by remember { mutableStateOf(setOf(MealSlotKind.LUNCH)) }
    var diet by rememberSaveable { mutableStateOf(Diet.VEG) }
    var prep by rememberSaveable { mutableStateOf("10") }
    var cook by rememberSaveable { mutableStateOf("20") }
    var servings by rememberSaveable { mutableStateOf("4") }
    var tags by rememberSaveable { mutableStateOf("") }
    var instructions by rememberSaveable { mutableStateOf("") }
    val drafts = remember { mutableStateListOf<IngredientDraft>() }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(products.isNotEmpty()) {
        if (loaded || products.isEmpty()) return@LaunchedEffect
        val byId = products.associateBy { it.product.id }
        vm.loadForEdit()?.let { (r, ings) ->
            original = r
            name = r.name; localName = r.localName.orEmpty(); slots = r.slots(); diet = r.diet; prep = r.prepMinutes.toString(); cook = r.cookMinutes.toString()
            servings = r.defaultServings.toString(); tags = r.tags; instructions = r.instructions
            drafts.clear()
            ings.forEach { i ->
                byId[i.ingredient.productId]?.let { p ->
                    val u = InputUnit.bestFor(p.product.unitKind, i.ingredient.quantityPerServing)
                    drafts += IngredientDraft(p, Quantities.formatNumber(i.ingredient.quantityPerServing / u.factor), u, i.ingredient.optional)
                }
            }
        } ?: run { servings = (household?.defaultServings ?: 4).toString() }
        loaded = true
    }

    HbScaffold(
        title = stringResource(if (vm.id == 0L) R.string.new_recipe else R.string.edit_recipe), onBack = back,
        actions = { if (original != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.dish_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(localName, { localName = it }, label = { Text(stringResource(R.string.local_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.suits_meals), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MealSlotKind.entries.forEach { s -> FilterChip(s in slots, { slots = if (s in slots) slots - s else slots + s }, label = { Text(stringResource(slotLabel(s))) }) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Diet.entries.forEach { d -> FilterChip(diet == d, { diet = d }, label = { Text(stringResource(dietLabel(d))) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(prep, { prep = it.filter(Char::isDigit).take(4) }, label = { Text(stringResource(R.string.prep_minutes)) }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(cook, { cook = it.filter(Char::isDigit).take(4) }, label = { Text(stringResource(R.string.cook_minutes)) }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(servings, { servings = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.servings)) }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            OutlinedTextField(tags, { tags = it }, label = { Text(stringResource(R.string.tags)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), supportingText = { Text(stringResource(R.string.tags_hint)) })

            SectionTitle(stringResource(R.string.ingredients_per_serving), action = { TextButton(onClick = { picking = true }) { Icon(Icons.Default.Add, null); Text(stringResource(R.string.add)) } })
            Text(stringResource(R.string.ingredients_per_serving_hint), style = MaterialTheme.typography.bodySmall)
            drafts.forEachIndexed { i, d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.product.product.name, modifier = Modifier.weight(1f))
                    OutlinedTextField(d.text, { v -> drafts[i] = d.copy(text = v.filter { c -> c.isDigit() || c == '.' }) }, singleLine = true, modifier = Modifier.weight(0.6f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    TextButton(onClick = {
                        val units = InputUnit.forKind(d.product.product.unitKind)
                        drafts[i] = d.copy(unit = units[(units.indexOf(d.unit) + 1) % units.size])
                    }) { Text(if (d.unit == InputUnit.PCS) d.product.product.countLabel else d.unit.symbol) }
                    IconButton(onClick = { drafts.removeAt(i) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                }
            }
            OutlinedTextField(instructions, { instructions = it }, label = { Text(stringResource(R.string.instructions)) }, minLines = 4, modifier = Modifier.fillMaxWidth())
            Button(
                enabled = name.isNotBlank() && slots.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                onClick = {
                    val r = (original ?: RecipeEntity(name = "", mealTypes = "", prepMinutes = 0, cookMinutes = 0, instructions = "", diet = diet, defaultServings = 4)).copy(
                        name = name, localName = localName.trim().ifEmpty { null }, mealTypes = slots.sortedBy { it.order }.joinToString(",") { it.name },
                        prepMinutes = prep.toIntOrNull() ?: 0, cookMinutes = cook.toIntOrNull() ?: 0, diet = diet, defaultServings = (servings.toIntOrNull() ?: 4).coerceAtLeast(1),
                        tags = tags.trim(), instructions = instructions.trim(),
                    )
                    val ings = drafts.mapNotNull { d ->
                        val q = (Quantities.parse(d.text) ?: return@mapNotNull null) * d.unit.factor
                        RecipeIngredientEntity(recipeId = r.id, productId = d.product.product.id, quantityPerServing = q, optional = d.optional)
                    }
                    vm.save(r, ings) { result ->
                        result.onSuccess { messenger.show(context.getString(R.string.saved)); back() }
                            .onFailure { messenger.show(context.getString(if (it.message == "duplicate") R.string.err_duplicate_recipe else R.string.err_generic)) }
                    }
                },
            ) { Text(stringResource(R.string.save)) }
        }
    }
    if (picking) {
        ProductPickerDialog(products, onPick = { p ->
            if (drafts.none { it.product.product.id == p.product.id }) {
                drafts += IngredientDraft(p, "", if (p.product.unitKind == UnitKind.COUNT) InputUnit.PCS else InputUnit.forKind(p.product.unitKind).first(), false)
            }
            picking = false
        }, onDismiss = { picking = false })
    }
    if (confirmDelete) {
        original?.let { r ->
            ConfirmDialog(stringResource(R.string.delete_recipe_title), stringResource(R.string.delete_recipe_text), stringResource(R.string.delete),
                onConfirm = { vm.delete(r) { back(); back() } }, onDismiss = { confirmDelete = false }, destructive = true)
        }
    }
}
