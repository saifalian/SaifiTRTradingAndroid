package com.godzilla.app.data.repository

import com.godzilla.app.data.remote.BitMartApi
import com.godzilla.app.domain.model.Candle
import com.godzilla.app.domain.model.MarketData
import com.godzilla.app.domain.model.MarketType
import com.godzilla.app.domain.model.OrderBookLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import kotlin.math.ln

class BitMartRepository @Inject constructor(
    private val api: BitMartApi,
    private val userPreferences: com.godzilla.app.data.local.UserPreferences
) : ExchangeRepository {

    override val exchangeName: String = "BitMart"

    private fun formatSymbol(symbol: String): String {
        // If it already has an underscore, assume it's correct
        if (symbol.contains("_")) return symbol
        
        // BTCUSDT -> BTC_USDT
        if (symbol.endsWith("USDT")) {
            val base = symbol.substring(0, symbol.length - 4)
            val quote = symbol.substring(symbol.length - 4)
            return "${base}_${quote}"
        }
        
        // Forex: EURUSD -> EUR_USD
        if (symbol.length == 6) {
            val base = symbol.substring(0, 3)
            val quote = symbol.substring(3)
            return "${base}_${quote}"
        }
        
        return symbol
    }

    override fun getMarketData(symbol: String, marketType: MarketType): Flow<MarketData> = flow {
        while (true) {
            try {
                val formatted = formatSymbol(symbol)
                
                if (marketType == MarketType.SPOT) {
                    val response = api.getSpotOrderBook(formatted)
                    if (response.code == 1000) {
                        val bids = (response.data.buys ?: emptyList()).map { 
                            OrderBookLevel(it[0].toDouble(), it[1].toDouble()) 
                        }
                        val asks = (response.data.sells ?: emptyList()).map { 
                            OrderBookLevel(it[0].toDouble(), it[1].toDouble()) 
                        }
                        
                        if (bids.isNotEmpty() && asks.isNotEmpty()) {
                            emit(MarketData(symbol, bids.first().price, asks.first().price, bids, asks))
                        }
                    }
                } else {
                    val response = api.getFuturesOrderBook(formatted)
                    if (response.code == 1000) {
                        val bids = (response.data.bids ?: emptyList()).map { 
                            OrderBookLevel(it[0].toDouble(), it[1].toDouble()) 
                        }
                        val asks = (response.data.asks ?: emptyList()).map { 
                            OrderBookLevel(it[0].toDouble(), it[1].toDouble()) 
                        }
                        
                        if (bids.isNotEmpty() && asks.isNotEmpty()) {
                            emit(MarketData(symbol, bids.first().price, asks.first().price, bids, asks))
                        }
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
            val formatted = formatSymbol(symbol)
            val step = interval.minutes
            
            if (marketType == MarketType.SPOT) {
                val response = api.getSpotKLines(formatted, step = step, limit = limit)
                if (response.code == 1000) {
                    response.data.map {
                        Candle(
                            timestamp = it.timestamp,
                            open = it.open.toDouble(),
                            high = it.high.toDouble(),
                            low = it.low.toDouble(),
                            close = it.close.toDouble(),
                            volume = it.volume.toDouble()
                        )
                    }
                } else emptyList()
            } else {
                val response = api.getFuturesKLines(formatted, step = step, limit = limit)
                if (response.code == 1000) {
                    response.data.map {
                        Candle(
                            timestamp = it.timestamp,
                            open = it.open.toDouble(),
                            high = it.high.toDouble(),
                            low = it.low.toDouble(),
                            close = it.close.toDouble(),
                            volume = it.volume.toDouble()
                        )
                    }
                } else emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
