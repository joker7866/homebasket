package com.homebasket.core.database.dao

import androidx.room.Embedded
import com.homebasket.core.database.entity.BudgetCategory
import com.homebasket.core.database.entity.Diet
import com.homebasket.core.database.entity.FoodWasteRecordEntity
import com.homebasket.core.database.entity.InventoryLotEntity
import com.homebasket.core.database.entity.InventoryTransactionEntity
import com.homebasket.core.database.entity.LocationKind
import com.homebasket.core.database.entity.MealSlotEntity
import com.homebasket.core.database.entity.ProductEntity
import com.homebasket.core.database.entity.PurchaseLineEntity
import com.homebasket.core.database.entity.RecipeIngredientEntity
import com.homebasket.core.domain.engine.MealSlotKind
import com.homebasket.core.domain.model.UnitKind

data class ProductRow(
    @Embedded val product: ProductEntity,
    val categoryName: String,
    val budgetCategory: BudgetCategory,
    val perishable: Boolean,
)

data class LotRow(
    @Embedded val lot: InventoryLotEntity,
    val productName: String,
    val productLocalName: String?,
    val unitKind: UnitKind,
    val countLabel: String,
    val packSize: Double,
    val lastPricePaise: Long?,
    val categoryId: Long,
    val categoryName: String,
    val budgetCategory: BudgetCategory,
    val locationName: String,
    val locationKind: LocationKind,
)

data class ProductQuantity(val productId: Long, val quantity: Double)

data class TransactionRow(
    @Embedded val tx: InventoryTransactionEntity,
    val productName: String,
    val unitKind: UnitKind,
    val countLabel: String,
    val locationName: String?,
)

data class MealSlotRow(
    @Embedded val slot: MealSlotEntity,
    val recipeName: String?,
    val recipeLocalName: String?,
    val diet: Diet?,
)

data class PlannedNeedRow(
    val mealSlotId: Long,
    val epochDay: Long,
    val slot: MealSlotKind,
    val label: String,
    val productId: Long,
    val quantity: Double,
)

data class RecipeIngredientRow(
    @Embedded val ingredient: RecipeIngredientEntity,
    val productName: String,
    val productLocalName: String?,
    val unitKind: UnitKind,
    val countLabel: String,
    val packSize: Double,
    val lastPricePaise: Long?,
)

data class ShoppingListSummary(
    val id: Long,
    val name: String,
    val type: com.homebasket.core.database.entity.ListType,
    val targetEpochDay: Long?,
    val status: com.homebasket.core.database.entity.ListStatus,
    val updatedAt: Long,
    val itemCount: Int,
    val doneCount: Int,
    val estimatedPaise: Long?,
)

data class PurchaseSummary(
    val id: Long,
    val epochDay: Long,
    val store: String?,
    val note: String?,
    val lineCount: Int,
    val totalPaise: Long,
)

data class PurchaseLineRow(
    @Embedded val line: PurchaseLineEntity,
    val epochDay: Long,
    val store: String?,
    val categoryName: String?,
    val packSize: Double?,
)

data class CategorySpend(val budgetCategory: BudgetCategory, val totalPaise: Long)
data class DaySpend(val epochDay: Long, val totalPaise: Long)
data class ProductFrequency(val name: String, val purchases: Int, val totalPaise: Long)
data class WasteRow(@Embedded val waste: FoodWasteRecordEntity)
