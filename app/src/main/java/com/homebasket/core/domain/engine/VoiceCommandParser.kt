package com.homebasket.core.domain.engine

import com.homebasket.core.domain.model.InputUnit

enum class MealSlotKind(val order: Int) { BREAKFAST(0), LUNCH(1), SNACKS(2), DINNER(3) }

/** A parsed command. Nothing is executed until the user confirms it on screen. */
sealed interface VoiceCommand {
    data class AddStock(val amount: Double, val unit: InputUnit?, val productText: String, val locationText: String) : VoiceCommand
    data class AddToShopping(val amount: Double, val unit: InputUnit?, val productText: String) : VoiceCommand
    data class PlanMeal(val dishText: String, val dayOffset: Int, val slot: MealSlotKind) : VoiceCommand
    data object ShowTomorrowShopping : VoiceCommand
    data class Unknown(val text: String) : VoiceCommand
}

/**
 * Small, rule-based command grammar. English sentences are fully supported; common Hindi and Tamil
 * unit/place/meal words are recognised so mixed speech ("2 kilo tomato fridge mein") still works.
 * Anything that does not parse is returned as [VoiceCommand.Unknown] so the user can retype it.
 */
object VoiceCommandParser {

    private val numberWords = mapOf(
        "half" to 0.5, "a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0,
        "five" to 5.0, "six" to 6.0, "seven" to 7.0, "eight" to 8.0, "nine" to 9.0, "ten" to 10.0,
        "ek" to 1.0, "do" to 2.0, "teen" to 3.0, "char" to 4.0, "paanch" to 5.0, "aadha" to 0.5,
        "एक" to 1.0, "दो" to 2.0, "तीन" to 3.0, "चार" to 4.0, "पांच" to 5.0, "आधा" to 0.5,
        "ஒன்று" to 1.0, "இரண்டு" to 2.0, "மூன்று" to 3.0, "நான்கு" to 4.0, "ஐந்து" to 5.0, "அரை" to 0.5,
    )

    private val unitWords: List<Pair<Regex, InputUnit>> = listOf(
        "kgs?|kilos?|kilograms?|किलो|கிலோ" to InputUnit.KG,
        "g|gms?|grams?|ग्राम|கிராம்" to InputUnit.G,
        "l|ltrs?|litres?|liters?|लीटर|லிட்டர்" to InputUnit.L,
        "ml|millilitres?|milliliters?" to InputUnit.ML,
        "dozens?|दर्जन|டஜன்" to InputUnit.DOZEN,
        "pcs|pieces?|nos|packets?|packs?|bunch(?:es)?|bottles?|पैकेट|பாக்கெட்" to InputUnit.PCS,
    ).map { (pattern, unit) -> Regex("^(?:$pattern)$") to unit }

    private val slotWords = listOf(
        MealSlotKind.BREAKFAST to listOf("breakfast", "tiffin", "nashta", "नाश्ता", "காலை உணவு", "காலை"),
        MealSlotKind.LUNCH to listOf("lunch", "khana", "दोपहर", "मध्याह्न", "மதிய உணவு", "மதியம்"),
        MealSlotKind.SNACKS to listOf("evening snacks", "evening snack", "snacks", "snack", "evening", "शाम", "மாலை", "சிற்றுண்டி"),
        MealSlotKind.DINNER to listOf("dinner", "supper", "रात", "இரவு உணவு", "இரவு"),
    )

    private val shoppingWords = listOf("shopping list", "shopping", "list", "शॉपिंग", "लिस्ट", "ஷாப்பிங்", "பட்டியல்")

    fun parse(raw: String): VoiceCommand {
        val text = raw.trim().lowercase().replace(Regex("[.!?,]"), " ").replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) return VoiceCommand.Unknown(raw)

        if (Regex("\\b(show|open|what)\\b.*\\btomorrow'?s?\\b.*\\b(shopping|list)\\b").containsMatchIn(text) ||
            text.contains("कल की शॉपिंग") || text.contains("நாளை ஷாப்பிங்")
        ) return VoiceCommand.ShowTomorrowShopping

        parsePlan(text)?.let { return it }
        parseAdd(text)?.let { return it }
        return VoiceCommand.Unknown(raw)
    }

    private fun parsePlan(text: String): VoiceCommand? {
        val m = Regex("^(?:plan|make|cook|prepare)\\s+(.+?)\\s+for\\s+(.+)$").find(text) ?: return null
        val dish = m.groupValues[1].trim()
        val rest = m.groupValues[2]
        val dayOffset = when {
            rest.contains("day after tomorrow") -> 2
            rest.contains("tomorrow") || rest.contains("कल") || rest.contains("நாளை") -> 1
            else -> 0
        }
        val slot = slotWords.firstOrNull { (_, words) -> words.any { rest.contains(it) } }?.first ?: return null
        return VoiceCommand.PlanMeal(dish, dayOffset, slot)
    }

    private fun parseAdd(text: String): VoiceCommand? {
        val stripped = text.removePrefix("please ")
        val verb = Regex("^(?:add|put|buy|store|daalo|डालो|जोड़ो|சேர்)\\s+").find(stripped)
        val body = verb?.let { stripped.substring(it.range.last + 1) } ?: stripped
        val tokens = body.split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        // Without an "add" verb only sentences that start with a quantity are accepted ("2 kg rice to list").
        if (verb == null && !tokens[0].first().isDigit() && !numberWords.containsKey(tokens[0])) return null

        var index = 0
        var amount = 1.0
        val first = tokens[0]
        val numeric = Regex("^(\\d+(?:[.,]\\d+)?)([a-z]+)?$").find(first)
        var unit: InputUnit? = null
        when {
            numeric != null -> {
                amount = numeric.groupValues[1].replace(',', '.').toDouble()
                numeric.groupValues[2].takeIf { it.isNotEmpty() }?.let { unit = unitFor(it) }
                index = 1
            }
            numberWords.containsKey(first) -> { amount = numberWords.getValue(first); index = 1 }
        }
        if (unit == null && index < tokens.size) {
            unitFor(tokens[index])?.let { unit = it; index++ }
        }
        if (index < tokens.size && tokens[index] == "of") index++

        val remainder = tokens.drop(index).joinToString(" ")
        val split = Regex("\\s(?:to|in|into|mein|में|ல்|இல்)\\s(?:the\\s|my\\s)?").find(" $remainder ")
        val productPart: String
        val targetPart: String
        if (split != null) {
            val padded = " $remainder "
            productPart = padded.substring(0, split.range.first).trim()
            targetPart = padded.substring(split.range.last + 1).trim()
        } else {
            productPart = remainder.trim()
            targetPart = ""
        }
        if (productPart.isBlank()) return null

        val isShopping = shoppingWords.any { targetPart.contains(it) } || (targetPart.isEmpty() && text.startsWith("buy "))
        return if (isShopping) VoiceCommand.AddToShopping(amount, unit, productPart)
        else VoiceCommand.AddStock(amount, unit, productPart, targetPart)
    }

    private fun unitFor(word: String): InputUnit? = unitWords.firstOrNull { it.first.matches(word) }?.second

    /** Loose matching key: lowercase, no plural "s"/"es". "Tomatoes" and "tomato" both become "tomato". */
    fun matchKey(name: String): String {
        val n = name.trim().lowercase()
        return when {
            n.endsWith("oes") -> n.dropLast(2)
            n.endsWith("ies") -> n.dropLast(3) + "y"
            n.endsWith("s") && !n.endsWith("ss") && n.length > 3 -> n.dropLast(1)
            else -> n
        }
    }
}
