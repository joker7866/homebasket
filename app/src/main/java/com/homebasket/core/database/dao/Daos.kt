package com.homebasket.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.homebasket.core.database.entity.BudgetEntity
import com.homebasket.core.database.entity.FoodWasteRecordEntity
import com.homebasket.core.database.entity.HouseholdEntity
import com.homebasket.core.database.entity.HouseholdSettingsEntity
import com.homebasket.core.database.entity.InventoryLotEntity
import com.homebasket.core.database.entity.InventoryTransactionEntity
import com.homebasket.core.database.entity.ListType
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
import kotlinx.coroutines.flow.Flow

/** Lots that may be used for cooking: positive, not spoiled/discarded, and not past their date. */
private const val USABLE_LOT =
    "l.quantity > 0.000001 AND l.condition IN ('FRESH','USE_SOON') AND (l.expiryEpochDay IS NULL OR l.expiryEpochDay >= :today)"

private const val PRODUCT_ROW_SELECT =
    "SELECT p.*, c.name AS categoryName, c.budgetCategory AS budgetCategory, c.perishable AS perishable " +
        "FROM product p JOIN product_category c ON c.id = p.categoryId"

private const val LOT_ROW_SELECT =
    "SELECT l.*, p.name AS productName, p.localName AS productLocalName, p.unitKind AS unitKind, p.countLabel AS countLabel, " +
        "p.packSize AS packSize, p.lastPricePaise AS lastPricePaise, p.categoryId AS categoryId, c.name AS categoryName, " +
        "c.budgetCategory AS budgetCategory, s.name AS locationName, s.kind AS locationKind " +
        "FROM inventory_lot l JOIN product p ON p.id = l.productId JOIN product_category c ON c.id = p.categoryId " +
        "JOIN storage_location s ON s.id = l.locationId"

@Dao
interface HouseholdDao {
    @Query("SELECT * FROM household WHERE id = 1")
    fun observeHousehold(): Flow<HouseholdEntity?>

    @Query("SELECT * FROM household WHERE id = 1")
    suspend fun getHousehold(): HouseholdEntity?

    @Query("SELECT * FROM household_settings WHERE householdId = 1")
    fun observeSettings(): Flow<HouseholdSettingsEntity?>

    @Query("SELECT * FROM household_settings WHERE householdId = 1")
    suspend fun getSettings(): HouseholdSettingsEntity?

    @Upsert suspend fun upsertHousehold(h: HouseholdEntity)
    @Upsert suspend fun upsertSettings(s: HouseholdSettingsEntity)
}

@Dao
interface CatalogDao {
    @Query("SELECT * FROM product_category ORDER BY sortOrder, name")
    fun observeCategories(): Flow<List<ProductCategoryEntity>>

    @Query("SELECT * FROM product_category ORDER BY sortOrder, name")
    suspend fun getCategories(): List<ProductCategoryEntity>

    @Query("SELECT * FROM product_category WHERE id = :id")
    suspend fun getCategory(id: Long): ProductCategoryEntity?

    @Insert suspend fun insertCategory(c: ProductCategoryEntity): Long
    @Update suspend fun updateCategory(c: ProductCategoryEntity)

    @Query("SELECT COUNT(*) FROM product WHERE categoryId = :categoryId")
    suspend fun countProductsInCategory(categoryId: Long): Int

    @Delete suspend fun deleteCategory(c: ProductCategoryEntity)

    @Query("$PRODUCT_ROW_SELECT WHERE p.archived = 0 ORDER BY c.sortOrder, p.name")
    fun observeProducts(): Flow<List<ProductRow>>

    @Query("$PRODUCT_ROW_SELECT WHERE p.archived = 0 ORDER BY c.sortOrder, p.name")
    suspend fun getProducts(): List<ProductRow>

    @Query("$PRODUCT_ROW_SELECT WHERE p.id = :id")
    suspend fun getProductRow(id: Long): ProductRow?

    @Query("$PRODUCT_ROW_SELECT WHERE p.id = :id")
    fun observeProductRow(id: Long): Flow<ProductRow?>

    @Query("SELECT * FROM product WHERE id = :id")
    suspend fun getProduct(id: Long): ProductEntity?

