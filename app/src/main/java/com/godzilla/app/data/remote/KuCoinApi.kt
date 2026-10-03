package com.godzilla.app.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface KuCoinSpotApi {
    @GET("api/v1/market/orderbook/level2_20")
    suspend fun getOrderBook(
        @Query("symbol") symbol: String
    ): KuCoinResponse<KuCoinOrderBook>

    @GET("api/v1/market/candles")
    suspend fun getKLines(
        @Query("symbol") symbol: String,
        @Query("type") type: String = "1min",
        @Query("startAt") startAt: Long? = null,
        @Query("endAt") endAt: Long? = null
    ): KuCoinResponse<List<List<String>>>
}

interface KuCoinFuturesApi {
    @GET("api/v1/level2/depth20")
    suspend fun getOrderBook(
        @Query("symbol") symbol: String
    ): KuCoinResponse<KuCoinOrderBook>

    @GET("api/v1/kline/query")
    suspend fun getKLines(
        @Query("symbol") symbol: String,
        @Query("granularity") granularity: Int = 1
    ): KuCoinResponse<List<List<String>>>
}

data class KuCoinResponse<T>(
    @SerializedName("code") val code: String,
    @SerializedName("data") val data: T
)

data class KuCoinOrderBook(
    @SerializedName("bids") val bids: List<List<String>>,
    @SerializedName("asks") val asks: List<List<String>>
)
