package com.homebasket.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import com.homebasket.core.domain.engine.MealSlotKind
import com.homebasket.core.domain.model.UnitKind

// ---------------------------------------------------------------------------------------------
// Conventions
//  * Quantities are Double in the product's base unit (g / ml / pieces) - see UnitKind.
//  * Money is Long paise. No floating point money is ever stored.
//  * Calendar dates are LocalDate.toEpochDay() (Long); instants are epoch millis.
// ---------------------------------------------------------------------------------------------

enum class BudgetCategory { GROCERY, VEGETABLES, FRUITS, DAIRY, SNACKS, CLEANING, PERSONAL_CARE, CLOTHING, HOUSEHOLD }
enum class PurchaseFrequency { DAILY, WEEKLY, MONTHLY, OCCASIONAL }
enum class LocationKind { PANTRY, FRIDGE, FREEZER, STOREROOM, BASKET, OTHER }
enum class FoodCondition { FRESH, USE_SOON, SPOILED, DISCARDED }

/** Kind of date on a lot, because an estimate is not the same thing as a manufacturer's use-by date. */
enum class ExpiryType { ESTIMATE, USE_BY, BEST_BEFORE }

enum class TxType { OPENING, PURCHASE, CONSUME, TRANSFER_OUT, TRANSFER_IN, WASTE, ADJUST, RETURN, RESTORE }
enum class TxReference { PURCHASE_LINE, MEAL_COMPLETION, TRANSFER, WASTE, MANUAL }
enum class Diet { VEG, EGG, NON_VEG }
enum class MealStatus { PLANNED, COOKED, SKIPPED }
enum class ListType { TOMORROW, DAILY, WEEKLY, MONTHLY, CUSTOM }
enum class ListStatus { ACTIVE, COMPLETED }
enum class Priority { HIGH, NORMAL, LOW }

/** PICKED is provisional (ticked in the shop); only PURCHASED has touched stock and expenses. */
enum class ItemState { PENDING, PICKED, UNAVAILABLE, PURCHASED }
enum class ItemSource { MEALS, LOW_STOCK, RECURRING, MANUAL, MONTHLY, VOICE, SCAN }
enum class WasteReason { SPOILED, EXPIRED, LEFTOVER, OVERCOOKED, OTHER }
enum class LeftoverStatus { AVAILABLE, EATEN, DISCARDED }

@Entity(tableName = "household")
@Serializable
data class HouseholdEntity(
    @PrimaryKey val id: Long = 1,
    val name: String,
    val members: Int,
    val defaultServings: Int,
    val diet: Diet,
    /** java.time.DayOfWeek value 1..7. */
    val shoppingDay: Int,
    /** Comma separated store names, e.g. "DMart,Local kirana". */
    val preferredStores: String,
    val createdAt: Long,
)

@Entity(
    tableName = "household_settings",
    foreignKeys = [ForeignKey(entity = HouseholdEntity::class, parentColumns = ["id"], childColumns = ["householdId"], onDelete = ForeignKey.CASCADE)],
)
@Serializable
data class HouseholdSettingsEntity(
    @PrimaryKey val householdId: Long = 1,
    val monthlyBudgetPaise: Long,
    val weeklyVegetableBudgetPaise: Long,
    val warnPercent1: Int = 80,
    val warnPercent2: Int = 90,
    val warnPercent3: Int = 100,
    /** Days before an expiry date when an item is shown as "expiring soon". */
    val expiryWarningDays: Int = 2,
    val roundToPacks: Boolean = true,
)

@Entity(tableName = "product_category", indices = [Index(value = ["name"], unique = true)])
@Serializable
data class ProductCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val localName: String? = null,
    val budgetCategory: BudgetCategory,
    /** Perishables (vegetables, fruit, dairy) are bought daily/weekly and left off the monthly list by default. */
    val perishable: Boolean,
    val sortOrder: Int,
    val isCustom: Boolean = false,
)

@Entity(
    tableName = "product",
    foreignKeys = [
        ForeignKey(entity = ProductCategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = StorageLocationEntity::class, parentColumns = ["id"], childColumns = ["defaultLocationId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("categoryId"), Index("barcode"), Index("defaultLocationId"), Index(value = ["name"], unique = true)],
)
@Serializable
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val localName: String? = null,
    val categoryId: Long,
    val brand: String? = null,
    val imagePath: String? = null,
    val barcode: String? = null,
    val unitKind: UnitKind,
    /** Label for counted products: pcs, bunch, packet, bottle... */
    val countLabel: String = "pcs",
    /** Base units in one purchase pack; also the quantity [lastPricePaise] refers to. */
    val packSize: Double,
    val preferredStore: String? = null,
    val frequency: PurchaseFrequency = PurchaseFrequency.MONTHLY,
    val lastPurchaseEpochDay: Long? = null,
    /** Last price paid for one pack of [packSize]. Null until the user records a price. */
    val lastPricePaise: Long? = null,
    val minStock: Double = 0.0,
    val desiredStock: Double = 0.0,
    /** User-set monthly requirement in base units; 0 = derive from history. */
    val monthlyRequirement: Double = 0.0,
    /** Typical days the product stays good after purchase, used for estimated expiry dates. */
    val shelfLifeDays: Int? = null,
    val defaultLocationId: Long? = null,
    val archived: Boolean = false,
    val notes: String? = null,
)

@Entity(
    tableName = "product_variant",
    foreignKeys = [ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("productId"), Index("barcode")],
)
@Serializable
data class ProductVariantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    /** e.g. "Aashirvaad 5 kg". */
    val name: String,
    val packSize: Double,
    val barcode: String? = null,
    val lastPricePaise: Long? = null,
)

@Entity(tableName = "storage_location", indices = [Index(value = ["name"], unique = true)])
@Serializable
data class StorageLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: LocationKind,
    val sortOrder: Int,
    val isDefault: Boolean = false,
)

