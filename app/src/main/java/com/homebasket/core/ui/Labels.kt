package com.homebasket.core.ui

import androidx.annotation.StringRes
import com.homebasket.R
import com.homebasket.core.data.ReminderType
import com.homebasket.core.database.entity.BudgetCategory
import com.homebasket.core.database.entity.Diet
import com.homebasket.core.database.entity.ExpiryType
import com.homebasket.core.database.entity.FoodCondition
import com.homebasket.core.database.entity.ItemSource
import com.homebasket.core.database.entity.ListType
import com.homebasket.core.database.entity.LocationKind
import com.homebasket.core.database.entity.Priority
import com.homebasket.core.database.entity.PurchaseFrequency
import com.homebasket.core.database.entity.TxType
import com.homebasket.core.database.entity.WasteReason
import com.homebasket.core.domain.engine.MealSlotKind

@StringRes fun conditionLabel(c: FoodCondition) = when (c) {
    FoodCondition.FRESH -> R.string.cond_fresh
    FoodCondition.USE_SOON -> R.string.cond_use_soon
    FoodCondition.SPOILED -> R.string.cond_spoiled
    FoodCondition.DISCARDED -> R.string.cond_discarded
}

@StringRes fun budgetCategoryLabel(c: BudgetCategory) = when (c) {
    BudgetCategory.GROCERY -> R.string.bc_grocery
    BudgetCategory.VEGETABLES -> R.string.bc_vegetables
    BudgetCategory.FRUITS -> R.string.bc_fruits
    BudgetCategory.DAIRY -> R.string.bc_dairy
    BudgetCategory.SNACKS -> R.string.bc_snacks
    BudgetCategory.CLEANING -> R.string.bc_cleaning
    BudgetCategory.PERSONAL_CARE -> R.string.bc_personal_care
    BudgetCategory.CLOTHING -> R.string.bc_clothing
    BudgetCategory.HOUSEHOLD -> R.string.bc_household
}

@StringRes fun slotLabel(s: MealSlotKind) = when (s) {
    MealSlotKind.BREAKFAST -> R.string.slot_breakfast
    MealSlotKind.LUNCH -> R.string.slot_lunch
    MealSlotKind.SNACKS -> R.string.slot_snacks
    MealSlotKind.DINNER -> R.string.slot_dinner
}

@StringRes fun dietLabel(d: Diet) = when (d) {
    Diet.VEG -> R.string.diet_veg
    Diet.EGG -> R.string.diet_egg
    Diet.NON_VEG -> R.string.diet_non_veg
}

/** How a household eats: non-veg households eat both veg and non-veg dishes. */
@StringRes fun householdDietLabel(d: Diet) = when (d) {
    Diet.VEG -> R.string.diet_veg
    Diet.EGG -> R.string.diet_veg_egg
    Diet.NON_VEG -> R.string.diet_both
}

@StringRes fun expiryTypeLabel(e: ExpiryType) = when (e) {
    ExpiryType.ESTIMATE -> R.string.expiry_estimate
    ExpiryType.USE_BY -> R.string.expiry_use_by
    ExpiryType.BEST_BEFORE -> R.string.expiry_best_before
}

@StringRes fun wasteReasonLabel(w: WasteReason) = when (w) {
    WasteReason.SPOILED -> R.string.waste_spoiled
    WasteReason.EXPIRED -> R.string.waste_expired
    WasteReason.LEFTOVER -> R.string.waste_leftover
    WasteReason.OVERCOOKED -> R.string.waste_overcooked
    WasteReason.OTHER -> R.string.waste_other
}

@StringRes fun txTypeLabel(t: TxType) = when (t) {
    TxType.OPENING -> R.string.tx_opening
    TxType.PURCHASE -> R.string.tx_purchase
    TxType.CONSUME -> R.string.tx_consume
    TxType.TRANSFER_OUT -> R.string.tx_transfer_out
    TxType.TRANSFER_IN -> R.string.tx_transfer_in
    TxType.WASTE -> R.string.tx_waste
    TxType.ADJUST -> R.string.tx_adjust
    TxType.RETURN -> R.string.tx_return
    TxType.RESTORE -> R.string.tx_restore
}

@StringRes fun listTypeLabel(t: ListType) = when (t) {
    ListType.TOMORROW -> R.string.lt_tomorrow
    ListType.DAILY -> R.string.lt_daily
    ListType.WEEKLY -> R.string.lt_weekly
    ListType.MONTHLY -> R.string.lt_monthly
    ListType.CUSTOM -> R.string.lt_custom
}

@StringRes fun priorityLabel(p: Priority) = when (p) {
    Priority.HIGH -> R.string.priority_high
    Priority.NORMAL -> R.string.priority_normal
    Priority.LOW -> R.string.priority_low
}

@StringRes fun sourceLabel(s: ItemSource) = when (s) {
    ItemSource.MEALS -> R.string.src_meals
    ItemSource.LOW_STOCK -> R.string.src_low_stock
    ItemSource.RECURRING -> R.string.src_recurring
    ItemSource.MANUAL -> R.string.src_manual
    ItemSource.MONTHLY -> R.string.src_monthly
    ItemSource.VOICE -> R.string.src_voice
    ItemSource.SCAN -> R.string.src_scan
}

@StringRes fun frequencyLabel(f: PurchaseFrequency) = when (f) {
    PurchaseFrequency.DAILY -> R.string.freq_daily
    PurchaseFrequency.WEEKLY -> R.string.freq_weekly
    PurchaseFrequency.MONTHLY -> R.string.freq_monthly
    PurchaseFrequency.OCCASIONAL -> R.string.freq_occasional
}

@StringRes fun locationKindLabel(k: LocationKind) = when (k) {
    LocationKind.PANTRY -> R.string.lk_pantry
    LocationKind.FRIDGE -> R.string.lk_fridge
    LocationKind.FREEZER -> R.string.lk_freezer
    LocationKind.STOREROOM -> R.string.lk_storeroom
    LocationKind.BASKET -> R.string.lk_basket
    LocationKind.OTHER -> R.string.lk_other
}

@StringRes fun reminderLabel(r: ReminderType) = when (r) {
    ReminderType.TOMORROW_SHOPPING -> R.string.rem_tomorrow
    ReminderType.MORNING_MILK -> R.string.rem_milk
    ReminderType.LOW_STOCK -> R.string.rem_low_stock
    ReminderType.EXPIRY -> R.string.rem_expiry
    ReminderType.MONTHLY_PLANNING -> R.string.rem_monthly
    ReminderType.WEEKLY_MEAL_PLAN -> R.string.rem_weekly
    ReminderType.STOCK_CHECK -> R.string.rem_stock_check
    ReminderType.BUDGET -> R.string.rem_budget
}
