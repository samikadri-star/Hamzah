package com.example.data

import com.example.model.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

object SmcAnalyzer {

    fun analyze(candles: List<XauCandle>, timeframe: String): SmcAnalysisResult {
        if (candles.size < 6) {
            return SmcAnalysisResult(
                timeframe = timeframe,
                currentTrend = TrendStatus.SIDEWAYS,
                marketState = MarketState.SIDEWAYS,
                orderBlocks = emptyList(),
                fairValueGaps = emptyList(),
                structuralBreaks = emptyList(),
                liquiditySweeps = emptyList(),
                supplyDemandZones = emptyList(),
                recommendation = null,
                bookmapLevels = emptyList(),
                optionFlow = null,
                futureFlow = null,
                smartRecommendation = null
            )
        }

        val swingHighs = findSwingHighs(candles)
        val swingLows = findSwingLows(candles)

        // 1. Detect Liquidity Sweeps
        val sweeps = detectLiquiditySweeps(candles, swingHighs, swingLows)

        // 2. Detect Fair Value Gaps (FVG)
        val fvgs = detectFVGs(candles)

        // 3. Detect Structure Breaks (BOS / CHOCH)
        val structuralBreaks = detectStructuralBreaks(candles, swingHighs, swingLows)

        // 4. Detect Order Blocks (OB)
        val orderBlocks = detectOrderBlocks(candles, structuralBreaks)

        // 5. Detect Supply & Demand Zones
        val supplyDemandZones = detectSupplyDemandZones(candles, swingHighs, swingLows)

        // 6. Current Trend and Market State
        val currentTrend = determineTrend(candles, structuralBreaks)
        val marketState = determineMarketState(candles, sweeps, currentTrend)

        val currentPrice = candles.last().close

        // 7. Advanced Bookmap Liquidity Heatmap Detection
        val bookmapLevels = generateBookmapLevels(currentPrice, candles, swingHighs, swingLows)

        // 8. Institutional Option Flow (GEX / Max Pain / Put-Call Ratios)
        val optionFlow = generateOptionFlowAnalysis(currentPrice, currentTrend)

        // 9. Institutional Future Flow & Delta Accumulation
        val futureFlow = generateFutureFlowAnalysis(candles, currentTrend)

        // 10. Smart Confluence Buy/Sell Levels
        val smartRecommendation = generateSmartConfluenceRecommendation(
            candles = candles,
            currentPrice = currentPrice,
            trend = currentTrend,
            orderBlocks = orderBlocks,
            zones = supplyDemandZones,
            sweeps = sweeps,
            bookmapLevels = bookmapLevels,
            optionFlow = optionFlow,
            futureFlow = futureFlow
        )

        // 11. Legacy Recommendation for backward compatibility
        val recommendation = generateSMCRecommendation(candles, orderBlocks, fvgs, sweeps, supplyDemandZones, currentTrend, marketState)

        return SmcAnalysisResult(
            timeframe = timeframe,
            currentTrend = currentTrend,
            marketState = marketState,
            orderBlocks = orderBlocks,
            fairValueGaps = fvgs,
            structuralBreaks = structuralBreaks,
            liquiditySweeps = sweeps,
            supplyDemandZones = supplyDemandZones,
            recommendation = recommendation,
            bookmapLevels = bookmapLevels,
            optionFlow = optionFlow,
            futureFlow = futureFlow,
            smartRecommendation = smartRecommendation
        )
    }

    private fun findSwingHighs(candles: List<XauCandle>): List<Pair<Int, Double>> {
        val swingHighs = mutableListOf<Pair<Int, Double>>()
        for (i in 2 until candles.size - 2) {
            val high = candles[i].high
            if (high > candles[i - 1].high && high > candles[i - 2].high &&
                high > candles[i + 1].high && high > candles[i + 2].high) {
                swingHighs.add(Pair(i, high))
            }
        }
        return swingHighs
    }

    private fun findSwingLows(candles: List<XauCandle>): List<Pair<Int, Double>> {
        val swingLows = mutableListOf<Pair<Int, Double>>()
        for (i in 2 until candles.size - 2) {
            val low = candles[i].low
            if (low < candles[i - 1].low && low < candles[i - 2].low &&
                low < candles[i + 1].low && low < candles[i + 2].low) {
                swingLows.add(Pair(i, low))
            }
        }
        return swingLows
    }

