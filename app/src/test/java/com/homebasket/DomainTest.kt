package com.homebasket

import com.homebasket.core.domain.engine.EngineProduct
import com.homebasket.core.domain.engine.MealSlotKind
import com.homebasket.core.domain.engine.MonthlyBasis
import com.homebasket.core.domain.engine.MonthlyInput
import com.homebasket.core.domain.engine.MonthlyPlanner
import com.homebasket.core.domain.engine.VoiceCommand
import com.homebasket.core.domain.engine.VoiceCommandParser
import com.homebasket.core.domain.model.InputUnit
import com.homebasket.core.domain.model.Money
import com.homebasket.core.domain.model.Quantities
import com.homebasket.core.domain.model.UnitKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthlyPlannerTest {
    private fun product(id: Long, pack: Double, min: Double = 0.0) =
        EngineProduct(id, "P$id", UnitKind.WEIGHT, "pcs", pack, 10000, min, 0.0)

    @Test
    fun `spec examples - rice, toor dal, turmeric`() {
        val inputs = listOf(
            MonthlyInput(product(1, 1000.0), 10000.0, 0.0, 0.0, 0.0, 4000.0),
            MonthlyInput(product(2, 1000.0), 3000.0, 0.0, 0.0, 0.0, 1000.0),
            MonthlyInput(product(3, 50.0), 200.0, 0.0, 0.0, 0.0, 150.0),
        )
        val s = MonthlyPlanner.suggest(inputs).associateBy { it.product.id }
        assertEquals(6000.0, s.getValue(1).suggestedPurchase, 1e-9)
        assertEquals(2000.0, s.getValue(2).suggestedPurchase, 1e-9)
        assertEquals(50.0, s.getValue(3).suggestedPurchase, 1e-9)
    }

    @Test
    fun `history is used when no requirement is set, and leftover stock is never reset`() {
        val s = MonthlyPlanner.suggest(listOf(MonthlyInput(product(1, 500.0), 0.0, 1200.0, 2000.0, 0.0, 900.0))).single()
        assertEquals(MonthlyBasis.HISTORY, s.basis)
        assertEquals(500.0, s.suggestedPurchase, 1e-9) // 1200 - 900 = 300 -> one 500 g pack
    }

    @Test
    fun `planned meals raise the requirement`() {
        val s = MonthlyPlanner.suggest(listOf(MonthlyInput(product(1, 100.0), 500.0, 0.0, 0.0, 800.0, 0.0)), roundToPacks = false).single()
        assertEquals(800.0, s.monthlyRequirement, 1e-9)
    }

    @Test
    fun `no data and enough stock gives no suggestion`() {
        assertTrue(MonthlyPlanner.suggest(listOf(MonthlyInput(product(1, 100.0), 0.0, 0.0, 0.0, 0.0, 100.0))).isEmpty())
    }
}

class MoneyAndUnitsTest {
    @Test
    fun `indian grouping and paise`() {
        assertEquals("₹1,23,456", Money.format(12_345_600))
        assertEquals("₹8,000", Money.format(800_000))
        assertEquals("₹12.50", Money.format(1250))
        assertEquals("-₹5", Money.format(-500))
    }

    @Test
    fun `parsing rupees is exact`() {
        assertEquals(1250L, Money.fromRupees("12.5"))
        assertEquals(10L, Money.fromRupees("0.1"))
        assertEquals(800_000L, Money.fromRupees("8,000"))
        assertNull(Money.fromRupees("abc"))
        assertEquals("12.50", Money.toEditable(1250))
    }

    @Test
    fun `cost scales pack price`() {
        assertEquals(3000L, Money.costFor(1500.0, 5000.0, 10000))
        assertNull(Money.costFor(100.0, 100.0, null))
    }

    @Test
    fun `quantity formatting picks a readable unit`() {
        assertEquals("1.5 kg", Quantities.format(1500.0, UnitKind.WEIGHT))
        assertEquals("250 g", Quantities.format(250.0, UnitKind.WEIGHT))
        assertEquals("1 L", Quantities.format(1000.0, UnitKind.VOLUME))
        assertEquals("2 bunch", Quantities.format(2.0, UnitKind.COUNT, "bunch"))
    }

    @Test
    fun `round up to packs`() {
        assertEquals(500.0, Quantities.roundUpToPack(400.0, 250.0), 1e-9)
        assertEquals(500.0, Quantities.roundUpToPack(500.0, 250.0), 1e-9)
        assertEquals(0.0, Quantities.roundUpToPack(0.0, 250.0), 1e-9)
        assertEquals(330.0, Quantities.roundUpToPack(330.0, 0.0), 1e-9)
    }
}

class VoiceCommandParserTest {
    @Test
    fun `add stock to refrigerator`() {
        val c = VoiceCommandParser.parse("Add 2 kg tomatoes to refrigerator") as VoiceCommand.AddStock
        assertEquals(2.0, c.amount, 1e-9)
        assertEquals(InputUnit.KG, c.unit)
        assertEquals("tomatoes", c.productText)
        assertEquals("refrigerator", c.locationText)
    }

    @Test
    fun `add to shopping list`() {
        val c = VoiceCommandParser.parse("Add 5 kg rice to shopping list.") as VoiceCommand.AddToShopping
        assertEquals(5.0, c.amount, 1e-9)
        assertEquals("rice", c.productText)
    }

    @Test
    fun `plan a meal`() {
        val c = VoiceCommandParser.parse("Plan dosa for tomorrow's breakfast") as VoiceCommand.PlanMeal
        assertEquals("dosa", c.dishText)
        assertEquals(1, c.dayOffset)
        assertEquals(MealSlotKind.BREAKFAST, c.slot)
    }

    @Test
    fun `show tomorrow's list`() {
        assertEquals(VoiceCommand.ShowTomorrowShopping, VoiceCommandParser.parse("Show tomorrow's shopping list"))
    }

    @Test
    fun `number words and compact units`() {
        val c = VoiceCommandParser.parse("add half kg onion to kitchen") as VoiceCommand.AddStock
        assertEquals(0.5, c.amount, 1e-9)
        val d = VoiceCommandParser.parse("add 500g paneer in the fridge") as VoiceCommand.AddStock
        assertEquals(InputUnit.G, d.unit)
        assertEquals("paneer", d.productText)
        assertEquals("fridge", d.locationText)
    }

    @Test
    fun `hindi unit word`() {
        val c = VoiceCommandParser.parse("add 2 किलो aloo to fridge") as VoiceCommand.AddStock
        assertEquals(InputUnit.KG, c.unit)
    }

    @Test
    fun `plural matching`() {
        assertEquals("tomato", VoiceCommandParser.matchKey("Tomatoes"))
        assertEquals("tomato", VoiceCommandParser.matchKey("tomato"))
        assertEquals("potato", VoiceCommandParser.matchKey("Potatoes"))
    }

    @Test
    fun `gibberish is unknown`() {
        assertTrue(VoiceCommandParser.parse("hello there") is VoiceCommand.Unknown)
    }
}
