package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FootprintCandle
import com.example.model.FootprintLevel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun FootprintScreen(
    footprintCandles: List<FootprintCandle>,
    currentPrice: Double,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    var isDetailedMatrix by remember { mutableStateOf(true) }
    var zoomLevel by remember { mutableStateOf(1.0f) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("footprint_terminal_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Header Banner: 5M Footprint & Delta Terminal
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(GoldPrimary.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridOn,
                                    contentDescription = "Footprint",
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "شارت الفوت برنت والدلتا (Footprint 5M)",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(GoldPrimary, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(text = "5M فريم", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = DarkCarbon)
                                    }
                                }
                                Text(
                                    text = "توزيع أحجام العقود الشرائية والبيعية لحظياً مع تحديد نقطة التحكم POC والدلتا التراكمية",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = onRefresh,
                            enabled = !isLoading,
                            modifier = Modifier
                                .background(DarkBorder, RoundedCornerShape(8.dp))
                                .size(36.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = GoldPrimary, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = GoldPrimary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Footprint Quick Stats Row
                    val latest = footprintCandles.lastOrNull()
                    val totalVol = latest?.totalVolume ?: 0
                    val delta = latest?.totalDelta ?: 0
                    val cvd = latest?.cumulativeDelta ?: 0
                    val poc = latest?.pocPrice ?: currentPrice

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkCarbon, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        FootprintStatItem("حجم الشمعة 5M", "$totalVol لوت", TextPrimary)
                        FootprintStatItem("الدلتا اللحظية", "${if (delta > 0) "+$delta" else "$delta"}", if (delta >= 0) GreenBullish else RedBearish)
                        FootprintStatItem("نقطة التحكم POC", "$poc$", GoldPrimary)
                        FootprintStatItem("CVD التراكمي", "${if (cvd > 0) "+$cvd" else "$cvd"}", if (cvd >= 0) GreenBullish else RedBearish)
                    }
                }
            }
        }

        // 2. Control Toolbar: Matrix vs Profile Mode + Zoom
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Toggle mode
                Row(
                    modifier = Modifier
                        .background(DarkCard, RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = isDetailedMatrix,
                        onClick = { isDetailedMatrix = true },
                        label = { Text("مصفوفة Bid x Ask", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GoldPrimary,
                            selectedLabelColor = DarkCarbon,
                            containerColor = DarkCard,
                            labelColor = TextSecondary
                        )
                    )
                    FilterChip(
                        selected = !isDetailedMatrix,
                        onClick = { isDetailedMatrix = false },
                        label = { Text("بروفايل الدلتا Compact", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GoldPrimary,
                            selectedLabelColor = DarkCarbon,
                            containerColor = DarkCard,
                            labelColor = TextSecondary
                        )
                    )
                }

                // Zoom controls
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { zoomLevel = (zoomLevel - 0.2f).coerceAtLeast(0.6f) },
                        modifier = Modifier
                            .size(32.dp)
                            .background(DarkCard, RoundedCornerShape(6.dp))
                    ) {
                        Icon(imageVector = Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = TextPrimary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { zoomLevel = (zoomLevel + 0.2f).coerceAtMost(2.0f) },
                        modifier = Modifier
                            .size(32.dp)
                            .background(DarkCard, RoundedCornerShape(6.dp))
                    ) {
                        Icon(imageVector = Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = TextPrimary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // 3. Interactive Horizontal Footprint Candles Canvas / Scroll
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCarbon),
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                if (footprintCandles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GoldPrimary)
                    }
                } else {
                    val scrollState = rememberScrollState()
                    LaunchedEffect(footprintCandles.size) {
                        scrollState.scrollTo(scrollState.maxValue)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(scrollState)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        footprintCandles.forEach { fpCandle ->
                            FootprintCandleColumn(
                                candle = fpCandle,
                                isDetailed = isDetailedMatrix,
                                zoom = zoomLevel
                            )
                        }
                    }
                }
            }
        }

        // 4. Cumulative Volume Delta (CVD) Chart Panel
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(imageVector = Icons.Default.ShowChart, contentDescription = "CVD", tint = GreenBullish, modifier = Modifier.size(18.dp))
                            Text(text = "مؤشر الدلتا التراكمية (Cumulative Volume Delta - CVD)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Text(text = "فريم 5 دقائق", fontSize = 10.sp, color = TextSecondary)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // CVD Mini Canvas
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .background(DarkCarbon, RoundedCornerShape(6.dp))
                            .padding(6.dp)
                    ) {
                        if (footprintCandles.isEmpty()) return@Canvas
                        val w = size.width
                        val h = size.height
                        val midY = h / 2f

                        // Draw Zero line
                        drawLine(
                            color = Color(0xFF333D4F),
                            start = Offset(0f, midY),
                            end = Offset(w, midY),
                            strokeWidth = 1f
                        )

                        val maxCvd = footprintCandles.maxOfOrNull { abs(it.cumulativeDelta) }?.coerceAtLeast(100) ?: 100
                        val stepX = w / max(1, footprintCandles.size - 1)

                        var prevPoint: Offset? = null
                        footprintCandles.forEachIndexed { i, c ->
                            val x = i * stepX
                            val normalized = (c.cumulativeDelta.toFloat() / maxCvd.toFloat()).coerceIn(-1f, 1f)
                            val y = midY - (normalized * (midY * 0.85f))

                            // Draw delta bar
                            val barColor = if (c.totalDelta >= 0) GreenBullish else RedBearish
                            val barH = (abs(c.totalDelta).toFloat() / maxCvd.toFloat() * (midY * 0.7f)).coerceAtLeast(2f)
                            val barTopY = if (c.totalDelta >= 0) midY - barH else midY
                            drawRect(
                                color = barColor.copy(alpha = 0.35f),
                                topLeft = Offset(x - 2f, barTopY),
                                size = Size(4f, barH)
                            )

                            // Draw CVD Line
                            val pt = Offset(x, y)
                            if (prevPoint != null) {
                                drawLine(
                                    color = GoldPrimary,
                                    start = prevPoint!!,
                                    end = pt,
                                    strokeWidth = 2.5f
                                )
                            }
                            prevPoint = pt
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "💡 عندما ترتفع خطوط CVD مع قيعان سعرية صاعدة يؤكد ذلك استمرار ضخ السيولة المؤسساتية (Bullish Confluence).",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // 5. Educational Footprint Legend Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "دليل قراءة شارت الفوت برنت (Legend):", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    FootprintLegendRow(color = GoldPrimary, label = "POC (Point of Control)", desc = "أعلى مستوى سعري تم تنفيذ عقود لوت عليه داخل شمعة الـ 5 دقائق.")
                    FootprintLegendRow(color = GreenBullish, label = "خلل شراء Imbalance (Ask > 3x Bid)", desc = "شراء هجومي مؤسساتي عالي القوة ينبئ باستمرار الصعود.")
                    FootprintLegendRow(color = RedBearish, label = "خلل بيع Imbalance (Bid > 3x Ask)", desc = "ضغط بيعي عدائي تصريفي ينبئ بهبوط قادم.")
                    FootprintLegendRow(color = Color(0xFF00D2FF), label = "Value Area (VAH / VAL)", desc = "نطاق الـ 70% من إجمالي سيولة التداول للشمعة.")
                }
            }
        }
    }
}