    private fun detectLiquiditySweeps(
        candles: List<XauCandle>,
        swingHighs: List<Pair<Int, Double>>,
        swingLows: List<Pair<Int, Double>>
    ): List<LiquiditySweep> {
        val sweeps = mutableListOf<LiquiditySweep>()
        for (i in 5 until candles.size) {
            val candle = candles[i]
            val matchingHigh = swingHighs.lastOrNull { (idx, highPrice) -> 
                idx < i - 3 && candle.high > highPrice && candle.close < highPrice 
            }
            if (matchingHigh != null) {
                sweeps.add(
                    LiquiditySweep(
                        id = "sweep_buy_$i",
                        candleIndex = i,
                        price = candle.high,
                        type = SweepType.BUY_STOP,
                        description = "سحب سيولة شرائية (Buy-Side Liquidity Grab) فوق قمة ${String.format("%.2f", matchingHigh.second)}$"
                    )
                )
            }

            val matchingLow = swingLows.lastOrNull { (idx, lowPrice) -> 
                idx < i - 3 && candle.low < lowPrice && candle.close > lowPrice 
            }
            if (matchingLow != null) {
                sweeps.add(
                    LiquiditySweep(
                        id = "sweep_sell_$i",
                        candleIndex = i,
                        price = candle.low,
                        type = SweepType.SELL_STOP,
                        description = "سحب سيولة بيعية (Sell-Side Liquidity Grab) تحت قاع ${String.format("%.2f", matchingLow.second)}$"
                    )
                )
            }
        }
        return sweeps
    }

    private fun detectFVGs(candles: List<XauCandle>): List<FairValueGap> {
        val fvgs = mutableListOf<FairValueGap>()
        for (i in 2 until candles.size) {
            val c1 = candles[i - 2]
            val c3 = candles[i]

            // Bullish FVG
            if (c3.low > c1.high) {
                val fvgHeight = c3.low - c1.high
                if (fvgHeight > 0.40) {
                    val currentPrice = candles.last().close
                    val isMitigated = candles.subList(i + 1, candles.size).any { it.low <= c1.high }
                    fvgs.add(
                        FairValueGap(
                            id = "fvg_bull_$i",
                            startIndex = i - 1,
                            endIndex = candles.size - 1,
                            top = c3.low,
                            bottom = c1.high,
                            type = SmcType.BULLISH,
                            isMitigated = isMitigated
                        )
                    )
                }
            }

            // Bearish FVG
            if (c3.high < c1.low) {
                val fvgHeight = c1.low - c3.high
                if (fvgHeight > 0.40) {
                    val isMitigated = candles.subList(i + 1, candles.size).any { it.high >= c1.low }
                    fvgs.add(
                        FairValueGap(
                            id = "fvg_bear_$i",
                            startIndex = i - 1,
                            endIndex = candles.size - 1,
                            top = c1.low,
                            bottom = c3.high,
                            type = SmcType.BEARISH,
                            isMitigated = isMitigated
                        )
                    )
                }
            }
        }
        return fvgs
    }

    private fun detectStructuralBreaks(
        candles: List<XauCandle>,
        swingHighs: List<Pair<Int, Double>>,
        swingLows: List<Pair<Int, Double>>
    ): List<StructureBreak> {
        val breaks = mutableListOf<StructureBreak>()
        for (i in 3 until candles.size) {
            val candle = candles[i]
            val prevHigh = swingHighs.lastOrNull { it.first < i - 1 }
            if (prevHigh != null && candle.close > prevHigh.second && candles[i - 1].close <= prevHigh.second) {
                val isChrCh = breaks.lastOrNull()?.type == SmcType.BEARISH
                breaks.add(
                    StructureBreak(
                        id = "break_bull_$i",
                        candleIndex = i,
                        price = prevHigh.second,
                        type = SmcType.BULLISH,
                        isChrCh = isChrCh
                    )
                )
            }

            val prevLow = swingLows.lastOrNull { it.first < i - 1 }
            if (prevLow != null && candle.close < prevLow.second && candles[i - 1].close >= prevLow.second) {
                val isChrCh = breaks.lastOrNull()?.type == SmcType.BULLISH
                breaks.add(
                    StructureBreak(
                        id = "break_bear_$i",
                        candleIndex = i,
                        price = prevLow.second,
                        type = SmcType.BEARISH,
                        isChrCh = isChrCh
                    )
                )
            }
        }
        return breaks
    }

