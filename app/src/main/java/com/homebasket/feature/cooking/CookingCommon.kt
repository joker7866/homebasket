package com.homebasket.feature.cooking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.homebasket.R
import com.homebasket.core.database.dao.RecipeIngredientRow
import com.homebasket.core.database.entity.Diet
import com.homebasket.core.database.entity.RecipeEntity
import com.homebasket.core.designsystem.DropdownField
import com.homebasket.core.designsystem.SearchField
import com.homebasket.core.designsystem.qty
import com.homebasket.core.domain.engine.MealSlotKind
import com.homebasket.core.domain.model.InputUnit
import com.homebasket.core.domain.model.Money
import com.homebasket.core.domain.model.Quantities
import com.homebasket.core.ui.dietLabel
import com.homebasket.core.ui.slotLabel

fun RecipeEntity.slots(): Set<MealSlotKind> = mealTypes.split(',').mapNotNull { runCatching { MealSlotKind.valueOf(it.trim()) }.getOrNull() }.toSet()

/** Estimated cost of a recipe for [servings], from last recorded prices. Second value: true if some prices are missing. */
fun estimateCost(ingredients: List<RecipeIngredientRow>, servings: Int): Pair<Long, Boolean> {
    var total = 0L
    var missing = false
    ingredients.forEach { i ->
        val c = Money.costFor(i.ingredient.quantityPerServing * servings, i.packSize, i.lastPricePaise)
        if (c == null) missing = true else total += c
    }
    return total to missing
}

/** Full-screen dish chooser with meal-type, diet and favourite filters, plus free-text custom dishes. */
@Composable
fun RecipePickerDialog(
    recipes: List<RecipeEntity>,
    slot: MealSlotKind?,
    onPick: (RecipeEntity) -> Unit,
    onCustom: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var onlySlot by rememberSaveable { mutableStateOf(slot != null) }
    var diet by rememberSaveable { mutableStateOf<Diet?>(null) }
    var favourites by rememberSaveable { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(slot?.let { stringResource(slotLabel(it)) } ?: stringResource(R.string.choose_dish), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
                }
                SearchField(query, { query = it }, stringResource(R.string.search_dishes))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (slot != null) FilterChip(onlySlot, { onlySlot = !onlySlot }, label = { Text(stringResource(slotLabel(slot))) })
                    FilterChip(diet == null, { diet = null }, label = { Text(stringResource(R.string.all)) })
                    FilterChip(favourites, { favourites = !favourites }, label = { Text(stringResource(R.string.favourites)) }, leadingIcon = { Icon(Icons.Default.Favorite, null) })
                    Diet.entries.forEach { d -> FilterChip(diet == d, { diet = if (diet == d) null else d }, label = { Text(stringResource(dietLabel(d))) }) }
                }
                val filtered = recipes.filter { r ->
                    (!onlySlot || slot == null || slot in r.slots()) && (diet == null || r.diet == diet) && (!favourites || r.isFavorite) &&
                        (query.isBlank() || r.name.contains(query, true) || r.localName?.contains(query, true) == true || r.tags.contains(query, true))
                }
                LazyColumn(Modifier.weight(1f)) {
                    if (query.isNotBlank()) {
                        item {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.custom_dish_named, query.trim())) },
                                supportingContent = { Text(stringResource(R.string.custom_dish_hint)) },
                                leadingContent = { Icon(Icons.Default.Add, null) },
                                modifier = Modifier.clickable { onCustom(query.trim()) },
                            )
                            HorizontalDivider()
                        }
                    }
                    items(filtered, key = { it.id }) { r ->
                        ListItem(
                            headlineContent = { Text(r.name) },
                            supportingContent = { Text(listOfNotNull(r.localName, stringResource(dietLabel(r.diet)), r.slots().joinToString(", ") { s -> s.name.lowercase().replaceFirstChar(Char::uppercase) }).joinToString(" • ")) },
                            trailingContent = { if (r.isFavorite) Icon(Icons.Default.Favorite, null, tint = MaterialTheme.colorScheme.tertiary) },
                            modifier = Modifier.clickable { onPick(r) },
                        )
                    }
                }
            }
        }
    }
}