@Entity(
    tableName = "inventory_lot",
    foreignKeys = [
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = StorageLocationEntity::class, parentColumns = ["id"], childColumns = ["locationId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = PurchaseLineEntity::class, parentColumns = ["id"], childColumns = ["purchaseLineId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("productId"), Index("locationId"), Index("purchaseLineId"), Index("expiryEpochDay")],
)
@Serializable
data class InventoryLotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val locationId: Long,
    val quantity: Double,
    val addedAt: Long,
    val purchaseEpochDay: Long? = null,
    val expiryEpochDay: Long? = null,
    val expiryType: ExpiryType = ExpiryType.ESTIMATE,
    val openedEpochDay: Long? = null,
    val condition: FoodCondition = FoodCondition.FRESH,
    val purchaseLineId: Long? = null,
    val notes: String? = null,
)

/** Append-only audit trail. Every change to a lot quantity writes exactly one row here. */
@Entity(
    tableName = "inventory_transaction",
    foreignKeys = [
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = InventoryLotEntity::class, parentColumns = ["id"], childColumns = ["lotId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = StorageLocationEntity::class, parentColumns = ["id"], childColumns = ["locationId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("productId"), Index("lotId"), Index("locationId"), Index("epochDay"), Index(value = ["referenceType", "referenceId"])],
)
@Serializable
data class InventoryTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val lotId: Long?,
    val locationId: Long?,
    val type: TxType,
    /** Signed change in base units. */
    val quantityDelta: Double,
    val timestamp: Long,
    val epochDay: Long,
    val referenceType: TxReference,
    val referenceId: Long? = null,
    val note: String? = null,
)

@Entity(
    tableName = "stock_transfer",
    foreignKeys = [
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = StorageLocationEntity::class, parentColumns = ["id"], childColumns = ["fromLocationId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = StorageLocationEntity::class, parentColumns = ["id"], childColumns = ["toLocationId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("productId"), Index("fromLocationId"), Index("toLocationId")],
)
@Serializable
data class StockTransferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val fromLocationId: Long,
    val toLocationId: Long,
    val quantity: Double,
    val timestamp: Long,
)

@Entity(tableName = "recipe", indices = [Index(value = ["name"], unique = true)])
@Serializable
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val localName: String? = null,
    /** Comma separated MealSlotKind names the dish suits. */
    val mealTypes: String,
    val prepMinutes: Int,
    val cookMinutes: Int,
    val instructions: String,
    val imagePath: String? = null,
    val diet: Diet,
    /** Free tags, comma separated: "south-indian,quick,festival". */
    val tags: String = "",
    val defaultServings: Int,
    val isFavorite: Boolean = false,
    val isCustom: Boolean = false,
)