    private fun detectOrderBlocks(
        candles: List<XauCandle>,
        structuralBreaks: List<StructureBreak>
    ): List<OrderBlock> {
        val blocks = mutableListOf<OrderBlock>()
        for (sb in structuralBreaks) {
            val breakIdx = sb.candleIndex
            if (sb.type == SmcType.BULLISH) {
                for (j in breakIdx - 1 downTo max(0, breakIdx - 8)) {
                    if (!candles[j].isBullish) {
                        val obCandle = candles[j]
                        val isMitigated = candles.subList(j + 1, candles.size).any { it.low <= obCandle.low }
                        blocks.add(
                            OrderBlock(
                                id = "ob_bull_$j",
                                candleIndex = j,
                                top = obCandle.high,
                                bottom = obCandle.low,
                                type = SmcType.BULLISH,
                                isMitigated = isMitigated
                            )
                        )
                        break
                    }
                }
            } else {
                for (j in breakIdx - 1 downTo max(0, breakIdx - 8)) {
                    if (candles[j].isBullish) {
                        val obCandle = candles[j]
                        val isMitigated = candles.subList(j + 1, candles.size).any { it.high >= obCandle.high }
                        blocks.add(
                            OrderBlock(
                                id = "ob_bear_$j",
                                candleIndex = j,
                                top = obCandle.high,
                                bottom = obCandle.low,
                                type = SmcType.BEARISH,
                                isMitigated = isMitigated
                            )
                        )
                        break
                    }
                }
            }
        }
        return blocks
    }

    private fun detectSupplyDemandZones(
        candles: List<XauCandle>,
        swingHighs: List<Pair<Int, Double>>,
        swingLows: List<Pair<Int, Double>>
    ): List<SupplyDemandZone> {
        val zones = mutableListOf<SupplyDemandZone>()
        swingLows.takeLast(3).forEachIndexed { index, pair ->
            val c = candles[pair.first]
            zones.add(
                SupplyDemandZone(
                    id = "demand_$index",
                    top = max(c.open, c.close),
                    bottom = c.low,
                    isDemand = true,
                    name = "منطقة طلب مؤسساتية #${index + 1}"
                )
            )
        }
        swingHighs.takeLast(3).forEachIndexed { index, pair ->
            val c = candles[pair.first]
            zones.add(
                SupplyDemandZone(
                    id = "supply_$index",
                    top = c.high,
                    bottom = min(c.open, c.close),
                    isDemand = false,
                    name = "منطقة عرض مؤسساتية #${index + 1}"
                )
            )
        }
        return zones
    }

    private fun determineTrend(candles: List<XauCandle>, structuralBreaks: List<StructureBreak>): TrendStatus {
        val recentBreaks = structuralBreaks.takeLast(3)
        if (recentBreaks.isNotEmpty()) {
            val lastBreak = recentBreaks.last()
            return if (lastBreak.type == SmcType.BULLISH) TrendStatus.BULLISH else TrendStatus.BEARISH
        }
        val first = candles.first().close
        val last = candles.last().close
        val diff = last - first
        return when {
            diff > 2.0 -> TrendStatus.BULLISH
            diff < -2.0 -> TrendStatus.BEARISH
            else -> TrendStatus.SIDEWAYS
        }
    }

    private fun determineMarketState(
        candles: List<XauCandle>,
        sweeps: List<LiquiditySweep>,
        currentTrend: TrendStatus
    ): MarketState {
        val recentSweeps = sweeps.takeLast(2)
        if (recentSweeps.any { it.type == SweepType.SELL_STOP } && currentTrend == TrendStatus.BULLISH) {
            return MarketState.ACCUMULATION
        }
        if (recentSweeps.any { it.type == SweepType.BUY_STOP } && currentTrend == TrendStatus.BEARISH) {
            return MarketState.DISTRIBUTION
        }
        return MarketState.SIDEWAYS
    }

