package com.homebasket.core.domain.engine

import com.homebasket.core.domain.model.UnitKind
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong

/** One recognised piece of text with its position on the photo (from the OCR engine). */
data class OcrLine(val text: String, val left: Int, val top: Int, val bottom: Int)

/** A printed pack size found in an item name, e.g. "-500g" or "1lt". [base] is in g / ml. */
data class PackInfo(val base: Double, val kind: UnitKind)

data class BillLine(
    val raw: String,
    val name: String,
    /** Number printed in the quantity column: a count (2) or a loose weight in kg (1.356). */
    val qty: Double,
    /** True for loose items sold by weight, where [qty] is kilograms. */
    val loose: Boolean,
    val ratePaise: Long?,
    val amountPaise: Long,
    val pack: PackInfo?,
    /** qty × rate agrees with the printed value; false means "please check this line". */
    val consistent: Boolean,
)

data class ParsedBill(
    val lines: List<BillLine>,
    val billTotalPaise: Long?,
    val date: LocalDate?,
    val store: String?,
) {
    val linesTotalPaise: Long get() = lines.sumOf { it.amountPaise }
}

/**
 * Turns OCR text from a supermarket receipt (DMart-style: HSN, name, qty/kg, rate, value) into item lines.
 * Pure Kotlin so it can be unit tested; nothing is saved until the user reviews the result.
 */
object BillParser {

