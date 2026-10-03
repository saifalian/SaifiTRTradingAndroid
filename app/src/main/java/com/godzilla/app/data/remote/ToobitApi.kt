package com.godzilla.app.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface ToobitApi {
    // Spot
    @GET("quote/v1/depth")
    suspend fun getSpotOrderBook(
        @Query("symbol") symbol: String,
        @Query("limit") limit: Int = 20
    ): ToobitOrderBook

    @GET("quote/v1/klines")
    suspend fun getSpotKLines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String = "1m",
        @Query("limit") limit: Int = 100
    ): List<List<Any>>

    // Futures
    @GET("quote/v1/futures/depth")
    suspend fun getFuturesOrderBook(
        @Query("symbol") symbol: String,
        @Query("limit") limit: Int = 20
    ): ToobitOrderBook

    @GET("quote/v1/futures/klines")
    suspend fun getFuturesKLines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String = "1m",
        @Query("limit") limit: Int = 100
    ): List<List<Any>>
}

data class ToobitOrderBook(
    @SerializedName("bids") val bids: List<List<String>>,
    @SerializedName("asks") val asks: List<List<String>>,
    @SerializedName("time") val time: Long
)