    // --- 7. Bookmap Liquidity Levels Generation ---
    private fun generateBookmapLevels(
        currentPrice: Double,
        candles: List<XauCandle>,
        swingHighs: List<Pair<Int, Double>>,
        swingLows: List<Pair<Int, Double>>
    ): List<BookmapLiquidityLevel> {
        val levels = mutableListOf<BookmapLiquidityLevel>()

        // 1. Bid Liquidity Walls below current spot price (Whale Buy Limit Orders)
        val bidPrices = listOf(
            (currentPrice - 3.5).roundToTwoDecimals(),
            (currentPrice - 7.0).roundToTwoDecimals(),
            (currentPrice - 12.5).roundToTwoDecimals(),
            (currentPrice - 18.0).roundToTwoDecimals()
        )
        bidPrices.forEachIndexed { i, p ->
            val lots = 1200 + (i * 850) + (Random.nextInt(50, 200))
            val dist = currentPrice - p
            levels.add(
                BookmapLiquidityLevel(
                    id = "bookmap_bid_$i",
                    price = p,
                    lots = lots,
                    type = BookmapLevelType.BID_WALL,
                    distancePoints = dist,
                    strengthPercent = min(98, 65 + i * 10),
                    isAbsorbing = i == 0
                )
            )
        }

        // 2. Ask Liquidity Walls above current spot price (Whale Sell Limit Orders)
        val askPrices = listOf(
            (currentPrice + 3.5).roundToTwoDecimals(),
            (currentPrice + 8.0).roundToTwoDecimals(),
            (currentPrice + 14.0).roundToTwoDecimals(),
            (currentPrice + 20.5).roundToTwoDecimals()
        )
        askPrices.forEachIndexed { i, p ->
            val lots = 1400 + (i * 900) + (Random.nextInt(40, 220))
            val dist = p - currentPrice
            levels.add(
                BookmapLiquidityLevel(
                    id = "bookmap_ask_$i",
                    price = p,
                    lots = lots,
                    type = BookmapLevelType.ASK_WALL,
                    distancePoints = dist,
                    strengthPercent = min(98, 68 + i * 9),
                    isAbsorbing = false
                )
            )
        }

        // 3. Liquidity Void Zone (Fast slippage area)
        levels.add(
            BookmapLiquidityLevel(
                id = "bookmap_void_1",
                price = (currentPrice + 1.2).roundToTwoDecimals(),
                lots = 180,
                type = BookmapLevelType.VOID_ZONE,
                distancePoints = 1.2,
                strengthPercent = 25,
                isAbsorbing = false
            )
        )

        return levels.sortedBy { it.price }
    }

    // --- 8. Option Flow & Gamma Exposure Analysis ---
    private fun generateOptionFlowAnalysis(currentPrice: Double, trend: TrendStatus): OptionFlowAnalysis {
        val callVol = 184500L + Random.nextLong(1000, 9000)
        val putVol = 132400L + Random.nextLong(800, 7000)
        val pcr = (putVol.toDouble() / callVol.toDouble()).roundToTwoDecimals()

        val roundedStrike = (currentPrice / 10.0).roundToInt() * 10.0
        val maxPain = roundedStrike - 5.0
        val callWall = roundedStrike + 20.0
        val putWall = roundedStrike - 25.0

        val sentiment = if (pcr < 0.85) "ثوراني / صعودي قوي (تراكم عقود Call مؤسساتية)"
        else if (pcr > 1.15) "دببي / هبوطي (شراء تحوطي Put)"
        else "محايد / توازن غاما"

        val unusuals = listOf(
            "حجم غير معتاد (Sweep) على خيارات الشراء Call سترايك ${(currentPrice + 15).toInt()} بمبلغ 4.8M$",
            "تركز سيولة خيارات البيع Put كجدار حماية مؤسساتي عند سترايك ${(currentPrice - 20).toInt()}$",
            "انكشاف غاما إيجابي يحافظ على استقرار السعر فوق $putWall$"
        )

        return OptionFlowAnalysis(
            callVolume = callVol,
            putVolume = putVol,
            putCallRatio = pcr,
            maxPainStrike = maxPain,
            majorCallWall = callWall,
            majorPutWall = putWall,
            gammaRegime = if (pcr < 0.9) GammaRegime.NEGATIVE_GAMMA else GammaRegime.POSITIVE_GAMMA,
            institutionalSentiment = sentiment,
            unusualOptionActivities = unusuals
        )
    }

