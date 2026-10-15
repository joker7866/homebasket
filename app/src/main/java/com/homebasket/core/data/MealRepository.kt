package com.homebasket.core.data

import androidx.room.withTransaction
import com.homebasket.core.database.HomeBasketDatabase
import com.homebasket.core.database.dao.MealSlotRow
import com.homebasket.core.database.dao.RecipeIngredientRow
import com.homebasket.core.database.entity.LeftoverStatus
import com.homebasket.core.database.entity.MealCompletionEntity
import com.homebasket.core.database.entity.MealPlanEntity
import com.homebasket.core.database.entity.MealSlotEntity
import com.homebasket.core.database.entity.MealStatus
import com.homebasket.core.database.entity.PreparedFoodLeftoverEntity
import com.homebasket.core.database.entity.RecipeEntity
import com.homebasket.core.database.entity.RecipeIngredientEntity
import com.homebasket.core.database.entity.TxReference
import com.homebasket.core.domain.engine.MealSlotKind
import com.homebasket.core.domain.model.QUANTITY_EPSILON
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class ConfirmedIngredient(val productId: Long, val quantity: Double)

data class CookResult(val shortfalls: Map<Long, Double>)

@Singleton
class RecipeRepository @Inject constructor(private val db: HomeBasketDatabase) {
    private val dao = db.recipeDao()

    val recipes: Flow<List<RecipeEntity>> = dao.observeRecipes()
    val allIngredients: Flow<List<RecipeIngredientRow>> = dao.observeAllIngredients()

    fun observeRecipe(id: Long) = dao.observeRecipe(id)
    fun observeIngredients(id: Long) = dao.observeIngredients(id)
    suspend fun getRecipe(id: Long) = dao.getRecipe(id)
    suspend fun getIngredients(id: Long) = dao.getIngredients(id)
    suspend fun getRecipes() = dao.getRecipes()

    suspend fun save(recipe: RecipeEntity, ingredients: List<RecipeIngredientEntity>): Long = db.withTransaction {
        require(recipe.name.isNotBlank()) { "name" }
        val clash = dao.getRecipeByName(recipe.name.trim())
        require(clash == null || clash.id == recipe.id) { "duplicate" }
        val id = if (recipe.id == 0L) dao.insertRecipe(recipe.copy(name = recipe.name.trim(), isCustom = true))
        else { dao.updateRecipe(recipe.copy(name = recipe.name.trim())); recipe.id }
        dao.clearIngredients(id)
        dao.insertIngredients(ingredients.filter { it.quantityPerServing > QUANTITY_EPSILON }.distinctBy { it.productId }.map { it.copy(id = 0, recipeId = id) })
        id
    }

    suspend fun toggleFavorite(r: RecipeEntity) = dao.updateRecipe(r.copy(isFavorite = !r.isFavorite))

    /** Planned meals keep their row (recipe becomes null via SET NULL) so history is not lost. */
    suspend fun delete(r: RecipeEntity) = dao.deleteRecipe(r)

    suspend fun findByName(text: String): RecipeEntity? {
        val t = text.trim().lowercase()
        val all = dao.getRecipes()
        return all.firstOrNull { it.name.lowercase() == t || it.localName?.lowercase() == t }
            ?: all.firstOrNull { it.name.lowercase().startsWith(t) || t.startsWith(it.name.lowercase()) }
            ?: all.firstOrNull { it.name.lowercase().contains(t) }
    }
}

/**
 * Meal planning. Planning never touches stock (Rule 4). Only [markCooked] reduces stock, and only by
 * the quantities the user confirmed on screen (Rule 5).
 */
