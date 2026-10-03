package com.godzilla.app.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface MexcSpotApi {
    @GET("api/v3/depth")
    suspend fun getOrderBook(
        @Query("symbol") symbol: String,
        @Query("limit") limit: Int = 20
    ): MexcOrderBook

    @GET("api/v3/klines")
    suspend fun getKLines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String = "1m",
        @Query("limit") limit: Int = 100
    ): List<List<Any>>
}

interface MexcFuturesApi {
    @GET("api/v1/contract/depth")
    suspend fun getOrderBook(
        @Query("symbol") symbol: String
    ): MexcFuturesResponse<MexcOrderBook>

    @GET("api/v1/contract/kline")
    suspend fun getKLines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String = "Min1"
    ): MexcFuturesResponse<List<List<Any>>>
}

data class MexcFuturesResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("code") val code: Int,
    @SerializedName("data") val data: T
)

data class MexcOrderBook(
    @SerializedName("bids") val bids: List<List<String>>,
    @SerializedName("asks") val asks: List<List<String>>
)
