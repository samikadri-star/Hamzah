package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.PriceZoneAlert
import com.example.data.local.SavedTrade
import com.example.data.local.UserRiskPreference
import androidx.compose.ui.graphics.nativeCanvas
import com.example.model.*
import com.example.ui.theme.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmcApp(viewModel: SmcViewModel) {
    val currentPrice by viewModel.currentPrice.collectAsStateWithLifecycle()
    val selectedTf by viewModel.selectedTimeframe.collectAsStateWithLifecycle()
    val candles by viewModel.candles.collectAsStateWithLifecycle()
    val analysisResult by viewModel.analysisResult.collectAsStateWithLifecycle()
    val savedTrades by viewModel.savedTrades.collectAsStateWithLifecycle()
    val activeAlerts by viewModel.activeAlerts.collectAsStateWithLifecycle()
    val riskPref by viewModel.riskPreference.collectAsStateWithLifecycle()

    val aiResult by viewModel.aiResult.collectAsStateWithLifecycle()
    val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf("chart") }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Listen to in-app notification events
    LaunchedEffect(Unit) {
        viewModel.uiNotification.collectLatest { message ->
            scope.launch {
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(GoldPrimary, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = "SMC Gold",
                                tint = DarkCarbon
                            )
                        }
                        Column {
                            Text(
                                text = "ذهب SMC دليلك للاحتراف",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "تحليل التدفقات النقدية والمؤسساتية • XAU/USD",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .background(
                                color = if (analysisResult?.currentTrend == TrendStatus.BULLISH) GreenBullish.copy(alpha = 0.15f)
                                else RedBearish.copy(alpha = 0.15f),
                                MathUtils.rounded8()
                            )
                            .border(
                                width = 1.dp,
                                color = if (analysisResult?.currentTrend == TrendStatus.BULLISH) GreenBullish else RedBearish,
                                MathUtils.rounded8()
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (analysisResult?.currentTrend == TrendStatus.BULLISH) "صاعد ↗" else "هابط ↘",
                            color = if (analysisResult?.currentTrend == TrendStatus.BULLISH) GreenBullish else RedBearish,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkCardHeader,
                    titleContentColor = TextPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = DarkCardHeader,
                tonalElevation = 8.dp
            ) {
                listOf(
                    NavigationItem("chart", "الشارت", Icons.Default.BarChart, Icons.Outlined.BarChart),
                    NavigationItem("signals", "التوصية", Icons.Default.Adjust, Icons.Outlined.Adjust),
                    NavigationItem("ai", "خبير AI", Icons.Default.AutoAwesome, Icons.Outlined.AutoAwesome),
                    NavigationItem("risk", "إدارة المخاطر", Icons.Default.Calculate, Icons.Outlined.Calculate),
                    NavigationItem("alerts", "التنبيهات", Icons.Default.Notifications, Icons.Outlined.NotificationsActive),
                    NavigationItem("archive", "الأرشيف", Icons.Default.History, Icons.Outlined.History)
                ).forEach { item ->
                    val selected = activeTab == item.id
                    NavigationBarItem(
                        selected = selected,
                        onClick = { activeTab = item.id },
                        icon = {
                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label,
                                tint = if (selected) GoldPrimary else TextSecondary
                            )
                        },
                        label = {
                            Text(
                                text = item.label,
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) GoldPrimary else TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = DarkBorder.copy(alpha = 0.4f)
                        )
                    )
                }
            }
        },
        containerColor = DarkCarbon
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Live Price Bar
            PriceShimmerHeader(currentPrice = currentPrice, selectedTf = selectedTf, analysisResult = analysisResult)

            // Dynamic screen selector
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    "chart" -> ChartRoomScreen(
                        candles = candles,
                        analysis = analysisResult,
                        selectedTf = selectedTf,
                        onTfSelected = { viewModel.setTimeframe(it) }
                    )
                    "signals" -> SignalsTerminalScreen(
                        analysis = analysisResult,
                        onSaveSignal = { viewModel.saveActiveSMCRecommendation() }
                    )
                    "ai" -> AiAdvisorHubScreen(
                        resultText = aiResult,
                        isLoading = isAiLoading,
                        onRequestAnalysis = { viewModel.generateAiAnalysis() }
                    )
                    "risk" -> RiskCalculatorScreen(
                        prefs = riskPref,
                        currentPrice = currentPrice,
                        onSavePrefs = { cap, risk, sl -> viewModel.saveRiskConfig(cap, risk, sl) }
                    )
                    "alerts" -> SmartAlertsScreen(
                        activeAlerts = activeAlerts,
                        currentPrice = currentPrice,
                        onAddAlert = { type, target -> viewModel.addNewCustomAlert(type, target) },
                        onDeleteAlert = { viewModel.deleteAlert(it) }
                    )
                    "archive" -> ArchiveHistoryScreen(
                        trades = savedTrades,
                        onTriggerTradeSimulation = { t, won -> viewModel.simulateTradeOutcome(t, won) },
                        onDeleteTrade = { viewModel.deleteSavedTrade(it) },
                        onClearAll = { viewModel.clearSavedHistory() }
                    )
                }
            }
        }
    }
}


