package com.godzilla.app.data.repository

import com.godzilla.app.data.remote.BybitApi
import com.godzilla.app.domain.model.Candle
import com.godzilla.app.domain.model.MarketData
import com.godzilla.app.domain.model.MarketType
import com.godzilla.app.domain.model.OrderBookLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import kotlin.math.ln

class BybitRepository @Inject constructor(
    private val api: BybitApi,
    private val userPreferences: com.godzilla.app.data.local.UserPreferences
) : ExchangeRepository {

    override val exchangeName: String = "Bybit"

    private fun getCategory(marketType: MarketType) = if (marketType == MarketType.SPOT) "spot" else "linear"

    // Cache for order book data (updated periodically)
    @Volatile private var cachedOrderBook: Pair<List<OrderBookLevel>, List<OrderBookLevel>>? = null
    @Volatile private var lastOrderBookUpdate: Long = 0L

    override fun getMarketData(symbol: String, marketType: MarketType): Flow<MarketData> = flow {
        while (true) {
            try {
                // Fast ticker for price
                val response = api.getTickers(
                    category = getCategory(marketType),
                    symbol = symbol
                )
                
                if (response.retCode == 0 && response.result.list.isNotEmpty()) {
                    val ticker = response.result.list.first()
                    val bestBid = ticker.bid1Price.toDoubleOrNull()
                    val bestAsk = ticker.ask1Price.toDoubleOrNull()
                    
                    if (bestBid != null && bestAsk != null) {
                        // Update order book every 2 seconds
                        val now = System.currentTimeMillis()
                        if (now - lastOrderBookUpdate > 2000) {
                            try {
                                val orderBookResponse = api.getOrderBook(
                                    category = getCategory(marketType),
                                    symbol = symbol,
                                    limit = 10
                                )
                                if (orderBookResponse.retCode == 0) {
                                    val bids = orderBookResponse.result.bids.map { OrderBookLevel(it[0].toDouble(), it[1].toDouble()) }
                                    val asks = orderBookResponse.result.asks.map { OrderBookLevel(it[0].toDouble(), it[1].toDouble()) }
                                    cachedOrderBook = Pair(bids, asks)
                                    lastOrderBookUpdate = now
                                }
                            } catch (_: Exception) {}
                        }
                        
                        val (bids, asks) = cachedOrderBook ?: Pair(
                            listOf(OrderBookLevel(bestBid, 1.0)),
                            listOf(OrderBookLevel(bestAsk, 1.0))
                        )
                        
                        emit(
                            MarketData(
                                symbol = symbol,
                                bestBid = bestBid,
                                bestAsk = bestAsk,
                                bids = bids,
                                asks = asks
                            )
                        )
                    }
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
            // Bybit uses 1, 3, 5, 15, 30, 60, 120, 240, D, W, M
            val bybitInterval = when (interval) {
                com.godzilla.app.domain.model.TimeFrame.M1 -> "1"
                com.godzilla.app.domain.model.TimeFrame.M5 -> "5"
                com.godzilla.app.domain.model.TimeFrame.M15 -> "15"
                com.godzilla.app.domain.model.TimeFrame.M30 -> "30"
                com.godzilla.app.domain.model.TimeFrame.H1 -> "60"
                com.godzilla.app.domain.model.TimeFrame.H4 -> "240"
                com.godzilla.app.domain.model.TimeFrame.D1 -> "D"
            }
            val response = api.getKLines(
                category = getCategory(marketType),
                symbol = symbol,
                interval = bybitInterval,
                limit = limit
            )
            if (response.retCode == 0) {
                response.result.list.map {
                    Candle(
                        timestamp = it[0].toLong(),
                        open = it[1].toDouble(),
                        high = it[2].toDouble(),
                        low = it[3].toDouble(),
                        close = it[4].toDouble(),
                        volume = it[5].toDouble()
                    )
                }
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
