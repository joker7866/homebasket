package com.homebasket.core.data

import android.content.Context
import androidx.room.Room
import com.homebasket.core.database.HomeBasketDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.LocalDate
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** Single source of "today" so date-dependent logic is consistent and testable. */
interface Clock {
    fun today(): LocalDate
    fun nowMillis(): Long
}

object SystemClock : Clock {
    override fun today(): LocalDate = LocalDate.now()
    override fun nowMillis(): Long = System.currentTimeMillis()
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): HomeBasketDatabase =
        Room.databaseBuilder(context, HomeBasketDatabase::class.java, HomeBasketDatabase.NAME).build()

    @Provides
    @Singleton
    @ApplicationScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    fun clock(): Clock = SystemClock
}
