package com.godzilla.app.data.repository

import com.godzilla.app.data.remote.OkxApi
import com.godzilla.app.domain.model.Candle
import com.godzilla.app.domain.model.MarketData
import com.godzilla.app.domain.model.MarketType
import com.godzilla.app.domain.model.OrderBookLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import kotlin.math.ln

class OkxRepository @Inject constructor(
    private val api: OkxApi,
    private val userPreferences: com.godzilla.app.data.local.UserPreferences
) : ExchangeRepository {

    override val exchangeName: String = "OKX"

    private fun getInstId(symbol: String, marketType: MarketType): String {
        return if (marketType == MarketType.SPOT) {
            val base = symbol.substring(0, symbol.length - 4)
            val quote = symbol.substring(symbol.length - 4)
            "$base-$quote"
        } else {
            val base = symbol.substring(0, symbol.length - 4)
            "$base-USDT-SWAP"
        }
    }

    override fun getMarketData(symbol: String, marketType: MarketType): Flow<MarketData> = flow {
        while (true) {
            try {
                val instId = getInstId(symbol, marketType)
                val response = api.getOrderBook(instId)
                if (response.code == "0" && response.data.isNotEmpty()) {
                    val data = response.data[0]
                    val bids = data.bids.map { OrderBookLevel(it[0].toDouble(), it[1].toDouble()) }
                    val asks = data.asks.map { OrderBookLevel(it[0].toDouble(), it[1].toDouble()) }
                    
                    if (bids.isNotEmpty() && asks.isNotEmpty()) {
                        emit(
                            MarketData(
                                symbol = symbol,
                                bestBid = bids.first().price,
                                bestAsk = asks.first().price,
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
            val instId = getInstId(symbol, marketType)
            // OKX uses 1m, 3m, 5m, 15m, 30m, 1H, 2H, 4H, 1D
            val bar = when (interval) {
                com.godzilla.app.domain.model.TimeFrame.H1 -> "1H"
                com.godzilla.app.domain.model.TimeFrame.H4 -> "4H"
                com.godzilla.app.domain.model.TimeFrame.D1 -> "1D"
                else -> interval.apiParam
            }
            val response = api.getKLines(instId, bar = bar, limit = limit)
            if (response.code == "0") {
                response.data.map {
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
