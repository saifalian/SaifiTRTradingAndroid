package com.godzilla.app.data.repository

import com.godzilla.app.data.remote.ToobitApi
import com.godzilla.app.domain.model.Candle
import com.godzilla.app.domain.model.MarketData
import com.godzilla.app.domain.model.MarketType
import com.godzilla.app.domain.model.OrderBookLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import kotlin.math.ln

class ToobitRepository @Inject constructor(
    private val api: ToobitApi,
    private val userPreferences: com.godzilla.app.data.local.UserPreferences
) : ExchangeRepository {

    override val exchangeName: String = "Toobit"

    override fun getMarketData(symbol: String, marketType: MarketType): Flow<MarketData> = flow {
        while (true) {
            try {
                val response = if (marketType == MarketType.SPOT) {
                    api.getSpotOrderBook(symbol)
                } else {
                    api.getFuturesOrderBook(symbol)
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
            val response = if (marketType == MarketType.SPOT) {
                api.getSpotKLines(symbol, interval = interval.apiParam, limit = limit)
            } else {
                api.getFuturesKLines(symbol, interval = interval.apiParam, limit = limit)
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
