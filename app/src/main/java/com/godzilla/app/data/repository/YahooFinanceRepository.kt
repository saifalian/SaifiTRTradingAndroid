package com.godzilla.app.data.repository

import com.godzilla.app.data.remote.YahooFinanceApi
import com.godzilla.app.domain.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YahooFinanceRepository @Inject constructor(
    private val api: YahooFinanceApi,
    private val userPreferences: com.godzilla.app.data.local.UserPreferences
) : ExchangeRepository {

    override val exchangeName: String = "Yahoo Finance"

    override fun getMarketData(symbol: String, marketType: MarketType): Flow<MarketData> = flow {
        val yahooSymbol = formatSymbol(symbol)
        while (true) {
            try {
                println("🔍 Fetching market data for $yahooSymbol...")
                val response = api.getChartData(yahooSymbol, interval = "1m", range = "1d")
                val result = response.chart.result?.firstOrNull()
                if (result != null) {
                    val price = result.meta.regularMarketPrice
                    println("✅ Market data received for $yahooSymbol: $price")
                    // Yahoo doesn't provide a full order book via this API, so we simulate a tight spread
                    val spread = price * 0.0001 
                    val bestBid = price - (spread / 2)
                    val bestAsk = price + (spread / 2)

                    emit(MarketData(
                        symbol = symbol,
                        bestBid = bestBid,
                        bestAsk = bestAsk,
                        bids = listOf(OrderBookLevel(bestBid, 1000.0)),
                        asks = listOf(OrderBookLevel(bestAsk, 1000.0))
                    ))
                } else {
                    println("⚠️ No result in Yahoo response for $yahooSymbol")
                }
            } catch (e: Exception) {
                println("❌ Yahoo Market Data Error ($yahooSymbol): ${e.message}")
                e.printStackTrace()
            }
            delay(userPreferences.getPriceUpdateDelay()) // Poll based on user preference
        }
    }

    override suspend fun getHistoricalReturns(symbol: String, marketType: MarketType, limit: Int): List<Double> {
        val candles = getKLines(symbol, marketType, TimeFrame.M1, limit)
        return candles.zipWithNext { a, b -> (b.close - a.close) / a.close }
    }

    override suspend fun getKLines(symbol: String, marketType: MarketType, interval: TimeFrame, limit: Int): List<Candle> {
        val yahooSymbol = formatSymbol(symbol)
        val yahooInterval = when(interval) {
            TimeFrame.M1 -> "1m"
            TimeFrame.M5 -> "5m"
            TimeFrame.M15 -> "15m"
            TimeFrame.H1 -> "1h"
            TimeFrame.H4 -> "1h"
            TimeFrame.D1 -> "1d"
            else -> "1m" // Default fallback
        }
        
        val range = when(interval) {
            TimeFrame.M1 -> "1d"
            TimeFrame.M5 -> "5d"
            TimeFrame.M15 -> "5d"
            TimeFrame.H1 -> "1mo"
            TimeFrame.H4 -> "3mo"
            TimeFrame.D1 -> "1y"
            else -> "1y"
        }

        return try {
            println("🔍 Fetching candles for $yahooSymbol ($yahooInterval)...")
            val response = api.getChartData(yahooSymbol, interval = yahooInterval, range = range)
            val result = response.chart.result?.firstOrNull() ?: run {
                println("⚠️ No candle result for $yahooSymbol")
                return emptyList()
            }
            val timestamps = result.timestamp
            val quote = result.indicators.quote.firstOrNull() ?: run {
                println("⚠️ No candle quotes for $yahooSymbol")
                return emptyList()
            }

            val candles = timestamps.indices.mapNotNull { i ->
                val close = quote.close.getOrNull(i) ?: return@mapNotNull null
                val open = quote.open.getOrNull(i) ?: close
                val high = quote.high.getOrNull(i) ?: maxOf(open, close)
                val low = quote.low.getOrNull(i) ?: minOf(open, close)
                
                Candle(
                    timestamp = timestamps[i] * 1000,
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = quote.volume.getOrNull(i)?.toDouble() ?: 0.0
                )
            }.takeLast(limit)
            println("✅ Received ${candles.size} candles for $yahooSymbol")
            candles
        } catch (e: Exception) {
            println("❌ Yahoo Candle Error ($yahooSymbol): ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    private fun formatSymbol(symbol: String): String {
        // Remove slashes and dashes first
        val cleanSymbol = symbol.replace("/", "").replace("-", "").uppercase()
        
        // If it's already a Yahoo-style symbol, return it
        if (cleanSymbol.contains("=X") || cleanSymbol.startsWith("^")) return cleanSymbol
        
        // Convert "EURUSD" to "EURUSD=X" for Yahoo Forex
        // Convert "AAPL" to "AAPL" for Stocks
        // Heuristic: 6 character symbols are usually Forex pairs
        return if (cleanSymbol.length == 6) {
            "$cleanSymbol=X"
        } else {
            cleanSymbol
        }
    }
}