    @Query("SELECT * FROM product WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getProductByName(name: String): ProductEntity?

    @Query("SELECT * FROM product WHERE barcode = :code LIMIT 1")
    suspend fun getProductByBarcode(code: String): ProductEntity?

    @Query("SELECT * FROM product_variant WHERE barcode = :code LIMIT 1")
    suspend fun getVariantByBarcode(code: String): ProductVariantEntity?

    @Query("SELECT COUNT(*) FROM product")
    suspend fun countProducts(): Int

    @Insert suspend fun insertProduct(p: ProductEntity): Long
    @Update suspend fun updateProduct(p: ProductEntity)

    @Query("UPDATE product SET lastPricePaise = :pricePaise, lastPurchaseEpochDay = :day WHERE id = :id")
    suspend fun recordPrice(id: Long, pricePaise: Long?, day: Long)

    @Query("UPDATE product SET lastPurchaseEpochDay = :day WHERE id = :id")
    suspend fun recordPurchaseDay(id: Long, day: Long)

    @Query("SELECT * FROM product_variant WHERE productId = :productId ORDER BY packSize")
    fun observeVariants(productId: Long): Flow<List<ProductVariantEntity>>

    @Insert suspend fun insertVariant(v: ProductVariantEntity): Long
    @Delete suspend fun deleteVariant(v: ProductVariantEntity)
}

@Dao
interface InventoryDao {
    @Query("SELECT * FROM storage_location ORDER BY sortOrder, name")
    fun observeLocations(): Flow<List<StorageLocationEntity>>

    @Query("SELECT * FROM storage_location ORDER BY sortOrder, name")
    suspend fun getLocations(): List<StorageLocationEntity>

    @Query("SELECT * FROM storage_location WHERE id = :id")
    suspend fun getLocation(id: Long): StorageLocationEntity?

    @Insert suspend fun insertLocation(l: StorageLocationEntity): Long
    @Update suspend fun updateLocation(l: StorageLocationEntity)
    @Delete suspend fun deleteLocation(l: StorageLocationEntity)

    @Query("SELECT COUNT(*) FROM inventory_lot WHERE locationId = :locationId AND quantity > 0.000001")
    suspend fun countLotsAt(locationId: Long): Int

    @Query("$LOT_ROW_SELECT WHERE l.quantity > 0.000001 AND l.condition != 'DISCARDED' ORDER BY p.name, l.expiryEpochDay IS NULL, l.expiryEpochDay")
    fun observeLotRows(): Flow<List<LotRow>>

    @Query("$LOT_ROW_SELECT WHERE l.quantity > 0.000001 AND l.condition != 'DISCARDED' ORDER BY p.name")
    suspend fun getLotRows(): List<LotRow>

    @Query("$LOT_ROW_SELECT WHERE l.productId = :productId AND l.quantity > 0.000001 AND l.condition != 'DISCARDED' ORDER BY l.expiryEpochDay IS NULL, l.expiryEpochDay")
    fun observeLotRowsForProduct(productId: Long): Flow<List<LotRow>>

    @Query("SELECT * FROM inventory_lot WHERE id = :id")
    suspend fun getLot(id: Long): InventoryLotEntity?

    /** First-expiring-first-out order. Undated lots are used after dated ones, oldest first. */
    @Query(
        "SELECT l.* FROM inventory_lot l WHERE l.productId = :productId AND $USABLE_LOT " +
            "ORDER BY l.expiryEpochDay IS NULL, l.expiryEpochDay, l.addedAt"
    )
    suspend fun getUsableLotsFefo(productId: Long, today: Long): List<InventoryLotEntity>

    @Query("SELECT l.productId AS productId, SUM(l.quantity) AS quantity FROM inventory_lot l WHERE $USABLE_LOT GROUP BY l.productId")
    suspend fun getUsableTotals(today: Long): List<ProductQuantity>

    @Query("SELECT l.productId AS productId, SUM(l.quantity) AS quantity FROM inventory_lot l WHERE $USABLE_LOT GROUP BY l.productId")
    fun observeUsableTotals(today: Long): Flow<List<ProductQuantity>>

    @Query("SELECT * FROM inventory_lot WHERE purchaseLineId = :lineId AND quantity > 0.000001")
    suspend fun getLotsForPurchaseLine(lineId: Long): List<InventoryLotEntity>

    @Insert suspend fun insertLot(l: InventoryLotEntity): Long
    @Update suspend fun updateLot(l: InventoryLotEntity)

