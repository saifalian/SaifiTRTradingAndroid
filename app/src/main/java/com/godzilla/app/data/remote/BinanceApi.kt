package com.godzilla.app.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface BinanceSpotApi {
    @GET("api/v3/depth")
    suspend fun getOrderBook(
        @Query("symbol") symbol: String,
        @Query("limit") limit: Int = 20
    ): BinanceOrderBookResponse

    @GET("api/v3/klines")
    suspend fun getKLines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String = "1m",
        @Query("limit") limit: Int = 100
    ): List<List<Any>>
    
    // Fast ticker endpoint - returns just price info, much faster than order book
    @GET("api/v3/ticker/price")
    suspend fun getTickerPrice(
        @Query("symbol") symbol: String
    ): BinanceTickerResponse
}

interface BinanceFuturesApi {
    @GET("fapi/v1/depth")
    suspend fun getOrderBook(
        @Query("symbol") symbol: String,
        @Query("limit") limit: Int = 20
    ): BinanceOrderBookResponse

    @GET("fapi/v1/klines")
    suspend fun getKLines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String = "1m",
        @Query("limit") limit: Int = 100
    ): List<List<Any>>
    
    // Fast ticker endpoint for futures
    @GET("fapi/v1/ticker/price")
    suspend fun getTickerPrice(
        @Query("symbol") symbol: String
    ): BinanceTickerResponse
}

data class BinanceTickerResponse(
    @SerializedName("symbol") val symbol: String,
    @SerializedName("price") val price: String
)

data class BinanceOrderBookResponse(
    @SerializedName("lastUpdateId") val lastUpdateId: Long,
    @SerializedName("bids") val bids: List<List<String>>,
    @SerializedName("asks") val asks: List<List<String>>
)