@Composable
private fun FootprintCandleColumn(
    candle: FootprintCandle,
    isDetailed: Boolean,
    zoom: Float
) {
    val colWidth = (if (isDetailed) 110.dp else 75.dp) * zoom
    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(candle.candle.timestamp))

    Column(
        modifier = Modifier
            .width(colWidth)
            .fillMaxHeight()
            .background(DarkCard, RoundedCornerShape(6.dp))
            .border(1.dp, if (candle.candle.isBullish) GreenBullish.copy(alpha = 0.3f) else RedBearish.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Candle Header (Time & OHLC)
        Text(
            text = timeStr,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (candle.candle.isBullish) GreenBullish else RedBearish
        )
        Text(
            text = "${String.format("%.1f", candle.candle.close)}$",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = TextPrimary
        )

        Divider(color = DarkBorder, modifier = Modifier.padding(vertical = 4.dp))

        // Footprint Stack (Cluster Levels)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(candle.levels) { level ->
                FootprintLevelRow(level = level, isDetailed = isDetailed, pocPrice = candle.pocPrice)
            }
        }

        Divider(color = DarkBorder, modifier = Modifier.padding(vertical = 4.dp))

        // Candle Delta Footer
        val delta = candle.totalDelta
        val isPos = delta >= 0
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isPos) GreenBullish.copy(alpha = 0.2f) else RedBearish.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Δ ${if (isPos) "+$delta" else "$delta"}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPos) GreenBullish else RedBearish,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            text = "حجم: ${candle.totalVolume}",
            fontSize = 9.sp,
            color = TextSecondary,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun FootprintLevelRow(
    level: FootprintLevel,
    isDetailed: Boolean,
    pocPrice: Double
) {
    val isPoc = level.isPoc
    val bgColor = when {
        isPoc -> GoldPrimary.copy(alpha = 0.25f)
        level.isBuyImbalance -> GreenBullish.copy(alpha = 0.22f)
        level.isSellImbalance -> RedBearish.copy(alpha = 0.22f)
        else -> Color.Transparent
    }

    val borderStroke = when {
        isPoc -> BorderStroke(1.dp, GoldPrimary)
        level.isBuyImbalance -> BorderStroke(0.8.dp, GreenBullish)
        level.isSellImbalance -> BorderStroke(0.8.dp, RedBearish)
        else -> null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(3.dp))
            .then(if (borderStroke != null) Modifier.border(borderStroke.width, borderStroke.brush, RoundedCornerShape(3.dp)) else Modifier)
            .padding(horizontal = 2.dp, vertical = 2.dp)
    ) {
        if (isDetailed) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bid volume (Sellers executing on bid)
                Text(
                    text = "${level.bidVolume}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (level.isSellImbalance) RedBearish else TextSecondary,
                    fontWeight = if (level.isSellImbalance || isPoc) FontWeight.Bold else FontWeight.Normal
                )

                // Price
                Text(
                    text = String.format("%.1f", level.price),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (isPoc) GoldPrimary else TextPrimary,
                    fontWeight = if (isPoc) FontWeight.Bold else FontWeight.Normal
                )

                // Ask volume (Buyers executing on ask)
                Text(
                    text = "${level.askVolume}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (level.isBuyImbalance) GreenBullish else TextSecondary,
                    fontWeight = if (level.isBuyImbalance || isPoc) FontWeight.Bold else FontWeight.Normal
                )
            }
        } else {
            // Compact Delta Mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format("%.1f", level.price),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (isPoc) GoldPrimary else TextPrimary
                )
                val d = level.delta
                Text(
                    text = if (d > 0) "+$d" else "$d",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (d >= 0) GreenBullish else RedBearish,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FootprintStatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 10.sp, color = TextSecondary)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun FootprintLegendRow(color: Color, label: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(10.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Column {
            Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
            Text(text = desc, fontSize = 10.sp, color = TextSecondary, lineHeight = 14.sp)
        }
    }
}
