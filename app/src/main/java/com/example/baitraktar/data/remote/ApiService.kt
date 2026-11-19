package com.example.baitraktar.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Зеркало эндпоинтов app/api.py на сервере — см. freelance_parser README. */
interface ApiService {
    /** Без токена и не кидает исключение при 401 — нужен статус-код как есть. */
    @GET("api/health")
    suspend fun health(): Response<HealthDto>

    @GET("api/orders")
    suspend fun orders(
        @Query("stage") stage: String = "sent",
        @Query("action") action: String = "all",
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
    ): OrdersResponseDto

    @GET("api/orders/{key}")
    suspend fun order(@Path("key") key: String): OrderDto

    @POST("api/orders/{key}/action")
    suspend fun sendAction(@Path("key") key: String, @Body body: ActionRequestDto): OkResponseDto

    @GET("api/stats")
    suspend fun stats(@Query("window") window: String = "today"): StatsDto

    @GET("api/me")
    suspend fun me(): MeDto

    @POST("api/me")
    suspend fun updateMe(@Body body: MeUpdateDto): MeDto
}
