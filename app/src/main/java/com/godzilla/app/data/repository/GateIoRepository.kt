package com.godzilla.app.data.repository

import com.godzilla.app.data.remote.GateIoApi
import com.godzilla.app.domain.model.Candle
import com.godzilla.app.domain.model.MarketData
import com.godzilla.app.domain.model.MarketType
import com.godzilla.app.domain.model.OrderBookLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import kotlin.math.ln

class GateIoRepository @Inject constructor(
    private val api: GateIoApi,
    private val userPreferences: com.godzilla.app.data.local.UserPreferences
) : ExchangeRepository {

    override val exchangeName: String = "Gate.io"

    private fun formatSymbol(symbol: String): String {
        val base = symbol.substring(0, symbol.length - 4)
        val quote = symbol.substring(symbol.length - 4)
        return "${base}_${quote}"
    }

    override fun getMarketData(symbol: String, marketType: MarketType): Flow<MarketData> = flow {
        while (true) {
            try {
                val formatted = formatSymbol(symbol)
                val response = if (marketType == MarketType.SPOT) {
                    api.getSpotOrderBook(formatted)
                } else {
                    api.getFuturesOrderBook(formatted)
                }

                val bids = response.bids.map { OrderBookLevel(it[0].toDouble(), it[1].toDouble()) }
                val asks = response.asks.map { OrderBookLevel(it[0].toDouble(), it[1].toDouble()) }
                
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
            val formatted = formatSymbol(symbol)
            if (marketType == MarketType.SPOT) {
                api.getSpotKLines(formatted, interval = interval.apiParam, limit = limit).map {
                    Candle(
                        timestamp = it[0].toLong() * 1000,
                        volume = it[1].toDouble(),
                        close = it[2].toDouble(),
                        high = it[3].toDouble(),
                        low = it[4].toDouble(),
                        open = it[5].toDouble()
                    )
                }
            } else {
                api.getFuturesKLines(formatted, interval = interval.apiParam, limit = limit).map {
                    Candle(
                        timestamp = it.time.toLong() * 1000,
                        volume = it.volume.toDouble(),
                        close = it.close.toDouble(),
                        high = it.high.toDouble(),
                        low = it.low.toDouble(),
                        open = it.open.toDouble()
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
