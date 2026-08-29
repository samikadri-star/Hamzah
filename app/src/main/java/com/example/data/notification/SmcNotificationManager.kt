package com.example.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.local.PriceZoneAlert
import com.example.model.*
import kotlin.math.abs

class SmcNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ORDER_BLOCKS = "channel_smc_order_blocks"
        const val CHANNEL_LIQUIDITY = "channel_smc_liquidity"
        const val CHANNEL_BOOKMAP = "channel_smc_bookmap"
        const val CHANNEL_ALERTS = "channel_smc_custom_alerts"

        // Cooldown period per zone in milliseconds (3 minutes)
        private const val COOLDOWN_MS = 180000L
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val triggeredCooldowns = mutableMapOf<String, Long>()

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibPattern = longArrayOf(0, 250, 150, 250)

            // 1. Order Blocks Channel
            val obChannel = NotificationChannel(
                CHANNEL_ORDER_BLOCKS,
                "مناطق الطلب والعرض (Order Blocks)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات فورية عند وصول سعر الذهب لمناطق الطلب والعرض المؤسساتية"
                enableVibration(true)
                setVibrationPattern(vibPattern)
                setShowBadge(true)
            }

            // 2. Liquidity & Sweeps Channel
            val liqChannel = NotificationChannel(
                CHANNEL_LIQUIDITY,
                "سحب السيولة وتجمعات الأوامر (Liquidity)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات عمليات سحب السيولة واختراق القمم والقيعان المؤسساتية"
                enableVibration(true)
                setVibrationPattern(vibPattern)
                setShowBadge(true)
            }

            // 3. Bookmap Limit Walls Channel
            val bookmapChannel = NotificationChannel(
                CHANNEL_BOOKMAP,
                "جدران سيولة البوكماب (Bookmap)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات اقتراب السعر من كتل أوامر البوكماب الضخمة"
                enableVibration(true)
                setVibrationPattern(vibPattern)
                setShowBadge(true)
            }

            // 4. Custom Alerts Channel
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "تنبيهات الأسعار المخصصة (Price Alerts)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات الأسعار والمستويات المستهدفة المخصصة من المستخدم"
                enableVibration(true)
                setVibrationPattern(vibPattern)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannels(listOf(obChannel, liqChannel, bookmapChannel, alertsChannel))
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    private fun getPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    /**
     * Trigger notification when spot price enters an institutional Order Block.
     */
    fun notifyOrderBlockHit(
        orderBlock: OrderBlock,
        currentPrice: Double,
        timeframe: String
    ): Boolean {
        val zoneKey = "OB_${orderBlock.id}_${orderBlock.type}"
        val now = System.currentTimeMillis()
        val lastTriggered = triggeredCooldowns[zoneKey] ?: 0L

        if (now - lastTriggered < COOLDOWN_MS) {
            return false // in cooldown
        }
        triggeredCooldowns[zoneKey] = now

        val isBullish = orderBlock.type == SmcType.BULLISH
        val zoneTypeArabic = if (isBullish) "منطقة طلب مؤسساتية (Demand OB)" else "منطقة عرض مؤسساتية (Supply OB)"
        val actionArabic = if (isBullish) "شراء محتمل (Buy Zone)" else "بيع محتمل (Sell Zone)"
        val title = "🚨 ملامسة $zoneTypeArabic!"
        val summary = "الذهب الآن عند ${String.format("%.2f", currentPrice)}$ داخل نطاق [${String.format("%.2f", orderBlock.bottom)}$ - ${String.format("%.2f", orderBlock.top)}$]"
        val bigText = """
            📍 فريم التداول: $timeframe
            🎯 نوع المنطقة: $zoneTypeArabic ($actionArabic)
            📊 النطاق السعري: ${String.format("%.2f", orderBlock.bottom)}$ — ${String.format("%.2f", orderBlock.top)}$
            ⚡ السعر الحالي: ${String.format("%.2f", currentPrice)}$
            🛡️ كسر التخفيف: ${if (orderBlock.isMitigated) "تم اختبارها مسبقاً (Mitigated)" else "منطقة عذراء طازجة (Fresh Unmitigated)"}
            💡 نصيحة: راقب شمعة الـ 5M أو كسر CHOCH لتأكيد الدخول الآمن.
        """.trimIndent()

        val notificationId = 1000 + abs(orderBlock.id.hashCode() % 1000)

        val builder = NotificationCompat.Builder(context, CHANNEL_ORDER_BLOCKS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(getPendingIntent())

        try {
            notificationManager.notify(notificationId, builder.build())
            return true
        } catch (e: SecurityException) {
            return false
        }
    }

    /**
     * Trigger notification when liquidity sweep or grab occurs.
     */
    fun notifyLiquiditySweep(
        sweep: LiquiditySweep,
        currentPrice: Double,
        timeframe: String
    ): Boolean {
        val sweepKey = "SWEEP_${sweep.type}_${String.format("%.1f", sweep.price)}"
        val now = System.currentTimeMillis()
        val lastTriggered = triggeredCooldowns[sweepKey] ?: 0L

        if (now - lastTriggered < COOLDOWN_MS) {
            return false
        }
        triggeredCooldowns[sweepKey] = now

        val isBuySide = sweep.type == SweepType.BUY_STOP
        val poolName = if (isBuySide) "سحب سيولة القمم (BSL - Buy-side Liquidity)" else "سحب سيولة القيعان (SSL - Sell-side Liquidity)"
        val title = "⚡ إنذار سحب سيولة مؤسساتي ($timeframe)"
        val summary = "${sweep.description} عند سعر ${String.format("%.2f", sweep.price)}$"
        val bigText = """
            🌊 نوع السيولة: $poolName
            💰 السعر المستهدف للسيولة: ${String.format("%.2f", sweep.price)}$
            📍 السعر الحالي: ${String.format("%.2f", currentPrice)}$
            ⏳ الفريم: $timeframe
            ⚠️ التفسير: الحيتان سحبوا الأوامر المعلقة، توقع حركة انعكاسية قوية قريباً!
        """.trimIndent()

        val notificationId = 2000 + abs(sweep.price.toInt() % 500)

        val builder = NotificationCompat.Builder(context, CHANNEL_LIQUIDITY)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(getPendingIntent())

        try {
            notificationManager.notify(notificationId, builder.build())
            return true
        } catch (e: SecurityException) {
            return false
        }
    }

    /**
     * Trigger notification when price hits a Bookmap order wall.
     */
    fun notifyBookmapWallProximity(
        wall: BookmapLiquidityLevel,
        currentPrice: Double
    ): Boolean {
        val wallKey = "WALL_${wall.type}_${String.format("%.1f", wall.price)}"
        val now = System.currentTimeMillis()
        val lastTriggered = triggeredCooldowns[wallKey] ?: 0L

        if (now - lastTriggered < COOLDOWN_MS) {
            return false
        }
        triggeredCooldowns[wallKey] = now

        val isBid = wall.type == BookmapLevelType.BID_WALL
        val title = if (isBid) "🛡️ اقتراب من جدار طلب بوكماب (${wall.lots} لوت)" else "🧱 اقتراب من جدار عرض بوكماب (${wall.lots} لوت)"
        val summary = "الذهب يقترب من مستوى ${String.format("%.2f", wall.price)}$ (أوامر معلقة ضخمة)"
        val bigText = """
            📦 نوع الجدار: ${if (isBid) "جدار طلب حيتان (Bid Limit Wall)" else "جدار عرض صناديق (Ask Limit Wall)"}
            📊 حجم السيولة المرصودة: ${wall.lots} لوت
            🎯 سعر الجدار: ${String.format("%.2f", wall.price)}$
            ⚡ السعر الحالي: ${String.format("%.2f", currentPrice)}$
            🔄 حالة الامتصاص: ${if (wall.isAbsorbing) "جاري الامتصاص النشط (Absorbing)" else "جدار صلب قيد التفاعل"}
        """.trimIndent()

        val notificationId = 3000 + abs(wall.price.toInt() % 500)

        val builder = NotificationCompat.Builder(context, CHANNEL_BOOKMAP)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(getPendingIntent())

        try {
            notificationManager.notify(notificationId, builder.build())
            return true
        } catch (e: SecurityException) {
            return false
        }
    }

    /**
     * Trigger custom price or structure alert notification.
     */
    fun notifyCustomAlert(
        alert: PriceZoneAlert,
        currentPrice: Double
    ): Boolean {
        val title = "🔔 إشعار هدف سعري: ${alert.message}"
        val summary = "سعر الذهب الفوري سجل ${String.format("%.2f", currentPrice)}$ (المستهدف: ${alert.targetPrice}$)"

        val notificationId = 4000 + alert.id

        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                """
                ⏰ تم تفعيل التنبيه المخصص بنجاح!
                📌 الشرط: ${alert.message}
                💵 السعر المستهدف: ${alert.targetPrice}$
                📈 السعر المنفذ: ${String.format("%.2f", currentPrice)}$
                ⏱️ فريم الرصد: ${alert.timeframe}
                """.trimIndent()
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(getPendingIntent())

        try {
            notificationManager.notify(notificationId, builder.build())
            return true
        } catch (e: SecurityException) {
            return false
        }
    }

    /**
     * Send instant test notification to verify audio, vibration, and channels.
     */
    fun sendTestNotification(currentPrice: Double): Boolean {
        val title = "🔔 اختبار نظام الإشعارات المؤسساتية الذكي"
        val summary = "نظام الإشعارات المحلي متصل وجاهز لرصد مناطق الأوردر بلوك والسيولة الفورية!"
        val bigText = """
            ✅ تم تفعيل قنوات التنبيه بنجاح.
            📈 سعر الذهب الفوري الحالي: ${String.format("%.2f", currentPrice)}$
            🛡️ جاهز لتنبيهك فور ملامسة مناطق الطلب (Demand) ومناطق العرض (Supply) وجدران البوكماب لحظياً!
        """.trimIndent()

        val builder = NotificationCompat.Builder(context, CHANNEL_ORDER_BLOCKS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(getPendingIntent())

        return try {
            notificationManager.notify(9999, builder.build())
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