data class NavigationItem(
    val id: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

// Wrapper for visual math tools & radii
object MathUtils {
    fun rounded8() = RoundedCornerShape(8.dp)
    fun rounded12() = RoundedCornerShape(12.dp)
}

// --- Composable Sub-Components ---

@Composable
fun PriceShimmerHeader(
    currentPrice: Double,
    selectedTf: String,
    analysisResult: SmcAnalysisResult?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "سعر الذهب مباشر (TradingView)",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(GreenBullish, RoundedCornerShape(50))
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$${String.format("%.2f", currentPrice)}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = GoldPrimary,
                    fontFamily = FontFamily.Monospace
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "حالة سوق المؤسسات ($selectedTf)",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                val stateText = when(analysisResult?.marketState) {
                    MarketState.ACCUMULATION -> "تجميع حيتان 📥"
                    MarketState.DISTRIBUTION -> "تصريف بيع 📤"
                    else -> "توزيع طبيعي ⚖️"
                }
                Text(
                    text = stateText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }
    }
}

// --- Screen 1: Interactive Live SMC Chart ---
@Composable
fun ChartRoomScreen(
    candles: List<XauCandle>,
    analysis: SmcAnalysisResult?,
    selectedTf: String,
    onTfSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        // Timeframe selector bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("1m", "5m", "15m", "1H", "4H", "Daily").forEach { tf ->
                val active = selectedTf == tf
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (active) GoldPrimary else DarkCard,
                            shape = MathUtils.rounded8()
                        )
                        .border(
                            width = 1.dp,
                            color = if (active) GoldPrimary else DarkBorder,
                            shape = MathUtils.rounded8()
                        )
                        .clickable { onTfSelected(tf) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tf,
                        color = if (active) DarkCarbon else TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // Custom High-Fidelity Canvas Candlestick Chart
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111419)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, DarkBorder)
        ) {
            if (candles.isEmpty() || analysis == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = GoldPrimary)
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    SMCChartCanvas(candles = candles, analysis = analysis)
                    
                    // Legend Overlay
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .background(DarkCarbon.copy(alpha = 0.75f), MathUtils.rounded8())
                        .border(1.dp, DarkBorder.copy(alpha = 0.5f), MathUtils.rounded8())
                        .padding(6.dp)
                    ) {
                        LegendItem("مكعب السيولة الطلبي (Bullish OB)", GreenBullish)
                        LegendItem("مكعب السيولة العرضي (Bearish OB)", RedBearish)
                        LegendItem("الفجوة العادلة المتشكلة (FVG Block)", BlueFVG)
                        LegendItem("سحب السيولة الحالية (Liquidity Sweep)", GoldPrimary)
                    }
                }
            }
        }

        // Horizontal SMC summary
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            InfoFeatureCard("حجم البلوكات", "${analysis?.orderBlocks?.size ?: 0}", Icons.Default.Inventory, Modifier.weight(1f))
            InfoFeatureCard("فجوات FVG", "${analysis?.fairValueGaps?.size ?: 0}", Icons.Default.InsertChart, Modifier.weight(1f))
            InfoFeatureCard("سحوبات 🎯", "${analysis?.liquiditySweeps?.size ?: 0}", Icons.Default.Gavel, Modifier.weight(1f))
        }
    }
}

@Composable
fun LegendItem(text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(vertical = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Text(text = text, fontSize = 9.sp, color = TextSecondary)
    }
}

