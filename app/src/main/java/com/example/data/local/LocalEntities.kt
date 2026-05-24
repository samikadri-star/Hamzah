package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_trades")
data class SavedTrade(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "BUY" or "SELL"
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val winRate: Int,
    val reasoning: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "PENDING", // PENDING, WON, LOST, CLOSED
    val resultPips: Double = 0.0
)

@Entity(tableName = "price_alerts")
data class PriceZoneAlert(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val alertType: String, // "PRICE_ABOVE", "PRICE_BELOW", "LIQUIDITY_SWEEP", "BOS_CHOCH"
    val targetPrice: Double,
    val isTriggered: Boolean = false,
    val message: String,
    val timeframe: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "risk_preferences")
data class UserRiskPreference(
    @PrimaryKey val id: Int = 1,
    val accountCapital: Double = 10000.0,
    val riskPercent: Double = 1.0,
    val defaultSlPips: Int = 50
)
