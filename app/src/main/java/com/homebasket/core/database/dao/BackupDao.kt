package com.homebasket.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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

/** Whole-table reads and inserts used only by backup/restore, which keep every primary key intact. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM household") suspend fun households(): List<HouseholdEntity>
    @Query("SELECT * FROM household_settings") suspend fun settings(): List<HouseholdSettingsEntity>
    @Query("SELECT * FROM product_category") suspend fun categories(): List<ProductCategoryEntity>
    @Query("SELECT * FROM product") suspend fun products(): List<ProductEntity>
    @Query("SELECT * FROM product_variant") suspend fun variants(): List<ProductVariantEntity>
    @Query("SELECT * FROM storage_location") suspend fun locations(): List<StorageLocationEntity>
    @Query("SELECT * FROM inventory_lot") suspend fun lots(): List<InventoryLotEntity>
    @Query("SELECT * FROM inventory_transaction") suspend fun transactions(): List<InventoryTransactionEntity>
    @Query("SELECT * FROM stock_transfer") suspend fun transfers(): List<StockTransferEntity>
    @Query("SELECT * FROM recipe") suspend fun recipes(): List<RecipeEntity>
    @Query("SELECT * FROM recipe_ingredient") suspend fun recipeIngredients(): List<RecipeIngredientEntity>
    @Query("SELECT * FROM meal_plan") suspend fun mealPlans(): List<MealPlanEntity>
    @Query("SELECT * FROM meal_slot") suspend fun mealSlots(): List<MealSlotEntity>
    @Query("SELECT * FROM meal_completion") suspend fun mealCompletions(): List<MealCompletionEntity>
    @Query("SELECT * FROM shopping_list") suspend fun shoppingLists(): List<ShoppingListEntity>
    @Query("SELECT * FROM shopping_list_item") suspend fun shoppingItems(): List<ShoppingListItemEntity>
    @Query("SELECT * FROM purchase") suspend fun purchases(): List<PurchaseEntity>
    @Query("SELECT * FROM purchase_line") suspend fun purchaseLines(): List<PurchaseLineEntity>
    @Query("SELECT * FROM budget") suspend fun budgets(): List<BudgetEntity>
    @Query("SELECT * FROM recurring_purchase_rule") suspend fun recurringRules(): List<RecurringPurchaseRuleEntity>
    @Query("SELECT * FROM food_waste") suspend fun waste(): List<FoodWasteRecordEntity>
    @Query("SELECT * FROM prepared_leftover") suspend fun leftovers(): List<PreparedFoodLeftoverEntity>

    @Insert suspend fun insertHouseholds(v: List<HouseholdEntity>)
    @Insert suspend fun insertSettings(v: List<HouseholdSettingsEntity>)
    @Insert suspend fun insertCategories(v: List<ProductCategoryEntity>)
    @Insert suspend fun insertProducts(v: List<ProductEntity>)
    @Insert suspend fun insertVariants(v: List<ProductVariantEntity>)
    @Insert suspend fun insertLocations(v: List<StorageLocationEntity>)
    @Insert suspend fun insertLots(v: List<InventoryLotEntity>)
    @Insert suspend fun insertTransactions(v: List<InventoryTransactionEntity>)
    @Insert suspend fun insertTransfers(v: List<StockTransferEntity>)
    @Insert suspend fun insertRecipes(v: List<RecipeEntity>)
    @Insert suspend fun insertRecipeIngredients(v: List<RecipeIngredientEntity>)
    @Insert suspend fun insertMealPlans(v: List<MealPlanEntity>)
    @Insert suspend fun insertMealSlots(v: List<MealSlotEntity>)
    @Insert suspend fun insertMealCompletions(v: List<MealCompletionEntity>)
    @Insert suspend fun insertShoppingLists(v: List<ShoppingListEntity>)
    @Insert suspend fun insertShoppingItems(v: List<ShoppingListItemEntity>)
    @Insert suspend fun insertPurchases(v: List<PurchaseEntity>)
    @Insert suspend fun insertPurchaseLines(v: List<PurchaseLineEntity>)
    @Insert suspend fun insertBudgets(v: List<BudgetEntity>)
    @Insert suspend fun insertRecurringRules(v: List<RecurringPurchaseRuleEntity>)
    @Insert suspend fun insertWaste(v: List<FoodWasteRecordEntity>)
    @Insert suspend fun insertLeftovers(v: List<PreparedFoodLeftoverEntity>)
}
