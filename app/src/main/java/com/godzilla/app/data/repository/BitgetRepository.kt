package com.godzilla.app.data.repository

import com.godzilla.app.data.remote.BitgetApi
import com.godzilla.app.domain.model.Candle
import com.godzilla.app.domain.model.MarketData
import com.godzilla.app.domain.model.MarketType
import com.godzilla.app.domain.model.OrderBookLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import kotlin.math.ln

class BitgetRepository @Inject constructor(
    private val api: BitgetApi,
    private val userPreferences: com.godzilla.app.data.local.UserPreferences
) : ExchangeRepository {

    override val exchangeName: String = "Bitget"

    override fun getMarketData(symbol: String, marketType: MarketType): Flow<MarketData> = flow {
        while (true) {
            try {
                val response = if (marketType == MarketType.SPOT) {
                    api.getSpotOrderBook(symbol)
                } else {
                    api.getFuturesOrderBook(symbol)
                }

                if (response.code == "00000") {
                    val bids = response.data.bids.map { OrderBookLevel(it[0].toDouble(), it[1].toDouble()) }
                    val asks = response.data.asks.map { OrderBookLevel(it[0].toDouble(), it[1].toDouble()) }
                    
                    if (bids.isNotEmpty() && asks.isNotEmpty()) {
                        emit(MarketData(symbol, bids.first().price, asks.first().price, bids, asks))
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
            val response = if (marketType == MarketType.SPOT) {
                api.getSpotKLines(symbol, granularity = interval.apiParam, limit = limit)
            } else {
                api.getFuturesKLines(symbol, granularity = interval.apiParam, limit = limit)
            }

            if (response.code == "00000") {
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
