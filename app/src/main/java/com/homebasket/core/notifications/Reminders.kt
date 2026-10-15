package com.homebasket.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.homebasket.MainActivity
import com.homebasket.R
import com.homebasket.core.data.BudgetRepository
import com.homebasket.core.data.Clock
import com.homebasket.core.data.HouseholdRepository
import com.homebasket.core.data.InventoryRepository
import com.homebasket.core.data.PlanningRepository
import com.homebasket.core.data.ReminderType
import com.homebasket.core.data.SettingsRepository
import com.homebasket.core.data.ShoppingRepository
import com.homebasket.core.data.key
import com.homebasket.core.database.HomeBasketDatabase
import com.homebasket.core.database.entity.FoodCondition
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class ReminderRun { MORNING, EVENING }

/**
 * Two daily runs (morning and evening, at user-chosen times) gather every enabled reminder into at most one
 * notification each. Each run re-schedules the next one. Work is unique per run, so it can never pile up.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    suspend fun rescheduleAll() {
        val prefs = settings.current().notifications
        val wm = WorkManager.getInstance(context)
        if (prefs.enabled.isEmpty()) {
            ReminderRun.entries.forEach { wm.cancelUniqueWork(workName(it)) }
            return
        }
        schedule(ReminderRun.MORNING, prefs.morningMinutes)
        schedule(ReminderRun.EVENING, prefs.eveningMinutes)
    }

    fun schedule(run: ReminderRun, minutesOfDay: Int) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atStartOfDay().plusMinutes(minutesOfDay.toLong())
        if (!next.isAfter(now.plusMinutes(1))) next = next.plusDays(1)
        val delay = ChronoUnit.MILLIS.between(now, next)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_RUN to run.name))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(run), ExistingWorkPolicy.REPLACE, request)
    }

    private fun workName(run: ReminderRun) = "reminder_${run.name.lowercase()}"
}

@Singleton
class Notifier @Inject constructor(@ApplicationContext private val context: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.notif_channel_desc) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun canPost(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(id: Int, title: String, lines: List<String>, route: String) {
        if (!canPost() || lines.isEmpty()) return
        ensureChannel()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_ROUTE, route)
        }
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val style = NotificationCompat.InboxStyle().also { s -> lines.forEach { s.addLine(it) } }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_basket)
            .setColor(0xFF247A50.toInt())
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(style)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the post; nothing to do.
        }
    }

    companion object {
        const val CHANNEL = "household_reminders"
    }
}

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val notifier: Notifier,
    private val planning: PlanningRepository,
    private val shopping: ShoppingRepository,
    private val inventory: InventoryRepository,
    private val household: HouseholdRepository,
    private val budget: BudgetRepository,
    private val db: HomeBasketDatabase,
    private val clock: Clock,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val run = runCatching { ReminderRun.valueOf(inputData.getString(KEY_RUN).orEmpty()) }.getOrDefault(ReminderRun.EVENING)
        val prefs = settings.current().notifications
        try {
            val today = clock.today()
            val day = today.toEpochDay()
            val enabled = prefs.enabled
            val lines = mutableListOf<String>()
            suspend fun claim(type: ReminderType) = type in enabled && settings.claimDailySlot("${run.name}_${type.name}", day)
            val ctx = applicationContext

            if (run == ReminderRun.MORNING) {
                if (claim(ReminderType.MORNING_MILK)) {
                    val milk = db.catalogDao().getProductByName("Milk")
                    if (milk != null) {
                        val have = inventory.usableTotals()[milk.id] ?: 0.0
                        val need = db.mealDao().getPlannedNeeds(day, day).filter { it.productId == milk.id }.sumOf { it.quantity }
                        if (have < need + 1.0) lines += ctx.getString(R.string.notif_milk)
                    }
                }
                if (claim(ReminderType.EXPIRY)) {
                    val warn = household.getSettings()?.expiryWarningDays ?: 2
                    val count = inventory.lots.first().count {
                        it.lot.condition == FoodCondition.USE_SOON || (it.lot.expiryEpochDay != null && it.lot.expiryEpochDay <= day + warn && it.lot.condition == FoodCondition.FRESH)
                    }
                    if (count > 0) lines += ctx.resources.getQuantityString(R.plurals.notif_expiry, count, count)
                }
                if (claim(ReminderType.LOW_STOCK)) {
                    val count = planning.shortages(day, day - 1, includeLowStock = true).count { it.isLowStockOnly }
                    if (count > 0) lines += ctx.resources.getQuantityString(R.plurals.notif_low_stock, count, count)
                }
                val shoppingDay = household.getHousehold()?.shoppingDay ?: 7
                if (today.plusDays(1).dayOfWeek.value == shoppingDay && claim(ReminderType.STOCK_CHECK)) lines += ctx.getString(R.string.notif_stock_check)
                if (lines.isNotEmpty()) notifier.post(1001, ctx.getString(R.string.notif_morning_title), lines, "home")
            } else {
                if (claim(ReminderType.TOMORROW_SHOPPING)) {
                    val tomorrow = day + 1
                    val needed = planning.shortages(tomorrow, tomorrow, includeLowStock = false).size
                    val open = shopping.observeOpenTomorrowCount().first()
                    val count = maxOf(needed, open)
                    if (count > 0) lines += ctx.resources.getQuantityString(R.plurals.notif_tomorrow, count, count)
                }
                if (today.dayOfWeek == DayOfWeek.SATURDAY && claim(ReminderType.WEEKLY_MEAL_PLAN)) {
                    val nextMonday = today.plusDays(2).toEpochDay()
                    val planned = db.mealDao().getSlots(nextMonday, nextMonday + 6).size
                    if (planned < 7) lines += ctx.getString(R.string.notif_weekly_plan)
                }
                if (today.plusDays(2).monthValue != today.monthValue && claim(ReminderType.MONTHLY_PLANNING)) lines += ctx.getString(R.string.notif_monthly)
                if (ReminderType.BUDGET in enabled) {
                    val overview = budget.current(YearMonth.from(today))
                    overview.crossedThreshold?.let { t ->
                        if (settings.claimDailySlot("budget_${YearMonth.from(today).key()}_$t", 0)) {
                            lines += ctx.getString(R.string.notif_budget, t)
                        }
                    }
                }
                if (lines.isNotEmpty()) notifier.post(1002, ctx.getString(R.string.notif_evening_title), lines, "shopping")
            }
        } finally {
            scheduler.schedule(run, if (run == ReminderRun.MORNING) prefs.morningMinutes else prefs.eveningMinutes)
        }
        return Result.success()
    }

    companion object {
        const val KEY_RUN = "run"
    }
}

/** Reminders are re-planned after reboot, app update and clock/time-zone changes. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var settings: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { scheduler.rescheduleAll() } finally { pending.finish() }
        }
    }
}
