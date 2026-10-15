package com.homebasket.core.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "homebasket_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Each reminder can be switched on or off independently. All are off until the user opts in. */
enum class ReminderType { TOMORROW_SHOPPING, MORNING_MILK, LOW_STOCK, EXPIRY, MONTHLY_PLANNING, WEEKLY_MEAL_PLAN, STOCK_CHECK, BUDGET }

data class NotificationPreferences(
    val enabled: Set<ReminderType> = emptySet(),
    /** Minutes after midnight. */
    val morningMinutes: Int = 6 * 60 + 30,
    val eveningMinutes: Int = 19 * 60,
)

data class AppPreferences(
    val onboardingDone: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notifications: NotificationPreferences = NotificationPreferences(),
    val includePerishablesInMonthly: Boolean = false,
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_done")
        val theme = stringPreferencesKey("theme_mode")
        val morning = intPreferencesKey("reminder_morning_minutes")
        val evening = intPreferencesKey("reminder_evening_minutes")
        val perishablesMonthly = booleanPreferencesKey("monthly_include_perishables")
        fun reminder(type: ReminderType) = booleanPreferencesKey("reminder_${type.name.lowercase()}")
        fun lastSent(key: String) = longPreferencesKey("last_sent_$key")
    }

    val preferences: Flow<AppPreferences> = context.dataStore.data.map { it.toAppPreferences() }

    suspend fun current(): AppPreferences = preferences.first()

    private fun Preferences.toAppPreferences() = AppPreferences(
        onboardingDone = this[Keys.onboarding] ?: false,
        themeMode = this[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        notifications = NotificationPreferences(
            enabled = ReminderType.entries.filter { this[Keys.reminder(it)] == true }.toSet(),
            morningMinutes = this[Keys.morning] ?: (6 * 60 + 30),
            eveningMinutes = this[Keys.evening] ?: (19 * 60),
        ),
        includePerishablesInMonthly = this[Keys.perishablesMonthly] ?: false,
    )

    suspend fun setOnboardingDone() = context.dataStore.edit { it[Keys.onboarding] = true }
    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setReminder(type: ReminderType, on: Boolean) = context.dataStore.edit { it[Keys.reminder(type)] = on }
    suspend fun setReminderTimes(morning: Int, evening: Int) = context.dataStore.edit {
        it[Keys.morning] = morning
        it[Keys.evening] = evening
    }
    suspend fun setIncludePerishablesInMonthly(on: Boolean) = context.dataStore.edit { it[Keys.perishablesMonthly] = on }

    /** Returns true (and records it) only the first time [key] is claimed for [epochDay]: no duplicate reminders. */
    suspend fun claimDailySlot(key: String, epochDay: Long): Boolean {
        var claimed = false
        context.dataStore.edit {
            if ((it[Keys.lastSent(key)] ?: Long.MIN_VALUE) != epochDay) {
                it[Keys.lastSent(key)] = epochDay
                claimed = true
            }
        }
        return claimed
    }

    suspend fun clearAll() = context.dataStore.edit { it.clear() }
}
