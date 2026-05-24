package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SavedTrade::class, PriceZoneAlert::class, UserRiskPreference::class],
    version = 1,
    exportSchema = false
)
abstract class SmcDatabase : RoomDatabase() {
    abstract fun savedTradeDao(): SavedTradeDao
    abstract fun priceZoneAlertDao(): PriceZoneAlertDao
    abstract fun userRiskPreferenceDao(): UserRiskPreferenceDao

    companion object {
        @Volatile
        private var INSTANCE: SmcDatabase? = null

        fun getDatabase(context: Context): SmcDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmcDatabase::class.java,
                    "smc_gold_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
