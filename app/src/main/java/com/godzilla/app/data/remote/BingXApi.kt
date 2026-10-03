package com.godzilla.app.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface BingXApi {
    // Spot
    @GET("openApi/spot/v1/market/depth")
    suspend fun getSpotOrderBook(
        @Query("symbol") symbol: String,
        @Query("limit") limit: Int = 20
    ): BingXResponse<BingXOrderBook>

    @GET("openApi/spot/v1/market/kline")
    suspend fun getSpotKLines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String = "1m",
        @Query("limit") limit: Int = 100
    ): BingXResponse<List<List<Any>>>

    // Swap (Futures)
    @GET("openApi/swap/v2/market/depth")
    suspend fun getFuturesOrderBook(
        @Query("symbol") symbol: String,
        @Query("limit") limit: Int = 20
    ): BingXResponse<BingXOrderBook>

    @GET("openApi/swap/v3/market/kline")
    suspend fun getFuturesKLines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String = "1m",
        @Query("limit") limit: Int = 100
    ): BingXResponse<List<List<Any>>>
}

data class BingXResponse<T>(
    @SerializedName("code") val code: Int,
    @SerializedName("msg") val msg: String,
    @SerializedName("data") val data: T
)

data class BingXOrderBook(
    @SerializedName("bids") val bids: List<List<String>>,
    @SerializedName("asks") val asks: List<List<String>>
)
