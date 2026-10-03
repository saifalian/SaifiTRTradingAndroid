package com.godzilla.app.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface GateIoApi {
    // Spot
    @GET("api/v4/spot/order_book")
    suspend fun getSpotOrderBook(
        @Query("currency_pair") currencyPair: String,
        @Query("limit") limit: Int = 20
    ): GateIoOrderBook

    @GET("api/v4/spot/candlesticks")
    suspend fun getSpotKLines(
        @Query("currency_pair") currencyPair: String,
        @Query("interval") interval: String = "1m",
        @Query("limit") limit: Int = 100
    ): List<List<String>>

    // Futures
    @GET("api/v4/futures/usdt/order_book")
    suspend fun getFuturesOrderBook(
        @Query("contract") contract: String,
        @Query("limit") limit: Int = 20
    ): GateIoOrderBook

    @GET("api/v4/futures/usdt/candlesticks")
    suspend fun getFuturesKLines(
        @Query("contract") contract: String,
        @Query("interval") interval: String = "1m",
        @Query("limit") limit: Int = 100
    ): List<GateIoCandle>
}

data class GateIoOrderBook(
    @SerializedName("bids") val bids: List<List<String>>,
    @SerializedName("asks") val asks: List<List<String>>,
    @SerializedName("current") val current: Long? = null
)

data class GateIoCandle(
    @SerializedName("t") val time: String,
    @SerializedName("v") val volume: String,
    @SerializedName("c") val close: String,
    @SerializedName("h") val high: String,
    @SerializedName("l") val low: String,
    @SerializedName("o") val open: String
)
