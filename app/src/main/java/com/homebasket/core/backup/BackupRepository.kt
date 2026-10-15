package com.homebasket.core.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import com.homebasket.core.data.Clock
import com.homebasket.core.data.seed.Seeder
import com.homebasket.core.database.HomeBasketDatabase
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupData(
    val households: List<HouseholdEntity>,
    val settings: List<HouseholdSettingsEntity>,
    val locations: List<StorageLocationEntity>,
    val categories: List<ProductCategoryEntity>,
    val products: List<ProductEntity>,
    val variants: List<ProductVariantEntity>,
    val recipes: List<RecipeEntity>,
    val recipeIngredients: List<RecipeIngredientEntity>,
    val mealPlans: List<MealPlanEntity>,
    val mealSlots: List<MealSlotEntity>,
    val mealCompletions: List<MealCompletionEntity>,
    val shoppingLists: List<ShoppingListEntity>,
    val purchases: List<PurchaseEntity>,
    val purchaseLines: List<PurchaseLineEntity>,
    val shoppingItems: List<ShoppingListItemEntity>,
    val lots: List<InventoryLotEntity>,
    val transactions: List<InventoryTransactionEntity>,
    val transfers: List<StockTransferEntity>,
    val budgets: List<BudgetEntity>,
    val recurringRules: List<RecurringPurchaseRuleEntity>,
    val waste: List<FoodWasteRecordEntity>,
    val leftovers: List<PreparedFoodLeftoverEntity>,
)

@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val createdAt: Long,
    val appVersion: String,
    val data: BackupData,
) {
    companion object {
        const val FORMAT = "homebasket-backup"
        const val VERSION = 1
    }
}

@Serializable
data class EncryptedBackupFile(
    val format: String = FORMAT,
    val version: Int = BackupFile.VERSION,
    val salt: String,
    val iv: String,
    val iterations: Int,
    val payload: String,
) {
    companion object { const val FORMAT = "homebasket-backup-encrypted" }
}

/** Summary shown before a restore so the user knows what will replace their current data. */
data class BackupPreview(val createdAt: Long, val products: Int, val stockLots: Int, val purchases: Int, val recipes: Int, val meals: Int)

sealed interface RestoreCheck {
    data class Ready(val preview: BackupPreview, internal val data: BackupData) : RestoreCheck
    data object NeedsPassword : RestoreCheck
    data object WrongPassword : RestoreCheck
    data object NotABackup : RestoreCheck
    data object NewerVersion : RestoreCheck
    data object Corrupted : RestoreCheck
}

