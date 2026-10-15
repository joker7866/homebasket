package com.homebasket.core.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Money is always held as a Long count of paise (1 rupee = 100 paise). Floating point is used only
 * transiently when scaling a pack price to a quantity, and the result is rounded back to paise.
 */
object Money {

    fun fromRupees(text: String): Long? {
        val cleaned = text.trim().replace(",", "").removePrefix("₹").trim()
        if (cleaned.isEmpty()) return null
        return try {
            BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
                .takeIf { it >= 0 }
        } catch (_: Exception) {
            null
        }
    }

    /** "1234.5" style string for editing fields (no symbol, no grouping). */
    fun toEditable(paise: Long?): String {
        if (paise == null) return ""
        val rupees = paise / 100
        val p = abs(paise % 100)
        return if (p == 0L) rupees.toString() else "$rupees.${p.toString().padStart(2, '0')}"
    }

    /** Indian grouping: ₹1,23,456.50. Whole rupee amounts drop the decimals. */
    fun format(paise: Long, showPaise: Boolean = false): String {
        val negative = paise < 0
        val absolute = abs(paise)
        val rupees = absolute / 100
        val p = absolute % 100
        val grouped = groupIndian(rupees.toString())
        val decimals = if (showPaise || p != 0L) "." + p.toString().padStart(2, '0') else ""
        return (if (negative) "-₹" else "₹") + grouped + decimals
    }

    private fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val last3 = digits.takeLast(3)
        val rest = digits.dropLast(3)
        val sb = StringBuilder()
        rest.reversed().chunked(2).forEachIndexed { i, chunk ->
            if (i > 0) sb.append(',')
            sb.append(chunk)
        }
        return sb.reverse().toString() + "," + last3
    }

    /** Cost of [quantity] base units when [packPricePaise] buys [packSize] base units. */
    fun costFor(quantity: Double, packSize: Double, packPricePaise: Long?): Long? {
        if (packPricePaise == null || packSize <= QUANTITY_EPSILON) return null
        return (quantity / packSize * packPricePaise).roundToLong()
    }

    fun percent(part: Long, whole: Long): Int = if (whole <= 0) 0 else ((part * 100.0) / whole).roundToLong().toInt()
}
