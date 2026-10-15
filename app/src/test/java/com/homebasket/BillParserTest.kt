package com.homebasket

import com.homebasket.core.data.seed.SeedData
import com.homebasket.core.domain.engine.BillMatcher
import com.homebasket.core.domain.engine.BillParser
import com.homebasket.core.domain.engine.OcrLine
import com.homebasket.core.domain.model.UnitKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Uses lines from a real DMart receipt (10 Oct 2026, total 6055.23). */
class BillParserTest {

    private val photo1 = listOf(
        "TAX INVOICE",
        "Bill No : 460410011-001712 Bill Dt : 10/10/2026( 6:08PM",
        "HSN Particulars Qty/Kg N/Rate Value",
        "1) CGST @ 0.00%, SGST @ 0.00%",
        "040110 AMUL TAAZA MILK-1lt 1 56.00 56.00",
        "071360 L LATUR TURDAL 1.356 141.00 191.20",
        "080112 COCONUT 2 34.00 68.00",
        "100630 L BIRYANI RICE 1.464 100.00 146.40",
        "100630 L BIRYANI RICE 0.860 100.00 86.00",
        "071339 PREMIA UDID-500g 2 85.00 170.00",
        "090230 TEA COUNTRY ORIGINA 1 75.00 75.00",
        "091030 (DK)HALDI POWD-100g 1 31.00 31.00",
    )
    private val photo2 = listOf(
        "090230 TEA COUNTRY ORIGINA 1 75.00 75.00",
        "091030 (DK)HALDI POWD-100g 1 31.00 31.00",
        "150790 ZZT RUCHI STAR -4kg 1 691.00 691.00",
        "610711 BRIEF TRUNK NF LNG 1 109.00 109.00",
        "610711 BRIEF TRUNK NF LNG 1 109.00 109.00",
        "3) CGST @ 9.00%, SGST @ 9.00%",
        "340119 SURF EXCEL DET -80g 2 9.00 18.00",
        "Items: 58 Qty: 63 6055.23",
        "T: 5751.73 151.75 151.75 6055.23",
        "Card Payment : 6055.23 /-",
    )

    @Test
    fun `parses item rows, date and total`() {
        val bill = BillParser.parse(photo1)
        assertEquals(LocalDate.of(2026, 10, 10), bill.date)
        assertEquals(8, bill.lines.size)
        val tur = bill.lines.first { it.name.contains("TURDAL") }
        assertTrue(tur.loose)
        assertEquals(1.356, tur.qty, 1e-9)
        assertEquals(19120L, tur.amountPaise)
        assertTrue(tur.consistent)
        val udid = bill.lines.first { it.name.contains("UDID") }
        assertFalse(udid.loose)
        assertEquals(500.0, udid.pack!!.base, 1e-9)
    }

    @Test
    fun `total comes from the Items line and tax rows are skipped`() {
        val bill = BillParser.parse(photo2)
        assertEquals(605523L, bill.billTotalPaise)
        assertTrue(bill.lines.none { it.name.contains("CGST") || it.name.startsWith("T") && it.name.length < 3 })
    }

    @Test
    fun `merging overlapping photos keeps genuine repeats but drops overlap`() {
        val merged = BillParser.merge(listOf(BillParser.parse(photo1), BillParser.parse(photo2)))
        assertEquals(1, merged.lines.count { it.name.contains("TEA COUNTRY") })
        assertEquals(2, merged.lines.count { it.name.contains("BRIEF TRUNK") })
        assertEquals(2, merged.lines.count { it.name.contains("BIRYANI") })
        assertEquals(605523L, merged.billTotalPaise)
    }

    @Test
    fun `rows are rebuilt from columns at the same height`() {
        val rows = BillParser.rows(
            listOf(
                OcrLine("071360 L LATUR TURDAL", 10, 100, 120),
                OcrLine("1.356", 300, 102, 121),
                OcrLine("141.00", 400, 99, 119),
                OcrLine("191.20", 500, 101, 120),
                OcrLine("080112 COCONUT", 10, 130, 150),
                OcrLine("2 34.00 68.00", 300, 131, 151),
            )
        )
        assertEquals(listOf("071360 L LATUR TURDAL 1.356 141.00 191.20", "080112 COCONUT 2 34.00 68.00"), rows)
    }