    @Insert suspend fun insertTransaction(t: InventoryTransactionEntity): Long
    @Insert suspend fun insertTransfer(t: StockTransferEntity): Long

    @Query(
        "SELECT t.*, p.name AS productName, p.unitKind AS unitKind, p.countLabel AS countLabel, s.name AS locationName " +
            "FROM inventory_transaction t JOIN product p ON p.id = t.productId LEFT JOIN storage_location s ON s.id = t.locationId " +
            "WHERE (:productId IS NULL OR t.productId = :productId) ORDER BY t.timestamp DESC, t.id DESC LIMIT :limit"
    )
    fun observeTransactions(productId: Long?, limit: Int): Flow<List<TransactionRow>>

    @Query(
        "SELECT t.*, p.name AS productName, p.unitKind AS unitKind, p.countLabel AS countLabel, s.name AS locationName " +
            "FROM inventory_transaction t JOIN product p ON p.id = t.productId LEFT JOIN storage_location s ON s.id = t.locationId " +
            "WHERE t.epochDay BETWEEN :start AND :end ORDER BY t.timestamp"
    )
    suspend fun getTransactionsBetween(start: Long, end: Long): List<TransactionRow>

    /** Positive quantity consumed (cooking + manual use) per product in a date range. */
    @Query(
        "SELECT productId, -SUM(quantityDelta) AS quantity FROM inventory_transaction " +
            "WHERE type = 'CONSUME' AND epochDay BETWEEN :start AND :end GROUP BY productId"
    )
    suspend fun getConsumptionBetween(start: Long, end: Long): List<ProductQuantity>
}

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipe ORDER BY isFavorite DESC, name")
    fun observeRecipes(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipe ORDER BY name")
    suspend fun getRecipes(): List<RecipeEntity>

    @Query("SELECT * FROM recipe WHERE id = :id")
    suspend fun getRecipe(id: Long): RecipeEntity?

    @Query("SELECT * FROM recipe WHERE id = :id")
    fun observeRecipe(id: Long): Flow<RecipeEntity?>

    @Query("SELECT * FROM recipe WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getRecipeByName(name: String): RecipeEntity?

    @Query(
        "SELECT ri.*, p.name AS productName, p.localName AS productLocalName, p.unitKind AS unitKind, p.countLabel AS countLabel, " +
            "p.packSize AS packSize, p.lastPricePaise AS lastPricePaise FROM recipe_ingredient ri JOIN product p ON p.id = ri.productId " +
            "WHERE ri.recipeId = :recipeId ORDER BY p.name"
    )
    suspend fun getIngredients(recipeId: Long): List<RecipeIngredientRow>

    @Query(
        "SELECT ri.*, p.name AS productName, p.localName AS productLocalName, p.unitKind AS unitKind, p.countLabel AS countLabel, " +
            "p.packSize AS packSize, p.lastPricePaise AS lastPricePaise FROM recipe_ingredient ri JOIN product p ON p.id = ri.productId " +
            "WHERE ri.recipeId = :recipeId ORDER BY p.name"
    )
    fun observeIngredients(recipeId: Long): Flow<List<RecipeIngredientRow>>

    @Query(
        "SELECT ri.*, p.name AS productName, p.localName AS productLocalName, p.unitKind AS unitKind, p.countLabel AS countLabel, " +
            "p.packSize AS packSize, p.lastPricePaise AS lastPricePaise FROM recipe_ingredient ri JOIN product p ON p.id = ri.productId"
    )
    fun observeAllIngredients(): Flow<List<RecipeIngredientRow>>

    @Query("SELECT COUNT(*) FROM recipe")
    suspend fun countRecipes(): Int

    @Insert suspend fun insertRecipe(r: RecipeEntity): Long
    @Update suspend fun updateRecipe(r: RecipeEntity)
    @Delete suspend fun deleteRecipe(r: RecipeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredients(items: List<RecipeIngredientEntity>)

    @Query("DELETE FROM recipe_ingredient WHERE recipeId = :recipeId")
    suspend fun clearIngredients(recipeId: Long)
}

@Dao
interface MealDao {
    @Query(
        "SELECT ms.*, r.name AS recipeName, r.localName AS recipeLocalName, r.diet AS diet FROM meal_slot ms " +
            "LEFT JOIN recipe r ON r.id = ms.recipeId WHERE ms.epochDay BETWEEN :start AND :end ORDER BY ms.epochDay, ms.slot"
    )
    fun observeSlots(start: Long, end: Long): Flow<List<MealSlotRow>>

    @Query(
        "SELECT ms.*, r.name AS recipeName, r.localName AS recipeLocalName, r.diet AS diet FROM meal_slot ms " +
            "LEFT JOIN recipe r ON r.id = ms.recipeId WHERE ms.epochDay BETWEEN :start AND :end ORDER BY ms.epochDay, ms.slot"
    )
    suspend fun getSlots(start: Long, end: Long): List<MealSlotRow>

    @Query("SELECT * FROM meal_slot WHERE id = :id")
    suspend fun getSlot(id: Long): MealSlotEntity?

    /** Ingredient needs of meals that are still only planned (cooked/skipped meals need nothing). */
    @Query(
        "SELECT ms.id AS mealSlotId, ms.epochDay AS epochDay, ms.slot AS slot, r.name AS label, ri.productId AS productId, " +
            "ri.quantityPerServing * ms.servings AS quantity FROM meal_slot ms JOIN recipe r ON r.id = ms.recipeId " +
            "JOIN recipe_ingredient ri ON ri.recipeId = r.id WHERE ms.status = 'PLANNED' AND ri.optional = 0 " +
            "AND ms.epochDay BETWEEN :fromDay AND :toDay"
    )
    suspend fun getPlannedNeeds(fromDay: Long, toDay: Long): List<PlannedNeedRow>

    @Query(
        "SELECT ms.id AS mealSlotId, ms.epochDay AS epochDay, ms.slot AS slot, r.name AS label, ri.productId AS productId, " +
            "ri.quantityPerServing * ms.servings AS quantity FROM meal_slot ms JOIN recipe r ON r.id = ms.recipeId " +
            "JOIN recipe_ingredient ri ON ri.recipeId = r.id WHERE ms.status = 'PLANNED' AND ri.optional = 0 " +
            "AND ms.epochDay BETWEEN :fromDay AND :toDay"
    )
    fun observePlannedNeeds(fromDay: Long, toDay: Long): Flow<List<PlannedNeedRow>>

    @Query("SELECT * FROM meal_plan WHERE epochDay BETWEEN :start AND :end")
    fun observePlans(start: Long, end: Long): Flow<List<MealPlanEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlanIfMissing(p: MealPlanEntity)

    @Upsert suspend fun upsertPlan(p: MealPlanEntity)

    @Insert suspend fun insertSlot(s: MealSlotEntity): Long
    @Update suspend fun updateSlot(s: MealSlotEntity)
    @Delete suspend fun deleteSlot(s: MealSlotEntity)

    @Query("DELETE FROM meal_slot WHERE epochDay BETWEEN :start AND :end AND status = 'PLANNED'")
    suspend fun deletePlannedBetween(start: Long, end: Long)

    @Insert suspend fun insertCompletion(c: MealCompletionEntity): Long

    @Query("SELECT COUNT(*) FROM meal_completion WHERE mealSlotId = :slotId")
    suspend fun countCompletions(slotId: Long): Int

    @Query("SELECT COUNT(*) FROM meal_slot WHERE status = 'COOKED' AND epochDay BETWEEN :start AND :end")
    suspend fun countCooked(start: Long, end: Long): Int
}

@Dao
interface ShoppingDao {
    @Query(
        "SELECT sl.id, sl.name, sl.type, sl.targetEpochDay, sl.status, sl.updatedAt, " +
            "(SELECT COUNT(*) FROM shopping_list_item i WHERE i.listId = sl.id) AS itemCount, " +
            "(SELECT COUNT(*) FROM shopping_list_item i WHERE i.listId = sl.id AND i.state IN ('PURCHASED','UNAVAILABLE')) AS doneCount, " +
            "(SELECT SUM(i.estimatedPricePaise) FROM shopping_list_item i WHERE i.listId = sl.id) AS estimatedPaise " +
            "FROM shopping_list sl ORDER BY sl.status, sl.updatedAt DESC"
    )
    fun observeListSummaries(): Flow<List<ShoppingListSummary>>

    @Query("SELECT * FROM shopping_list WHERE id = :id")
    fun observeList(id: Long): Flow<ShoppingListEntity?>

    @Query("SELECT * FROM shopping_list WHERE id = :id")
    suspend fun getList(id: Long): ShoppingListEntity?

    @Query("SELECT * FROM shopping_list WHERE type = :type AND status = 'ACTIVE' AND (:day IS NULL OR targetEpochDay = :day) ORDER BY updatedAt DESC LIMIT 1")
    suspend fun findActiveList(type: ListType, day: Long?): ShoppingListEntity?

    @Insert suspend fun insertList(l: ShoppingListEntity): Long
    @Update suspend fun updateList(l: ShoppingListEntity)
    @Delete suspend fun deleteList(l: ShoppingListEntity)

    @Query("UPDATE shopping_list SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("SELECT * FROM shopping_list_item WHERE listId = :listId ORDER BY state = 'PURCHASED', priority, categoryName, name")
    fun observeItems(listId: Long): Flow<List<ShoppingListItemEntity>>

    @Query("SELECT * FROM shopping_list_item WHERE listId = :listId ORDER BY categoryName, name")
    suspend fun getItems(listId: Long): List<ShoppingListItemEntity>

    @Query("SELECT * FROM shopping_list_item WHERE id = :id")
    suspend fun getItem(id: Long): ShoppingListItemEntity?

    @Query("SELECT * FROM shopping_list_item WHERE listId = :listId AND productId = :productId AND state = 'PENDING' LIMIT 1")
    suspend fun findPendingItem(listId: Long, productId: Long): ShoppingListItemEntity?

    @Insert suspend fun insertItem(i: ShoppingListItemEntity): Long
    @Insert suspend fun insertItems(i: List<ShoppingListItemEntity>)
    @Update suspend fun updateItem(i: ShoppingListItemEntity)
    @Delete suspend fun deleteItem(i: ShoppingListItemEntity)

    @Query("DELETE FROM shopping_list_item WHERE listId = :listId AND state = 'PENDING' AND source != 'MANUAL' AND source != 'VOICE' AND source != 'SCAN'")
    suspend fun clearGeneratedPending(listId: Long)

    @Query(
        "SELECT COUNT(*) FROM shopping_list_item i JOIN shopping_list sl ON sl.id = i.listId " +
            "WHERE sl.type = 'TOMORROW' AND sl.status = 'ACTIVE' AND sl.targetEpochDay = :day AND i.state IN ('PENDING','PICKED')"
    )
    fun observeOpenTomorrowCount(day: Long): Flow<Int>
}

@Dao
interface PurchaseDao {
    @Insert suspend fun insertPurchase(p: PurchaseEntity): Long
    @Insert suspend fun insertLine(l: PurchaseLineEntity): Long

    @Query("SELECT * FROM purchase WHERE id = :id")
    suspend fun getPurchase(id: Long): PurchaseEntity?

    @Query("SELECT * FROM purchase_line WHERE id = :id")
    suspend fun getLine(id: Long): PurchaseLineEntity?

    @Query("SELECT * FROM purchase_line WHERE purchaseId = :purchaseId ORDER BY id")
    fun observeLines(purchaseId: Long): Flow<List<PurchaseLineEntity>>

    @Query("SELECT COALESCE(-SUM(quantity), 0) FROM purchase_line WHERE returnOfLineId = :lineId")
    suspend fun returnedQuantity(lineId: Long): Double

    @Query(
        "SELECT p.id, p.epochDay, p.store, p.note, COUNT(pl.id) AS lineCount, COALESCE(SUM(pl.amountPaise), 0) AS totalPaise " +
            "FROM purchase p LEFT JOIN purchase_line pl ON pl.purchaseId = p.id GROUP BY p.id ORDER BY p.epochDay DESC, p.id DESC LIMIT :limit"
    )
    fun observePurchaseSummaries(limit: Int): Flow<List<PurchaseSummary>>

    @Query(
        "SELECT pl.*, p.epochDay AS epochDay, p.store AS store, c.name AS categoryName, pr.packSize AS packSize FROM purchase_line pl " +
            "JOIN purchase p ON p.id = pl.purchaseId LEFT JOIN product pr ON pr.id = pl.productId " +
            "LEFT JOIN product_category c ON c.id = pr.categoryId WHERE p.epochDay BETWEEN :start AND :end ORDER BY p.epochDay, pl.id"
    )
    suspend fun getLinesBetween(start: Long, end: Long): List<PurchaseLineRow>

    @Query(
        "SELECT pl.*, p.epochDay AS epochDay, p.store AS store, c.name AS categoryName, pr.packSize AS packSize FROM purchase_line pl " +
            "JOIN purchase p ON p.id = pl.purchaseId LEFT JOIN product pr ON pr.id = pl.productId " +
            "LEFT JOIN product_category c ON c.id = pr.categoryId WHERE p.epochDay BETWEEN :start AND :end ORDER BY p.epochDay, pl.id"
    )
    fun observeLinesBetween(start: Long, end: Long): Flow<List<PurchaseLineRow>>

    @Query(
        "SELECT pl.budgetCategory AS budgetCategory, SUM(pl.amountPaise) AS totalPaise FROM purchase_line pl " +
            "JOIN purchase p ON p.id = pl.purchaseId WHERE p.epochDay BETWEEN :start AND :end GROUP BY pl.budgetCategory"
    )
    fun observeSpendByCategory(start: Long, end: Long): Flow<List<CategorySpend>>

    @Query(
        "SELECT pl.budgetCategory AS budgetCategory, SUM(pl.amountPaise) AS totalPaise FROM purchase_line pl " +
            "JOIN purchase p ON p.id = pl.purchaseId WHERE p.epochDay BETWEEN :start AND :end GROUP BY pl.budgetCategory"
    )
    suspend fun getSpendByCategory(start: Long, end: Long): List<CategorySpend>

    @Query(
        "SELECT pl.productId AS productId, SUM(pl.quantity) AS quantity FROM purchase_line pl JOIN purchase p ON p.id = pl.purchaseId " +
            "WHERE pl.productId IS NOT NULL AND p.epochDay BETWEEN :start AND :end GROUP BY pl.productId"
    )
    suspend fun getPurchasedQuantities(start: Long, end: Long): List<ProductQuantity>
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budget WHERE yearMonth = :yearMonth")
    fun observeBudgets(yearMonth: Int): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budget WHERE yearMonth = :yearMonth")
    suspend fun getBudgets(yearMonth: Int): List<BudgetEntity>

    @Upsert suspend fun upsert(b: BudgetEntity)

    @Query("SELECT * FROM budget WHERE yearMonth = :yearMonth AND category = :category")
    suspend fun get(yearMonth: Int, category: com.homebasket.core.database.entity.BudgetCategory): BudgetEntity?
}

@Dao
interface WasteDao {
    @Insert suspend fun insert(w: FoodWasteRecordEntity): Long
    @Delete suspend fun delete(w: FoodWasteRecordEntity)

    @Query("SELECT * FROM food_waste WHERE epochDay BETWEEN :start AND :end ORDER BY epochDay DESC, id DESC")
    fun observeBetween(start: Long, end: Long): Flow<List<FoodWasteRecordEntity>>

    @Query("SELECT * FROM food_waste WHERE epochDay BETWEEN :start AND :end ORDER BY epochDay DESC, id DESC")
    suspend fun getBetween(start: Long, end: Long): List<FoodWasteRecordEntity>
}

@Dao
interface LeftoverDao {
    @Query("SELECT * FROM prepared_leftover WHERE status = 'AVAILABLE' ORDER BY useByEpochDay")
    fun observeAvailable(): Flow<List<PreparedFoodLeftoverEntity>>

    @Insert suspend fun insert(l: PreparedFoodLeftoverEntity): Long
    @Update suspend fun update(l: PreparedFoodLeftoverEntity)
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring_purchase_rule ORDER BY nextDueEpochDay")
    fun observeRules(): Flow<List<RecurringPurchaseRuleEntity>>

    @Query("SELECT * FROM recurring_purchase_rule WHERE active = 1 AND nextDueEpochDay <= :day")
    suspend fun getDue(day: Long): List<RecurringPurchaseRuleEntity>

    @Query("SELECT * FROM recurring_purchase_rule WHERE productId = :productId")
    suspend fun getForProduct(productId: Long): RecurringPurchaseRuleEntity?

    @Upsert suspend fun upsert(r: RecurringPurchaseRuleEntity)
    @Delete suspend fun delete(r: RecurringPurchaseRuleEntity)
}
