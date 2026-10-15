package com.homebasket

import com.homebasket.core.domain.engine.EngineProduct
import com.homebasket.core.domain.engine.MealIngredientNeed
import com.homebasket.core.domain.engine.ShortageEngine
import com.homebasket.core.domain.model.UnitKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortageEngineTest {

    private val tomato = EngineProduct(1, "Tomato", UnitKind.WEIGHT, "pcs", packSize = 250.0, packPricePaise = 1000, minStock = 0.0, desiredStock = 0.0)
    private val milk = EngineProduct(2, "Milk", UnitKind.VOLUME, "pcs", packSize = 500.0, packPricePaise = 2800, minStock = 0.0, desiredStock = 0.0)
    private val rice = EngineProduct(3, "Rice", UnitKind.WEIGHT, "pcs", packSize = 5000.0, packPricePaise = 30000, minStock = 2000.0, desiredStock = 10000.0)
    private val products = listOf(tomato, milk, rice).associateBy { it.id }

    private val today = 100L
    private val tomorrow = 101L

    @Test
    fun `spec example - need 1 kg tomatoes, have 600 g, buy 400 g rounded to packs`() {
        val needs = listOf(MealIngredientNeed(tomorrow, 1, "Sambar", tomato.id, 1000.0))
        val lines = ShortageEngine.compute(products, mapOf(tomato.id to 600.0), needs, tomorrow, tomorrow, includeLowStockReserve = false)
        assertEquals(1, lines.size)
        assertEquals(400.0, lines[0].shortage, 1e-9)
        assertEquals(500.0, lines[0].suggestedPurchase, 1e-9) // two 250 g packs
        assertEquals(2000L, lines[0].estimatedCostPaise)
    }

    @Test
    fun `without rounding the exact shortage is suggested`() {
        val needs = listOf(MealIngredientNeed(tomorrow, 1, "Sambar", tomato.id, 1000.0))
        val lines = ShortageEngine.compute(products, mapOf(tomato.id to 600.0), needs, tomorrow, tomorrow, false, roundToPacks = false)
        assertEquals(400.0, lines[0].suggestedPurchase, 1e-9)
    }

    @Test
    fun `ingredients used by several recipes are aggregated, stock subtracted once`() {
        val needs = listOf(
            MealIngredientNeed(tomorrow, 0, "Sambar", tomato.id, 300.0),
            MealIngredientNeed(tomorrow, 1, "Rasam", tomato.id, 400.0),
            MealIngredientNeed(tomorrow, 3, "Curry", tomato.id, 300.0),
        )
        val lines = ShortageEngine.compute(products, mapOf(tomato.id to 600.0), needs, tomorrow, tomorrow, false, roundToPacks = false)
        assertEquals(1, lines.size)
        assertEquals(400.0, lines[0].shortage, 1e-9)
        assertEquals(listOf("Sambar", "Rasam", "Curry"), lines[0].reason.mealLabels)
    }

    @Test
    fun `earlier planned meals reserve stock before the window`() {
        val needs = listOf(
            MealIngredientNeed(today, 3, "Dinner curry", tomato.id, 500.0),
            MealIngredientNeed(tomorrow, 1, "Sambar", tomato.id, 300.0),
        )
        val lines = ShortageEngine.compute(products, mapOf(tomato.id to 600.0), needs, tomorrow, tomorrow, false, roundToPacks = false)
        // 600 - 500 reserved for tonight = 100 available; tomorrow needs 300 -> buy 200.
        assertEquals(200.0, lines.single().shortage, 1e-9)
        assertEquals(500.0, lines.single().reason.reservedForEarlierMeals, 1e-9)
    }

    @Test
    fun `nothing suggested when stock covers meals`() {
        val needs = listOf(MealIngredientNeed(tomorrow, 0, "Tea", milk.id, 400.0))
        val lines = ShortageEngine.compute(products, mapOf(milk.id to 1000.0), needs, tomorrow, tomorrow, false)
        assertTrue(lines.isEmpty())
    }

    @Test
    fun `low stock reserve tops up to desired stock`() {
        val lines = ShortageEngine.compute(products, mapOf(rice.id to 1500.0), emptyList(), tomorrow, tomorrow, includeLowStockReserve = true, roundToPacks = false)
        val line = lines.single()
        assertTrue(line.isLowStockOnly)
        assertEquals(8500.0, line.shortage, 1e-9) // up to 10 kg
    }

    @Test
    fun `stock above minimum triggers no reserve`() {
        val lines = ShortageEngine.compute(products, mapOf(rice.id to 3000.0), emptyList(), tomorrow, tomorrow, includeLowStockReserve = true)
        assertTrue(lines.isEmpty())
    }

    @Test
    fun `confirmed incoming reduces the purchase, list items do not exist as incoming`() {
        val needs = listOf(MealIngredientNeed(tomorrow, 0, "Tea", milk.id, 1000.0))
        val lines = ShortageEngine.compute(products, emptyMap(), needs, tomorrow, tomorrow, false, confirmedIncoming = mapOf(milk.id to 500.0), roundToPacks = false)
        assertEquals(500.0, lines.single().shortage, 1e-9)
    }

    @Test
    fun `meals outside the window are ignored`() {
        val needs = listOf(MealIngredientNeed(tomorrow + 3, 0, "Later", tomato.id, 1000.0))
        assertTrue(ShortageEngine.compute(products, emptyMap(), needs, tomorrow, tomorrow, false).isEmpty())
    }
}
