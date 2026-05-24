package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedTradeDao {
    @Query("SELECT * FROM saved_trades ORDER BY timestamp DESC")
    fun getAllSavedTrades(): Flow<List<SavedTrade>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrade(trade: SavedTrade): Long

    @Update
    suspend fun updateTrade(trade: SavedTrade)

    @Query("UPDATE saved_trades SET status = :status, resultPips = :pips WHERE id = :id")
    suspend fun updateTradeStatus(id: Int, status: String, pips: Double)

    @Query("DELETE FROM saved_trades WHERE id = :id")
    suspend fun deleteTradeById(id: Int)

    @Query("DELETE FROM saved_trades")
    suspend fun clearHistory()
}

@Dao
interface PriceZoneAlertDao {
    @Query("SELECT * FROM price_alerts ORDER BY timestamp DESC")
    fun getAllAlerts(): Flow<List<PriceZoneAlert>>

    @Query("SELECT * FROM price_alerts WHERE isTriggered = 0")
    suspend fun getPendingAlerts(): List<PriceZoneAlert>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: PriceZoneAlert)

    @Query("UPDATE price_alerts SET isTriggered = 1 WHERE id = :id")
    suspend fun triggerAlert(id: Int)

    @Query("DELETE FROM price_alerts WHERE id = :id")
    suspend fun deleteAlertById(id: Int)

    @Query("DELETE FROM price_alerts")
    suspend fun clearAllAlerts()
}

@Dao
interface UserRiskPreferenceDao {
    @Query("SELECT * FROM risk_preferences WHERE id = 1 LIMIT 1")
    fun getRiskPreference(): Flow<UserRiskPreference?>

    @Query("SELECT * FROM risk_preferences WHERE id = 1 LIMIT 1")
    suspend fun getRiskPreferenceSync(): UserRiskPreference?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRiskPreference(pref: UserRiskPreference)
}
