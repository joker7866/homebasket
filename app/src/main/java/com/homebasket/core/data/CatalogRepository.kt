package com.homebasket.core.data

import android.content.Context
import android.net.Uri
import com.homebasket.core.database.HomeBasketDatabase
import com.homebasket.core.database.dao.ProductRow
import com.homebasket.core.database.entity.ProductCategoryEntity
import com.homebasket.core.database.entity.ProductEntity
import com.homebasket.core.database.entity.ProductVariantEntity
import com.homebasket.core.domain.engine.VoiceCommandParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BarcodeMatch {
    data class Product(val product: ProductEntity, val variant: ProductVariantEntity?) : BarcodeMatch
    data class NotFound(val code: String) : BarcodeMatch
}

@Singleton
class CatalogRepository @Inject constructor(
    private val db: HomeBasketDatabase,
    @ApplicationContext private val context: Context,
) {
    private val dao = db.catalogDao()

    val categories: Flow<List<ProductCategoryEntity>> = dao.observeCategories()
    val products: Flow<List<ProductRow>> = dao.observeProducts()

    fun observeProduct(id: Long) = dao.observeProductRow(id)
    fun observeVariants(productId: Long) = dao.observeVariants(productId)

    suspend fun getProducts() = dao.getProducts()
    suspend fun getProduct(id: Long) = dao.getProduct(id)
    suspend fun getProductRow(id: Long) = dao.getProductRow(id)
    suspend fun getCategories() = dao.getCategories()

    /** Insert or update. Throws IllegalArgumentException with a user-facing reason on duplicates. */
    suspend fun saveProduct(p: ProductEntity): Long {
        require(p.name.isNotBlank()) { "name" }
        val existing = dao.getProductByName(p.name.trim())
        require(existing == null || existing.id == p.id) { "duplicate" }
        val clean = p.copy(name = p.name.trim(), localName = p.localName?.trim()?.ifEmpty { null }, barcode = p.barcode?.trim()?.ifEmpty { null })
        return if (p.id == 0L) dao.insertProduct(clean) else { dao.updateProduct(clean); p.id }
    }

    suspend fun archiveProduct(id: Long) {
        dao.getProduct(id)?.let { dao.updateProduct(it.copy(archived = true)) }
    }

    suspend fun saveCategory(c: ProductCategoryEntity): Long =
        if (c.id == 0L) dao.insertCategory(c.copy(isCustom = true)) else { dao.updateCategory(c); c.id }

    /** Categories still holding products cannot be deleted (foreign key RESTRICT); returns false then. */
    suspend fun deleteCategory(c: ProductCategoryEntity): Boolean {
        if (dao.countProductsInCategory(c.id) > 0) return false
        dao.deleteCategory(c)
        return true
    }

    suspend fun addVariant(v: ProductVariantEntity) = dao.insertVariant(v)
    suspend fun deleteVariant(v: ProductVariantEntity) = dao.deleteVariant(v)

    suspend fun findByBarcode(code: String): BarcodeMatch {
        dao.getProductByBarcode(code)?.let { return BarcodeMatch.Product(it, null) }
        dao.getVariantByBarcode(code)?.let { v -> dao.getProduct(v.productId)?.let { return BarcodeMatch.Product(it, v) } }
        return BarcodeMatch.NotFound(code)
    }

    suspend fun linkBarcode(productId: Long, code: String) {
        dao.getProduct(productId)?.let { dao.updateProduct(it.copy(barcode = code)) }
    }

    /** Name match for voice/text entry: exact, local name, then singular/plural-insensitive, then prefix. */
    suspend fun matchProduct(text: String): ProductEntity? {
        val key = VoiceCommandParser.matchKey(text)
        val all = dao.getProducts().map { it.product }
        return all.firstOrNull { it.name.equals(text.trim(), true) || it.localName?.equals(text.trim(), true) == true }
            ?: all.firstOrNull { VoiceCommandParser.matchKey(it.name) == key || it.localName?.let { l -> VoiceCommandParser.matchKey(l) } == key }
            ?: all.firstOrNull { VoiceCommandParser.matchKey(it.name).startsWith(key) || key.startsWith(VoiceCommandParser.matchKey(it.name)) }
    }

    /** Copies a picked image into app storage so it survives the picker's temporary permission. */
    suspend fun importImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "images").apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
            file.absolutePath
        }.getOrNull()
    }
}