data class EditableIngredient(val row: RecipeIngredientRow, val text: String, val unit: InputUnit, val include: Boolean) {
    fun baseQuantity(): Double = if (!include) 0.0 else (Quantities.parse(text) ?: 0.0) * unit.factor
}

/**
 * "Mark as cooked": shows the scaled ingredient list, lets the user change any quantity or untick ingredients
 * they did not use, and records leftovers. Nothing changes until Confirm (Rule 5).
 */
@Composable
fun CookDialog(
    dishName: String,
    servings: Int,
    ingredients: List<RecipeIngredientRow>,
    onConfirm: (servings: Int, used: List<Pair<Long, Double>>, leftoverPortions: Double, note: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var cookedServings by remember { mutableStateOf(servings.toString()) }
    val rows = remember(ingredients) {
        mutableStateListOf(*ingredients.map { r ->
            val q = r.ingredient.quantityPerServing * servings
            val u = InputUnit.bestFor(r.unitKind, q)
            EditableIngredient(r, Quantities.formatNumber(q / u.factor), u, true)
        }.toTypedArray())
    }
    var leftovers by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mark_cooked_title, dishName)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.mark_cooked_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(cookedServings, { v ->
                    cookedServings = v.filter(Char::isDigit).take(3)
                    val n = cookedServings.toIntOrNull() ?: return@OutlinedTextField
                    rows.indices.forEach { i ->
                        val r = rows[i]
                        val q = r.row.ingredient.quantityPerServing * n
                        rows[i] = r.copy(text = Quantities.formatNumber(q / r.unit.factor))
                    }
                }, label = { Text(stringResource(R.string.servings)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                rows.forEachIndexed { i, r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(r.include, { rows[i] = r.copy(include = it) })
                        Text(r.row.productName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(
                            r.text, { v -> rows[i] = r.copy(text = v.filter { c -> c.isDigit() || c == '.' }) },
                            singleLine = true, enabled = r.include, modifier = Modifier.weight(0.7f),
                            suffix = { Text(if (r.unit == InputUnit.PCS) r.row.countLabel else r.unit.symbol) },
                        )
                    }
                }
                OutlinedTextField(leftovers, { leftovers = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text(stringResource(R.string.leftover_portions)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.cooking_notes)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(cookedServings.toIntOrNull() ?: servings, rows.map { it.row.ingredient.productId to it.baseQuantity() }, leftovers.toDoubleOrNull() ?: 0.0, note.ifBlank { null })
            }) { Text(stringResource(R.string.confirm_cooked)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Choose date and meal for "Plan this dish". */
@Composable
fun PlanDishDialog(today: Long, defaultServings: Int, onConfirm: (Long, MealSlotKind, Int) -> Unit, onDismiss: () -> Unit) {
    var day by remember { mutableStateOf(today + 1) }
    var slot by remember { mutableStateOf(MealSlotKind.LUNCH) }
    var servings by remember { mutableStateOf(defaultServings.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plan_this)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                com.homebasket.core.designsystem.DateField(stringResource(R.string.date), day, { it?.let { d -> day = d } }, allowClear = false)
                DropdownField(stringResource(R.string.meal), MealSlotKind.entries, slot, { stringResource(slotLabel(it)) }, { slot = it })
                OutlinedTextField(servings, { servings = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.servings)) }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(day, slot, servings.toIntOrNull() ?: defaultServings) }) { Text(stringResource(R.string.plan)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun IngredientLine(row: RecipeIngredientRow, servings: Int, available: Double?) {
    val need = row.ingredient.quantityPerServing * servings
    val short = available != null && available + 1e-6 < need
    ListItem(
        headlineContent = { Text(row.productName + (row.productLocalName?.let { " ($it)" } ?: "")) },
        supportingContent = available?.let {
            {
                Text(
                    stringResource(R.string.have_qty, qty(it, row.unitKind, row.countLabel)),
                    color = if (short) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = { Text(qty(need, row.unitKind, row.countLabel), style = MaterialTheme.typography.titleSmall) },
    )
}
