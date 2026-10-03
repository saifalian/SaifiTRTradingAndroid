package com.godzilla.app.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface BitgetApi {
    // Spot
    @GET("api/v2/spot/market/orderbook")
    suspend fun getSpotOrderBook(
        @Query("symbol") symbol: String,
        @Query("limit") limit: Int = 20
    ): BitgetResponse<BitgetOrderBook>

    @GET("api/v2/spot/market/candles")
    suspend fun getSpotKLines(
        @Query("symbol") symbol: String,
        @Query("granularity") granularity: String = "1m",
        @Query("limit") limit: Int = 100
    ): BitgetResponse<List<List<String>>>

    // Futures (Mix)
    @GET("api/v2/mix/market/orderbook")
    suspend fun getFuturesOrderBook(
        @Query("symbol") symbol: String,
        @Query("productType") productType: String = "usdt-futures",
        @Query("limit") limit: Int = 20
    ): BitgetResponse<BitgetOrderBook>

    @GET("api/v2/mix/market/candles")
    suspend fun getFuturesKLines(
        @Query("symbol") symbol: String,
        @Query("productType") productType: String = "usdt-futures",
        @Query("granularity") granularity: String = "1m",
        @Query("limit") limit: Int = 100
    ): BitgetResponse<List<List<String>>>
}

data class BitgetResponse<T>(
    @SerializedName("code") val code: String,
    @SerializedName("msg") val msg: String,
    @SerializedName("data") val data: T
)

data class BitgetOrderBook(
    @SerializedName("bids") val bids: List<List<String>>,
    @SerializedName("asks") val asks: List<List<String>>,
    @SerializedName("ts") val ts: String
)
