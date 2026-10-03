package com.godzilla.app.data.repository

import com.godzilla.app.data.remote.KuCoinFuturesApi
import com.godzilla.app.data.remote.KuCoinSpotApi
import com.godzilla.app.domain.model.Candle
import com.godzilla.app.domain.model.MarketData
import com.godzilla.app.domain.model.MarketType
import com.godzilla.app.domain.model.OrderBookLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import kotlin.math.ln

class KuCoinRepository @Inject constructor(
    private val spotApi: KuCoinSpotApi,
    private val futuresApi: KuCoinFuturesApi,
    private val userPreferences: com.godzilla.app.data.local.UserPreferences
) : ExchangeRepository {

    override val exchangeName: String = "KuCoin"

    private fun formatSymbol(symbol: String, marketType: MarketType): String {
        return if (marketType == MarketType.SPOT) {
            // If it already has a hyphen, assume it's correct (e.g. BTC-USDT)
            if (symbol.contains("-")) return symbol
            
            // If it ends with USDT, format as BASE-USDT
            if (symbol.endsWith("USDT")) {
                val base = symbol.substring(0, symbol.length - 4)
                return "$base-USDT"
            }
            
            // If it looks like a forex pair (6 chars, e.g. EURUSD), try splitting in half
            if (symbol.length == 6 && !symbol.contains("-")) {
                val base = symbol.substring(0, 3)
                val quote = symbol.substring(3)
                return "$base-$quote"
            }
            
            // Fallback: just return as is or try to guess
            symbol
        } else {
            symbol // KuCoin Futures usually uses BTCUSDTM or similar, but let's stick to symbol for now
        }
    }

    override fun getMarketData(symbol: String, marketType: MarketType): Flow<MarketData> = flow {
        while (true) {
            try {
                val formatted = formatSymbol(symbol, marketType)
                val bids: List<OrderBookLevel>
                val asks: List<OrderBookLevel>

                if (marketType == MarketType.SPOT) {
                    val response = spotApi.getOrderBook(formatted)
                    bids = response.data.bids.map { OrderBookLevel(it[0].toDouble(), it[1].toDouble()) }
                    asks = response.data.asks.map { OrderBookLevel(it[0].toDouble(), it[1].toDouble()) }
                } else {
                    val response = futuresApi.getOrderBook(formatted)
                    bids = response.data.bids.map { OrderBookLevel(it[0].toDouble(), it[1].toDouble()) }
                    asks = response.data.asks.map { OrderBookLevel(it[0].toDouble(), it[1].toDouble()) }
                }

                if (bids.isNotEmpty() && asks.isNotEmpty()) {
                    emit(MarketData(symbol, bids.first().price, asks.first().price, bids, asks))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            delay(userPreferences.getPriceUpdateDelay())
        }
    }

    override suspend fun getHistoricalReturns(symbol: String, marketType: MarketType, limit: Int): List<Double> {
        val candles = getKLines(symbol, marketType, com.godzilla.app.domain.model.TimeFrame.M1, limit + 1)
        val closePrices = candles.map { it.close }
        
        val returns = mutableListOf<Double>()
        for (i in 1 until closePrices.size) {
            val pCurrent = closePrices[i]
            val pPrev = closePrices[i - 1]
            if (pPrev > 0) {
                returns.add(ln(pCurrent / pPrev))
            }
        }
        return returns
    }

    override suspend fun getKLines(symbol: String, marketType: MarketType, interval: com.godzilla.app.domain.model.TimeFrame, limit: Int): List<Candle> {
        return try {
            val formatted = formatSymbol(symbol, marketType)
            if (marketType == MarketType.SPOT) {
                // Spot: 1min, 3min, 5min, 15min, 30min, 1hour, 2hour, 4hour, 6hour, 8hour, 12hour, 1day, 1week
                val type = when (interval) {
                    com.godzilla.app.domain.model.TimeFrame.M1 -> "1min"
                    com.godzilla.app.domain.model.TimeFrame.M5 -> "5min"
                    com.godzilla.app.domain.model.TimeFrame.M15 -> "15min"
                    com.godzilla.app.domain.model.TimeFrame.M30 -> "30min"
                    com.godzilla.app.domain.model.TimeFrame.H1 -> "1hour"
                    com.godzilla.app.domain.model.TimeFrame.H4 -> "4hour"
                    com.godzilla.app.domain.model.TimeFrame.D1 -> "1day"
                }
                val response = spotApi.getKLines(formatted, type = type)
                response.data.map {
                    Candle(
                        timestamp = it[0].toLong() * 1000,
                        open = it[1].toDouble(),
                        close = it[2].toDouble(),
                        high = it[3].toDouble(),
                        low = it[4].toDouble(),
                        volume = it[5].toDouble()
                    )
                }.take(limit)
            } else {
                // Futures: granularity in minutes
                val granularity = interval.minutes
                val response = futuresApi.getKLines(formatted, granularity = granularity)
                response.data.map {
                    Candle(
                        timestamp = it[0].toLong(),
                        open = it[1].toDouble(),
                        high = it[2].toDouble(),
                        low = it[3].toDouble(),
                        close = it[4].toDouble(),
                        volume = it[5].toDouble()
                    )
                }.take(limit)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