@Singleton
class BackupRepository @Inject constructor(
    private val db: HomeBasketDatabase,
    private val seeder: Seeder,
    private val clock: Clock,
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val dao = db.backupDao()

    private suspend fun snapshot(): BackupData = db.withTransaction {
        BackupData(
            dao.households(), dao.settings(), dao.locations(), dao.categories(), dao.products(), dao.variants(), dao.recipes(),
            dao.recipeIngredients(), dao.mealPlans(), dao.mealSlots(), dao.mealCompletions(), dao.shoppingLists(), dao.purchases(),
            dao.purchaseLines(), dao.shoppingItems(), dao.lots(), dao.transactions(), dao.transfers(), dao.budgets(), dao.recurringRules(),
            dao.waste(), dao.leftovers(),
        )
    }

    /** Writes a backup to a user-chosen document (Storage Access Framework - no storage permission needed). */
    suspend fun export(uri: Uri, password: String?) = withContext(Dispatchers.IO) {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
        val plain = json.encodeToString(BackupFile.serializer(), BackupFile(createdAt = clock.nowMillis(), appVersion = version, data = snapshot()))
        val text = if (password.isNullOrEmpty()) plain else json.encodeToString(EncryptedBackupFile.serializer(), encrypt(plain, password))
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    suspend fun inspect(uri: Uri, password: String?): RestoreCheck = withContext(Dispatchers.IO) {
        val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
            ?: return@withContext RestoreCheck.NotABackup
        val plain = when {
            text.contains("\"${EncryptedBackupFile.FORMAT}\"") -> {
                if (password.isNullOrEmpty()) return@withContext RestoreCheck.NeedsPassword
                val enc = runCatching { json.decodeFromString(EncryptedBackupFile.serializer(), text) }.getOrNull() ?: return@withContext RestoreCheck.Corrupted
                runCatching { decrypt(enc, password) }.getOrNull() ?: return@withContext RestoreCheck.WrongPassword
            }
            text.contains("\"${BackupFile.FORMAT}\"") -> text
            else -> return@withContext RestoreCheck.NotABackup
        }
        val file = runCatching { json.decodeFromString(BackupFile.serializer(), plain) }.getOrNull() ?: return@withContext RestoreCheck.Corrupted
        if (file.format != BackupFile.FORMAT) return@withContext RestoreCheck.NotABackup
        if (file.version > BackupFile.VERSION) return@withContext RestoreCheck.NewerVersion
        if (!isConsistent(file.data)) return@withContext RestoreCheck.Corrupted
        val d = file.data
        RestoreCheck.Ready(BackupPreview(file.createdAt, d.products.size, d.lots.count { it.quantity > 0 }, d.purchases.size, d.recipes.size, d.mealSlots.size), d)
    }

    /** Checks that every reference in the file points at a row that is also in the file. */
    private fun isConsistent(d: BackupData): Boolean {
        val products = d.products.map { it.id }.toSet()
        val categories = d.categories.map { it.id }.toSet()
        val locations = d.locations.map { it.id }.toSet()
        val recipes = d.recipes.map { it.id }.toSet()
        val lists = d.shoppingLists.map { it.id }.toSet()
        val purchases = d.purchases.map { it.id }.toSet()
        return d.households.isNotEmpty() &&
            d.products.all { it.categoryId in categories } &&
            d.lots.all { it.productId in products && it.locationId in locations } &&
            d.transactions.all { it.productId in products } &&
            d.recipeIngredients.all { it.recipeId in recipes && it.productId in products } &&
            d.shoppingItems.all { it.listId in lists } &&
            d.purchaseLines.all { it.purchaseId in purchases }
    }

    /** Replaces all data with the backup in one transaction; on any failure nothing changes. */
    suspend fun restore(check: RestoreCheck.Ready) = db.withTransaction {
        deleteEverything()
        val d = check.data
        dao.insertHouseholds(d.households); dao.insertSettings(d.settings); dao.insertLocations(d.locations)
        dao.insertCategories(d.categories); dao.insertProducts(d.products); dao.insertVariants(d.variants)
        dao.insertRecipes(d.recipes); dao.insertRecipeIngredients(d.recipeIngredients)
        dao.insertMealPlans(d.mealPlans); dao.insertMealSlots(d.mealSlots); dao.insertMealCompletions(d.mealCompletions)
        dao.insertShoppingLists(d.shoppingLists); dao.insertPurchases(d.purchases); dao.insertPurchaseLines(d.purchaseLines)
        dao.insertShoppingItems(d.shoppingItems); dao.insertLots(d.lots); dao.insertTransactions(d.transactions)
        dao.insertTransfers(d.transfers); dao.insertBudgets(d.budgets); dao.insertRecurringRules(d.recurringRules)
        dao.insertWaste(d.waste); dao.insertLeftovers(d.leftovers)
    }

    /** Deletes every record and restores the starter catalog. */
    suspend fun clearAllData() {
        db.withTransaction { deleteEverything() }
        seeder.seedIfEmpty()
        context.filesDir.resolve("images").deleteRecursively()
    }

    private suspend fun deleteEverything() {
        // Children before parents so foreign keys are never violated.
        listOf(
            "meal_completion", "inventory_transaction", "stock_transfer", "food_waste", "prepared_leftover", "shopping_list_item",
            "inventory_lot", "purchase_line", "purchase", "shopping_list", "meal_slot", "meal_plan", "recipe_ingredient", "recipe",
            "recurring_purchase_rule", "product_variant", "budget", "product", "product_category", "storage_location",
            "household_settings", "household",
        ).forEach { db.openHelper.writableDatabase.execSQL("DELETE FROM $it") }
    }

    private fun encrypt(plain: String, password: String): EncryptedBackupFile {
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(password, salt, ITERATIONS), GCMParameterSpec(128, iv)) }
        return EncryptedBackupFile(salt = b64(salt), iv = b64(iv), iterations = ITERATIONS, payload = b64(cipher.doFinal(plain.toByteArray(Charsets.UTF_8))))
    }

    private fun decrypt(file: EncryptedBackupFile, password: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(password, unb64(file.salt), file.iterations), GCMParameterSpec(128, unb64(file.iv)))
        }
        return cipher.doFinal(unb64(file.payload)).toString(Charsets.UTF_8)
    }

    private fun key(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    private companion object { const val ITERATIONS = 120_000 }
}
