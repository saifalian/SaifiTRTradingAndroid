package com.godzilla.app.domain

import com.godzilla.app.domain.model.MarketData
import com.godzilla.app.domain.model.OrderBookLevel
import com.godzilla.app.domain.model.TradeDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TradingCalculatorTest {

    private val garchModel = GarchModel()
    private val calculator = TradingCalculator(garchModel)
    private val config = TradingCalculator.Config()

    @Test
    fun `test imbalance calculation and long decision`() {
        // Setup: Strong buy pressure
        // Bids: 100 volume, Asks: 20 volume
        // Imbalance = (100 - 20) / 120 = 80 / 120 = 0.66 > 0.3 (threshold)
        val bids = listOf(OrderBookLevel(100.0, 100.0))
        val asks = listOf(OrderBookLevel(101.0, 20.0))
        val marketData = MarketData("BTCUSDT", 100.0, 101.0, bids, asks)

        val metrics = calculator.calculateMetrics(marketData, 0.01)
        val decision = calculator.makeDecision(metrics, config)

        assertEquals(0.666, metrics.imbalance, 0.01)
        assertEquals(TradeDirection.LONG, decision.direction)
    }

    @Test
    fun `test spread filter`() {
        // Setup: High spread
        // Bid: 100, Ask: 110. Spread = 10. Mid = 105. RelSpread = 10/105 ~ 9.5% > 0.5%
        val bids = listOf(OrderBookLevel(100.0, 50.0))
        val asks = listOf(OrderBookLevel(110.0, 50.0))
        val marketData = MarketData("BTCUSDT", 100.0, 110.0, bids, asks)

        val metrics = calculator.calculateMetrics(marketData, 0.01)
        val decision = calculator.makeDecision(metrics, config)

        assertEquals(TradeDirection.NO_TRADE, decision.direction)
        assertTrue(decision.reason.contains("Spread too high"))
    }

    @Test
    fun `test volatility filter`() {
        val bids = listOf(OrderBookLevel(100.0, 100.0))
        val asks = listOf(OrderBookLevel(101.0, 20.0))
        val marketData = MarketData("BTCUSDT", 100.0, 101.0, bids, asks)

        // Volatility 10% > 5% max
        val metrics = calculator.calculateMetrics(marketData, 0.10)
        val decision = calculator.makeDecision(metrics, config)

        assertEquals(TradeDirection.NO_TRADE, decision.direction)
        assertTrue(decision.reason.contains("Volatility too high"))
    }

    @Test
    fun `test leverage calculation`() {
        val bids = listOf(OrderBookLevel(100.0, 100.0))
        val asks = listOf(OrderBookLevel(101.0, 20.0))
        val marketData = MarketData("BTCUSDT", 100.0, 101.0, bids, asks)

        // Volatility 2% (0.02). MaxLeverage = 1 + 1/0.02 = 51.
        // Capped at exchange max (20.0)
        val metrics = calculator.calculateMetrics(marketData, 0.02)
        val decision = calculator.makeDecision(metrics, config)

        assertEquals(20.0, decision.leverage, 0.01)
    }
}
