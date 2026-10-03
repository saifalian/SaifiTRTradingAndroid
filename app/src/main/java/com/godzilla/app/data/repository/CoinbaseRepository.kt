package com.godzilla.app.data.repository

import com.godzilla.app.data.remote.CoinbaseApi
import com.godzilla.app.domain.model.Candle
import com.godzilla.app.domain.model.MarketData
import com.godzilla.app.domain.model.MarketType
import com.godzilla.app.domain.model.OrderBookLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import javax.inject.Inject
import kotlin.math.ln

class CoinbaseRepository @Inject constructor(
    private val api: CoinbaseApi,
    private val userPreferences: com.godzilla.app.data.local.UserPreferences
) : ExchangeRepository {

    override val exchangeName: String = "Coinbase"

    private fun formatSymbol(symbol: String): String {
        val base = symbol.substring(0, symbol.length - 4)
        val quote = symbol.substring(symbol.length - 4)
        return "$base-$quote"
    }

    override fun getMarketData(symbol: String, marketType: MarketType): Flow<MarketData> = flow {
        while (true) {
            try {
                if (marketType == MarketType.SPOT) {
                    val formatted = formatSymbol(symbol)
                    val response = api.getOrderBook(formatted)
                    response.pricebook?.let { book ->
                        val bids = book.bids.map { OrderBookLevel(it.price.toDouble(), it.size.toDouble()) }
                        val asks = book.asks.map { OrderBookLevel(it.price.toDouble(), it.size.toDouble()) }
                        
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
            if (marketType == MarketType.SPOT) {
                val formatted = formatSymbol(symbol)
                val end = Instant.now()
                // Calculate start time based on interval minutes * limit
                val start = end.minusSeconds(limit.toLong() * interval.minutes * 60)
                // Coinbase API usually takes granularity in seconds as a parameter, but the current API interface 
                // seems to only take start/end. If the API supports granularity, we should pass it.
                // Assuming for now we just need the correct time range.
                val response = api.getKLines(formatted, start.toString(), end.toString())
                response.candles?.map {
                    Candle(
                        timestamp = Instant.parse(it.start).toEpochMilli(),
                        open = it.open.toDouble(),
                        high = it.high.toDouble(),
                        low = it.low.toDouble(),
                        close = it.close.toDouble(),
                        volume = it.volume.toDouble()
                    )
                } ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