@Singleton
class MealRepository @Inject constructor(
    private val db: HomeBasketDatabase,
    private val inventory: InventoryRepository,
    private val clock: Clock,
) {
    private val dao = db.mealDao()

    fun observeSlots(start: Long, end: Long): Flow<List<MealSlotRow>> =
        dao.observeSlots(start, end).map { rows -> rows.sortedWith(compareBy({ it.slot.epochDay }, { it.slot.slot.order }, { it.slot.id })) }

    fun observePlans(start: Long, end: Long) = dao.observePlans(start, end)
    fun observePlannedNeeds(from: Long, to: Long) = dao.observePlannedNeeds(from, to)
    val leftovers = db.leftoverDao().observeAvailable()

    suspend fun addMeal(day: Long, slot: MealSlotKind, recipeId: Long?, customName: String?, servings: Int, notes: String? = null): Long =
        db.withTransaction {
            dao.insertPlanIfMissing(MealPlanEntity(epochDay = day))
            dao.insertSlot(MealSlotEntity(epochDay = day, slot = slot, recipeId = recipeId, customName = customName?.trim()?.ifEmpty { null }, servings = servings.coerceAtLeast(1), notes = notes))
        }

    suspend fun updateMeal(slot: MealSlotEntity) = dao.updateSlot(slot)

    suspend fun deleteMeal(slot: MealSlotEntity) = dao.deleteSlot(slot)

    suspend fun restoreMeal(slot: MealSlotEntity) = db.withTransaction {
        dao.insertPlanIfMissing(MealPlanEntity(epochDay = slot.epochDay))
        dao.insertSlot(slot.copy(id = 0))
    }

    suspend fun setSkipped(slot: MealSlotEntity, skipped: Boolean) {
        if (slot.status == MealStatus.COOKED) return
        dao.updateSlot(slot.copy(status = if (skipped) MealStatus.SKIPPED else MealStatus.PLANNED))
    }

    suspend fun setDayNote(day: Long, occasion: String?, notes: String?) =
        dao.upsertPlan(MealPlanEntity(epochDay = day, occasion = occasion?.trim()?.ifEmpty { null }, notes = notes?.trim()?.ifEmpty { null }))

    /** Copies planned dishes (not their cooked status) from one range to another of equal length. */
    suspend fun copyRange(fromStart: Long, fromEnd: Long, toStart: Long, replaceExisting: Boolean) = db.withTransaction {
        val offset = toStart - fromStart
        val source = dao.getSlots(fromStart, fromEnd)
        if (replaceExisting) dao.deletePlannedBetween(toStart, toStart + (fromEnd - fromStart))
        source.forEach { row ->
            val s = row.slot
            dao.insertPlanIfMissing(MealPlanEntity(epochDay = s.epochDay + offset))
            dao.insertSlot(s.copy(id = 0, epochDay = s.epochDay + offset, status = MealStatus.PLANNED))
        }
        source.size
    }

    /**
     * Confirms a cooked meal: consumes exactly the confirmed quantities (FEFO), stores a completion record,
     * optionally records leftovers. Idempotent: a meal already completed is not consumed again.
     */
    suspend fun markCooked(
        slotId: Long,
        servingsCooked: Int,
        ingredients: List<ConfirmedIngredient>,
        leftoverPortions: Double,
        leftoverLocationId: Long?,
        note: String?,
    ): CookResult = db.withTransaction {
        val slot = requireNotNull(dao.getSlot(slotId))
        if (slot.status == MealStatus.COOKED || dao.countCompletions(slotId) > 0) return@withTransaction CookResult(emptyMap())
        val completionId = dao.insertCompletion(MealCompletionEntity(mealSlotId = slotId, completedAt = clock.nowMillis(), servingsCooked = servingsCooked, note = note))
        val shortfalls = mutableMapOf<Long, Double>()
        ingredients.filter { it.quantity > QUANTITY_EPSILON }.forEach { ing ->
            val missing = inventory.consume(ing.productId, ing.quantity, TxReference.MEAL_COMPLETION, completionId)
            if (missing > QUANTITY_EPSILON) shortfalls[ing.productId] = missing
        }
        dao.updateSlot(slot.copy(status = MealStatus.COOKED))
        if (leftoverPortions > QUANTITY_EPSILON) {
            val today = clock.today().toEpochDay()
            val name = slot.customName ?: slot.recipeId?.let { db.recipeDao().getRecipe(it)?.name } ?: "Leftover"
            db.leftoverDao().insert(
                PreparedFoodLeftoverEntity(name = name, mealSlotId = slotId, portions = leftoverPortions, storedEpochDay = today, locationId = leftoverLocationId, useByEpochDay = today + 1)
            )
        }
        CookResult(shortfalls)
    }

    suspend fun addLeftover(name: String, portions: Double, locationId: Long?, useByDay: Long) {
        db.leftoverDao().insert(PreparedFoodLeftoverEntity(name = name.trim(), portions = portions, storedEpochDay = clock.today().toEpochDay(), locationId = locationId, useByEpochDay = useByDay))
    }

    suspend fun setLeftoverStatus(l: PreparedFoodLeftoverEntity, status: LeftoverStatus) = db.leftoverDao().update(l.copy(status = status))
}
