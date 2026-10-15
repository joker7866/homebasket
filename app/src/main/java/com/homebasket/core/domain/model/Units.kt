package com.homebasket.core.domain.model

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * Every quantity in the database is stored in the product's base unit:
 * grams for weight, millilitres for volume, and pieces (or bunches/packets) for counted items.
 * Display units are converted at the UI edge only, so stock, recipes and shopping lists never mix units.
 */
enum class UnitKind { WEIGHT, VOLUME, COUNT }

/** Units the user can type a quantity in. [factor] converts to the base unit of [kind]. */
enum class InputUnit(val kind: UnitKind, val factor: Double, val symbol: String) {
    G(UnitKind.WEIGHT, 1.0, "g"),
    KG(UnitKind.WEIGHT, 1000.0, "kg"),
    ML(UnitKind.VOLUME, 1.0, "ml"),
    L(UnitKind.VOLUME, 1000.0, "L"),
    PCS(UnitKind.COUNT, 1.0, "pcs"),
    DOZEN(UnitKind.COUNT, 12.0, "dozen");

    fun toBase(value: Double): Double = value * factor

    companion object {
        fun forKind(kind: UnitKind): List<InputUnit> = entries.filter { it.kind == kind }

        /** The unit a quantity is most naturally shown in, e.g. 1500 g -> kg, 300 g -> g. */
        fun bestFor(kind: UnitKind, baseQuantity: Double): InputUnit = when (kind) {
            UnitKind.WEIGHT -> if (abs(baseQuantity) >= 1000) KG else G
            UnitKind.VOLUME -> if (abs(baseQuantity) >= 1000) L else ML
            UnitKind.COUNT -> PCS
        }
    }
}

const val QUANTITY_EPSILON = 1e-6

object Quantities {

    /** "1.5 kg", "250 g", "2 bunch". [countLabel] names counted items (pcs, bunch, packet...). */
    fun format(baseQuantity: Double, kind: UnitKind, countLabel: String = "pcs"): String {
        val unit = InputUnit.bestFor(kind, baseQuantity)
        val value = baseQuantity / unit.factor
        val symbol = if (kind == UnitKind.COUNT) countLabel.ifBlank { "pcs" } else unit.symbol
        return "${formatNumber(value)} $symbol"
    }

    /** Up to 2 decimals, no trailing zeros: 1.0 -> "1", 1.25 -> "1.25", 0.333 -> "0.33". */
    fun formatNumber(value: Double): String {
        val rounded = (value * 100).roundToLong() / 100.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString()
        else "%.2f".format(java.util.Locale.ROOT, rounded).trimEnd('0').trimEnd('.')
    }

    /** Rounds a required quantity up to whole purchase packs. Pack size <= 0 means "buy exactly". */
    fun roundUpToPack(quantity: Double, packSize: Double): Double {
        if (quantity <= QUANTITY_EPSILON) return 0.0
        if (packSize <= QUANTITY_EPSILON) return quantity
        val packs = ceil(quantity / packSize - QUANTITY_EPSILON)
        return packs * packSize
    }

    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }
}
