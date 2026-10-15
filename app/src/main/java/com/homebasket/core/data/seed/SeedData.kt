package com.homebasket.core.data.seed

import com.homebasket.core.database.entity.BudgetCategory
import com.homebasket.core.database.entity.Diet
import com.homebasket.core.database.entity.LocationKind
import com.homebasket.core.database.entity.PurchaseFrequency
import com.homebasket.core.database.entity.PurchaseFrequency.DAILY
import com.homebasket.core.database.entity.PurchaseFrequency.MONTHLY
import com.homebasket.core.database.entity.PurchaseFrequency.OCCASIONAL
import com.homebasket.core.database.entity.PurchaseFrequency.WEEKLY
import com.homebasket.core.domain.engine.MealSlotKind
import com.homebasket.core.domain.engine.MealSlotKind.BREAKFAST
import com.homebasket.core.domain.engine.MealSlotKind.DINNER
import com.homebasket.core.domain.engine.MealSlotKind.LUNCH
import com.homebasket.core.domain.engine.MealSlotKind.SNACKS
import com.homebasket.core.domain.model.UnitKind
import com.homebasket.core.domain.model.UnitKind.COUNT
import com.homebasket.core.domain.model.UnitKind.VOLUME
import com.homebasket.core.domain.model.UnitKind.WEIGHT

data class SeedLocation(val name: String, val kind: LocationKind)

data class SeedCategory(val name: String, val budget: BudgetCategory, val perishable: Boolean)

data class SeedProduct(
    val name: String,
    val kind: UnitKind,
    val pack: Double,
    val label: String = "pcs",
    val shelfLifeDays: Int? = null,
    val location: LocationKind = LocationKind.PANTRY,
    val frequency: PurchaseFrequency = MONTHLY,
    val minStock: Double = 0.0,
    val desiredStock: Double = 0.0,
)

data class SeedRecipe(
    val name: String,
    val slots: List<MealSlotKind>,
    val prep: Int,
    val cook: Int,
    val diet: Diet,
    val tags: String,
    val instructions: String,
    /** Product name to base units per serving. */
    val ingredients: List<Pair<String, Double>>,
    val servings: Int = 4,
)

/**
 * Starting catalog for an Indian household. Everything here is editable or deletable in the app.
 * No prices are seeded: estimates appear only after the user records a real price, so the app never
 * pretends to know DMart or market prices.
 */
object SeedData {

    val locations = listOf(
        SeedLocation("Kitchen / Pantry", LocationKind.PANTRY),
        SeedLocation("Refrigerator", LocationKind.FRIDGE),
        SeedLocation("Freezer", LocationKind.FREEZER),
        SeedLocation("Storeroom", LocationKind.STOREROOM),
        SeedLocation("Vegetable Basket", LocationKind.BASKET),
    )

    private val FRIDGE = LocationKind.FRIDGE
    private val FREEZER = LocationKind.FREEZER
    private val BASKET = LocationKind.BASKET
    private val STORE = LocationKind.STOREROOM