    private val skipWords = Regex(
        "\\b(C?GST|SGST|IGST|CESS|TOTAL|TAXABLE|ITEMS|QTY|AMOUNT|CARD|CASH|UPI|PAYMENT|SAVED|BALANCE|ROUND|INVOICE|BILL|CASHIER|RECEIVED|PARTICULARS|HSN|RATE|VALUE|CHANGE|TENDER)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val fullRow = Regex("^(?:(\\d{4,8})\\s+)?(.+?)\\s+(\\d{1,3}(?:\\.\\d{1,3})?)\\s+(\\d{1,6}\\.\\d{2})\\s+(\\d{1,6}\\.\\d{2})$")
    /** Quantity column lost by OCR (a lone "1" is easy to miss): name, rate, value. */
    private val rateValueRow = Regex("^(?:(\\d{4,8})\\s+)?(.+?)\\s+(\\d{1,6}\\.\\d{2})\\s+(\\d{1,6}\\.\\d{2})$")
    /** Rate column lost: name, qty (whole or 3-decimal kg), value. */
    private val shortRow = Regex("^(?:(\\d{4,8})\\s+)?(.+?)\\s+(\\d{1,3}(?:\\.\\d{3})?)\\s+(\\d{1,6}\\.\\d{2})$")
    private val packRegex = Regex("(\\d+(?:\\.\\d+)?)\\s*(kg|kgs|g|gm|gms|gr|ml|l|lt|ltr|litre)\\b", RegexOption.IGNORE_CASE)
    private val dateRegex = Regex("(\\d{2})[/.-](\\d{2})[/.-](\\d{4})")

    /** Groups OCR lines that sit at the same height into receipt rows, read left to right. */
    fun rows(lines: List<OcrLine>): List<String> {
        if (lines.isEmpty()) return emptyList()
        val avgHeight = lines.map { (it.bottom - it.top).coerceAtLeast(1) }.average()
        val sorted = lines.sortedBy { (it.top + it.bottom) / 2.0 }
        val groups = mutableListOf<MutableList<OcrLine>>()
        for (line in sorted) {
            val center = (line.top + line.bottom) / 2.0
            val group = groups.lastOrNull()
            val groupCenter = group?.map { (it.top + it.bottom) / 2.0 }?.average()
            if (group != null && groupCenter != null && abs(center - groupCenter) < avgHeight * 0.55) group += line
            else groups += mutableListOf(line)
        }
        return groups.map { g -> g.sortedBy { it.left }.joinToString(" ") { it.text.trim() } }
    }

    fun parse(rows: List<String>): ParsedBill {
        val items = mutableListOf<BillLine>()
        var total: Long? = null
        for (raw in rows) {
            val text = normalize(raw)
            if (text.isBlank()) continue
            if (total == null && Regex("ITEMS\\s*:?\\s*\\d+", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                total = Regex("(\\d{1,7}\\.\\d{2})").findAll(text).lastOrNull()?.value?.let(::paise)
                continue
            }
            if (skipWords.containsMatchIn(text)) continue
            parseRow(raw, text)?.let { items += it }
        }
        val joined = rows.joinToString("\n")
        if (total == null) {
            total = rows.filter { Regex("(PAYMENT|NET|GRAND|^T\\s*:)", RegexOption.IGNORE_CASE).containsMatchIn(it) }
                .mapNotNull { r -> Regex("(\\d{1,7}\\.\\d{2})").findAll(normalize(r)).lastOrNull()?.value?.let(::paise) }
                .maxOrNull()
        }
        val date = dateRegex.find(joined)?.let { m ->
            runCatching { LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
        }
        val upper = joined.uppercase()
        val store = when {
            "DMART" in upper || "AVENUE SUPERMARTS" in upper -> "DMart"
            "RELIANCE" in upper -> "Reliance Smart"
            "MORE" in upper && "RETAIL" in upper -> "More"
            "SPENCER" in upper -> "Spencer's"
            else -> null
        }
        return ParsedBill(items, total, date, store)
    }

    private fun normalize(raw: String): String = raw
        .replace(Regex("(?<=\\d),(?=\\d{2}\\b)"), ".")
        .replace(Regex("[|]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .split(' ')
        .joinToString(" ") { fixDigits(it) }

    /** OCR often reads 0 as O and 1 as I/l inside numbers: "O.638" -> "0.638", "O7136O" -> "071360". */
    private fun fixDigits(token: String): String {
        if (!token.any { it.isDigit() } || !token.all { it.isDigit() || it in "OoIl.," }) return token
        if (token.count { it.isDigit() } < token.count { it in "OoIl" }) return token
        return token.map { c -> when (c) { 'O', 'o' -> '0'; 'I', 'l' -> '1'; else -> c } }.joinToString("")
    }

    private fun paise(text: String): Long = (text.toDouble() * 100).roundToLong()

    private fun parseRow(raw: String, text: String): BillLine? {
        val full = fullRow.find(text)
        val rateValue = if (full == null) rateValueRow.find(text) else null
        val (name, qtyText, rate, value) = when {
            full != null -> listOf(full.groupValues[2], full.groupValues[3], full.groupValues[4], full.groupValues[5])
            rateValue != null -> {
                val r = rateValue.groupValues[3].toDouble()
                val v = rateValue.groupValues[4].toDouble()
                val q = if (r > 0) v / r else 1.0
                // Whole packs print as integers; anything else is a loose weight in kg (3 decimals).
                val qText = if (abs(q - Math.round(q)) < 0.02) Math.round(q).coerceAtLeast(1).toString() else "%.3f".format(java.util.Locale.ROOT, q)
                listOf(rateValue.groupValues[2], qText, rateValue.groupValues[3], rateValue.groupValues[4])
            }
            else -> shortRow.find(text)?.let { listOf(it.groupValues[2], it.groupValues[3], "", it.groupValues[4]) } ?: return null
        }
        val cleanName = name.trim().trimEnd('-', '.', ':')
        if (cleanName.count { it.isLetter() } < 2) return null
        val qty = qtyText.toDoubleOrNull() ?: return null
        if (qty <= 0) return null
        val amount = paise(value)
        if (amount <= 0) return null
        val ratePaise = rate.takeIf { it.isNotEmpty() }?.let(::paise)
        val loose = qtyText.contains('.') && qty != qty.toLong().toDouble()
        val consistent = ratePaise == null || abs(ratePaise * qty - amount) <= maxOf(100.0, amount * 0.02)
        return BillLine(raw, cleanName, qty, loose, ratePaise, amount, packOf(cleanName), consistent)
    }

    fun packOf(name: String): PackInfo? {
        val m = packRegex.findAll(name).lastOrNull() ?: return null
        val v = m.groupValues[1].toDoubleOrNull() ?: return null
        return when (m.groupValues[2].lowercase()) {
            "kg", "kgs" -> PackInfo(v * 1000, UnitKind.WEIGHT)
            "g", "gm", "gms", "gr" -> PackInfo(v, UnitKind.WEIGHT)
            "ml" -> PackInfo(v, UnitKind.VOLUME)
            else -> PackInfo(v * 1000, UnitKind.VOLUME)
        }
    }

    /**
     * Combines several photos of one long receipt. Photos overlap, so each distinct line (name + value) is
     * counted as many times as it appears in the photo where it appears most - genuine repeats survive,
     * overlap duplicates do not.
     */
    fun merge(bills: List<ParsedBill>): ParsedBill {
        if (bills.size == 1) return bills.first()
        val result = mutableListOf<BillLine>()
        val kept = mutableMapOf<String, Int>()
        val maxCount = mutableMapOf<String, Int>()
        bills.forEach { b -> b.lines.groupingBy(::key).eachCount().forEach { (k, c) -> maxCount[k] = maxOf(maxCount[k] ?: 0, c) } }
        bills.forEach { b ->
            b.lines.forEach { line ->
                val k = key(line)
                if ((kept[k] ?: 0) < (maxCount[k] ?: 0)) {
                    result += line
                    kept[k] = (kept[k] ?: 0) + 1
                }
            }
        }
        return ParsedBill(
            lines = result,
            billTotalPaise = bills.mapNotNull { it.billTotalPaise }.maxOrNull(),
            date = bills.firstNotNullOfOrNull { it.date },
            store = bills.firstNotNullOfOrNull { it.store },
        )
    }

    private fun key(l: BillLine) = l.name.uppercase().filter { it.isLetterOrDigit() } + "|" + l.amountPaise

    /** Base quantity (g / ml / pcs) for a product of [kind] whose own pack is [productPack]. */
    fun baseQuantity(line: BillLine, kind: UnitKind, productPack: Double): Double = when {
        kind == UnitKind.COUNT -> if (line.loose) 1.0 else line.qty
        line.loose && kind == UnitKind.WEIGHT -> line.qty * 1000
        line.loose && kind == UnitKind.VOLUME -> line.qty * 1000
        line.pack != null && line.pack.kind == kind -> line.qty * line.pack.base
        // Oil is often sold as "4kg" but tracked in ml; 1 g ≈ 1 ml is close enough for a stock estimate.
        line.pack != null -> line.qty * line.pack.base
        else -> line.qty * productPack
    }
}

/**
 * Maps receipt item names ("L LATUR TURDAL", "(DK)HALDI POWD-100g") to catalog product names.
 * Ordered rules: specific phrases first so "BIRYANI RICE" wins over "RICE".
 */
object BillMatcher {
    private val rules: List<Pair<Regex, String>> = listOf(
        "BIRYANI RICE|BASMATI" to "Basmati rice",
        "IDLI RICE" to "Idli rice",
        "TUR ?DAL|TOOR|ARHAR|TURDAL" to "Toor dal",
        "MOONG|MUNG" to "Moong dal",
        "MASOOR" to "Masoor dal",
        "UDID|URAD" to "Urad dal",
        "DAL CHANA|CHANA DAL" to "Chana dal",
        "KABULI|CHICK ?PEA" to "Chickpeas",
        "CHANA B|BLACK CHANA|KALA CHANA" to "Black chana",
        "RAJMA" to "Rajma",
        "POHA" to "Poha",
        "RAVA|SOOJI|SUJI" to "Rava",
        "MAIDA" to "Maida",
        "ATTA" to "Atta",
        "BESAN" to "Besan",
        "OATS" to "Oats",
        "RICE" to "Rice",
        "BLACK PEP|PEPPER" to "Black pepper",
        "ELAICHI|CARDAMOM" to "Cardamom",
        "JEERA|CUMIN" to "Cumin seeds",
        "HALDI|TURMERIC" to "Turmeric",
        "SAMBAR" to "Sambar powder",
        "RASAM" to "Rasam powder",
        "GARAM" to "Garam masala",
        "BIRYANI MAS" to "Biryani masala",
        "CHICKEN MAS" to "Chicken masala",
        "DHANIA|CORIANDER POW" to "Coriander powder",
        "CHILLI|CHILLY|MIRCH" to "Red chilli powder",
        "MUSTARD|RAI" to "Mustard seeds",
        "CLOVE|LAUNG" to "Cloves",
        "CINNAMON|DALCHINI" to "Cinnamon",
        "SUNFLOWER|RUCHI|FORTUNE|SAFFOLA|REFINED|OIL" to "Sunflower oil",
        "GHEE" to "Ghee",
        "SUGAR" to "Sugar",
        "JAGGERY|GUD" to "Jaggery",
        "SALT" to "Salt",
        "TAMARIND|IMLI" to "Tamarind",
        "COFFEE|CONTINENTAL|NESCAFE|BRU" to "Coffee",
        "TEA" to "Tea",
        "MILK" to "Milk",
        "CURD|DAHI" to "Curd",
        "PANEER" to "Paneer",
        "BUTTER" to "Butter",
        "CHEESE" to "Cheese",
        "BREAD" to "Bread",
        "BISCUIT" to "Biscuits",
        "COCONUT" to "Coconut",
        "EGG" to "Eggs",
        "SURF|GHADI|DETERGENT|TIDE|ARIEL|WASHING" to "Washing powder",
        "DISHWASH|VIM|PRIL" to "Dishwash liquid",
        "LIZOL|FLOOR|COLIN" to "Floor cleaner",
        "HARPIC|TOILET" to "Toilet cleaner",
        "PHENYL|DOCTOR BRAND" to "Phenyl",
        "GARBAGE" to "Garbage bags",
        "SCRUB|REFLECT ST|STEEL WOOL" to "Scrubbers",
        "LIFEBU|DETTOL|LUX|SOAP|SANTOOR|LAVENDE|MEADOWS" to "Bath soap",
        "SHAMPOO" to "Shampoo",
        "TOOTHPASTE|COLGATE|PEPSODENT|CLOSE ?UP" to "Toothpaste",
        "TOOTHBRUSH" to "Toothbrush",
        "HAIR OIL|PARACHUTE" to "Hair oil",
        "FACE ?WASH|DERM" to "Face wash",
        "T ?SHIRT|TSHRT" to "T-shirts",
        "SHIRT|SHT MEN|SHT " to "Shirts",
        "SAREE" to "Sarees",
        "TROUSER|JEANS|PANT" to "Trousers",
        "SOCK" to "Socks",
        "BRIEF|TRUNK|VEST|INNER" to "Innerwear",
    ).map { (pattern, product) -> Regex("\\b(?:$pattern)", RegexOption.IGNORE_CASE) to product }

    /** Removes shop codes and noise: "L LATUR TURDAL" -> "LATUR TURDAL", "(DK)HALDI POWD-100g" -> "HALDI POWD". */
    fun clean(name: String): String = name.uppercase()
        .replace(Regex("\\([A-Z]{1,4}\\)"), " ")
        .replace(Regex("^(L|ZZT|ZZ)\\s+"), "")
        .replace(Regex("[-–]?\\s*\\d+(?:\\.\\d+)?\\s*(KG|KGS|G|GM|GMS|ML|L|LT|LTR|NOS)\\b"), " ")
        .replace(Regex("[^A-Z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** Best catalog product name for a receipt item, or null when unsure. */
    fun suggest(receiptName: String, catalogNames: Collection<String>): String? {
        val cleaned = clean(receiptName)
        val available = catalogNames.associateBy { it.lowercase() }
        rules.firstOrNull { it.first.containsMatchIn(cleaned) }?.second?.let { available[it.lowercase()] }?.let { return it }
        // Otherwise a catalog name inside the text (covers products the user added themselves).
        return catalogNames.sortedByDescending { it.length }.firstOrNull { n ->
            val key = VoiceCommandParser.matchKey(n).uppercase()
            key.length >= 3 && Regex("\\b${Regex.escape(key)}").containsMatchIn(cleaned)
        }
    }
}
