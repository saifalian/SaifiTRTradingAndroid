package com.godzilla.app.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface OkxApi {
    @GET("api/v5/market/books")
    suspend fun getOrderBook(
        @Query("instId") instId: String,
        @Query("sz") sz: Int = 20
    ): OkxResponse<List<OkxOrderBook>>

    @GET("api/v5/market/candles")
    suspend fun getKLines(
        @Query("instId") instId: String,
        @Query("bar") bar: String = "1m",
        @Query("limit") limit: Int = 100
    ): OkxResponse<List<List<String>>>
}

data class OkxResponse<T>(
    @SerializedName("code") val code: String,
    @SerializedName("msg") val msg: String,
    @SerializedName("data") val data: T
)

data class OkxOrderBook(
    @SerializedName("bids") val bids: List<List<String>>,
    @SerializedName("asks") val asks: List<List<String>>,
    @SerializedName("ts") val ts: String
)
