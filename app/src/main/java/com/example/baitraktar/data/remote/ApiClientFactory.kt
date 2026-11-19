package com.example.baitraktar.data.remote

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.Retrofit

private val json = Json {
    // Сервер вправе присылать поля, которых клиент ещё не знает (например,
    // "pending" в /api/stats) — падать из-за этого незачем.
    ignoreUnknownKeys = true
    coerceInputValues = true
    // MeUpdateDto: незаданное поле не должно уйти как null — сервер счёл бы это ошибкой.
    explicitNulls = false
}

private class AuthInterceptor(private val token: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .build()
        return chain.proceed(request)
    }
}

/** Собирает Retrofit под текущий адрес/токен сервера и кэширует его —
 * пересобирать клиент на каждый запрос незачем, а адрес меняется редко
 * (только когда пользователь правит его в Настройках). */
object ApiClientFactory {
    private var cachedKey: String? = null
    private var cachedService: ApiService? = null

    @Synchronized
    fun get(baseUrl: String, token: String): ApiService? {
        val normalized = normalizeBaseUrl(baseUrl) ?: return null
        val key = "$normalized|$token"
        cachedService?.let { if (cachedKey == key) return it }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(token))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        val service = retrofit.create(ApiService::class.java)
        cachedKey = key
        cachedService = service
        return service
    }

    /** "203.0.113.10:8766" / "http://..." / "https://.../" → всегда полный URL со слэшем в конце (Retrofit требует). */
    fun normalizeBaseUrl(raw: String): String? {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
        return "$withScheme/"
    }
}
