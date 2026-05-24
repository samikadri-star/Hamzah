package com.example.data.api

import com.example.BuildConfig
import com.example.model.XauCandle
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Gemini REST API Request & Response Schema ---

data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null
)

data class GeminiContent(
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String
)

data class GeminiGenerationConfig(
    val temperature: Float = 0.5f,
    val topP: Float = 0.95f,
    val maxOutputTokens: Int = 1200
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

data class GeminiCandidate(
    val content: GeminiContentResponse?
)

data class GeminiContentResponse(
    val parts: List<GeminiPartResponse>?
)

data class GeminiPartResponse(
    val text: String?
)

// --- Retrofit Endpoint Definition ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiRetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
        }
    }

// --- High-Level Generator Helper ---

object GeminiSMCGenerator {

    suspend fun generateGoldAnalysis(
        price: Double,
        timeframe: String,
        trend: String,
        state: String,
        candlesSummary: String,
        recentBOS: String
    ): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "تنبيه: مفتاح Gemini API غير مضبوط حالياً في إعدادات التطبيق. يرجى إدخال مفتاح الذكاء الاصطناعي في لوحة أسرار AI Studio للحصول على تحليلات الأسواق والأخبار المباشرة بالذكاء الاصطناعي."
        }

        val prompt = """
            حلل حركة الذهب XAU/USD الحالية بالاختصار بناءً على المعطيات التالية:
            - السعر اللحظي الحالي للذهب: ${String.format("%.2f", price)} $
            - الفريم الزمني الحالي المعتمد للتحليل: $timeframe
            - الاتجاه العام لهيكل السوق المكتشف بالخوارزمية: $trend
            - وضع السوق المؤسساتي (سلوك السيولة): $state
            - خلاصة الفريم الكلي (ملخص حركة الشموع): $candlesSummary
            - آخر هياكل السوق المخترقة (BOS/CHOCH): $recentBOS
            
            أريد تقريراً ذكياً واحترافياً وموجهاً للمتداولين بلغة عربية سلسلة واضحة وأنيقة (باستخدام نقاط منظمة):
            1. خلاصة اتجاه السوق والسيناريو الراجح (شراء أم بيع أم انتظار) مع تحديد الأهداف القادمة.
            2. تحليل الأوضاع الاقتصادية العالمية الحالية وتأثيرها المباشر على تحركات الذهب (خاصة: الفيدرالي ومعدلات التضخم CPI وبيانات العمالة NFP) بأسلوب مبسط للغاية يعتمد على المنطق البسيط ليفهمه المبتدئ والمحترف.
            3. نصيحة عملية وسريعة لإدارة المخاطر وحجم اللوت المناسب بناءً على هذه المعطيات كخبير مالي متمرس.
            
            اجعل النص منسقاً بشكل جميل ونقاط واضحة ومباشرة وتجنب الحشو الطويل.
        """.trimIndent()

        val systemPrompt = """
            أنت خبير اقتصادي ومحلل أسواق فني واقتصادي عالي المستوى في بنك استثماري عالمي (Wall Street Portfolio Manager) متخصص بالكامل في تحليل حركة الذهب (XAU/USD) ومفاهيم الأموال الذكية (SMC) والسيولة المؤسساتية.
            تتميز ردودك بالاختصار واللباقة، وقوة التحليل والدقة البالغة، وحرصك على تسهيل المفاهيم الصعبة بلغة عربية احترافية مبهرة.
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = prompt)))
            ),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt))),
            generationConfig = GeminiGenerationConfig(temperature = 0.4f)
        )

        return try {
            val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: "لم نتمكن من تلقي إجابة صحيحة من الذكاء الاصطناعي. يرجى المحاولة لاحقاً."
        } catch (e: Exception) {
            "فشل استدعاء الذكاء الاصطناعي: ${e.localizedMessage}. يرجى التحقق من اتصال الإنترنت وصلاحية مفتاح API الخاص بك."
        }
    }
}