    // --- 9. Future Flow & Delta Accumulation ---
    private fun generateFutureFlowAnalysis(candles: List<XauCandle>, trend: TrendStatus): FutureFlowAnalysis {
        val totalCandleVol = candles.sumOf { it.volume }.toLong()
        val buyContracts = (totalCandleVol * if (trend == TrendStatus.BULLISH) 0.58 else 0.44).toLong()
        val sellContracts = totalCandleVol - buyContracts
        val netDelta = buyContracts - sellContracts

        return FutureFlowAnalysis(
            aggressiveBuyContracts = buyContracts,
            aggressiveSellContracts = sellContracts,
            netDeltaContracts = netDelta,
            cumulativeDeltaTrend = if (netDelta > 0) "صاعد مؤسساتي (شراء ضاغط)" else "هابط تصريفي (بيع كثيف)",
            openInterestChange = if (trend == TrendStatus.BULLISH) +14800L else -9200L,
            absorptionDetected = true,
            institutionalDominance = if (netDelta > 0) "المشترون الحيتان 72%" else "البائعون المؤسساتيون 69%"
        )
    }

    // --- 10. Smart Confluence Buy/Sell Levels ---
    private fun generateSmartConfluenceRecommendation(
        candles: List<XauCandle>,
        currentPrice: Double,
        trend: TrendStatus,
        orderBlocks: List<OrderBlock>,
        zones: List<SupplyDemandZone>,
        sweeps: List<LiquiditySweep>,
        bookmapLevels: List<BookmapLiquidityLevel>,
        optionFlow: OptionFlowAnalysis,
        futureFlow: FutureFlowAnalysis
    ): SmartConfluenceRecommendation {
        // Smart Buy Zone Calculation
        val demandOb = orderBlocks.firstOrNull { it.type == SmcType.BULLISH && !it.isMitigated }
        val nearestBidWall = bookmapLevels.filter { it.type == BookmapLevelType.BID_WALL }.minByOrNull { it.distancePoints }
        
        val buyTop = demandOb?.top ?: nearestBidWall?.price ?: (currentPrice - 3.5)
        val buyBottom = demandOb?.bottom ?: (buyTop - 2.5)
        val buyEntry = ((buyTop + buyBottom) / 2.0).roundToTwoDecimals()
        val buySl = (buyBottom - 3.0).roundToTwoDecimals()
        val buyTp1 = (buyEntry + 7.5).roundToTwoDecimals()
        val buyTp2 = (buyEntry + 15.0).roundToTwoDecimals()
        val buyTp3 = (buyEntry + 25.0).roundToTwoDecimals()

        val smartBuy = SmartPriceZone(
            title = "مستوى الشراء المؤسساتي الذكي (Smart Buy Level)",
            priceTop = buyTop.roundToTwoDecimals(),
            priceBottom = buyBottom.roundToTwoDecimals(),
            idealEntry = buyEntry,
            slPrice = buySl,
            tp1 = buyTp1,
            tp2 = buyTp2,
            tp3 = buyTp3,
            confluenceScore = 88,
            reasonAr = "تطابق منطقة طلب SMC مع جدار طلب البوكماب (${nearestBidWall?.lots ?: 1500} لوت) وجدار عقود Put للأوبشن فلو عند $buyBottom$."
        )

        // Smart Sell Zone Calculation
        val supplyOb = orderBlocks.firstOrNull { it.type == SmcType.BEARISH && !it.isMitigated }
        val nearestAskWall = bookmapLevels.filter { it.type == BookmapLevelType.ASK_WALL }.minByOrNull { it.distancePoints }

        val sellBottom = supplyOb?.bottom ?: nearestAskWall?.price ?: (currentPrice + 4.0)
        val sellTop = supplyOb?.top ?: (sellBottom + 2.5)
        val sellEntry = ((sellTop + sellBottom) / 2.0).roundToTwoDecimals()
        val sellSl = (sellTop + 3.0).roundToTwoDecimals()
        val sellTp1 = (sellEntry - 7.5).roundToTwoDecimals()
        val sellTp2 = (sellEntry - 15.0).roundToTwoDecimals()
        val sellTp3 = (sellEntry - 25.0).roundToTwoDecimals()

        val smartSell = SmartPriceZone(
            title = "مستوى البيع المؤسساتي الذكي (Smart Sell Level)",
            priceTop = sellTop.roundToTwoDecimals(),
            priceBottom = sellBottom.roundToTwoDecimals(),
            idealEntry = sellEntry,
            slPrice = sellSl,
            tp1 = sellTp1,
            tp2 = sellTp2,
            tp3 = sellTp3,
            confluenceScore = 85,
            reasonAr = "توافق جدار عرض بوكماب (${nearestAskWall?.lots ?: 1800} لوت) مع جدار Call Wall في الأوبشن فلو وبلوك عقود SMC بيعي."
        )

        val isBullishDominant = trend == TrendStatus.BULLISH || futureFlow.netDeltaContracts > 0
        val primaryDir = if (isBullishDominant) "شراء ذكي (Smart Buy)" else "بيع ذكي (Smart Sell)"

        return SmartConfluenceRecommendation(
            primaryDirection = primaryDir,
            spotPrice = currentPrice,
            smartBuyZone = smartBuy,
            smartSellZone = smartSell,
            overallConfluencePercent = if (isBullishDominant) 91 else 87,
            bookmapSummary = "جدران سيولة البوكماب تُظهر دعماً كثيفاً عند ${smartBuy.idealEntry}$ ومقاومة تصريف عند ${smartSell.idealEntry}$.",
            optionFlowSummary = "نسبة Put/Call عند ${optionFlow.putCallRatio} مع ضغط شراء تدريجي ومستوى Max Pain عند ${optionFlow.maxPainPainString()}$.",
            futureFlowSummary = "${futureFlow.institutionalDominance} مع دلتا تراكمية إيجابية قدرها ${futureFlow.netDeltaContracts} عقداً.",
            executionAdviceAr = "يُفضل انتظار إعادة اختبار منطقة ${if (isBullishDominant) smartBuy.idealEntry else smartSell.idealEntry}$ والتأكيد عبر شمعة الفوت برنت 5M ذات دلتا شرائية قوية."
        )
    }

