package com.homebasket.core.data

import androidx.room.withTransaction
import com.homebasket.core.database.HomeBasketDatabase
import com.homebasket.core.database.entity.HouseholdEntity
import com.homebasket.core.database.entity.HouseholdSettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HouseholdRepository @Inject constructor(private val db: HomeBasketDatabase) {
    private val dao = db.householdDao()

    val household: Flow<HouseholdEntity> = dao.observeHousehold().filterNotNull()
    val settings: Flow<HouseholdSettingsEntity> = dao.observeSettings().filterNotNull()

    suspend fun getHousehold(): HouseholdEntity? = dao.getHousehold()
    suspend fun getSettings(): HouseholdSettingsEntity? = dao.getSettings()

    suspend fun save(household: HouseholdEntity, settings: HouseholdSettingsEntity) = db.withTransaction {
        dao.upsertHousehold(household)
        dao.upsertSettings(settings)
    }

    fun storesOf(h: HouseholdEntity): List<String> = h.preferredStores.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