    val catalog: List<Pair<SeedCategory, List<SeedProduct>>> = listOf(
        SeedCategory("Rice & Grains", BudgetCategory.GROCERY, false) to listOf(
            SeedProduct("Rice", WEIGHT, 5000.0, minStock = 2000.0, desiredStock = 10000.0),
            SeedProduct("Basmati rice", WEIGHT, 1000.0),
            SeedProduct("Idli rice", WEIGHT, 5000.0, minStock = 1000.0, desiredStock = 5000.0),
            SeedProduct("Wheat", WEIGHT, 5000.0),
            SeedProduct("Atta", WEIGHT, 5000.0, minStock = 1000.0, desiredStock = 5000.0),
            SeedProduct("Maida", WEIGHT, 1000.0),
            SeedProduct("Rava", WEIGHT, 500.0),
            SeedProduct("Poha", WEIGHT, 500.0),
            SeedProduct("Millet", WEIGHT, 500.0),
            SeedProduct("Oats", WEIGHT, 1000.0),
            SeedProduct("Besan", WEIGHT, 500.0),
        ),
        SeedCategory("Dal & Pulses", BudgetCategory.GROCERY, false) to listOf(
            SeedProduct("Toor dal", WEIGHT, 1000.0, minStock = 500.0, desiredStock = 2000.0),
            SeedProduct("Moong dal", WEIGHT, 500.0),
            SeedProduct("Urad dal", WEIGHT, 500.0, minStock = 250.0, desiredStock = 1000.0),
            SeedProduct("Chana dal", WEIGHT, 500.0),
            SeedProduct("Masoor dal", WEIGHT, 500.0),
            SeedProduct("Rajma", WEIGHT, 500.0),
            SeedProduct("Chickpeas", WEIGHT, 500.0),
            SeedProduct("Green gram", WEIGHT, 500.0),
            SeedProduct("Black chana", WEIGHT, 500.0),
        ),
        SeedCategory("Whole Spices", BudgetCategory.GROCERY, false) to listOf(
            SeedProduct("Mustard seeds", WEIGHT, 100.0),
            SeedProduct("Cumin seeds", WEIGHT, 100.0),
            SeedProduct("Black pepper", WEIGHT, 50.0),
            SeedProduct("Cloves", WEIGHT, 25.0),
            SeedProduct("Cardamom", WEIGHT, 25.0),
            SeedProduct("Cinnamon", WEIGHT, 25.0),
            SeedProduct("Star anise", WEIGHT, 25.0),
            SeedProduct("Fennel seeds", WEIGHT, 100.0),
            SeedProduct("Fenugreek", WEIGHT, 100.0),
            SeedProduct("Bay leaves", WEIGHT, 10.0),
        ),
        SeedCategory("Masala Powders", BudgetCategory.GROCERY, false) to listOf(
            SeedProduct("Turmeric", WEIGHT, 100.0),
            SeedProduct("Red chilli powder", WEIGHT, 100.0),
            SeedProduct("Coriander powder", WEIGHT, 100.0),
            SeedProduct("Garam masala", WEIGHT, 50.0),
            SeedProduct("Sambar powder", WEIGHT, 100.0),
            SeedProduct("Rasam powder", WEIGHT, 100.0),
            SeedProduct("Biryani masala", WEIGHT, 50.0),
            SeedProduct("Chicken masala", WEIGHT, 50.0),
            SeedProduct("Curry powder", WEIGHT, 100.0),
        ),
        SeedCategory("Cooking Essentials", BudgetCategory.GROCERY, false) to listOf(
            SeedProduct("Sunflower oil", VOLUME, 1000.0, minStock = 500.0, desiredStock = 2000.0),
            SeedProduct("Groundnut oil", VOLUME, 1000.0),
            SeedProduct("Coconut oil", VOLUME, 500.0),
            SeedProduct("Ghee", VOLUME, 500.0),
            SeedProduct("Salt", WEIGHT, 1000.0, minStock = 250.0, desiredStock = 1000.0),
            SeedProduct("Sugar", WEIGHT, 1000.0),
            SeedProduct("Jaggery", WEIGHT, 500.0),
            SeedProduct("Tamarind", WEIGHT, 250.0),
            SeedProduct("Tea", WEIGHT, 250.0),
            SeedProduct("Coffee", WEIGHT, 200.0),
        ),
        SeedCategory("Bakery & Snacks", BudgetCategory.SNACKS, true) to listOf(
            SeedProduct("Bread", WEIGHT, 400.0, shelfLifeDays = 4, frequency = WEEKLY),
            SeedProduct("Biscuits", WEIGHT, 200.0, frequency = WEEKLY),
        ),
        SeedCategory("Vegetables", BudgetCategory.VEGETABLES, true) to listOf(
            SeedProduct("Tomato", WEIGHT, 250.0, shelfLifeDays = 6, location = FRIDGE, frequency = DAILY),
            SeedProduct("Onion", WEIGHT, 500.0, shelfLifeDays = 20, location = BASKET, frequency = WEEKLY),
            SeedProduct("Potato", WEIGHT, 500.0, shelfLifeDays = 20, location = BASKET, frequency = WEEKLY),
            SeedProduct("Carrot", WEIGHT, 250.0, shelfLifeDays = 10, location = FRIDGE, frequency = WEEKLY),
            SeedProduct("Beans", WEIGHT, 250.0, shelfLifeDays = 5, location = FRIDGE, frequency = WEEKLY),
            SeedProduct("Brinjal", WEIGHT, 250.0, shelfLifeDays = 5, location = FRIDGE, frequency = WEEKLY),
            SeedProduct("Okra", WEIGHT, 250.0, shelfLifeDays = 4, location = FRIDGE, frequency = WEEKLY),
            SeedProduct("Cabbage", WEIGHT, 500.0, shelfLifeDays = 10, location = FRIDGE, frequency = WEEKLY),
            SeedProduct("Cauliflower", COUNT, 1.0, shelfLifeDays = 6, location = FRIDGE, frequency = WEEKLY),
            SeedProduct("Spinach", COUNT, 1.0, "bunch", shelfLifeDays = 2, location = FRIDGE, frequency = DAILY),
            SeedProduct("Coriander leaves", COUNT, 1.0, "bunch", shelfLifeDays = 4, location = FRIDGE, frequency = DAILY),
            SeedProduct("Mint", COUNT, 1.0, "bunch", shelfLifeDays = 4, location = FRIDGE, frequency = WEEKLY),
            SeedProduct("Drumstick", COUNT, 2.0, shelfLifeDays = 5, location = FRIDGE, frequency = WEEKLY),
            SeedProduct("Beetroot", WEIGHT, 250.0, shelfLifeDays = 10, location = FRIDGE, frequency = WEEKLY),
            SeedProduct("Radish", WEIGHT, 250.0, shelfLifeDays = 6, location = FRIDGE, frequency = WEEKLY),
            SeedProduct("Green chilli", WEIGHT, 100.0, shelfLifeDays = 7, location = FRIDGE, frequency = WEEKLY),
            SeedProduct("Ginger", WEIGHT, 100.0, shelfLifeDays = 14, location = FRIDGE, frequency = WEEKLY),
            SeedProduct("Garlic", WEIGHT, 250.0, shelfLifeDays = 30, location = BASKET, frequency = WEEKLY),
            SeedProduct("Cucumber", WEIGHT, 250.0, shelfLifeDays = 5, location = FRIDGE, frequency = WEEKLY),
            SeedProduct("Coconut", COUNT, 1.0, shelfLifeDays = 7, location = BASKET, frequency = WEEKLY),
            SeedProduct("Lemon", COUNT, 4.0, shelfLifeDays = 10, location = FRIDGE, frequency = WEEKLY),
        ),
        SeedCategory("Fruits", BudgetCategory.FRUITS, true) to listOf(
            SeedProduct("Banana", COUNT, 6.0, shelfLifeDays = 4, location = BASKET, frequency = WEEKLY),
            SeedProduct("Apple", WEIGHT, 500.0, shelfLifeDays = 14, location = FRIDGE, frequency = WEEKLY),
            SeedProduct("Orange", WEIGHT, 500.0, shelfLifeDays = 10, location = FRIDGE, frequency = WEEKLY),
            SeedProduct("Grapes", WEIGHT, 500.0, shelfLifeDays = 4, location = FRIDGE, frequency = WEEKLY),
            SeedProduct("Mango", WEIGHT, 1000.0, shelfLifeDays = 5, location = BASKET, frequency = OCCASIONAL),
            SeedProduct("Papaya", COUNT, 1.0, shelfLifeDays = 4, location = BASKET, frequency = WEEKLY),
            SeedProduct("Guava", WEIGHT, 500.0, shelfLifeDays = 5, location = BASKET, frequency = WEEKLY),
            SeedProduct("Pomegranate", WEIGHT, 500.0, shelfLifeDays = 14, location = FRIDGE, frequency = WEEKLY),
        ),
        SeedCategory("Dairy", BudgetCategory.DAIRY, true) to listOf(
            SeedProduct("Milk", VOLUME, 500.0, shelfLifeDays = 2, location = FRIDGE, frequency = DAILY),
            SeedProduct("Curd", WEIGHT, 500.0, shelfLifeDays = 4, location = FRIDGE, frequency = DAILY),
            SeedProduct("Butter", WEIGHT, 100.0, shelfLifeDays = 60, location = FRIDGE),
            SeedProduct("Paneer", WEIGHT, 200.0, shelfLifeDays = 4, location = FRIDGE, frequency = WEEKLY),
            SeedProduct("Cheese", WEIGHT, 200.0, shelfLifeDays = 30, location = FRIDGE),
            SeedProduct("Buttermilk", VOLUME, 500.0, shelfLifeDays = 2, location = FRIDGE, frequency = DAILY),
        ),
        SeedCategory("Meat & Eggs", BudgetCategory.GROCERY, true) to listOf(
            SeedProduct("Eggs", COUNT, 6.0, shelfLifeDays = 14, location = FRIDGE, frequency = WEEKLY),
            SeedProduct("Chicken", WEIGHT, 500.0, shelfLifeDays = 2, location = FREEZER, frequency = WEEKLY),
            SeedProduct("Fish", WEIGHT, 500.0, shelfLifeDays = 1, location = FREEZER, frequency = WEEKLY),
        ),
        SeedCategory("Cleaning", BudgetCategory.CLEANING, false) to listOf(
            SeedProduct("Washing powder", WEIGHT, 1000.0, location = STORE),
            SeedProduct("Dishwash liquid", VOLUME, 500.0, location = STORE),
            SeedProduct("Floor cleaner", VOLUME, 1000.0, location = STORE),
            SeedProduct("Toilet cleaner", VOLUME, 500.0, location = STORE),
            SeedProduct("Phenyl", VOLUME, 1000.0, location = STORE),
            SeedProduct("Garbage bags", COUNT, 1.0, "roll", location = STORE),
            SeedProduct("Scrubbers", COUNT, 3.0, location = STORE),
        ),
        SeedCategory("Personal Care", BudgetCategory.PERSONAL_CARE, false) to listOf(
            SeedProduct("Bath soap", COUNT, 4.0, location = STORE),
            SeedProduct("Shampoo", VOLUME, 340.0, location = STORE),
            SeedProduct("Toothpaste", WEIGHT, 150.0, location = STORE),
            SeedProduct("Toothbrush", COUNT, 2.0, location = STORE, frequency = OCCASIONAL),
            SeedProduct("Hair oil", VOLUME, 300.0, location = STORE),
            SeedProduct("Face wash", WEIGHT, 100.0, location = STORE),
        ),
        SeedCategory("Home Essentials", BudgetCategory.HOUSEHOLD, false) to listOf(
            SeedProduct("Kitchen utensils", COUNT, 1.0, location = STORE, frequency = OCCASIONAL),
            SeedProduct("Storage containers", COUNT, 1.0, location = STORE, frequency = OCCASIONAL),
            SeedProduct("Buckets", COUNT, 1.0, location = STORE, frequency = OCCASIONAL),
            SeedProduct("Towels", COUNT, 1.0, location = STORE, frequency = OCCASIONAL),
            SeedProduct("Bedsheets", COUNT, 1.0, location = STORE, frequency = OCCASIONAL),
            SeedProduct("Kitchen cleaning accessories", COUNT, 1.0, location = STORE, frequency = OCCASIONAL),
        ),
        SeedCategory("Clothing", BudgetCategory.CLOTHING, false) to listOf(
            SeedProduct("Shirts", COUNT, 1.0, location = STORE, frequency = OCCASIONAL),
            SeedProduct("T-shirts", COUNT, 1.0, location = STORE, frequency = OCCASIONAL),
            SeedProduct("Sarees", COUNT, 1.0, location = STORE, frequency = OCCASIONAL),
            SeedProduct("Trousers", COUNT, 1.0, location = STORE, frequency = OCCASIONAL),
            SeedProduct("Socks", COUNT, 1.0, "pair", location = STORE, frequency = OCCASIONAL),
            SeedProduct("Innerwear", COUNT, 1.0, location = STORE, frequency = OCCASIONAL),
            SeedProduct("Children's clothing", COUNT, 1.0, location = STORE, frequency = OCCASIONAL),
        ),
    )