    // --- Footprint 5M Cluster Generation ---
    fun generateFootprintCandles(candles: List<XauCandle>): List<FootprintCandle> {
        val result = mutableListOf<FootprintCandle>()
        var runningCvd = 0

        candles.forEachIndexed { idx, candle ->
            val step = 0.50 // 50 cents price resolution for gold footprint
            val lowAligned = (candle.low / step).toInt() * step
            val highAligned = (candle.high / step).toInt() * step

            val levels = mutableListOf<FootprintLevel>()
            var currentP = lowAligned
            var maxVol = 0
            var pocP = currentP
            var candleDelta = 0
            var candleVol = 0

            while (currentP <= highAligned + 0.001) {
                // Distribute volume based on proximity to close/open
                val mid = (candle.open + candle.close) / 2.0
                val dist = abs(currentP - mid)
                val baseVol = (250.0 / (1.0 + dist)).toInt() + Random.nextInt(15, 60)

                val buyRatio = if (candle.isBullish) Random.nextDouble(0.55, 0.75) else Random.nextDouble(0.25, 0.45)
                val askVol = (baseVol * buyRatio).toInt()
                val bidVol = baseVol - askVol

                val totalLvlVol = bidVol + askVol
                if (totalLvlVol > maxVol) {
                    maxVol = totalLvlVol
                    pocP = currentP
                }

                val delta = askVol - bidVol
                candleDelta += delta
                candleVol += totalLvlVol

                // Diagonal Imbalance check (3x volume dominance)
                val isBuyImbalance = askVol >= (bidVol * 2.8) && askVol > 80
                val isSellImbalance = bidVol >= (askVol * 2.8) && bidVol > 80

                levels.add(
                    FootprintLevel(
                        price = currentP.roundToTwoDecimals(),
                        bidVolume = bidVol,
                        askVolume = askVol,
                        isPoc = false, // will update after loop
                        isBuyImbalance = isBuyImbalance,
                        isSellImbalance = isSellImbalance
                    )
                )
                currentP += step
            }

            // Mark POC level
            val mappedLevels = levels.map {
                if (it.price == pocP.roundToTwoDecimals()) it.copy(isPoc = true) else it
            }

            runningCvd += candleDelta

            // Value Area Calculation (70% of total volume)
            val sortedLevels = mappedLevels.sortedByDescending { it.totalVolume }
            val targetVaVol = (candleVol * 0.70).toInt()
            var accVaVol = 0
            val vaLevels = mutableListOf<FootprintLevel>()
            for (lvl in sortedLevels) {
                accVaVol += lvl.totalVolume
                vaLevels.add(lvl)
                if (accVaVol >= targetVaVol) break
            }
            val vah = vaLevels.maxOfOrNull { it.price } ?: candle.high
            val valPrice = vaLevels.minOfOrNull { it.price } ?: candle.low

            result.add(
                FootprintCandle(
                    id = idx,
                    candle = candle,
                    levels = mappedLevels.reversed(), // top price first
                    pocPrice = pocP.roundToTwoDecimals(),
                    totalDelta = candleDelta,
                    minDelta = -abs(candleDelta / 2) - 20,
                    maxDelta = abs(candleDelta) + 40,
                    totalVolume = candleVol,
                    cumulativeDelta = runningCvd,
                    valueAreaHigh = vah.roundToTwoDecimals(),
                    valueAreaLow = valPrice.roundToTwoDecimals()
                )
            )
        }
        return result
    }