    @Test
    fun `quantities convert to base units`() {
        val bill = BillParser.parse(photo1 + photo2)
        val tur = bill.lines.first { it.name.contains("TURDAL") }
        assertEquals(1356.0, BillParser.baseQuantity(tur, UnitKind.WEIGHT, 1000.0), 1e-6)
        val udid = bill.lines.first { it.name.contains("UDID") }
        assertEquals(1000.0, BillParser.baseQuantity(udid, UnitKind.WEIGHT, 500.0), 1e-6)
        val oil = bill.lines.first { it.name.contains("RUCHI") }
        assertEquals(4000.0, BillParser.baseQuantity(oil, UnitKind.VOLUME, 1000.0), 1e-6) // 4kg printed, volume product -> packs
        val coconut = bill.lines.first { it.name.contains("COCONUT") }
        assertEquals(2.0, BillParser.baseQuantity(coconut, UnitKind.COUNT, 1.0), 1e-6)
    }

    @Test
    fun `receipt names map to catalog products`() {
        val catalog = SeedData.catalog.flatMap { (_, products) -> products.map { it.name } }
        assertEquals("Toor dal", BillMatcher.suggest("L LATUR TURDAL", catalog))
        assertEquals("Basmati rice", BillMatcher.suggest("L BIRYANI RICE", catalog))
        assertEquals("Rice", BillMatcher.suggest("L MASURI RICE", catalog))
        assertEquals("Turmeric", BillMatcher.suggest("(DK)HALDI POWD-100g", catalog))
        assertEquals("Urad dal", BillMatcher.suggest("PREMIA UDID-500g", catalog))
        assertEquals("Sunflower oil", BillMatcher.suggest("ZZT RUCHI STAR -4kg", catalog))
        assertEquals("Milk", BillMatcher.suggest("AMUL TAAZA MILK-1lt", catalog))
        assertEquals("Washing powder", BillMatcher.suggest("GHADI DETERGENT-3kg", catalog))
        assertEquals("T-shirts", BillMatcher.suggest("TSHRT MHSRN VARSITY", catalog))
        assertEquals("Shirts", BillMatcher.suggest("SHIRT M D1 FR CHECK", catalog))
        assertEquals("Innerwear", BillMatcher.suggest("BRIEF TRUNK NF LNG", catalog))
        assertEquals("Chana dal", BillMatcher.suggest("L DAL CHANA", catalog))
        assertEquals("Chickpeas", BillMatcher.suggest("PREMIA DESI KABULI", catalog))
        assertNotNull(BillMatcher.suggest("(DK)BLACK PEPP-100g", catalog))
        assertNull(BillMatcher.suggest("TINY RUBBER BAND BL", catalog))
    }

    @Test
    fun `OCR letter-for-digit mistakes in numbers are repaired`() {
        val bill = BillParser.parse(listOf("O7136O L DAL CHANA O.638 98.0O 62.52", "080112 COCONUT 2 34.00 68.00"))
        val chana = bill.lines.first()
        assertEquals("L DAL CHANA", chana.name)
        assertEquals(0.638, chana.qty, 1e-9)
        assertTrue(chana.loose)
        assertEquals(6252L, chana.amountPaise)
        assertTrue(chana.consistent)
    }

    @Test
    fun `missing quantity column is recovered from rate and value`() {
        val bill = BillParser.parse(listOf("610711 BRIEF TRUNK NF LNG 109.00 109.00", "34029 GHADI DETERGENT-3kg 1 188.00 188.0O", "120242 L GROUNDNUT 158.00 88.48"))
        assertEquals(3, bill.lines.size)
        assertEquals(1.0, bill.lines[0].qty, 1e-9)
        assertEquals(10900L, bill.lines[0].amountPaise)
        assertEquals(18800L, bill.lines[1].amountPaise)
        assertTrue(bill.lines[2].loose)
        assertEquals(0.56, bill.lines[2].qty, 1e-3)
    }

    @Test
    fun `wrong arithmetic is flagged for review`() {
        val line = BillParser.parse(listOf("123456 SOMETHING ODD 2 50.00 180.00")).lines.single()
        assertFalse(line.consistent)
    }
}