    private const val TEMPER = "Heat oil, add mustard seeds and let them splutter."

    val recipes: List<SeedRecipe> = listOf(
        SeedRecipe(
            "Idli", listOf(BREAKFAST, DINNER), 20, 15, Diet.VEG, "south-indian,steamed",
            "Soak idli rice and urad dal separately for 5 hours with a pinch of fenugreek. Grind, mix with salt and ferment overnight. Steam in greased idli plates for 10-12 minutes.",
            listOf("Idli rice" to 60.0, "Urad dal" to 15.0, "Fenugreek" to 0.5, "Salt" to 2.0),
        ),
        SeedRecipe(
            "Dosa", listOf(BREAKFAST, DINNER), 20, 20, Diet.VEG, "south-indian",
            "Use fermented idli/dosa batter. Spread a ladle thinly on a hot tawa, drizzle oil and cook until golden.",
            listOf("Idli rice" to 55.0, "Urad dal" to 15.0, "Fenugreek" to 0.5, "Salt" to 2.0, "Sunflower oil" to 5.0),
        ),
        SeedRecipe(
            "Pongal", listOf(BREAKFAST), 10, 25, Diet.VEG, "south-indian,festival",
            "Dry roast moong dal, pressure cook with rice and 4 cups water. Temper pepper, cumin and ginger in ghee and mix in with salt.",
            listOf("Rice" to 50.0, "Moong dal" to 20.0, "Ghee" to 8.0, "Black pepper" to 1.0, "Cumin seeds" to 1.0, "Ginger" to 3.0, "Salt" to 2.0),
        ),
        SeedRecipe(
            "Upma", listOf(BREAKFAST), 10, 15, Diet.VEG, "quick",
            "Roast rava. $TEMPER Add urad dal, onion, chilli and ginger. Add water and salt, boil, then stir in rava until thick.",
            listOf("Rava" to 50.0, "Onion" to 20.0, "Green chilli" to 3.0, "Ginger" to 2.0, "Mustard seeds" to 1.0, "Urad dal" to 2.0, "Sunflower oil" to 8.0, "Salt" to 2.0, "Coriander leaves" to 0.05),
        ),
        SeedRecipe(
            "Poha", listOf(BREAKFAST, SNACKS), 10, 10, Diet.VEG, "quick",
            "Rinse poha. $TEMPER Saute onion, potato and chilli, add turmeric and salt, mix in poha and finish with lemon.",
            listOf("Poha" to 50.0, "Onion" to 25.0, "Potato" to 30.0, "Green chilli" to 3.0, "Mustard seeds" to 1.0, "Turmeric" to 0.5, "Sunflower oil" to 8.0, "Salt" to 2.0, "Lemon" to 0.25),
        ),
        SeedRecipe(
            "Chapati", listOf(LUNCH, DINNER), 15, 15, Diet.VEG, "north-indian",
            "Knead atta with water, salt and a little oil. Rest 15 minutes, roll and cook on a hot tawa.",
            listOf("Atta" to 70.0, "Sunflower oil" to 3.0, "Salt" to 1.0),
        ),
        SeedRecipe(
            "Poori", listOf(BREAKFAST), 15, 15, Diet.VEG, "festival",
            "Knead a stiff atta dough with salt, roll small discs and deep fry until puffed.",
            listOf("Atta" to 60.0, "Sunflower oil" to 25.0, "Salt" to 1.0),
        ),
        SeedRecipe(
            "Sambar", listOf(BREAKFAST, LUNCH, DINNER), 15, 30, Diet.VEG, "south-indian",
            "Pressure cook toor dal with turmeric. Boil vegetables in tamarind water with sambar powder and salt, add dal. $TEMPER Garnish with coriander.",
            listOf("Toor dal" to 25.0, "Tomato" to 30.0, "Onion" to 20.0, "Drumstick" to 0.25, "Sambar powder" to 5.0, "Tamarind" to 4.0, "Mustard seeds" to 1.0, "Turmeric" to 0.5, "Sunflower oil" to 5.0, "Salt" to 2.0, "Coriander leaves" to 0.05),
        ),
        SeedRecipe(
            "Rasam", listOf(LUNCH), 10, 20, Diet.VEG, "south-indian",
            "Boil tomato in tamarind water with rasam powder, crushed garlic, pepper and salt. Add a little cooked dal water. Temper with ghee, mustard and cumin.",
            listOf("Toor dal" to 10.0, "Tomato" to 40.0, "Tamarind" to 4.0, "Rasam powder" to 4.0, "Garlic" to 3.0, "Black pepper" to 1.0, "Cumin seeds" to 1.0, "Mustard seeds" to 0.5, "Ghee" to 3.0, "Salt" to 2.0, "Coriander leaves" to 0.05),
        ),
        SeedRecipe(
            "Vegetable Biryani", listOf(LUNCH, DINNER), 25, 35, Diet.VEG, "sunday-special,festival",
            "Soak basmati rice. Fry onions, add ginger-garlic, tomato, vegetables, curd and biryani masala. Add rice and water, cook covered on low heat.",
            listOf("Basmati rice" to 80.0, "Onion" to 40.0, "Tomato" to 30.0, "Carrot" to 25.0, "Beans" to 20.0, "Potato" to 30.0, "Curd" to 20.0, "Biryani masala" to 4.0, "Ginger" to 4.0, "Garlic" to 4.0, "Mint" to 0.1, "Green chilli" to 3.0, "Ghee" to 8.0, "Sunflower oil" to 8.0, "Salt" to 3.0),
        ),
        SeedRecipe(
            "Tomato Rice", listOf(LUNCH), 10, 20, Diet.VEG, "lunchbox",
            "Cook rice. $TEMPER Saute onion, chilli and tomato with turmeric, chilli powder and salt until mushy. Mix in rice.",
            listOf("Rice" to 75.0, "Tomato" to 60.0, "Onion" to 25.0, "Green chilli" to 3.0, "Mustard seeds" to 1.0, "Turmeric" to 0.5, "Red chilli powder" to 1.0, "Sunflower oil" to 10.0, "Salt" to 2.0),
        ),
        SeedRecipe(
            "Lemon Rice", listOf(LUNCH), 10, 15, Diet.VEG, "lunchbox,quick",
            "Cook rice and cool. Temper mustard, chana dal, urad dal, chilli and turmeric in oil. Mix with rice, salt and lemon juice.",
            listOf("Rice" to 75.0, "Lemon" to 0.5, "Mustard seeds" to 1.0, "Chana dal" to 3.0, "Urad dal" to 3.0, "Turmeric" to 0.5, "Green chilli" to 3.0, "Groundnut oil" to 8.0, "Salt" to 2.0),
        ),
        SeedRecipe(
            "Curd Rice", listOf(LUNCH, DINNER), 5, 20, Diet.VEG, "south-indian",
            "Mash soft cooked rice with curd, a little milk and salt. Temper mustard, chilli and ginger and mix in.",
            listOf("Rice" to 60.0, "Curd" to 120.0, "Milk" to 30.0, "Mustard seeds" to 0.5, "Green chilli" to 2.0, "Ginger" to 2.0, "Salt" to 2.0),
        ),
        SeedRecipe(
            "Vegetable Curry", listOf(LUNCH, DINNER), 15, 25, Diet.VEG, "north-indian",
            "Saute onion, ginger and garlic, add tomato and spice powders. Add chopped vegetables and water, simmer until cooked.",
            listOf("Potato" to 40.0, "Carrot" to 30.0, "Beans" to 30.0, "Onion" to 30.0, "Tomato" to 30.0, "Ginger" to 3.0, "Garlic" to 3.0, "Garam masala" to 2.0, "Coriander powder" to 2.0, "Red chilli powder" to 1.0, "Turmeric" to 0.5, "Sunflower oil" to 10.0, "Salt" to 2.0),
        ),
        SeedRecipe(
            "Potato Masala", listOf(BREAKFAST, LUNCH, DINNER), 15, 15, Diet.VEG, "south-indian",
            "Boil and mash potatoes. $TEMPER Saute onion, chilli and ginger, add turmeric, salt, potato and a little water.",
            listOf("Potato" to 100.0, "Onion" to 30.0, "Green chilli" to 3.0, "Ginger" to 2.0, "Mustard seeds" to 1.0, "Turmeric" to 0.5, "Sunflower oil" to 8.0, "Salt" to 2.0, "Coriander leaves" to 0.05),
        ),
        SeedRecipe(
            "Beans Poriyal", listOf(LUNCH), 15, 10, Diet.VEG, "south-indian",
            "$TEMPER Add urad dal and chilli, then chopped beans and salt. Cook covered and finish with grated coconut.",
            listOf("Beans" to 80.0, "Coconut" to 0.1, "Mustard seeds" to 1.0, "Urad dal" to 2.0, "Green chilli" to 2.0, "Sunflower oil" to 5.0, "Salt" to 1.5),
        ),
        SeedRecipe(
            "Cabbage Poriyal", listOf(LUNCH), 10, 10, Diet.VEG, "south-indian,quick",
            "$TEMPER Add urad dal and chilli, then shredded cabbage and salt. Cook until just soft and add coconut.",
            listOf("Cabbage" to 100.0, "Coconut" to 0.1, "Mustard seeds" to 1.0, "Urad dal" to 2.0, "Green chilli" to 2.0, "Sunflower oil" to 5.0, "Salt" to 1.5),
        ),
        SeedRecipe(
            "Dal", listOf(LUNCH, DINNER), 10, 25, Diet.VEG, "north-indian",
            "Pressure cook toor dal with tomato and turmeric. Temper cumin, garlic and onion in ghee, add chilli powder and salt, mix with dal.",
            listOf("Toor dal" to 35.0, "Tomato" to 25.0, "Onion" to 20.0, "Garlic" to 3.0, "Cumin seeds" to 1.0, "Turmeric" to 0.5, "Red chilli powder" to 1.0, "Ghee" to 5.0, "Salt" to 2.0),
        ),
        SeedRecipe(
            "Palak Dal", listOf(LUNCH, DINNER), 10, 25, Diet.VEG, "use-soon,spinach",
            "Cook toor dal. Saute garlic and cumin in ghee, add chopped spinach and turmeric, then the dal and salt.",
            listOf("Toor dal" to 30.0, "Spinach" to 0.25, "Garlic" to 3.0, "Cumin seeds" to 1.0, "Turmeric" to 0.5, "Ghee" to 5.0, "Salt" to 2.0),
        ),
        SeedRecipe(
            "Khichdi", listOf(LUNCH, DINNER), 5, 25, Diet.VEG, "light",
            "Wash rice and moong dal, pressure cook with turmeric, ginger, salt and water. Temper cumin in ghee.",
            listOf("Rice" to 50.0, "Moong dal" to 30.0, "Ghee" to 8.0, "Cumin seeds" to 1.0, "Turmeric" to 0.5, "Ginger" to 2.0, "Salt" to 2.0),
        ),
        SeedRecipe(
            "Vegetable Sandwich", listOf(SNACKS, BREAKFAST), 10, 5, Diet.VEG, "kids,quick",
            "Butter the bread, layer sliced tomato, onion and cucumber, season with salt and pepper and toast.",
            listOf("Bread" to 60.0, "Butter" to 8.0, "Tomato" to 30.0, "Onion" to 15.0, "Cucumber" to 30.0, "Salt" to 0.5, "Black pepper" to 0.3),
        ),
        SeedRecipe(
            "Sundal", listOf(SNACKS), 480, 20, Diet.VEG, "festival,protein",
            "Soak chickpeas overnight and pressure cook with salt. $TEMPER Add urad dal and chilli, toss chickpeas and coconut.",
            listOf("Chickpeas" to 40.0, "Coconut" to 0.05, "Mustard seeds" to 0.5, "Urad dal" to 1.0, "Green chilli" to 2.0, "Sunflower oil" to 3.0, "Salt" to 1.0),
        ),
        SeedRecipe(
            "Pakora", listOf(SNACKS), 10, 15, Diet.VEG, "tea-time",
            "Mix besan with sliced onion, chilli, chilli powder, salt and a little water. Deep fry spoonfuls until crisp.",
            listOf("Besan" to 40.0, "Onion" to 50.0, "Green chilli" to 3.0, "Red chilli powder" to 1.0, "Salt" to 1.5, "Sunflower oil" to 20.0),
        ),
        SeedRecipe(
            "Masala Tea", listOf(BREAKFAST, SNACKS), 2, 5, Diet.VEG, "drink",
            "Boil water with tea and a crushed cardamom, add milk and sugar and simmer.",
            listOf("Milk" to 100.0, "Tea" to 3.0, "Sugar" to 8.0, "Cardamom" to 0.2),
        ),
        SeedRecipe(
            "Filter Coffee", listOf(BREAKFAST, SNACKS), 2, 10, Diet.VEG, "drink,south-indian",
            "Brew coffee decoction, mix with hot milk and sugar.",
            listOf("Milk" to 100.0, "Coffee" to 6.0, "Sugar" to 8.0),
        ),
        SeedRecipe(
            "Egg Curry", listOf(LUNCH, DINNER), 15, 25, Diet.EGG, "protein",
            "Boil and peel eggs. Saute onion, ginger and garlic, add tomato and spices, simmer with water and add eggs.",
            listOf("Eggs" to 2.0, "Onion" to 40.0, "Tomato" to 40.0, "Ginger" to 3.0, "Garlic" to 3.0, "Garam masala" to 2.0, "Red chilli powder" to 1.5, "Turmeric" to 0.5, "Sunflower oil" to 10.0, "Salt" to 2.0),
        ),
        SeedRecipe(
            "Chicken Curry", listOf(LUNCH, DINNER), 20, 35, Diet.NON_VEG, "sunday-special",
            "Marinate chicken with curd and chicken masala. Saute onion, ginger and garlic, add tomato, then chicken; cook covered until done.",
            listOf("Chicken" to 150.0, "Onion" to 50.0, "Tomato" to 40.0, "Ginger" to 5.0, "Garlic" to 5.0, "Chicken masala" to 5.0, "Curd" to 20.0, "Sunflower oil" to 12.0, "Salt" to 2.5, "Coriander leaves" to 0.05),
        ),
    )
}