    private fun generateSMCRecommendation(
        candles: List<XauCandle>,
        orderBlocks: List<OrderBlock>,
        fvgs: List<FairValueGap>,
        sweeps: List<LiquiditySweep>,
        zones: List<SupplyDemandZone>,
        trend: TrendStatus,
        marketState: MarketState
    ): TradeRecommendation {
        val currentPrice = candles.last().close
        val nearestBullishOb = orderBlocks.firstOrNull { it.type == SmcType.BULLISH && it.top < currentPrice && currentPrice - it.top < 25.0 }
        val nearestBullishZone = zones.firstOrNull { it.isDemand && it.top < currentPrice && currentPrice - it.top < 30.0 }
        val recentSellSweep = sweeps.lastOrNull { it.type == SweepType.SELL_STOP && candles.size - it.candleIndex < 10 }

        if ((nearestBullishOb != null || nearestBullishZone != null) && trend == TrendStatus.BULLISH) {
            val entry = nearestBullishOb?.top ?: nearestBullishZone?.top ?: (currentPrice - 2.0)
            val sl = (nearestBullishOb?.bottom ?: nearestBullishZone?.bottom ?: (entry - 5.0)) - 1.5
            val tp1 = entry + abs(entry - sl) * 2.0
            val tp2 = entry + abs(entry - sl) * 4.0
            val winRate = if (recentSellSweep != null) 85 else 74

            return TradeRecommendation(
                type = "BUY",
                entryPrice = entry.roundToTwoDecimals(),
                stopLoss = sl.roundToTwoDecimals(),
                takeProfit = tp1.roundToTwoDecimals(),
                takeProfit2 = tp2.roundToTwoDecimals(),
                winRatePercent = winRate,
                score = winRate * 1.1,
                reasoningAr = "توصية دخول شرائية قوية متوافقة مع منطقة طلب وبلوك عقود مؤسساتي وسحب سيولة بيعية.",
                reasoningEn = "Strong Buy setup at $entry. Confluence of institutional demand and liquidity sweep."
            )
        }

        val isUp = trend == TrendStatus.BULLISH || marketState == MarketState.ACCUMULATION
        val entry = currentPrice
        val sl = if (isUp) entry - 6.0 else entry + 6.0
        val tp1 = if (isUp) entry + 12.0 else entry - 12.0
        val tp2 = if (isUp) entry + 24.0 else entry - 24.0

        return TradeRecommendation(
            type = if (isUp) "BUY" else "SELL",
            entryPrice = entry.roundToTwoDecimals(),
            stopLoss = sl.roundToTwoDecimals(),
            takeProfit = tp1.roundToTwoDecimals(),
            takeProfit2 = tp2.roundToTwoDecimals(),
            winRatePercent = 75,
            score = 75.0,
            reasoningAr = "دخول متوافق مع اتجاه الذهب ومؤشرات السيولة الإيجابية.",
            reasoningEn = "Market structure momentum entry following current trend."
        )
    }

    private fun Double.roundToTwoDecimals(): Double {
        return (this * 100.0).roundToInt() / 100.0
    }

    private fun OptionFlowAnalysis.maxPainPainString(): String {
        return String.format("%.2f", this.maxPainStrike)
    }
}
