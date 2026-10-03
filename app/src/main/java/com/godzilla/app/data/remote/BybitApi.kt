package com.godzilla.app.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface BybitApi {
    @GET("v5/market/orderbook")
    suspend fun getOrderBook(
        @Query("category") category: String = "linear",
        @Query("symbol") symbol: String,
        @Query("limit") limit: Int = 25
    ): BybitResponse<BybitOrderBook>

    @GET("v5/market/kline")
    suspend fun getKLines(
        @Query("category") category: String = "linear",
        @Query("symbol") symbol: String,
        @Query("interval") interval: String = "1",
        @Query("limit") limit: Int = 100
    ): BybitResponse<BybitKlineList>
    
    // Fast ticker endpoint
    @GET("v5/market/tickers")
    suspend fun getTickers(
        @Query("category") category: String = "linear",
        @Query("symbol") symbol: String
    ): BybitResponse<BybitTickerList>
}

data class BybitResponse<T>(
    @SerializedName("retCode") val retCode: Int,
    @SerializedName("retMsg") val retMsg: String,
    @SerializedName("result") val result: T
)

data class BybitOrderBook(
    @SerializedName("s") val symbol: String,
    @SerializedName("b") val bids: List<List<String>>,
    @SerializedName("a") val asks: List<List<String>>
)

data class BybitKlineList(
    @SerializedName("list") val list: List<List<String>>
)

data class BybitTickerList(
    @SerializedName("list") val list: List<BybitTicker>
)

data class BybitTicker(
    @SerializedName("symbol") val symbol: String,
    @SerializedName("lastPrice") val lastPrice: String,
    @SerializedName("bid1Price") val bid1Price: String,
    @SerializedName("ask1Price") val ask1Price: String
)
