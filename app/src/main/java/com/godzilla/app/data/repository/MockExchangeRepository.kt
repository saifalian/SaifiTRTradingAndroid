package com.godzilla.app.data.repository

import com.godzilla.app.domain.model.Candle
import com.godzilla.app.domain.model.MarketData
import com.godzilla.app.domain.model.MarketType
import com.godzilla.app.domain.model.OrderBookLevel
import com.godzilla.app.domain.model.TimeFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class MockExchangeRepository(
    override val exchangeName: String,
    private val userPreferences: com.godzilla.app.data.local.UserPreferences,
    private val baseLatencyMs: Long = 100,
    private val priceOffsetPct: Double = 0.0 // Simulates slight price differences between exchanges
) : ExchangeRepository {

    private var currentPrice: Double = 100.0 // Default starting price
    private val random = Random(exchangeName.hashCode()) // Deterministic random for consistent behavior per exchange

    // Initialize price based on symbol hash to have different prices for different assets
    private fun initializePrice(symbol: String) {
        val hash = symbol.hashCode()
        currentPrice = when {
            symbol.contains("BTC") -> 45000.0 + (hash % 1000)
            symbol.contains("EUR") -> 1.08 + (hash % 100) / 10000.0
            symbol.contains("JPY") -> 145.0 + (hash % 100) / 10.0
            symbol.contains("AAPL") -> 180.0 + (hash % 50)
            else -> 100.0 + (hash % 50)
        }
        // Apply exchange-specific offset
        currentPrice *= (1.0 + priceOffsetPct)
    }

    override fun getMarketData(symbol: String, marketType: MarketType): Flow<MarketData> = flow {
        initializePrice(symbol)
        
        while (true) {
            // Simulate random walk
            val change = (random.nextDouble() - 0.5) * 0.001 * currentPrice // 0.1% max move
            currentPrice += change
            
            // Generate Order Book
            val spread = currentPrice * 0.0002 // 0.02% spread
            val bestBid = currentPrice - (spread / 2)
            val bestAsk = currentPrice + (spread / 2)
            
            val bids = (0..9).map { i ->
                OrderBookLevel(bestBid - (i * spread * 0.5), 1.0 + random.nextDouble() * 5)
            }
            val asks = (0..9).map { i ->
                OrderBookLevel(bestAsk + (i * spread * 0.5), 1.0 + random.nextDouble() * 5)
            }
            
            emit(MarketData(
                symbol = symbol,
                bestBid = bestBid,
                bestAsk = bestAsk,
                bids = bids,
                asks = asks
            ))
            
            delay(userPreferences.getPriceUpdateDelay()) // Use user preference delay
        }
    }

    override suspend fun getHistoricalReturns(symbol: String, marketType: MarketType, limit: Int): List<Double> {
        // Generate random returns
        return List(limit) {
            (random.nextDouble() - 0.5) * 0.01 // +/- 0.5% return
        }
    }

    override suspend fun getKLines(symbol: String, marketType: MarketType, interval: TimeFrame, limit: Int): List<Candle> {
        initializePrice(symbol)
        var tempPrice = currentPrice
        
        val candles = mutableListOf<Candle>()
        val currentTime = System.currentTimeMillis()
        val intervalMs = interval.minutes * 60 * 1000L
        
        for (i in 0 until limit) {
            val open = tempPrice
            val close = open * (1.0 + (random.nextDouble() - 0.5) * 0.005)
            val high = max(open, close) * (1.0 + random.nextDouble() * 0.002)
            val low = min(open, close) * (1.0 - random.nextDouble() * 0.002)
            val volume = 1000.0 + random.nextDouble() * 5000.0
            
            candles.add(Candle(
                timestamp = currentTime - ((limit - 1 - i) * intervalMs),
                open = open,
                high = high,
                low = low,
                close = close,
                volume = volume
            ))
            
            tempPrice = close // Next candle starts at this close
        }
        
        return candles
    }
}