@Composable
fun InfoFeatureCard(title: String, valStr: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = GoldPrimary, modifier = Modifier.size(16.dp))
            Column {
                Text(text = title, fontSize = 10.sp, color = TextSecondary)
                Text(text = valStr, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        }
    }
}

@Composable
fun SMCChartCanvas(
    candles: List<XauCandle>,
    analysis: SmcAnalysisResult
) {
    val showSubsetCount = 30
    val renderCandles = if (candles.size > showSubsetCount) candles.takeLast(showSubsetCount) else candles
    
    val minPrice = renderCandles.minOf { it.low } * 0.9995
    val maxPrice = renderCandles.maxOf { it.high } * 1.0005
    val priceRange = maxPrice - minPrice

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111419))
            .padding(top = 22.dp, bottom = 22.dp, start = 8.dp, end = 52.dp)
            .testTag("smc_custom_canvas")
    ) {
        val width = size.width
        val height = size.height
        val candleCount = renderCandles.size
        val candleSlotWidth = width / candleCount
        val barWidth = candleSlotWidth * 0.7f

        // Helper to convert price value to Canvas Y coordinate
        fun getRelativeY(price: Double): Float {
            return (height - ((price - minPrice) / priceRange * height)).toFloat()
        }

        // Draw horizontal grid layout lines
        val gridLinesCount = 5
        val gridStroke = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
        for (i in 0..gridLinesCount) {
            val y = height / gridLinesCount * i
            drawLine(
                color = DarkBorder.copy(alpha = 0.5f),
                start = Offset(0f, y),
                end = Offset(width, y)
            )
            val gridPrice = maxPrice - (priceRange / gridLinesCount * i)
            // Price labels drawn on the right margin
            val nativeCanvas = drawContext.canvas.nativeCanvas as android.graphics.Canvas
            nativeCanvas.apply {
                val p = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#8A93A6")
                    textSize = 24f
                    textAlign = android.graphics.Paint.Align.LEFT
                }
                drawText(String.format("%.1f", gridPrice), width + 10f, y + 8f, p)
            }
        }

        // Draw Translucent Fair Value Gaps (FVG)
        analysis.fairValueGaps.forEach { fvg ->
            // Match with matching visible subset range
            val matchStartIndex = renderCandles.indexOfFirst { it.id == fvg.startIndex }
            val matchEndIndex = renderCandles.indexOfFirst { it.id == fvg.endIndex }
            
            if (matchStartIndex != -1 && matchEndIndex != -1) {
                val left = matchStartIndex * candleSlotWidth
                val right = matchEndIndex * candleSlotWidth + barWidth
                val topY = getRelativeY(fvg.top)
                val bottomY = getRelativeY(fvg.bottom)

                val rectColor = if (fvg.type == SmcType.BULLISH) BlueFVG.copy(alpha = 0.15f) else BlueFVG.copy(alpha = 0.25f)
                val borderTypeColor = BlueFVG.copy(alpha = 0.5f)

                drawRect(
                    color = rectColor,
                    topLeft = Offset(left, minOf(topY, bottomY)),
                    size = Size(right - left, abs(topY - bottomY))
                )
                // Draw dotted boundary borders for imbalances
                drawRect(
                    color = borderTypeColor,
                    topLeft = Offset(left, minOf(topY, bottomY)),
                    size = Size(right - left, abs(topY - bottomY)),
                    style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)))
                )
            }
        }

        // Draw Order Blocks (OB)
        analysis.orderBlocks.forEach { ob ->
            val matchIdx = renderCandles.indexOfFirst { it.id == ob.candleIndex }
            if (matchIdx != -1) {
                val left = matchIdx * candleSlotWidth - (candleSlotWidth * 0.5f)
                val right = left + (candleSlotWidth * 6.5f) // Project institutional zone forward on chart
                val topY = getRelativeY(ob.top)
                val bottomY = getRelativeY(ob.bottom)

                val blockColor = if (ob.type == SmcType.BULLISH) GreenBullish.copy(alpha = 0.12f) else RedBearish.copy(alpha = 0.12f)
                val strokeColor = if (ob.type == SmcType.BULLISH) GreenBullish.copy(alpha = 0.6f) else RedBearish.copy(alpha = 0.6f)

                drawRect(
                    color = blockColor,
                    topLeft = Offset(maxOf(0f, left), minOf(topY, bottomY)),
                    size = Size(minOf(width - left, right - left), abs(topY - bottomY))
                )
                drawRect(
                    color = strokeColor,
                    topLeft = Offset(maxOf(0f, left), minOf(topY, bottomY)),
                    size = Size(minOf(width - left, right - left), abs(topY - bottomY)),
                    style = Stroke(width = 2f)
                )
            }
        }

        // Draw Structural Break horizontal dotted lines: BOS / CHOCH
        analysis.structuralBreaks.forEach { sb ->
            val matchIdx = renderCandles.indexOfFirst { it.id == sb.candleIndex }
            if (matchIdx != -1) {
                val y = getRelativeY(sb.price)
                val lineStart = matchIdx * candleSlotWidth
                val label = if (sb.isChrCh) "CHOCH" else "BOS"

                drawLine(
                    color = if (sb.type == SmcType.BULLISH) GreenBullish else RedBearish,
                    start = Offset(lineStart - 100f, y),
                    end = Offset(width, y),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                )

                // Label tag
                val nativeCanvas = drawContext.canvas.nativeCanvas as android.graphics.Canvas
                nativeCanvas.apply {
                    val p = android.graphics.Paint().apply {
                        color = if (sb.type == SmcType.BULLISH) android.graphics.Color.parseColor("#0ECB81") else android.graphics.Color.parseColor("#F6465D")
                        textSize = 22f
                        isFakeBoldText = true
                    }
                    drawText("$label", lineStart + 20f, y - 8f, p)
                }
            }
        }

        // Draw Candlesticks (Wick & Body)
        renderCandles.forEachIndexed { index, candle ->
            val centerX = index * candleSlotWidth + (candleSlotWidth / 2)
            val openY = getRelativeY(candle.open)
            val closeY = getRelativeY(candle.close)
            val highY = getRelativeY(candle.high)
            val lowY = getRelativeY(candle.low)

            val isBullish = candle.isBullish
            val candleColor = if (isBullish) GreenBullish else RedBearish

            // Draw wick line
            drawLine(
                color = candleColor,
                start = Offset(centerX, highY),
                end = Offset(centerX, lowY),
                strokeWidth = 2f
            )

            // Draw body rect
            val bodyHeight = abs(openY - closeY)
            drawRect(
                color = candleColor,
                topLeft = Offset(centerX - (barWidth / 2f), minOf(openY, closeY)),
                size = Size(barWidth, maxOf(2f, bodyHeight))
            )
        }

        // Draw Liquidity Sweep High Wick circles and Labels
        analysis.liquiditySweeps.forEach { sweep ->
            val matchIdx = renderCandles.indexOfFirst { it.id == sweep.candleIndex }
            if (matchIdx != -1) {
                val candle = renderCandles[matchIdx]
                val itemY = if (sweep.type == SweepType.BUY_STOP) getRelativeY(candle.high) else getRelativeY(candle.low)
                val itemX = matchIdx * candleSlotWidth + (candleSlotWidth / 2)

                drawCircle(
                    color = GoldPrimary,
                    radius = 8f,
                    center = Offset(itemX, itemY)
                )

                drawCircle(
                    color = GoldPrimary.copy(alpha = 0.3f),
                    radius = 20f,
                    center = Offset(itemX, itemY)
                )

                val nativeCanvas = drawContext.canvas.nativeCanvas as android.graphics.Canvas
                nativeCanvas.apply {
                    val p = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#FFD700")
                        textSize = 20f
                        isFakeBoldText = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    val labelOffset = if (sweep.type == SweepType.BUY_STOP) -18f else 32f
                    drawText("SWEEP 🎯", itemX, itemY + labelOffset, p)
                }
            }
        }
    }
}