@Entity(
    tableName = "recipe_ingredient",
    foreignKeys = [
        ForeignKey(entity = RecipeEntity::class, parentColumns = ["id"], childColumns = ["recipeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["recipeId", "productId"], unique = true), Index("productId")],
)
@Serializable
data class RecipeIngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val productId: Long,
    /** Base units per serving. */
    val quantityPerServing: Double,
    val optional: Boolean = false,
)

/** One calendar day's plan: notes and occasions (Sunday special, festival). */
@Entity(tableName = "meal_plan")
@Serializable
data class MealPlanEntity(
    @PrimaryKey val epochDay: Long,
    val occasion: String? = null,
    val notes: String? = null,
)

@Entity(
    tableName = "meal_slot",
    foreignKeys = [
        ForeignKey(entity = MealPlanEntity::class, parentColumns = ["epochDay"], childColumns = ["epochDay"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = RecipeEntity::class, parentColumns = ["id"], childColumns = ["recipeId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("epochDay"), Index("recipeId")],
)
@Serializable
data class MealSlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val slot: MealSlotKind,
    /** Null for a custom dish without a recipe (no ingredients are calculated for it). */
    val recipeId: Long?,
    val customName: String? = null,
    val servings: Int,
    val status: MealStatus = MealStatus.PLANNED,
    val notes: String? = null,
)

@Entity(
    tableName = "meal_completion",
    foreignKeys = [ForeignKey(entity = MealSlotEntity::class, parentColumns = ["id"], childColumns = ["mealSlotId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["mealSlotId"], unique = true)],
)
@Serializable
data class MealCompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealSlotId: Long,
    val completedAt: Long,
    val servingsCooked: Int,
    val note: String? = null,
)

@Entity(tableName = "shopping_list", indices = [Index("type"), Index("status")])
@Serializable
data class ShoppingListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: ListType,
    val targetEpochDay: Long? = null,
    val store: String? = null,
    val status: ListStatus = ListStatus.ACTIVE,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "shopping_list_item",
    foreignKeys = [
        ForeignKey(entity = ShoppingListEntity::class, parentColumns = ["id"], childColumns = ["listId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = PurchaseLineEntity::class, parentColumns = ["id"], childColumns = ["purchaseLineId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = StorageLocationEntity::class, parentColumns = ["id"], childColumns = ["locationId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("listId"), Index("productId"), Index("purchaseLineId"), Index("locationId")],
)
@Serializable
data class ShoppingListItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val productId: Long?,
    /** Snapshot of the name so custom (non-catalog) items work too. */
    val name: String,
    val categoryName: String,
    val budgetCategory: BudgetCategory,
    val unitKind: UnitKind,
    val countLabel: String = "pcs",
    val quantity: Double,
    /** Estimate for the whole [quantity]; null when no price is known. */
    val estimatedPricePaise: Long? = null,
    val actualQuantity: Double? = null,
    /** Actual amount paid for [actualQuantity]. */
    val actualPricePaise: Long? = null,
    val store: String? = null,
    val priority: Priority = Priority.NORMAL,
    val state: ItemState = ItemState.PENDING,
    val substituteName: String? = null,
    val notes: String? = null,
    val reason: String? = null,
    val source: ItemSource = ItemSource.MANUAL,
    val purchaseLineId: Long? = null,
    /** Where the stock goes when the purchase is confirmed. */
    val locationId: Long? = null,
)

@Entity(
    tableName = "purchase",
    foreignKeys = [ForeignKey(entity = ShoppingListEntity::class, parentColumns = ["id"], childColumns = ["shoppingListId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("epochDay"), Index("shoppingListId")],
)
@Serializable
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val timestamp: Long,
    val store: String? = null,
    val shoppingListId: Long? = null,
    val note: String? = null,
)

@Entity(
    tableName = "purchase_line",
    foreignKeys = [
        ForeignKey(entity = PurchaseEntity::class, parentColumns = ["id"], childColumns = ["purchaseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = StorageLocationEntity::class, parentColumns = ["id"], childColumns = ["locationId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("purchaseId"), Index("productId"), Index("locationId"), Index("returnOfLineId")],
)
@Serializable
data class PurchaseLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseId: Long,
    val productId: Long?,
    val name: String,
    val unitKind: UnitKind,
    val countLabel: String = "pcs",
    /** Negative for a return. */
    val quantity: Double,
    /** Negative for a return/refund. */
    val amountPaise: Long,
    val budgetCategory: BudgetCategory,
    val locationId: Long?,
    val returnOfLineId: Long? = null,
    /** False for things like clothes that are expenses but not tracked as stock. */
    val addedToInventory: Boolean,
)

@Entity(tableName = "budget", indices = [Index(value = ["yearMonth", "category"], unique = true)])
@Serializable
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** e.g. 202610 for October 2026. */
    val yearMonth: Int,
    val category: BudgetCategory,
    val amountPaise: Long,
)

@Entity(
    tableName = "recurring_purchase_rule",
    foreignKeys = [ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["productId"], unique = true)],
)
@Serializable
data class RecurringPurchaseRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val quantity: Double,
    val intervalDays: Int,
    val nextDueEpochDay: Long,
    val active: Boolean = true,
)

@Entity(
    tableName = "food_waste",
    foreignKeys = [
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = InventoryLotEntity::class, parentColumns = ["id"], childColumns = ["lotId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("productId"), Index("lotId"), Index("epochDay")],
)
@Serializable
data class FoodWasteRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long?,
    val name: String,
    val quantity: Double,
    val unitKind: UnitKind,
    val countLabel: String = "pcs",
    val reason: WasteReason,
    val epochDay: Long,
    /** Estimated from the last recorded price; labelled as an estimate everywhere. */
    val estimatedCostPaise: Long? = null,
    val lotId: Long? = null,
    val note: String? = null,
)

@Entity(
    tableName = "prepared_leftover",
    foreignKeys = [
        ForeignKey(entity = MealSlotEntity::class, parentColumns = ["id"], childColumns = ["mealSlotId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = StorageLocationEntity::class, parentColumns = ["id"], childColumns = ["locationId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("mealSlotId"), Index("locationId"), Index("status")],
)
@Serializable
data class PreparedFoodLeftoverEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mealSlotId: Long? = null,
    val portions: Double,
    val storedEpochDay: Long,
    val locationId: Long? = null,
    val useByEpochDay: Long,
    val status: LeftoverStatus = LeftoverStatus.AVAILABLE,
    val note: String? = null,
)
