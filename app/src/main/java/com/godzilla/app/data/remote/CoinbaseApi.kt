package com.godzilla.app.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CoinbaseApi {
    @GET("api/v3/brokerage/products/{product_id}/book")
    suspend fun getOrderBook(
        @Path("product_id") productId: String,
        @Query("limit") limit: Int = 20
    ): CoinbaseResponse<CoinbaseOrderBook>

    @GET("api/v3/brokerage/products/{product_id}/candles")
    suspend fun getKLines(
        @Path("product_id") productId: String,
        @Query("start") start: String,
        @Query("end") end: String,
        @Query("granularity") granularity: String = "ONE_MINUTE"
    ): CoinbaseResponse<List<CoinbaseCandle>>
}

data class CoinbaseResponse<T>(
    @SerializedName("pricebook") val pricebook: T? = null,
    @SerializedName("candles") val candles: T? = null
)

data class CoinbaseOrderBook(
    @SerializedName("product_id") val productId: String,
    @SerializedName("bids") val bids: List<CoinbaseLevel>,
    @SerializedName("asks") val asks: List<CoinbaseLevel>
)

data class CoinbaseLevel(
    @SerializedName("price") val price: String,
    @SerializedName("size") val size: String
)

data class CoinbaseCandle(
    @SerializedName("start") val start: String,
    @SerializedName("low") val low: String,
    @SerializedName("high") val high: String,
    @SerializedName("open") val open: String,
    @SerializedName("close") val close: String,
    @SerializedName("volume") val volume: String
)
