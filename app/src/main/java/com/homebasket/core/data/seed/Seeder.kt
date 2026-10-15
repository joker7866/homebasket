package com.homebasket.core.data.seed

import androidx.room.withTransaction
import com.homebasket.core.data.Clock
import com.homebasket.core.database.HomeBasketDatabase
import com.homebasket.core.database.entity.Diet
import com.homebasket.core.database.entity.HouseholdEntity
import com.homebasket.core.database.entity.HouseholdSettingsEntity
import com.homebasket.core.database.entity.ProductCategoryEntity
import com.homebasket.core.database.entity.ProductEntity
import com.homebasket.core.database.entity.RecipeEntity
import com.homebasket.core.database.entity.RecipeIngredientEntity
import com.homebasket.core.database.entity.StorageLocationEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Seeder @Inject constructor(
    private val db: HomeBasketDatabase,
    private val clock: Clock,
) {
    /** Idempotent: only fills an empty database. Never touches existing user data. */
    suspend fun seedIfEmpty() = db.withTransaction {
        if (db.householdDao().getHousehold() == null) {
            db.householdDao().upsertHousehold(
                HouseholdEntity(name = "My Home", members = 4, defaultServings = 4, diet = Diet.VEG, shoppingDay = 7, preferredStores = "DMart,Local market", createdAt = clock.nowMillis())
            )
            db.householdDao().upsertSettings(HouseholdSettingsEntity(monthlyBudgetPaise = 800_000, weeklyVegetableBudgetPaise = 80_000))
        }
        if (db.inventoryDao().getLocations().isEmpty()) {
            SeedData.locations.forEachIndexed { i, l ->
                db.inventoryDao().insertLocation(StorageLocationEntity(name = l.name, kind = l.kind, sortOrder = i, isDefault = true))
            }
        }
        if (db.catalogDao().countProducts() == 0) seedCatalog()
        if (db.recipeDao().countRecipes() == 0) seedRecipes()
    }

    private suspend fun seedCatalog() {
        val locationsByKind = db.inventoryDao().getLocations().associateBy { it.kind }
        SeedData.catalog.forEachIndexed { index, (category, products) ->
            val categoryId = db.catalogDao().getCategories().firstOrNull { it.name == category.name }?.id
                ?: db.catalogDao().insertCategory(
                    ProductCategoryEntity(name = category.name, budgetCategory = category.budget, perishable = category.perishable, sortOrder = index)
                )
            products.forEach { p ->
                db.catalogDao().insertProduct(
                    ProductEntity(
                        name = p.name,
                        categoryId = categoryId,
                        unitKind = p.kind,
                        countLabel = p.label,
                        packSize = p.pack,
                        frequency = p.frequency,
                        minStock = p.minStock,
                        desiredStock = p.desiredStock,
                        shelfLifeDays = p.shelfLifeDays,
                        defaultLocationId = locationsByKind[p.location]?.id,
                    )
                )
            }
        }
    }

    private suspend fun seedRecipes() {
        val productIds = db.catalogDao().getProducts().associate { it.product.name to it.product.id }
        SeedData.recipes.forEach { r ->
            val id = db.recipeDao().insertRecipe(
                RecipeEntity(
                    name = r.name,
                    mealTypes = r.slots.joinToString(",") { it.name },
                    prepMinutes = r.prep,
                    cookMinutes = r.cook,
                    instructions = r.instructions,
                    diet = r.diet,
                    tags = r.tags,
                    defaultServings = r.servings,
                )
            )
            db.recipeDao().insertIngredients(
                r.ingredients.mapNotNull { (name, qty) ->
                    productIds[name]?.let { RecipeIngredientEntity(recipeId = id, productId = it, quantityPerServing = qty) }
                }
            )
        }
    }
}
