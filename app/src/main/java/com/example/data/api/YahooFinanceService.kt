package com.example.data.api

import android.util.Log
import com.example.model.XauCandle
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object YahooFinanceService {
    private const val TAG = "YahooFinanceService"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches historical candles for Gold Spot (GC=F on Yahoo Finance) for the specified timeframe.
     */
    fun fetchGoldCandles(timeframe: String): List<XauCandle> {
        val interval: String
        val range: String
        var aggregateFour = false

        when (timeframe) {
            "1m" -> {
                interval = "1m"
                range = "1d"
            }
            "5m" -> {
                interval = "5m"
                range = "2d"
            }
            "15m" -> {
                interval = "15m"
                range = "5d"
            }
            "1H" -> {
                interval = "1h"
                range = "30d"
            }
            "4H" -> {
                interval = "1h"
                range = "60d" // Fetch 1h and aggregate into 4h
                aggregateFour = true
            }
            "Daily", "D" -> {
                interval = "1d"
                range = "1y"
            }
            else -> {
                interval = "15m"
                range = "5d"
            }
        }

        // We target GC=F which is the COMEX Gold Futures contract (highly reactive live proxy for XAU/USD)
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/GC=F?interval=$interval&range=$range&includePrePost=false"
        Log.d(TAG, "Fetching gold candles from Yahoo Finance: $url")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to fetch Yahoo Finance: code ${response.code}")
                    return emptyList()
                }

                val bodyString = response.body?.string() ?: return emptyList()
                val candles = parseYahooFinanceJson(bodyString)

                if (candles.isEmpty()) {
                    Log.w(TAG, "Parsed candles are empty")
                    return emptyList()
                }

                if (aggregateFour) {
                    return aggregateCandles(candles, 4)
                }

                // Make sure to return max 50-100 items for responsive smart indicators UI
                return if (candles.size > 80) candles.takeLast(80) else candles
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching gold candles from Yahoo Finance", e)
            return emptyList()
        }
    }

    private fun parseYahooFinanceJson(jsonStr: String): List<XauCandle> {
        val list = mutableListOf<XauCandle>()
        try {
            val root = JSONObject(jsonStr)
            val chart = root.optJSONObject("chart") ?: return emptyList()
            val resultArr = chart.optJSONArray("result") ?: return emptyList()
            if (resultArr.length() == 0) return emptyList()
            
            val result = resultArr.getJSONObject(0)
            val timestampArr = result.optJSONArray("timestamp") ?: return emptyList()
            val indicators = result.optJSONObject("indicators") ?: return emptyList()
            val quoteArr = indicators.optJSONArray("quote") ?: return emptyList()
            if (quoteArr.length() == 0) return emptyList()
            
            val quote = quoteArr.getJSONObject(0)
            val openArr = quote.optJSONArray("open")
            val highArr = quote.optJSONArray("high")
            val lowArr = quote.optJSONArray("low")
            val closeArr = quote.optJSONArray("close")
            val volumeArr = quote.optJSONArray("volume")

            if (openArr == null || highArr == null || lowArr == null || closeArr == null) {
                return emptyList()
            }

            var lastValidPrice = 2342.0 // Fallback base gold price
            val len = timestampArr.length()

            for (i in 0 until len) {
                // Safeguard against missing/null value items
                if (openArr.isNull(i) || highArr.isNull(i) || lowArr.isNull(i) || closeArr.isNull(i)) {
                    continue
                }

                val o = openArr.optDouble(i, lastValidPrice)
                val h = highArr.optDouble(i, lastValidPrice)
                val l = lowArr.optDouble(i, lastValidPrice)
                val c = closeArr.optDouble(i, lastValidPrice)
                
                // Timestamp in Yahoo Finance is in seconds, converting to milliseconds
                val timestampSec = timestampArr.optLong(i, 0L)
                if (timestampSec == 0L) continue
                val timestampMs = timestampSec * 1000L

                val v = if (volumeArr != null && !volumeArr.isNull(i)) {
                    volumeArr.optDouble(i, 500.0)
                } else {
                    500.0
                }

                lastValidPrice = c // Update fallback

                list.add(
                    XauCandle(
                        id = i,
                        timestamp = timestampMs,
                        open = o,
                        high = h,
                        low = l,
                        close = c,
                        volume = v
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Yahoo Finance response JSON", e)
        }
        return list
    }

    /**
     * Groups a list of candles by factor. E.g. merges 4 H1 candles into single 4H candle.
     */
    private fun aggregateCandles(source: List<XauCandle>, factor: Int): List<XauCandle> {
        if (source.isEmpty() || factor <= 1) return source
        val aggregated = mutableListOf<XauCandle>()
        
        var counter = 0
        var open = 0.0
        var high = Double.MIN_VALUE
        var low = Double.MAX_VALUE
        var volume = 0.0
        var timestamp = 0L

        for (candle in source) {
            if (counter == 0) {
                open = candle.open
                high = candle.high
                low = candle.low
                timestamp = candle.timestamp
                volume = 0.0
            } else {
                high = maxOf(high, candle.high)
                low = minOf(low, candle.low)
            }
            volume += candle.volume
            counter++

            if (counter == factor) {
                aggregated.add(
                    XauCandle(
                        id = aggregated.size,
                        timestamp = timestamp,
                        open = open,
                        high = high,
                        low = low,
                        close = candle.close,
                        volume = volume
                    )
                )
                counter = 0
            }
        }

        // Handle leftovers
        if (counter > 0) {
            aggregated.add(
                XauCandle(
                    id = aggregated.size,
                    timestamp = timestamp,
                    open = open,
                    high = high,
                    low = low,
                    close = source.last().close,
                    volume = volume
                )
            )
        }

        return aggregated
    }
}
