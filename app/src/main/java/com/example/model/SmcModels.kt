package com.example.model

data class XauCandle(
    val id: Int,
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
) {
    val isBullish: Boolean get() = close >= open
}

enum class SmcType {
    BULLISH, BEARISH
}

data class FairValueGap(
    val id: String,
    val startIndex: Int,
    val endIndex: Int,
    val top: Double,
    val bottom: Double,
    val type: SmcType,
    val isMitigated: Boolean = false
)

data class OrderBlock(
    val id: String,
    val candleIndex: Int,
    val top: Double,
    val bottom: Double,
    val type: SmcType,
    val isMitigated: Boolean = false
)

data class StructureBreak(
    val id: String,
    val candleIndex: Int,
    val price: Double,
    val type: SmcType, // Bullish BOS, Bearish BOS, etc.
    val isChrCh: Boolean = false // If true, it is Change of Character (CHOCH), else Break of Structure (BOS)
)

enum class SweepType {
    BUY_STOP, SELL_STOP
}

data class LiquiditySweep(
    val id: String,
    val candleIndex: Int,
    val price: Double,
    val type: SweepType,
    val description: String
)

data class SupplyDemandZone(
    val id: String,
    val top: Double,
    val bottom: Double,
    val isDemand: Boolean, // True for demand, false for supply
    val name: String
)

enum class MarketState {
    ACCUMULATION, // تجميع
    DISTRIBUTION,  // تصريف
    SIDEWAYS      // حيرة / جانبي
}

enum class TrendStatus {
    BULLISH, // صاعد
    BEARISH, // هابط
    SIDEWAYS // جانبي
}

data class SmcAnalysisResult(
    val timeframe: String,
    val currentTrend: TrendStatus,
    val marketState: MarketState,
    val orderBlocks: List<OrderBlock>,
    val fairValueGaps: List<FairValueGap>,
    val structuralBreaks: List<StructureBreak>,
    val liquiditySweeps: List<LiquiditySweep>,
    val supplyDemandZones: List<SupplyDemandZone>,
    val recommendation: TradeRecommendation?
)

data class TradeRecommendation(
    val type: String, // "BUY" or "SELL"
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val takeProfit2: Double,
    val winRatePercent: Int,
    val score: Double,
    val reasoningAr: String,
    val reasoningEn: String
)
