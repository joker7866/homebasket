package com.homebasket.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.homebasket.core.database.dao.BackupDao
import com.homebasket.core.database.dao.BudgetDao
import com.homebasket.core.database.dao.CatalogDao
import com.homebasket.core.database.dao.HouseholdDao
import com.homebasket.core.database.dao.InventoryDao
import com.homebasket.core.database.dao.LeftoverDao
import com.homebasket.core.database.dao.MealDao
import com.homebasket.core.database.dao.PurchaseDao
import com.homebasket.core.database.dao.RecipeDao
import com.homebasket.core.database.dao.RecurringDao
import com.homebasket.core.database.dao.ShoppingDao
import com.homebasket.core.database.dao.WasteDao
import com.homebasket.core.database.entity.BudgetEntity
import com.homebasket.core.database.entity.FoodWasteRecordEntity
import com.homebasket.core.database.entity.HouseholdEntity
import com.homebasket.core.database.entity.HouseholdSettingsEntity
import com.homebasket.core.database.entity.InventoryLotEntity
import com.homebasket.core.database.entity.InventoryTransactionEntity
import com.homebasket.core.database.entity.MealCompletionEntity
import com.homebasket.core.database.entity.MealPlanEntity
import com.homebasket.core.database.entity.MealSlotEntity
import com.homebasket.core.database.entity.PreparedFoodLeftoverEntity
import com.homebasket.core.database.entity.ProductCategoryEntity
import com.homebasket.core.database.entity.ProductEntity
import com.homebasket.core.database.entity.ProductVariantEntity
import com.homebasket.core.database.entity.PurchaseEntity
import com.homebasket.core.database.entity.PurchaseLineEntity
import com.homebasket.core.database.entity.RecipeEntity
import com.homebasket.core.database.entity.RecipeIngredientEntity
import com.homebasket.core.database.entity.RecurringPurchaseRuleEntity
import com.homebasket.core.database.entity.ShoppingListEntity
import com.homebasket.core.database.entity.ShoppingListItemEntity
import com.homebasket.core.database.entity.StockTransferEntity
import com.homebasket.core.database.entity.StorageLocationEntity

/**
 * Schema v1. Future versions must add a Migration (see the exported JSON in app/schemas);
 * destructive migration is deliberately not enabled because it would erase household stock.
 */
@Database(
    version = 1,
    exportSchema = true,
    entities = [
        HouseholdEntity::class,
        HouseholdSettingsEntity::class,
        ProductCategoryEntity::class,
        ProductEntity::class,
        ProductVariantEntity::class,
        StorageLocationEntity::class,
        InventoryLotEntity::class,
        InventoryTransactionEntity::class,
        StockTransferEntity::class,
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        MealPlanEntity::class,
        MealSlotEntity::class,
        MealCompletionEntity::class,
        ShoppingListEntity::class,
        ShoppingListItemEntity::class,
        PurchaseEntity::class,
        PurchaseLineEntity::class,
        BudgetEntity::class,
        RecurringPurchaseRuleEntity::class,
        FoodWasteRecordEntity::class,
        PreparedFoodLeftoverEntity::class,
    ],
)
abstract class HomeBasketDatabase : RoomDatabase() {
    abstract fun householdDao(): HouseholdDao
    abstract fun catalogDao(): CatalogDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun recipeDao(): RecipeDao
    abstract fun mealDao(): MealDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun budgetDao(): BudgetDao
    abstract fun wasteDao(): WasteDao
    abstract fun leftoverDao(): LeftoverDao
    abstract fun recurringDao(): RecurringDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val NAME = "homebasket.db"
    }
}
