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

// --- Footprint & Delta Cluster Models (5M Timeframe) ---

data class FootprintLevel(
    val price: Double,
    val bidVolume: Int,
    val askVolume: Int,
    val isPoc: Boolean = false,
    val isBuyImbalance: Boolean = false,
    val isSellImbalance: Boolean = false
) {
    val totalVolume: Int get() = bidVolume + askVolume
    val delta: Int get() = askVolume - bidVolume
}

data class FootprintCandle(
    val id: Int,
    val candle: XauCandle,
    val levels: List<FootprintLevel>,
    val pocPrice: Double,
    val totalDelta: Int,
    val minDelta: Int,
    val maxDelta: Int,
    val totalVolume: Int,
    val cumulativeDelta: Int,
    val valueAreaHigh: Double,
    val valueAreaLow: Double
)

// --- Bookmap Liquidity Heatmap Models ---

enum class BookmapLevelType {
    BID_WALL,    // جدار شراء مؤسساتي (حيتان)
    ASK_WALL,    // جدار بيع ومقاومة سيولة (صناديق)
    VOID_ZONE    // فراغ سيولة (تسارع محتمل)
}

data class BookmapLiquidityLevel(
    val id: String,
    val price: Double,
    val lots: Int,
    val type: BookmapLevelType,
    val distancePoints: Double,
    val strengthPercent: Int,
    val isAbsorbing: Boolean = false
)

// --- Option Flow & Gamma Exposure Models ---

enum class GammaRegime {
    POSITIVE_GAMMA, // تثبيت أسعار وهدوء
    NEGATIVE_GAMMA  // تسارع وانفجار سعري
}

data class OptionFlowAnalysis(
    val callVolume: Long,
    val putVolume: Long,
    val putCallRatio: Double,
    val maxPainStrike: Double,
    val majorCallWall: Double,
    val majorPutWall: Double,
    val gammaRegime: GammaRegime,
    val institutionalSentiment: String, // "ثوراني / صعودي قوي", "دببي / هبوطي", etc.
    val unusualOptionActivities: List<String>
)

// --- Future Flow & Order Flow Delta Models ---

data class FutureFlowAnalysis(
    val aggressiveBuyContracts: Long,
    val aggressiveSellContracts: Long,
    val netDeltaContracts: Long,
    val cumulativeDeltaTrend: String, // "صاعد بقوة", "هابط تصريفي"
    val openInterestChange: Long,
    val absorptionDetected: Boolean,
    val institutionalDominance: String // "المشترون الحيتان 73%", "البائعون 68%"
)

// --- Smart Money Buy / Sell Levels & Confluence Recommendation ---

data class SmartPriceZone(
    val title: String,
    val priceTop: Double,
    val priceBottom: Double,
    val idealEntry: Double,
    val slPrice: Double,
    val tp1: Double,
    val tp2: Double,
    val tp3: Double,
    val confluenceScore: Int, // 1-100%
    val reasonAr: String
)

data class SmartConfluenceRecommendation(
    val primaryDirection: String, // "شراء ذكي (Smart Buy)" or "بيع ذكي (Smart Sell)" or "مراقبة سيولة (Wait)"
    val spotPrice: Double,
    val smartBuyZone: SmartPriceZone,
    val smartSellZone: SmartPriceZone,
    val overallConfluencePercent: Int,
    val bookmapSummary: String,
    val optionFlowSummary: String,
    val futureFlowSummary: String,
    val executionAdviceAr: String
)

data class SmcAnalysisResult(
    val timeframe: String,
    val currentTrend: TrendStatus,
    val marketState: MarketState,
    val orderBlocks: List<OrderBlock>,
    val fairValueGaps: List<FairValueGap>,
    val structuralBreaks: List<StructureBreak>,
    val liquiditySweeps: List<LiquiditySweep>,
    val supplyDemandZones: List<SupplyDemandZone>,
    val recommendation: TradeRecommendation?,
    val bookmapLevels: List<BookmapLiquidityLevel> = emptyList(),
    val optionFlow: OptionFlowAnalysis? = null,
    val futureFlow: FutureFlowAnalysis? = null,
    val smartRecommendation: SmartConfluenceRecommendation? = null
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

// --- Local Institutional Notification Models ---

enum class NotificationZoneType {
    ORDER_BLOCK_DEMAND,
    ORDER_BLOCK_SUPPLY,
    LIQUIDITY_SWEEP,
    BOOKMAP_WALL,
    BOS_CHOCH_BREAK,
    CUSTOM_PRICE_ALERT
}

data class NotificationLogItem(
    val id: String,
    val title: String,
    val message: String,
    val zoneType: NotificationZoneType,
    val price: Double,
    val timeframe: String,
    val timestamp: Long = System.currentTimeMillis()
)

