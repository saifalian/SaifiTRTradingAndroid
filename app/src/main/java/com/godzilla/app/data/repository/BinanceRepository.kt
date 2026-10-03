package com.godzilla.app.data.repository

import com.godzilla.app.data.remote.BinanceFuturesApi
import com.godzilla.app.data.remote.BinanceSpotApi
import com.godzilla.app.domain.model.Candle
import com.godzilla.app.domain.model.MarketData
import com.godzilla.app.domain.model.MarketType
import com.godzilla.app.domain.model.OrderBookLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import kotlin.math.ln

class BinanceRepository @Inject constructor(
    private val spotApi: BinanceSpotApi,
    private val futuresApi: BinanceFuturesApi,
    private val userPreferences: com.godzilla.app.data.local.UserPreferences
) : ExchangeRepository {

    override val exchangeName: String = "Binance"
    
    // Cache for order book data (updated periodically)
    @Volatile private var cachedOrderBook: Pair<List<OrderBookLevel>, List<OrderBookLevel>>? = null
    @Volatile private var lastOrderBookUpdate: Long = 0L

    override fun getMarketData(symbol: String, marketType: MarketType): Flow<MarketData> = flow {
        while (true) {
            try {
                // Fast ticker for price
                val tickerResponse = if (marketType == MarketType.SPOT) {
                    spotApi.getTickerPrice(symbol)
                } else {
                    futuresApi.getTickerPrice(symbol)
                }
                
                val price = tickerResponse.price.toDoubleOrNull()
                if (price != null) {
                    val spread = price * 0.0001
                    val bestBid = price - (spread / 2)
                    val bestAsk = price + (spread / 2)
                    
                    // Update order book every 2 seconds
                    val now = System.currentTimeMillis()
                    if (now - lastOrderBookUpdate > 2000) {
                        try {
                            val response = if (marketType == MarketType.SPOT) {
                                spotApi.getOrderBook(symbol, limit = 10)
                            } else {
                                futuresApi.getOrderBook(symbol, limit = 10)
                            }
                            val bids = response.bids.map { OrderBookLevel(it[0].toDouble(), it[1].toDouble()) }
                            val asks = response.asks.map { OrderBookLevel(it[0].toDouble(), it[1].toDouble()) }
                            cachedOrderBook = Pair(bids, asks)
                            lastOrderBookUpdate = now
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
            val response = if (marketType == MarketType.SPOT) {
                spotApi.getKLines(symbol, interval = interval.apiParam, limit = limit)
            } else {
                futuresApi.getKLines(symbol, interval = interval.apiParam, limit = limit)
            }
            
            response.map {
                Candle(
                    timestamp = it[0].toString().toDouble().toLong(),
                    open = it[1].toString().toDouble(),
                    high = it[2].toString().toDouble(),
                    low = it[3].toString().toDouble(),
                    close = it[4].toString().toDouble(),
                    volume = it[5].toString().toDouble()
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