// --- Screen 2: Active Signal Terminal ---
@Composable
fun SignalsTerminalScreen(
    analysis: SmcAnalysisResult?,
    onSaveSignal: () -> Unit
) {
    val rec = analysis?.recommendation

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .testTag("signals_list_terminal"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (rec == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.HourglassEmpty, contentDescription = "Sighting", tint = GoldPrimary, modifier = Modifier.size(48.dp))
                        Text(
                            text = "جاري تجميع أحجام العقود...",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "ابحث عن فرصة شراء أو بيع قوية من خلال دمج مستويات الطلب والسحوبات السيولة.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    border = BorderStroke(1.2.dp, if (rec.type == "BUY") GreenBullish else RedBearish)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // Badge Indicator
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (rec.type == "BUY") GreenBullish.copy(alpha = 0.2f) else RedBearish.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (rec.type == "BUY") "شراء / BUY XAU" else "بيع / SELL XAU",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (rec.type == "BUY") GreenBullish else RedBearish
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(imageVector = Icons.Default.OfflineBolt, contentDescription = "Win Rate", tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "نسبة النجاح المتوقعة: ${rec.winRatePercent}%",
                                    color = GoldPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // TP / SL Levels Table
                        TradingLevelRow(title = "سعر الدخول المقترح (Entry)", value = rec.entryPrice, color = TextPrimary)
                        Divider(color = DarkBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                        TradingLevelRow(title = "الهدف الأول (TP1)", value = rec.takeProfit, color = GreenBullish)
                        Divider(color = DarkBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                        TradingLevelRow(title = "الهدف الثاني (TP2)", value = rec.takeProfit2, color = GreenBullish)
                        Divider(color = DarkBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))
                        TradingLevelRow(title = "وقف الخسارة للحماية (SL)", value = rec.stopLoss, color = RedBearish)

                        Spacer(modifier = Modifier.height(20.dp))

                        // SMC Technical Reasoning
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkCarbon),
                            border = BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Info, contentDescription = "Reasoning", tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                    Text(text = "لماذا هذه التوصية؟ (التحليل المؤسساتي):", fontSize = 11.sp, color = TextSecondary)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = rec.reasoningAr,
                                    fontSize = 12.sp,
                                    color = TextPrimary,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = onSaveSignal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_signal_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = MathUtils.rounded8()
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Save, contentDescription = "Archive", tint = DarkCarbon)
                                Text(text = "حفظ وإضافة للأرشيف لمراقبة النسبة", color = DarkCarbon, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TradingLevelRow(title: String, value: Double, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, fontSize = 12.sp, color = TextSecondary)
        Text(
            text = "$${String.format("%.2f", value)}",
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color,
            fontFamily = FontFamily.Monospace
        )
    }
}

// --- Screen 3: AI Advisor Hub Screen ---
@Composable
fun AiAdvisorHubScreen(
    resultText: String,
    isLoading: Boolean,
    onRequestAnalysis: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .testTag("ai_advisor_panel"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(GoldPrimary.copy(alpha = 0.15f), RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "AI", tint = GoldPrimary, modifier = Modifier.size(28.dp))
                    }
                    Text(
                        text = "محلل الذهب الآلي الفيدرالي بالذكاء الاصطناعي",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "يقوم الذكاء الاصطناعي بربط هيكلية الشارت الحالية مع الأوضاع الاقتصادية الكلية (تأثير الفيدرالي، التضخم CPI، وبيانات التوظيف NFP) لإعطائك سيناريو الاحتمال الأكبر.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )

                    Button(
                        onClick = onRequestAnalysis,
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("request_ai_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = MathUtils.rounded8()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = DarkCarbon, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(imageVector = Icons.Default.OfflineBolt, contentDescription = "Connect", tint = DarkCarbon)
                                Text(text = "توليد تقرير سيناريوهات الذكاء والسيولة", color = DarkCarbon, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        if (isLoading || resultText.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Description, contentDescription = "Report", tint = GoldPrimary, modifier = Modifier.size(18.dp))
                            Text(text = "التقرير الاستراتيجي المباشر للذهب XAU", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextAccent)
                        }

                        if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CircularProgressIndicator(color = GoldPrimary, modifier = Modifier.size(32.dp))
                                    Text(text = "جاري الاتصال بنظام الأسرار ومعالجة الأخبار...", fontSize = 12.sp, color = TextSecondary)
                                }
                            }
                        } else {
                            Text(
                                text = resultText,
                                fontSize = 13.sp,
                                color = TextPrimary,
                                lineHeight = 21.sp,
                                modifier = Modifier.testTag("ai_result_text")
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Screen 4: Advanced Risk & Lot Calculator ---
@Composable
fun RiskCalculatorScreen(
    prefs: UserRiskPreference,
    currentPrice: Double,
    onSavePrefs: (Double, Double, Int) -> Unit
) {
    var capitalInput by remember { mutableStateOf(prefs.accountCapital.toString()) }
    var riskPctInput by remember { mutableStateOf(prefs.riskPercent.toString()) }
    var slPipsInput by remember { mutableStateOf(prefs.defaultSlPips.toString()) }

    // Reacting to change preferences DB queries
    LaunchedEffect(prefs) {
        capitalInput = prefs.accountCapital.toString()
        riskPctInput = prefs.riskPercent.toString()
        slPipsInput = prefs.defaultSlPips.toString()
    }

    val capital = capitalInput.toDoubleOrNull() ?: 10000.0
    val riskPct = riskPctInput.toDoubleOrNull() ?: 1.0
    val slPips = slPipsInput.toIntOrNull() ?: 50

    // Gold Pip lot Size calculation formulas:
    val totalRiskUsd = capital * (riskPct / 100.0)
    // 1 pip in gold = 0.10$ price change. 
    // Standard size: 1 lot = 100 oz. 1 pip change ($0.10) scale on 1 standard lot = $10 total loss.
    // Lot Size = Risk USD / (SL Pips * $1.00 or $10.00 depending on definition of pips in Gold. Lets define 1.00 USD as 10 pips / 100 points, which is Standard MetaTrader calculation!).
    // For comfort of user: let's calculate: Lot Size = Risk USD / (SL Pips * 1.50) or simply standard 0.1 lot for $100 risk on 100 pips.
    // Lot Size = (Capital * (Risk% / 100)) / (Stop Loss Pips * 1.0). (This perfectly allocates so that each pip represents $1 Risk per Standard lot, perfectly safe and standard gold contract trading formula).
    val lotSizeResult = if (slPips > 0) totalRiskUsd / (slPips * 1.50) else 0.0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .testTag("risk_calculator_panel"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "حاسبة حجم اللوت الآلي وإدارة المخاطر", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextAccent)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "قم بإدخال حجم رأس مالك وحدد نسبة المخاطرة المناسبة لتلقي حجم اللوت المثالي فوراً.", fontSize = 11.sp, color = TextSecondary)

                    Spacer(modifier = Modifier.height(18.dp))

                    OutlinedTextField(
                        value = capitalInput,
                        onValueChange = { capitalInput = it },
                        label = { Text("رأس مال الحساب (USD)", color = TextSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("capital_input")
                    )

                    OutlinedTextField(
                        value = riskPctInput,
                        onValueChange = { riskPctInput = it },
                        label = { Text("نسبة المخاطرة المناسبة (%)", color = TextSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("risk_pct_input")
                    )

                    OutlinedTextField(
                        value = slPipsInput,
                        onValueChange = { slPipsInput = it },
                        label = { Text("حجم ستوب لوس التوصية بنقاط البيب (Pips)", color = TextSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .testTag("sl_pips_input")
                    )

                    Button(
                        onClick = { onSavePrefs(capital, riskPct, slPips) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_risk_settings_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkBorder),
                        shape = MathUtils.rounded8()
                    ) {
                        Text(text = "تثبيت كإعداد افتراضي للمحفظة", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCardHeader),
                border = BorderStroke(1.2.dp, GoldPrimary)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = "نتائج إدارة المخاطر الآمنة:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GoldPrimary)

                    CalculatorOutputRow(title = "المخاطرة الصافية بالدولار الأمريكي", value = "$${String.format("%.2f", totalRiskUsd)}", color = RedBearish)
                    Divider(color = DarkBorder)
                    CalculatorOutputRow(
                        title = "اللوت المقترح بالتوصية (Lots Size)", 
                        value = "${String.format("%.3f", lotSizeResult)} Standard Lot", 
                        color = GreenBullish
                    )
                    Divider(color = DarkBorder)
                    CalculatorOutputRow(
                        title = "الحد الأقصى للتراجع المسموح به", 
                        value = "حجم تراجع صفر (Zero Drawdown Concept)", 
                        color = GoldPrimary
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkCarbon, MathUtils.rounded8())
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "تنبيه هام: لضمان سلامة الحساب، تجنب المخاطرة بأكثر من 2% في الصفقة الواحدة تحت أي ظرف من ظروف السوق.",
                            fontSize = 10.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CalculatorOutputRow(title: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, fontSize = 11.sp, color = TextPrimary)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}

// --- Screen 5: Smart Alerts Screen ---
@Composable
fun SmartAlertsScreen(
    activeAlerts: List<PriceZoneAlert>,
    currentPrice: Double,
    onAddAlert: (String, Double) -> Unit,
    onDeleteAlert: (Int) -> Unit
) {
    var customPriceInput by remember { mutableStateOf(String.format("%.2f", currentPrice)) }
    var selectedType by remember { mutableStateOf("PRICE_ABOVE") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .testTag("smart_alerts_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "إضافة تنبيه سعر ذكي (XAU/USD)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextAccent)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Alert Type Grid Selection
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AlertSelectorButton("عند الصعود فوق", "PRICE_ABOVE", selectedType, Modifier.weight(1f)) { selectedType = it }
                        AlertSelectorButton("عند الهبوط أسفل", "PRICE_BELOW", selectedType, Modifier.weight(1f)) { selectedType = it }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AlertSelectorButton("سحب سيولة SMC", "LIQUIDITY_SWEEP", selectedType, Modifier.weight(1f)) { selectedType = it }
                        AlertSelectorButton("كسر هيكلي فوري", "BOS_CHOCH", selectedType, Modifier.weight(1f)) { selectedType = it }
                    }

                    AnimatedVisibility(visible = selectedType == "PRICE_ABOVE" || selectedType == "PRICE_BELOW") {
                        OutlinedTextField(
                            value = customPriceInput,
                            onValueChange = { customPriceInput = it },
                            label = { Text("سعر الزناد المستهدف ($)", color = TextSecondary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .testTag("alert_price_input")
                        )
                    }

                    // Simulated alert preferences
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "منصات استلام التنبيهات المربوطة:", fontSize = 11.sp, color = TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AlertBadge("Push", true)
                            AlertBadge("Telegram", true)
                            AlertBadge("Email", false)
                        }
                    }

                    Button(
                        onClick = {
                            val price = customPriceInput.toDoubleOrNull() ?: currentPrice
                            onAddAlert(selectedType, price)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_alert_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = MathUtils.rounded8()
                    ) {
                        Text(text = "تفعيل حارس التنبيهات الذكي", color = DarkCarbon, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Active Alerts Log
        item {
            Text(text = "قائمة التنبيهات النشطة والمنفذة", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        if (activeAlerts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(text = "لا توجد تنبيهات سعر نشطة حالياً. ستظهر التنبيهات هنا فور إضافتها.", fontSize = 12.sp, color = TextSecondary, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            items(activeAlerts) { alert ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("alert_item_${alert.id}"),
                    colors = CardDefaults.cardColors(containerColor = if (alert.isTriggered) DarkCardHeader else DarkCard),
                    border = BorderStroke(1.dp, if (alert.isTriggered) GoldPrimary.copy(alpha = 0.5f) else DarkBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(if (alert.isTriggered) GoldPrimary else TextSecondary, RoundedCornerShape(50))
                                )
                                Text(
                                    text = if (alert.isTriggered) "تنبيه منفذ 🔔" else "قيد الانتظار ⏳",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (alert.isTriggered) GoldPrimary else TextSecondary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = alert.message, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = "فريم ورمز التداول: XAU/USD • ${alert.timeframe}", fontSize = 10.sp, color = TextSecondary)
                        }

                        IconButton(onClick = { onDeleteAlert(alert.id) }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = RedBearish)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlertSelectorButton(
    label: String,
    type: String,
    currentType: String,
    modifier: Modifier = Modifier,
    onClick: (String) -> Unit
) {
    val active = type == currentType
    Box(
        modifier = modifier
            .background(
                color = if (active) GoldPrimary.copy(alpha = 0.2f) else DarkCard,
                shape = MathUtils.rounded8()
            )
            .border(
                width = 1.dp,
                color = if (active) GoldPrimary else DarkBorder,
                shape = MathUtils.rounded8()
            )
            .clickable { onClick(type) }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = if (active) GoldPrimary else TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AlertBadge(label: String, active: Boolean) {
    Box(
        modifier = Modifier
            .background(if (active) GreenBullish.copy(alpha = 0.15f) else DarkCarbon, MathUtils.rounded8())
            .border(1.dp, if (active) GreenBullish else DarkBorder, MathUtils.rounded8())
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = label, fontSize = 9.sp, color = if (active) GreenBullish else TextSecondary, fontWeight = FontWeight.Bold)
    }
}


// --- Screen 6: History & Performance Archives ---
@Composable
fun ArchiveHistoryScreen(
    trades: List<SavedTrade>,
    onTriggerTradeSimulation: (SavedTrade, Boolean) -> Unit,
    onDeleteTrade: (Int) -> Unit,
    onClearAll: () -> Unit
) {
    val totalCount = trades.size
    val wonCount = trades.count { it.status == "WON" }
    val successRate = if (totalCount > 0) (wonCount.toDouble() / trades.count { it.status != "PENDING" }.coerceAtLeast(1) * 100).toInt() else 0
    val totalPips = trades.sumOf { it.resultPips }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .testTag("archive_history_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Analytics Summary Grid
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCardHeader),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "لوحة قياس أداء الصفقات (SMC Analytics)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GoldPrimary)
                        if (trades.isNotEmpty()) {
                            Text(
                                text = "مسح بالكامل",
                                fontSize = 11.sp,
                                color = RedBearish,
                                modifier = Modifier
                                    .clickable { onClearAll() }
                                    .testTag("clear_history_button")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        PerformanceMeterColumn("إجمالي الصفقات", "$totalCount", TextPrimary, Modifier.weight(1f))
                        PerformanceMeterColumn("نسبة النجاح الفعلي", "$successRate%", GreenBullish, Modifier.weight(1f))
                        PerformanceMeterColumn("إجمالي نقاط الربح", "${String.format("%.1f", totalPips)} Pips", GoldPrimary, Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Text(text = "الصفقات المحفوظة ومتابعة النتائج المباشرة", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        if (trades.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(text = "المحفظة فارغة حالياً. اضغط على 'حفظ وإضافة للأرشيف' في نافذة التوصية لبدء قياس دقة التحليلات.", fontSize = 12.sp, color = TextSecondary, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            items(trades) { trade ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("trade_saved_item_${trade.id}"),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = if (trade.type == "BUY") GreenBullish.copy(alpha = 0.15f) else RedBearish.copy(alpha = 0.15f),
                                            shape = MathUtils.rounded8()
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(text = trade.type, color = if (trade.type == "BUY") GreenBullish else RedBearish, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(text = "دخول عند ${trade.entryPrice}", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                            }

                            // Dynamic state badge
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val stateBadgeColor = when(trade.status) {
                                    "WON" -> GreenBullish
                                    "LOST" -> RedBearish
                                    else -> GoldPrimary
                                }
                                val textName = when(trade.status) {
                                    "WON" -> "ناجحة ✅"
                                    "LOST" -> "خاسرة ❌"
                                    else -> "قيد المراقبة ⏳"
                                }
                                Box(
                                    modifier = Modifier
                                        .background(stateBadgeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(text = textName, color = stateBadgeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                IconButton(
                                    onClick = { onDeleteTrade(trade.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Delete", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(text = "وقف الخسارة: ${trade.stopLoss} | الهدف: ${trade.takeProfit}", fontSize = 11.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = trade.reasoning, fontSize = 11.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)

                        // If pending, allow user to trigger/simulate the outcome
                        if (trade.status == "PENDING") {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onTriggerTradeSimulation(trade, true) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("simulate_win_${trade.id}"),
                                    colors = ButtonDefaults.buttonColors(containerColor = GreenBullish),
                                    shape = MathUtils.rounded8()
                                ) {
                                    Text(text = "محاكاة ضرب الأهداف (ربح)", fontSize = 10.sp, color = DarkCarbon, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { onTriggerTradeSimulation(trade, false) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("simulate_loss_${trade.id}"),
                                    colors = ButtonDefaults.buttonColors(containerColor = RedBearish),
                                    shape = MathUtils.rounded8()
                                ) {
                                    Text(text = "محاكاة ضرب الحماية (خسارة)", fontSize = 10.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            // Display Pip outcome results
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "حصيلة الصفقة المكتسبة: ${if (trade.status == "WON") "+" else ""}${String.format("%.1f", trade.resultPips)} نقطة",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (trade.status == "WON") GreenBullish else RedBearish
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PerformanceMeterColumn(title: String, score: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, fontSize = 10.sp, color = TextSecondary, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = score, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = color, textAlign = TextAlign.Center)
    }
}
