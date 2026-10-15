package com.homebasket

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.homebasket.core.data.ApplicationScope
import com.homebasket.core.data.seed.Seeder
import com.homebasket.core.notifications.Notifier
import com.homebasket.core.notifications.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class HomeBasketApplication : Application(), Configuration.Provider {

    @Inject lateinit var seeder: Seeder
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var reminderScheduler: ReminderScheduler

    @Inject @ApplicationScope
    lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannel()
        appScope.launch {
            seeder.seedIfEmpty()
            reminderScheduler.rescheduleAll()
        }
    }
}
