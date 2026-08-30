package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.SmcAnalyzer
import com.example.data.api.GeminiSMCGenerator
import com.example.data.api.TradingViewService
import com.example.data.local.*
import com.example.data.notification.SmcNotificationManager
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

class SmcViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SmcDatabase.getDatabase(application)
    private val savedTradeDao = db.savedTradeDao()
    private val alertDao = db.priceZoneAlertDao()
    private val riskDao = db.userRiskPreferenceDao()
    val notificationManager = SmcNotificationManager(application)

    // --- Notification Toggles & States ---
    private val _enableOrderBlockAlerts = MutableStateFlow(true)
    val enableOrderBlockAlerts: StateFlow<Boolean> = _enableOrderBlockAlerts.asStateFlow()

    private val _enableLiquidityAlerts = MutableStateFlow(true)
    val enableLiquidityAlerts: StateFlow<Boolean> = _enableLiquidityAlerts.asStateFlow()

    private val _enableBookmapAlerts = MutableStateFlow(true)
    val enableBookmapAlerts: StateFlow<Boolean> = _enableBookmapAlerts.asStateFlow()

    private val _enableBosChochAlerts = MutableStateFlow(true)
    val enableBosChochAlerts: StateFlow<Boolean> = _enableBosChochAlerts.asStateFlow()

    private val _isNotificationPermissionGranted = MutableStateFlow(notificationManager.hasNotificationPermission())
    val isNotificationPermissionGranted: StateFlow<Boolean> = _isNotificationPermissionGranted.asStateFlow()

    private val _notificationHistoryLog = MutableStateFlow<List<NotificationLogItem>>(emptyList())
    val notificationHistoryLog: StateFlow<List<NotificationLogItem>> = _notificationHistoryLog.asStateFlow()

    // --- Timeframe State ---
    val timeframes = listOf("1m", "5m", "15m", "1H", "4H", "Daily")
    private val _selectedTimeframe = MutableStateFlow("15m")
    val selectedTimeframe: StateFlow<String> = _selectedTimeframe.asStateFlow()

    // --- Candlestick states per timeframe ---
    private val timeframeCandles = mutableMapOf<String, List<XauCandle>>()

    private val _candles = MutableStateFlow<List<XauCandle>>(emptyList())
    val candles: StateFlow<List<XauCandle>> = _candles.asStateFlow()

    // --- Live Spot Gold Price & Market Ticker Stats (XAU/USD Spot) ---
    private val _currentPrice = MutableStateFlow(2514.80)
    val currentPrice: StateFlow<Double> = _currentPrice.asStateFlow()

    private val _openPrice = MutableStateFlow(2502.30)
    val openPrice: StateFlow<Double> = _openPrice.asStateFlow()

    private val _dayHigh = MutableStateFlow(2522.60)
    val dayHigh: StateFlow<Double> = _dayHigh.asStateFlow()

    private val _dayLow = MutableStateFlow(2496.10)
    val dayLow: StateFlow<Double> = _dayLow.asStateFlow()

    private val _dayChangeUsd = MutableStateFlow(12.50)
    val dayChangeUsd: StateFlow<Double> = _dayChangeUsd.asStateFlow()

    private val _dayChangePercent = MutableStateFlow(0.50)
    val dayChangePercent: StateFlow<Double> = _dayChangePercent.asStateFlow()

    private val _tickDirection = MutableStateFlow(1) // +1: Up Green, -1: Down Red, 0: Neutral
    val tickDirection: StateFlow<Int> = _tickDirection.asStateFlow()

    private val _spotSpread = MutableStateFlow(0.18) // Typical spot gold spread in USD
    val spotSpread: StateFlow<Double> = _spotSpread.asStateFlow()

    // TradingView Real-Time Bid & Ask Spot Prices
    private val _bidPrice = MutableStateFlow(2514.71)
    val bidPrice: StateFlow<Double> = _bidPrice.asStateFlow()

    private val _askPrice = MutableStateFlow(2514.89)
    val askPrice: StateFlow<Double> = _askPrice.asStateFlow()

    // TradingView Active Candle Countdown (e.g. "04:32")
    private val _candleCountdown = MutableStateFlow("04:32")
    val candleCountdown: StateFlow<String> = _candleCountdown.asStateFlow()

    // Active candle details
    val activeCandle: StateFlow<XauCandle?> = _candles.map { it.lastOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // --- SMC Analysis Result ---
    private val _analysisResult = MutableStateFlow<SmcAnalysisResult?>(null)
    val analysisResult: StateFlow<SmcAnalysisResult?> = _analysisResult.asStateFlow()

    // --- Dedicated 5M Footprint & Delta Analysis ---
    private val _footprintCandles = MutableStateFlow<List<FootprintCandle>>(emptyList())
    val footprintCandles: StateFlow<List<FootprintCandle>> = _footprintCandles.asStateFlow()

    private val _isFootprintLoading = MutableStateFlow(false)
    val isFootprintLoading: StateFlow<Boolean> = _isFootprintLoading.asStateFlow()

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

    // --- Gemini Liquidity & Supply/Demand Analysis State ---
    private val _sndResult = MutableStateFlow<String>("")
    val sndResult: StateFlow<String> = _sndResult.asStateFlow()

    private val _isSndLoading = MutableStateFlow(false)
    val isSndLoading: StateFlow<Boolean> = _isSndLoading.asStateFlow()

    // --- In-App Message Alerts ---
    private val _uiNotification = MutableSharedFlow<String>()
    val uiNotification: SharedFlow<String> = _uiNotification.asSharedFlow()

    // --- Background Coroutines for Live Price Swings ---
    private var liveUpdateJob: Job? = null

    init {
        // 1. Generate base realistic spot gold structure for all timeframes
        generateAllTimeframeHistories()

        // 2. Load default timeframe candles
        loadTimeframeCandles(_selectedTimeframe.value)

        // 3. Generate initial 5M Footprint dataset
        generateInitialFootprintData()

        // 4. Start live price simulator & auto background TradingView Spot sync
        startLiveTickingService()
        startCountdownTimerService()

        // 5. Trigger active background fetching of all timeframes from TradingView Spot
        triggerAllTimeframesRealDataFetch()

        // 6. Set first risk model defaults to DB if empty
        viewModelScope.launch(Dispatchers.IO) {
            if (riskDao.getRiskPreferenceSync() == null) {
                riskDao.saveRiskPreference(UserRiskPreference())
            }
        }
    }

    private fun generateAllTimeframeHistories() {
        val basePrices = mapOf(
            "1m" to 2514.20,
            "5m" to 2512.80,
            "15m" to 2514.50,
            "1H" to 2518.10,
            "4H" to 2505.00,
            "Daily" to 2480.00
        )

        timeframes.forEach { tf ->
            val count = 50
            val startPrice = basePrices[tf] ?: 2514.00
            val candleList = mutableListOf<XauCandle>()
            var currentClose = startPrice

            val random = Random(tf.hashCode())
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
                val change = (random.nextDouble() - 0.47) * when (tf) {
                    "1m" -> 1.5
                    "5m" -> 3.2
                    "15m" -> 6.5
                    "1H" -> 12.0
                    "4H" -> 25.0
                    else -> 45.0
                }
                val o = currentClose
                val c = currentClose + change

                val h = maxOf(o, c) + (random.nextDouble() * when (tf) { "1m" -> 0.8; "5m" -> 1.5; "15m" -> 3.0; else -> 8.0 })
                val l = minOf(o, c) - (random.nextDouble() * when (tf) { "1m" -> 0.8; "5m" -> 1.5; "15m" -> 3.0; else -> 8.0 })

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

    private fun generateInitialFootprintData() {
        val candles5m = timeframeCandles["5m"] ?: emptyList()
        if (candles5m.isNotEmpty()) {
            _footprintCandles.value = SmcAnalyzer.generateFootprintCandles(candles5m.takeLast(25))
        }
    }

    fun setTimeframe(tf: String) {
        if (_selectedTimeframe.value == tf) return
        _selectedTimeframe.value = tf
        loadTimeframeCandles(tf)
    }

    private fun loadTimeframeCandles(tf: String) {
        val currentList = timeframeCandles[tf] ?: emptyList()
        if (currentList.isNotEmpty()) {
            _candles.value = currentList
            updatePriceMetrics(currentList.last().close)
            recalculateSMC(tf, currentList)
        }

        // Fetch up-to-date real market candles from TradingView Spot asynchronously
        fetchLiveCandles(tf)
    }

    private fun fetchLiveCandles(tf: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val realCandles = TradingViewService.fetchSpotGoldCandles(tf)
                if (realCandles.isNotEmpty()) {
                    timeframeCandles[tf] = realCandles
                    if (_selectedTimeframe.value == tf) {
                        _candles.value = realCandles
                        updatePriceMetrics(realCandles.last().close)
                        recalculateSMC(tf, realCandles)
                    }
                    if (tf == "5m") {
                        _footprintCandles.value = SmcAnalyzer.generateFootprintCandles(realCandles.takeLast(25))
                    }
                }
            } catch (e: Exception) {
                Log.e("SmcViewModel", "TradingView spot fetch failed for timeframe $tf: ${e.message}")
            }
        }
    }

    private fun triggerAllTimeframesRealDataFetch() {
        viewModelScope.launch(Dispatchers.IO) {
            val activeTf = _selectedTimeframe.value
            try {
                val realCandles = TradingViewService.fetchSpotGoldCandles(activeTf)
                if (realCandles.isNotEmpty()) {
                    timeframeCandles[activeTf] = realCandles
                    _candles.value = realCandles
                    updatePriceMetrics(realCandles.last().close)
                    recalculateSMC(activeTf, realCandles)
                }
            } catch (e: Exception) {
                Log.e("SmcViewModel", "Warmup active fetch failed: ${e.message}")
            }

            // Sync 5m footprint specifically
            try {
                val real5m = TradingViewService.fetchSpotGoldCandles("5m")
                if (real5m.isNotEmpty()) {
                    timeframeCandles["5m"] = real5m
                    _footprintCandles.value = SmcAnalyzer.generateFootprintCandles(real5m.takeLast(25))
                }
            } catch (e: Exception) {
                Log.e("SmcViewModel", "5m footprint sync warmup failed")
            }

            // Sync other timeframes progressively
            timeframes.filter { it != activeTf && it != "5m" }.forEach { tf ->
                delay(1500)
                try {
                    val realCandles = TradingViewService.fetchSpotGoldCandles(tf)
                    if (realCandles.isNotEmpty()) {
                        timeframeCandles[tf] = realCandles
                    }
                } catch (e: Exception) {
                    Log.e("SmcViewModel", "Warmup background sync failed for $tf")
                }
            }
        }
    }

    private fun recalculateSMC(tf: String, list: List<XauCandle>) {
        val result = SmcAnalyzer.analyze(list, tf)
        _analysisResult.value = result
    }

    private fun updatePriceMetrics(newPrice: Double) {
        val oldPrice = _currentPrice.value
        _currentPrice.value = newPrice

        // Tick direction (+1 up, -1 down)
        _tickDirection.value = if (newPrice > oldPrice) 1 else if (newPrice < oldPrice) -1 else 0

        // Day High / Low
        if (newPrice > _dayHigh.value) _dayHigh.value = newPrice
        if (newPrice < _dayLow.value) _dayLow.value = newPrice

        // TradingView Bid & Ask Real-Time Calculation
        val spread = _spotSpread.value
        _bidPrice.value = (newPrice - (spread / 2.0)).roundToTwoDecimals()
        _askPrice.value = (newPrice + (spread / 2.0)).roundToTwoDecimals()

        // Change Calculation
        val open = _openPrice.value
        val changeUsd = newPrice - open
        val changePct = (changeUsd / open) * 100.0
        _dayChangeUsd.value = changeUsd
        _dayChangePercent.value = changePct
    }

    private fun startCountdownTimerService() {
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                _candleCountdown.value = calculateRemainingTimeframeCountdown(_selectedTimeframe.value)
                delay(1000)
            }
        }
    }

    private fun calculateRemainingTimeframeCountdown(tf: String): String {
        val now = System.currentTimeMillis()
        val intervalMs = when (tf) {
            "1m" -> 60_000L
            "5m" -> 300_000L
            "15m" -> 900_000L
            "1H" -> 3_600_000L
            "4H" -> 14_400_000L
            "Daily", "D" -> 86_400_000L
            else -> 900_000L
        }
        val remainingMs = intervalMs - (now % intervalMs)
        val remainingSec = (remainingMs / 1000L).coerceAtLeast(0)
        val hours = remainingSec / 3600
        val minutes = (remainingSec % 3600) / 60
        val seconds = remainingSec % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun startLiveTickingService() {
        liveUpdateJob?.cancel()
        liveUpdateJob = viewModelScope.launch(Dispatchers.Default) {
            var loopCounter = 0
            while (true) {
                delay(1500) // update tick every 1.5 seconds

                val tf = _selectedTimeframe.value
                val currentList = _candles.value.toMutableList()
                if (currentList.isEmpty()) continue

                val lastIdx = currentList.size - 1
                val lastCandle = currentList[lastIdx]

                // Spot Gold micro-ticks
                val tickNoise = (Random.nextDouble() - 0.49) * when (tf) {
                    "1m" -> 0.20
                    "5m" -> 0.35
                    "15m" -> 0.65
                    "1H" -> 1.5
                    "4H" -> 2.5
                    else -> 4.5
                }

                val newClose = (lastCandle.close + tickNoise).roundToTwoDecimals()
                val newHigh = maxOf(lastCandle.high, newClose)
                val newLow = minOf(lastCandle.low, newClose)

                val updatedCandle = lastCandle.copy(
                    close = newClose,
                    high = newHigh,
                    low = newLow,
                    volume = lastCandle.volume + Random.nextDouble() * 35.0
                )

                currentList[lastIdx] = updatedCandle
                _candles.value = currentList
                updatePriceMetrics(newClose)

                // Alert Engine checks
                checkTargetAlertsAndTrigger(newClose, tf)

                // Recalculate SMC
                recalculateSMC(tf, currentList)

                // Update 5m footprint live candle
                val current5m = timeframeCandles["5m"]
                if (current5m != null && current5m.isNotEmpty()) {
                    val updated5m = current5m.toMutableList()
                    val last5mIdx = updated5m.size - 1
                    val l5m = updated5m[last5mIdx]
                    updated5m[last5mIdx] = l5m.copy(
                        close = newClose,
                        high = maxOf(l5m.high, newClose),
                        low = minOf(l5m.low, newClose)
                    )
                    timeframeCandles["5m"] = updated5m
                    _footprintCandles.value = SmcAnalyzer.generateFootprintCandles(updated5m.takeLast(25))
                }

                // Sync with TradingView Spot endpoint in background every 25 seconds
                loopCounter++
                if (loopCounter >= 18) {
                    loopCounter = 0
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val realCandles = TradingViewService.fetchSpotGoldCandles(tf)
                            if (realCandles.isNotEmpty()) {
                                timeframeCandles[tf] = realCandles
                                if (_selectedTimeframe.value == tf) {
                                    val mergedList = realCandles.toMutableList()
                                    if (mergedList.isNotEmpty()) {
                                        val lastReal = mergedList.last()
                                        mergedList[mergedList.size - 1] = lastReal.copy(
                                            close = newClose,
                                            high = maxOf(lastReal.high, newHigh),
                                            low = minOf(lastReal.low, newLow)
                                        )
                                        _candles.value = mergedList
                                        recalculateSMC(tf, mergedList)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("SmcViewModel", "Auto-background TradingView sync error", e)
                        }
                    }
                }
            }
        }
    }

    fun refreshFootprintData() {
        _isFootprintLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val real5m = TradingViewService.fetchSpotGoldCandles("5m")
                if (real5m.isNotEmpty()) {
                    timeframeCandles["5m"] = real5m
                    _footprintCandles.value = SmcAnalyzer.generateFootprintCandles(real5m.takeLast(30))
                }
                _uiNotification.emit("⚡ تم تحديث بيانات الفوت برنت والدلتا لفريم الـ 5 دقائق مباشرة من TradingView!")
            } catch (e: Exception) {
                _uiNotification.emit("تنبيه: تعذر تحديث الفوت برنت، يرجى المحاولة لاحقاً")
            } finally {
                _isFootprintLoading.value = false
            }
        }
    }

    // --- Alerting & Notification Trigger Checks ---
    private suspend fun checkTargetAlertsAndTrigger(price: Double, currentTf: String) {
        val analysis = _analysisResult.value

        // 1. Institutional Order Block Hit Detection (Demand / Supply zones)
        if (_enableOrderBlockAlerts.value && analysis != null) {
            for (ob in analysis.orderBlocks) {
                // If price enters the Order Block range [bottom, top] with slight tolerance
                val inRange = price >= (ob.bottom - 0.15) && price <= (ob.top + 0.15)
                if (inRange) {
                    val notified = notificationManager.notifyOrderBlockHit(ob, price, currentTf)
                    if (notified) {
                        val isBullish = ob.type == SmcType.BULLISH
                        val zoneType = if (isBullish) NotificationZoneType.ORDER_BLOCK_DEMAND else NotificationZoneType.ORDER_BLOCK_SUPPLY
                        val title = if (isBullish) "ملامسة منطقة طلب (Demand OB)" else "ملامسة منطقة عرض (Supply OB)"
                        val desc = "الذهب دخل منطقة [${String.format("%.2f", ob.bottom)}$ - ${String.format("%.2f", ob.top)}$] عند ${String.format("%.2f", price)}$"
                        addNotificationToLog(title, desc, zoneType, price, currentTf)
                        _uiNotification.emit("🚨 ملامسة أوردر بلوك مؤسساتي: $title عند ${String.format("%.2f", price)}$!")
                    }
                }
            }
        }

        // 2. Liquidity Sweep & Liquidity Pool Incursion Detection
        if (_enableLiquidityAlerts.value && analysis != null) {
            for (sweep in analysis.liquiditySweeps) {
                if (abs(price - sweep.price) <= 0.45) {
                    val notified = notificationManager.notifyLiquiditySweep(sweep, price, currentTf)
                    if (notified) {
                        addNotificationToLog(
                            title = "سحب سيولة مؤسساتي ($currentTf)",
                            message = "${sweep.description} عند ${String.format("%.2f", sweep.price)}$",
                            zoneType = NotificationZoneType.LIQUIDITY_SWEEP,
                            price = price,
                            timeframe = currentTf
                        )
                        _uiNotification.emit("⚡ رصد سحب سيولة: ${sweep.description}")
                    }
                }
            }
        }

        // 3. Bookmap Liquidity Limit Wall Hit Detection
        if (_enableBookmapAlerts.value && analysis != null) {
            for (wall in analysis.bookmapLevels) {
                if (abs(price - wall.price) <= 0.35) {
                    val notified = notificationManager.notifyBookmapWallProximity(wall, price)
                    if (notified) {
                        val isBid = wall.type == BookmapLevelType.BID_WALL
                        val title = if (isBid) "اقتراب من جدار طلب بوكماب (${wall.lots} لوت)" else "اقتراب من جدار عرض بوكماب (${wall.lots} لوت)"
                        addNotificationToLog(
                            title = title,
                            message = "الذهب عند ${String.format("%.2f", price)}$ يقترب من جدار أوامر ${wall.lots} لوت عند ${String.format("%.2f", wall.price)}$",
                            zoneType = NotificationZoneType.BOOKMAP_WALL,
                            price = price,
                            timeframe = currentTf
                        )
                        _uiNotification.emit("🛡️ تنبيه سيولة بوكماب: $title")
                    }
                }
            }
        }

        // 4. Custom User Price Alerts & Structural Breaks
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
                notificationManager.notifyCustomAlert(alert, price)
                addNotificationToLog(
                    title = "هدف سعري مخصص",
                    message = alert.message,
                    zoneType = NotificationZoneType.CUSTOM_PRICE_ALERT,
                    price = price,
                    timeframe = alert.timeframe
                )
                _uiNotification.emit(message)
            }
        }
    }

    private fun addNotificationToLog(
        title: String,
        message: String,
        zoneType: NotificationZoneType,
        price: Double,
        timeframe: String
    ) {
        val newItem = NotificationLogItem(
            id = System.currentTimeMillis().toString() + "_" + Random.nextInt(1000),
            title = title,
            message = message,
            zoneType = zoneType,
            price = price,
            timeframe = timeframe,
            timestamp = System.currentTimeMillis()
        )
        _notificationHistoryLog.value = listOf(newItem) + _notificationHistoryLog.value.take(49)
    }

    fun toggleOrderBlockAlerts() {
        _enableOrderBlockAlerts.value = !_enableOrderBlockAlerts.value
    }

    fun toggleLiquidityAlerts() {
        _enableLiquidityAlerts.value = !_enableLiquidityAlerts.value
    }

    fun toggleBookmapAlerts() {
        _enableBookmapAlerts.value = !_enableBookmapAlerts.value
    }

    fun toggleBosChochAlerts() {
        _enableBosChochAlerts.value = !_enableBosChochAlerts.value
    }

    fun checkAndRefreshNotificationPermission() {
        _isNotificationPermissionGranted.value = notificationManager.hasNotificationPermission()
    }

    fun sendTestNotification() {
        val success = notificationManager.sendTestNotification(_currentPrice.value)
        if (success) {
            addNotificationToLog(
                title = "اختبار الإشعارات الناجح",
                message = "تم إرسال إشعار تجريبي بنجاح إلى شريط الإشعارات والصوت",
                zoneType = NotificationZoneType.ORDER_BLOCK_DEMAND,
                price = _currentPrice.value,
                timeframe = _selectedTimeframe.value
            )
            viewModelScope.launch {
                _uiNotification.emit("🔔 تم إرسال الإشعار التجريبي إلى شريط إشعارات الهاتف بنجاح!")
            }
        } else {
            viewModelScope.launch {
                _uiNotification.emit("⚠️ تعذر إرسال الإشعار. يرجى التأكد من منح صلاحيات الإشعارات للتطبيق!")
            }
        }
    }

    fun clearNotificationHistoryLog() {
        _notificationHistoryLog.value = emptyList()
        viewModelScope.launch {
            _uiNotification.emit("🗑️ تم مسح سجل إشعارات المناطق المؤسساتية.")
        }
    }

    // --- Database User Action Triggers ---
    fun saveActiveSMCRecommendation() {
        val activeSetup = _analysisResult.value?.recommendation ?: return
        viewModelScope.launch(Dispatchers.IO) {
            savedTradeDao.insertTrade(
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

    fun saveSmartConfluenceRecommendation(zone: SmartPriceZone, isBuy: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            savedTradeDao.insertTrade(
                SavedTrade(
                    type = if (isBuy) "BUY" else "SELL",
                    entryPrice = zone.idealEntry,
                    stopLoss = zone.slPrice,
                    takeProfit = zone.tp1,
                    winRate = zone.confluenceScore,
                    reasoning = "${zone.title}: ${zone.reasonAr}",
                    status = "PENDING"
                )
            )
            _uiNotification.emit("🎯 تم حفظ توصية مستوى ${if (isBuy) "الشراء" else "البيع"} الذكي في الأرشيف!")
        }
    }

    fun simulateTradeOutcome(trade: SavedTrade, forceWin: Boolean? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val outcome = forceWin ?: (Random.nextInt(100) < trade.winRate)
            val updatedStatus = if (outcome) "WON" else "LOST"
            val diff = abs(trade.entryPrice - trade.stopLoss)
            val resultPips = if (outcome) diff * 10 else -diff * 10

            savedTradeDao.updateTradeStatus(
                id = trade.id,
                status = updatedStatus,
                pips = resultPips
            )

            val stateText = if (outcome) "✅ صفقة رابحة! الأهداف تحققت" else "❌ صفقة خاسرة! ضرب وقف الخسارة"
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
        return when (type) {
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
            _uiNotification.emit("⚙️ تم تحديث إعدادات المحفظة وحساب إدارة المخاطر المتطورة!")
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

    fun generateLiquiditySndAnalysis() {
        val result = _analysisResult.value
        val price = _currentPrice.value
        val tf = _selectedTimeframe.value
        if (result == null) return

        _isSndLoading.value = true
        _sndResult.value = ""

        viewModelScope.launch {
            val trendText = when (result.currentTrend) {
                TrendStatus.BULLISH -> "صاعد مؤسساتي (شراء)"
                TrendStatus.BEARISH -> "هابط مؤسساتي (بيع)"
                else -> "عرضي حائر"
            }

            val demandObs = result.orderBlocks.filter { it.type == SmcType.BULLISH }
                .take(3)
                .joinToString("\n") { "- منطقة طلب (Demand Zone / OB) بين: ${String.format("%.2f", it.bottom)}$ و ${String.format("%.2f", it.top)}$ (مخففة: ${if (it.isMitigated) "نعم" else "لا"})" }
                .ifEmpty { "- لا توجد مناطق طلب رئيسية مكتشفة حالياً في جغرافيا الشارت الحالية." }

            val supplyObs = result.orderBlocks.filter { it.type == SmcType.BEARISH }
                .take(3)
                .joinToString("\n") { "- منطقة عرض (Supply Zone / OB) بين: ${String.format("%.2f", it.bottom)}$ و ${String.format("%.2f", it.top)}$ (مخففة: ${if (it.isMitigated) "نعم" else "لا"})" }
                .ifEmpty { "- لا توجد مناطق عرض رئيسية مكتشفة حالياً في جغرافيا الشارت الحالية." }

            val sweepsText = result.liquiditySweeps
                .take(3)
                .joinToString("\n") { "- ${it.description} عند سعر ${String.format("%.2f", it.price)}$" }
                .ifEmpty { "- لم يتم تسجيل عمليات سحب سيولة عدائية حديثة مؤخراً." }

            val response = GeminiSMCGenerator.generateSndAnalysis(
                price = price,
                timeframe = tf,
                trend = trendText,
                demandZones = demandObs,
                supplyZones = supplyObs,
                sweepsText = sweepsText
            )

            _sndResult.value = response
            _isSndLoading.value = false
        }
    }

    private fun Double.roundToTwoDecimals(): Double {
        return (this * 100.0).roundToInt() / 100.0
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
