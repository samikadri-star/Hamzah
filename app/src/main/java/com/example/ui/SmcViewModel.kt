package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.SmcAnalyzer
import com.example.data.api.GeminiSMCGenerator
import com.example.data.local.*
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random
import kotlin.math.abs

class SmcViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SmcDatabase.getDatabase(application)
    private val savedTradeDao = db.savedTradeDao()
    private val alertDao = db.priceZoneAlertDao()
    private val riskDao = db.userRiskPreferenceDao()

    // --- Timeframe State ---
    val timeframes = listOf("1m", "5m", "15m", "1H", "4H", "Daily")
    private val _selectedTimeframe = MutableStateFlow("15m")
    val selectedTimeframe: StateFlow<String> = _selectedTimeframe.asStateFlow()

    // --- Candlestick states per timeframe ---
    private val timeframeCandles = mutableMapOf<String, List<XauCandle>>()

    private val _candles = MutableStateFlow<List<XauCandle>>(emptyList())
    val candles: StateFlow<List<XauCandle>> = _candles.asStateFlow()

    private val _currentPrice = MutableStateFlow(2342.50)
    val currentPrice: StateFlow<Double> = _currentPrice.asStateFlow()

    // --- SMC Analysis Result ---
    private val _analysisResult = MutableStateFlow<SmcAnalysisResult?>(null)
    val analysisResult: StateFlow<SmcAnalysisResult?> = _analysisResult.asStateFlow()

    // --- Persisted Database Data StateFlows ---
    val savedTrades: StateFlow<List<SavedTrade>> = savedTradeDao.getAllSavedTrades()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeAlerts: StateFlow<List<PriceZoneAlert>> = alertDao.getAllAlerts()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val riskPreference: StateFlow<UserRiskPreference> = riskDao.getRiskPreference()
        .filterNotNull()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserRiskPreference())

    // --- AI Sentiment State ---
    private val _aiResult = MutableStateFlow<String>("")
    val aiResult: StateFlow<String> = _aiResult.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    // --- In-App Message Alerts ---
    private val _uiNotification = MutableSharedFlow<String>()
    val uiNotification: SharedFlow<String> = _uiNotification.asSharedFlow()

    // --- Background Coroutines for Live Price Swings ---
    private var liveUpdateJob: Job? = null

    init {
        // 1. Generate core historic candle databases for all timeframes
        generateAllTimeframeHistories()
        
        // 2. Load default timeframe candles
        loadTimeframeCandles(_selectedTimeframe.value)

        // 3. Start live price simulator
        startLiveTickingService()

        // 4. Set first risk model defaults to DB if empty
        viewModelScope.launch(Dispatchers.IO) {
            if (riskDao.getRiskPreferenceSync() == null) {
                riskDao.saveRiskPreference(UserRiskPreference())
            }
        }
    }

    private fun generateAllTimeframeHistories() {
        val basePrices = mapOf(
            "1m" to 2340.50,
            "5m" to 2338.00,
            "15m" to 2342.20,
            "1H" to 2345.10,
            "4H" to 2330.00,
            "Daily" to 2310.00
        )
        
        timeframes.forEach { tf ->
            val count = 50
            val startPrice = basePrices[tf] ?: 2340.00
            val candleList = mutableListOf<XauCandle>()
            var currentClose = startPrice
            
            val random = Random(tf.hashCode()) // Consistent base data per TF
            val timeOffset = when (tf) {
                "1m" -> 60000L
                "5m" -> 300000L
                "15m" -> 900000L
                "1H" -> 3600000L
                "4H" -> 14400000L
                else -> 86400000L
            }
            
            val baseTime = System.currentTimeMillis() - (count * timeOffset)

            for (i in 0 until count) {
                val change = (random.nextDouble() - 0.48) * when(tf) {
                    "1m" -> 1.5
                    "5m" -> 3.2
                    "15m" -> 6.5
                    "1H" -> 12.0
                    "4H" -> 25.0
                    else -> 45.0
                }
                val o = currentClose
                val c = currentClose + change
                
                // Keep gold price realistic
                val h = maxOf(o, c) + (random.nextDouble() * when(tf) { "1m" -> 0.8; "5m" -> 1.5; "15m" -> 3.0; else -> 8.0 })
                val l = minOf(o, c) - (random.nextDouble() * when(tf) { "1m" -> 0.8; "5m" -> 1.5; "15m" -> 3.0; else -> 8.0 })
                
                candleList.add(
                    XauCandle(
                        id = i,
                        timestamp = baseTime + (i * timeOffset),
                        open = o,
                        high = h,
                        low = l,
                        close = c,
                        volume = random.nextDouble() * 2000.0 + 500.0
                    )
                )
                currentClose = c
            }
            timeframeCandles[tf] = candleList
        }
    }

    fun setTimeframe(tf: String) {
        if (_selectedTimeframe.value == tf) return
        _selectedTimeframe.value = tf
        loadTimeframeCandles(tf)
    }

    private fun loadTimeframeCandles(tf: String) {
        val currentList = timeframeCandles[tf] ?: return
        _candles.value = currentList
        _currentPrice.value = currentList.last().close
        recalculateSMC(tf, currentList)
    }

    private fun recalculateSMC(tf: String, list: List<XauCandle>) {
        val result = SmcAnalyzer.analyze(list, tf)
        _analysisResult.value = result
    }

    private fun startLiveTickingService() {
        liveUpdateJob?.cancel()
        liveUpdateJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(1500) // update tick every 1.5 seconds

                val tf = _selectedTimeframe.value
                val currentList = _candles.value.toMutableList()
                if (currentList.isEmpty()) continue

                val lastIdx = currentList.size - 1
                val lastCandle = currentList[lastIdx]

                // Live price fluctuation
                val tickNoise = (Random.nextDouble() - 0.5) * when (tf) {
                    "1m" -> 0.15
                    "5m" -> 0.35
                    "15m" -> 0.65
                    "1H" -> 1.5
                    "4H" -> 2.5
                    else -> 4.5
                }
                
                val newClose = lastCandle.close + tickNoise
                val newHigh = maxOf(lastCandle.high, newClose)
                val newLow = minOf(lastCandle.low, newClose)

                val updatedCandle = lastCandle.copy(
                    close = newClose,
                    high = newHigh,
                    low = newLow,
                    volume = lastCandle.volume + Random.nextDouble() * 50.0
                )

                currentList[lastIdx] = updatedCandle
                _candles.value = currentList
                _currentPrice.value = newClose

                // Immediate Alert Monitoring Engine checks
                checkTargetAlertsAndTrigger(newClose, tf)

                // Run SMC analysis and state updates in light background thread
                recalculateSMC(tf, currentList)
            }
        }
    }

    // --- Alerting Trigger Checks ---
    private suspend fun checkTargetAlertsAndTrigger(price: Double, currentTf: String) {
        val pendingAlerts = alertDao.getPendingAlerts()
        for (alert in pendingAlerts) {
            var triggerNow = false
            var message = ""

            when (alert.alertType) {
                "PRICE_ABOVE" -> {
                    if (price >= alert.targetPrice) {
                        triggerNow = true
                        message = "🚨 الذهب تجاوز السعر المستهدف ${alert.targetPrice}$ ! (فرصة واعدة)"
                    }
                }
                "PRICE_BELOW" -> {
                    if (price <= alert.targetPrice) {
                        triggerNow = true
                        message = "🚨 الذهب انخفض أسفل السعر المستهدف ${alert.targetPrice}$ ! (منطقة دخول)"
                    }
                }
                "LIQUIDITY_SWEEP" -> {
                    // Trigger randomly or based on swing sweeps context
                    val lastSweep = _analysisResult.value?.liquiditySweeps?.lastOrNull()
                    if (lastSweep != null && System.currentTimeMillis() - alert.timestamp < 30000) {
                        triggerNow = true
                        message = "⚠️ تنبيه مؤسساتي: ${lastSweep.description} على فريم $currentTf !"
                    }
                }
                "BOS_CHOCH" -> {
                    val lastBreak = _analysisResult.value?.structuralBreaks?.lastOrNull()
                    if (lastBreak != null && System.currentTimeMillis() - alert.timestamp < 30000) {
                        triggerNow = true
                        val label = if (lastBreak.isChrCh) "CHOCH (انعكاس)" else "BOS (اختراق)"
                        message = "📊 كسر هيكل سوق ذكي: تم تحديد $label مالي عند المستوى ${lastBreak.price}$ !"
                    }
                }
            }

            if (triggerNow) {
                alertDao.triggerAlert(alert.id)
                _uiNotification.emit(message)
            }
        }
    }

    // --- Database User Action Triggers ---
    fun saveActiveSMCRecommendation() {
        val activeSetup = _analysisResult.value?.recommendation ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val count = savedTradeDao.insertTrade(
                SavedTrade(
                    type = activeSetup.type,
                    entryPrice = activeSetup.entryPrice,
                    stopLoss = activeSetup.stopLoss,
                    takeProfit = activeSetup.takeProfit,
                    winRate = activeSetup.winRatePercent,
                    reasoning = activeSetup.reasoningAr,
                    status = "PENDING"
                )
            )
            _uiNotification.emit("📊 تم حفظ الصفقة بنجاح في سجل الأرشفة لمتابعة الأداء!")
        }
    }

    fun simulateTradeOutcome(trade: SavedTrade, forceWin: Boolean? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val outcome = forceWin ?: (Random.nextInt(100) < trade.winRate)
            val updatedStatus = if (outcome) "WON" else "LOST"
            val diff = abs(trade.entryPrice - trade.stopLoss)
            val resultPips = if (outcome) diff * 10 else -diff * 10 // conversion factor
            
            savedTradeDao.updateTradeStatus(
                id = trade.id,
                status = updatedStatus,
                pips = resultPips
            )
            
            val stateText = if (outcome) "✅ صفققة رابحة! الأهداف تحققت" else "❌ صفقة خاسرة! ضرب وقف الخسارة"
            _uiNotification.emit("تحديث الأرشيف: $stateText (${String.format("%.1f", resultPips)} نقطة)")
        }
    }

    fun deleteSavedTrade(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            savedTradeDao.deleteTradeById(id)
        }
    }

    fun clearSavedHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            savedTradeDao.clearHistory()
        }
    }

    fun addNewCustomAlert(type: String, price: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            alertDao.insertAlert(
                PriceZoneAlert(
                    alertType = type,
                    targetPrice = price,
                    message = tfAndSymbol(type, price),
                    timeframe = _selectedTimeframe.value
                )
            )
            val messageText = when (type) {
                "PRICE_ABOVE" -> "فوق السعر $price$"
                "PRICE_BELOW" -> "تحت السعر $price$"
                "LIQUIDITY_SWEEP" -> "حدوث سحب سيولة"
                else -> "اختراق أو كسر هيكلي (BOS/CHOCH)"
            }
            _uiNotification.emit("🔔 تم إعداد التنبيه الذكي بنجاح: عندما يكون النطاق $messageText")
        }
    }

    private fun tfAndSymbol(type: String, price: Double): String {
        return when(type) {
            "PRICE_ABOVE" -> "تنبيه تجاوز السعر لـ $price"
            "PRICE_BELOW" -> "تنبيه انخفاض السعر لـ $price"
            "LIQUIDITY_SWEEP" -> "تنبيه سحب سيولة مؤسساتي"
            else -> "تنبيه كسر هيكلية السوق (BOS/CHOCH)"
        }
    }

    fun deleteAlert(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            alertDao.deleteAlertById(id)
        }
    }

    fun clearAlertsLog() {
        viewModelScope.launch(Dispatchers.IO) {
            alertDao.clearAllAlerts()
        }
    }

    fun saveRiskConfig(capital: Double, riskPct: Double, stopLossPips: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            riskDao.saveRiskPreference(
                UserRiskPreference(
                    accountCapital = capital,
                    riskPercent = riskPct,
                    defaultSlPips = stopLossPips
                )
            )
            _uiNotification.emit("⚙️ تم تحديث إعدادات المحفظة وحسابة إدارة المخاطر المتطورة!")
        }
    }

    // --- Gemini AI Analysis Core Hook ---
    fun generateAiAnalysis() {
        val result = _analysisResult.value
        val price = _currentPrice.value
        val tf = _selectedTimeframe.value
        if (result == null) return

        _isAiLoading.value = true
        _aiResult.value = ""

        viewModelScope.launch {
            val trendText = when (result.currentTrend) {
                TrendStatus.BULLISH -> "صاعد مؤسساتي (شراء)"
                TrendStatus.BEARISH -> "هابط مؤسساتي (بيع)"
                else -> "عرضي / حائر"
            }

            val stateText = when (result.marketState) {
                MarketState.ACCUMULATION -> "تجميع حيتان (Accumulation)"
                MarketState.DISTRIBUTION -> "تصريف مؤسساتي (Distribution)"
                else -> "توزيع سيولة طبيعي"
            }

            val summaryCandles = "متوسط التداول ${_candles.value.takeLast(5).map { String.format("%.1f", it.close) }.joinToString(" -> ")}"
            val lastBreak = result.structuralBreaks.lastOrNull()
            val breakText = if (lastBreak != null) {
                "${if (lastBreak.isChrCh) "CHOCH" else "BOS"} عند سعر ${lastBreak.price}"
            } else "لا توجد كسور في النطاق القريب"

            val response = GeminiSMCGenerator.generateGoldAnalysis(
                price = price,
                timeframe = tf,
                trend = trendText,
                state = stateText,
                candlesSummary = summaryCandles,
                recentBOS = breakText
            )

            _aiResult.value = response
            _isAiLoading.value = false
        }
    }
}

class SmcViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SmcViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SmcViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
