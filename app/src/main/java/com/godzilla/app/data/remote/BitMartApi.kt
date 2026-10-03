package com.godzilla.app.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface BitMartApi {
    // Spot
    @GET("spot/quotation/v3/books")
    suspend fun getSpotOrderBook(
        @Query("symbol") symbol: String,
        @Query("limit") limit: Int = 20
    ): BitMartResponse<BitMartOrderBook>

    @GET("spot/quotation/v3/klines")
    suspend fun getSpotKLines(
        @Query("symbol") symbol: String,
        @Query("step") step: Int = 1, // 1 minute
        @Query("limit") limit: Int = 100
    ): BitMartResponse<List<BitMartKline>>

    // Futures
    @GET("contract/public/depth")
    suspend fun getFuturesOrderBook(
        @Query("symbol") symbol: String
    ): BitMartFuturesResponse<BitMartOrderBook>

    @GET("contract/public/kline")
    suspend fun getFuturesKLines(
        @Query("symbol") symbol: String,
        @Query("step") step: Int = 1,
        @Query("limit") limit: Int = 100
    ): BitMartFuturesResponse<List<BitMartKline>>
}

data class BitMartResponse<T>(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: T
)

data class BitMartFuturesResponse<T>(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: T
)

data class BitMartOrderBook(
    @SerializedName("buys") val buys: List<List<String>>? = null,
    @SerializedName("sells") val sells: List<List<String>>? = null,
    @SerializedName("bids") val bids: List<List<String>>? = null,
    @SerializedName("asks") val asks: List<List<String>>? = null
)

data class BitMartKline(
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("open") val open: String,
    @SerializedName("high") val high: String,
    @SerializedName("low") val low: String,
    @SerializedName("close") val close: String,
    @SerializedName("volume") val volume: String
)
