package com.godzilla.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface YahooFinanceApi {
    /**
     * Fetches real-time price and historical data for Forex and Stocks.
     * Example: EURUSD=X (Forex), AAPL (Stocks), ^GSPC (S&P 500)
     */
    @GET("v8/finance/chart/{symbol}")
    suspend fun getChartData(
        @Path("symbol") symbol: String,
        @Query("interval") interval: String = "1m",
        @Query("range") range: String = "1d",
        @Query("includePrePost") includePrePost: Boolean = true
    ): YahooChartResponse
}

data class YahooChartResponse(
    val chart: YahooChartData
)

data class YahooChartData(
    val result: List<YahooChartResult>?,
    val error: YahooChartError?
)

data class YahooChartResult(
    val meta: YahooMeta,
    val timestamp: List<Long>,
    val indicators: YahooIndicators
)

data class YahooMeta(
    val symbol: String,
    val regularMarketPrice: Double,
    val chartPreviousClose: Double,
    val priceHint: Int
)

data class YahooIndicators(
    val quote: List<YahooQuote>
)

data class YahooQuote(
    val open: List<Double?>,
    val high: List<Double?>,
    val low: List<Double?>,
    val close: List<Double?>,
    val volume: List<Long?>
)

data class YahooChartError(
    val code: String,
    val description: String
)
