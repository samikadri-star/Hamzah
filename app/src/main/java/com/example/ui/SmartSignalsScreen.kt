package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*

@Composable
fun SmartSignalsScreen(
    analysis: SmcAnalysisResult?,
    currentPrice: Double,
    onSaveSmartBuy: (SmartPriceZone) -> Unit,
    onSaveSmartSell: (SmartPriceZone) -> Unit,
    onSaveLegacySignal: () -> Unit
) {
    val smartRec = analysis?.smartRecommendation
    val bookmapLevels = analysis?.bookmapLevels ?: emptyList()
    val optionFlow = analysis?.optionFlow
    val futureFlow = analysis?.futureFlow

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("smart_signals_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Confluence Master Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                border = BorderStroke(1.2.dp, GoldPrimary)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(GoldPrimary.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Default.Psychology, contentDescription = "Confluence", tint = GoldPrimary, modifier = Modifier.size(24.dp))
                            }
                            Column {
                                Text(text = "محرك التوصيات المؤسساتية الذكية", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(text = "تطابق سيولة بوكماب + أوبشن فلو + فيوتشر فلو + SMC", fontSize = 11.sp, color = TextSecondary)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .background(GoldPrimary, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "تطابق ${smartRec?.overallConfluencePercent ?: 88}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkCarbon
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Primary Signal Direction Badge
                    val isBullish = smartRec?.primaryDirection?.contains("شراء") == true
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isBullish) GreenBullish.copy(alpha = 0.15f) else RedBearish.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isBullish) GreenBullish else RedBearish,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "الاتجاه المؤسساتي المرجح:",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = smartRec?.primaryDirection ?: "شراء ذكي (Smart Buy)",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isBullish) GreenBullish else RedBearish
                                )
                            }
                            Icon(
                                imageVector = if (isBullish) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                contentDescription = "Dir",
                                tint = if (isBullish) GreenBullish else RedBearish,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    if (smartRec?.executionAdviceAr != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "💡 نصيحة التنفيذ: ${smartRec.executionAdviceAr}",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // 2. Smart Buy Level Card (مستوى الشراء الذكي)
        if (smartRec?.smartBuyZone != null) {
            val buy = smartRec.smartBuyZone
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    border = BorderStroke(1.2.dp, GreenBullish.copy(alpha = 0.8f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(imageVector = Icons.Default.ArrowCircleUp, contentDescription = "Buy Level", tint = GreenBullish, modifier = Modifier.size(22.dp))
                                Text(text = "مستوى الشراء المؤسساتي الذكي (Smart Buy)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GreenBullish)
                            }
                            Text(text = "قوة ${buy.confluenceScore}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GreenBullish)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Smart Buy Price Grid
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkCarbon, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            LevelGridCell("نطاق الدخول", "${buy.priceBottom} - ${buy.priceTop}$", TextPrimary)
                            LevelGridCell("الدخول النموذجي", "${buy.idealEntry}$", GreenBullish)
                            LevelGridCell("وقف الخسارة SL", "${buy.slPrice}$", RedBearish)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Take Profit Targets
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkCarbon, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            LevelGridCell("الهدف الأول TP1", "${buy.tp1}$", GoldPrimary)
                            LevelGridCell("الهدف الثاني TP2", "${buy.tp2}$", GoldPrimary)
                            LevelGridCell("الهدف الثالث TP3", "${buy.tp3}$", GoldPrimary)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(text = "📌 السبب المؤسساتي: ${buy.reasonAr}", fontSize = 11.sp, color = TextSecondary, lineHeight = 15.sp)

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = { onSaveSmartBuy(buy) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenBullish),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.BookmarkAdd, contentDescription = "Save Buy", tint = DarkCarbon)
                                Text(text = "حفظ صفقة الشراء الذكية بالأرشيف", color = DarkCarbon, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 3. Smart Sell Level Card (مستوى البيع الذكي)
        if (smartRec?.smartSellZone != null) {
            val sell = smartRec.smartSellZone
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    border = BorderStroke(1.2.dp, RedBearish.copy(alpha = 0.8f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(imageVector = Icons.Default.ArrowCircleDown, contentDescription = "Sell Level", tint = RedBearish, modifier = Modifier.size(22.dp))
                                Text(text = "مستوى البيع المؤسساتي الذكي (Smart Sell)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RedBearish)
                            }
                            Text(text = "قوة ${sell.confluenceScore}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RedBearish)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Smart Sell Price Grid
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkCarbon, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            LevelGridCell("نطاق الدخول", "${sell.priceBottom} - ${sell.priceTop}$", TextPrimary)
                            LevelGridCell("الدخول النموذجي", "${sell.idealEntry}$", RedBearish)
                            LevelGridCell("وقف الخسارة SL", "${sell.slPrice}$", RedBearish)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Take Profit Targets
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkCarbon, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            LevelGridCell("الهدف الأول TP1", "${sell.tp1}$", GoldPrimary)
                            LevelGridCell("الهدف الثاني TP2", "${sell.tp2}$", GoldPrimary)
                            LevelGridCell("الهدف الثالث TP3", "${sell.tp3}$", GoldPrimary)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(text = "📌 السبب المؤسساتي: ${sell.reasonAr}", fontSize = 11.sp, color = TextSecondary, lineHeight = 15.sp)

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = { onSaveSmartSell(sell) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = RedBearish),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.BookmarkAdd, contentDescription = "Save Sell", tint = Color.White)
                                Text(text = "حفظ صفقة البيع الذكية بالأرشيف", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 4. Bookmap Liquidity Heatmap Radar Card
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
                            Icon(imageVector = Icons.Default.Layers, contentDescription = "Bookmap", tint = GoldPrimary, modifier = Modifier.size(20.dp))
                            Text(text = "خريطة سيولة البوكماب (Bookmap Liquidity)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Text(text = "عمق الأوامر المعلقة", fontSize = 11.sp, color = TextSecondary)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Heatmap Table
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkCarbon, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        bookmapLevels.forEach { lvl ->
                            val isBid = lvl.type == BookmapLevelType.BID_WALL
                            val isAsk = lvl.type == BookmapLevelType.ASK_WALL
                            val color = if (isBid) GreenBullish else if (isAsk) RedBearish else TextSecondary

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(color.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(color, RoundedCornerShape(50))
                                    )
                                    Text(
                                        text = if (isBid) "جدار طلب حيتان (Bid)" else if (isAsk) "جدار عرض صناديق (Ask)" else "فراغ سيولة (Void)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = color
                                    )
                                }

                                Text(
                                    text = "${lvl.price}$",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )

                                Text(
                                    text = "${lvl.lots} لوت",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = color,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = if (lvl.isAbsorbing) "امتصاص 🔄" else "نشط ⚡",
                                    fontSize = 10.sp,
                                    color = if (lvl.isAbsorbing) GoldPrimary else TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Option Flow & Gamma Exposure Analysis Card
        if (optionFlow != null) {
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
                                Icon(imageVector = Icons.Default.Analytics, contentDescription = "Option Flow", tint = Color(0xFF00D2FF), modifier = Modifier.size(20.dp))
                                Text(text = "تدفق عقود الخيارات (Option Flow & GEX)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                            Text(text = "تحليل كبار المؤسسات", fontSize = 10.sp, color = TextSecondary)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkCarbon, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            LevelGridCell("نسبة Put/Call", "${optionFlow.putCallRatio}", if (optionFlow.putCallRatio < 1.0) GreenBullish else RedBearish)
                            LevelGridCell("مستوى Max Pain", "${optionFlow.maxPainStrike}$", GoldPrimary)
                            LevelGridCell("جدار Put Wall (دعم)", "${optionFlow.majorPutWall}$", GreenBullish)
                            LevelGridCell("جدار Call Wall (مقاومة)", "${optionFlow.majorCallWall}$", RedBearish)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "📊 المزاج المؤسساتي: ${optionFlow.institutionalSentiment}", fontSize = 11.sp, color = TextPrimary)

                        Spacer(modifier = Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            optionFlow.unusualOptionActivities.forEach { act ->
                                Text(text = "• $act", fontSize = 10.sp, color = TextSecondary, lineHeight = 14.sp)
                            }
                        }
                    }
                }
            }
        }

        // 6. Future Flow & Delta Accumulation Card
        if (futureFlow != null) {
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
                                Icon(imageVector = Icons.Default.FlashOn, contentDescription = "Future Flow", tint = GoldPrimary, modifier = Modifier.size(20.dp))
                                Text(text = "الفيوتشر فلو والصفقات العدائية (Future Flow)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                            Text(text = "العقود التراكمية", fontSize = 10.sp, color = TextSecondary)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkCarbon, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            LevelGridCell("شراء عدائي Market", "${futureFlow.aggressiveBuyContracts}", GreenBullish)
                            LevelGridCell("بيع عدائي Market", "${futureFlow.aggressiveSellContracts}", RedBearish)
                            LevelGridCell("صافي الدلتا Net Δ", "${futureFlow.netDeltaContracts}", if (futureFlow.netDeltaContracts > 0) GreenBullish else RedBearish)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "⚡ الهيمنة اللحظية: ${futureFlow.institutionalDominance} • الدلتا: ${futureFlow.cumulativeDeltaTrend}", fontSize = 11.sp, color = TextPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelGridCell(title: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = title, fontSize = 9.sp, color = TextSecondary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            fontFamily = FontFamily.Monospace
        )
    }
}
