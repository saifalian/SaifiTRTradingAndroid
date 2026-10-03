package com.godzilla.app.domain

import com.godzilla.app.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Singleton
class LiquidityDrawStrategy @Inject constructor() {

    data class Config(
        val ltfTimeFrame: TimeFrame = TimeFrame.M1,
        val htfTimeFrame1: TimeFrame = TimeFrame.H1,
        val htfTimeFrame2: TimeFrame = TimeFrame.H4,
        val sessionLondonStart: Int = 8, // 8 AM UTC
        val sessionLondonEnd: Int = 16, // 4 PM UTC
        val sessionAsianStart: Int = 0,  // 12 AM UTC
        val sessionAsianEnd: Int = 8,   // 8 AM UTC
        val fibLevel: Double = 0.79,
        val equilibriumLevel: Double = 0.5
    )

    data class LiquidityLevels(
        val h1High: Double?,
        val h1Low: Double?,
        val h4High: Double?,
        val h4Low: Double?,
        val londonHigh: Double?,
        val londonLow: Double?,
        val asianHigh: Double?,
        val asianLow: Double?
    )

    data class StrategyState(
        var lastHtfSweep: TradeDirection = TradeDirection.NO_TRADE,
        var lastHtfSweepPrice: Double = 0.0,
        var reversalConfirmed: Boolean = false,
        var entryFound: Boolean = false,
        var stopLoss: Double = 0.0,
        var takeProfit: Double = 0.0,
        var currentVisuals: List<VisualElement> = emptyList()
    )


    private val stateMap = mutableMapOf<String, StrategyState>()

    fun makeDecision(
        symbol: String,
        ltfCandles: List<Candle>,
        h1Candles: List<Candle>,
        h4Candles: List<Candle>,
        config: Config = Config()
    ): TradingDecision {
        val state = stateMap.getOrPut(symbol) { StrategyState() }
        val visuals = mutableListOf<VisualElement>()
        
        if (ltfCandles.isEmpty()) return TradingDecision(TradeDirection.NO_TRADE, 0.0, 0.0, "No LTF data", TradeMetrics(0.0, 0.0, 0.0, 0.0, 0.0))

        val currentPrice = ltfCandles.last().close
        val currentTime = ltfCandles.last().timestamp

        // Step 1: Identify HTF Draws on Liquidity
        val levels = calculateLiquidityLevels(h1Candles, h4Candles, ltfCandles, config)
        
        // Add visuals for levels
        levels.h1High?.let { visuals.add(VisualElement(VisualType.HORIZONTAL_LINE, it, label = "H1 High", color = "#FFA500")) }
        levels.h1Low?.let { visuals.add(VisualElement(VisualType.HORIZONTAL_LINE, it, label = "H1 Low", color = "#FFA500")) }
        levels.h4High?.let { visuals.add(VisualElement(VisualType.HORIZONTAL_LINE, it, label = "H4 High", color = "#FF4500")) }
        levels.h4Low?.let { visuals.add(VisualElement(VisualType.HORIZONTAL_LINE, it, label = "H4 Low", color = "#FF4500")) }
        levels.londonHigh?.let { visuals.add(VisualElement(VisualType.HORIZONTAL_LINE, it, label = "London High", color = "#00BFFF")) }
        levels.londonLow?.let { visuals.add(VisualElement(VisualType.HORIZONTAL_LINE, it, label = "London Low", color = "#00BFFF")) }
        levels.asianHigh?.let { visuals.add(VisualElement(VisualType.HORIZONTAL_LINE, it, label = "Asian High", color = "#9370DB")) }
        levels.asianLow?.let { visuals.add(VisualElement(VisualType.HORIZONTAL_LINE, it, label = "Asian Low", color = "#9370DB")) }

        // Step 2: Waiting for LTF Reversal
        // Check if price swept an HTF level
        val sweptLevel = checkLiquiditySweep(currentPrice, levels)
        if (sweptLevel != TradeDirection.NO_TRADE) {
            state.lastHtfSweep = sweptLevel
            state.lastHtfSweepPrice = currentPrice
            state.reversalConfirmed = false
            state.entryFound = false
        }

        var decision = TradeDirection.NO_TRADE
        var reason = "Waiting for HTF Liquidity Sweep"

        if (state.lastHtfSweep != TradeDirection.NO_TRADE) {
            reason = "HTF Sweep detected (${state.lastHtfSweep}). Waiting for LTF Reversal (BOS/IFVG/79% Fib)."
            
            // Look for reversal confluences
            val reversal = findLTFReversal(ltfCandles, state.lastHtfSweep, config)
            if (reversal.confirmed) {
                state.reversalConfirmed = true
                visuals.add(VisualElement(VisualType.MARKER, reversal.price, label = reversal.label, color = "#00FF00"))
            }
        }

        // Step 3: Finding Entry via Continuation
        if (state.reversalConfirmed) {
            reason = "Reversal confirmed. Looking for Entry (FVG/OB/Equilibrium/Breaker)."
            val entry = findEntryContinuation(ltfCandles, state.lastHtfSweep, config)
            
            if (entry.found) {
                state.entryFound = true
                decision = if (state.lastHtfSweep == TradeDirection.SHORT) TradeDirection.LONG else TradeDirection.SHORT
                state.stopLoss = entry.stopLoss
                state.takeProfit = calculateTarget(state.lastHtfSweep, levels)
                reason = "Entry found: ${entry.label}. Target: ${state.takeProfit}"
                
                entry.fvg?.let { visuals.add(VisualElement(VisualType.RECTANGLE, priceHigh = it.high, priceLow = it.low, label = "FVG", color = "#FFFF00")) }
            }
        }

        state.currentVisuals = visuals

        return TradingDecision(
            direction = decision,
            leverage = 10.0, // Default for this strategy
            positionSizePct = 100.0,
            reason = reason,
            metrics = TradeMetrics(currentPrice, 0.0, 0.0, 0.0, 0.0),
            visuals = visuals
        )

    }

    private fun calculateLiquidityLevels(
        h1Candles: List<Candle>,
        h4Candles: List<Candle>,
        ltfCandles: List<Candle>,
        config: Config
    ): LiquidityLevels {
        val h1High = h1Candles.map { it.high }.maxOrNull()
        val h1Low = h1Candles.map { it.low }.minOrNull()
        val h4High = h4Candles.map { it.high }.maxOrNull()
        val h4Low = h4Candles.map { it.low }.minOrNull()

        // Session levels (simplified: last 24h)
        // In a real app, we'd filter by UTC hours
        val londonHigh = ltfCandles.filter { isTimeInSession(it.timestamp, config.sessionLondonStart, config.sessionLondonEnd) }.map { it.high }.maxOrNull()
        val londonLow = ltfCandles.filter { isTimeInSession(it.timestamp, config.sessionLondonStart, config.sessionLondonEnd) }.map { it.low }.minOrNull()
        val asianHigh = ltfCandles.filter { isTimeInSession(it.timestamp, config.sessionAsianStart, config.sessionAsianEnd) }.map { it.high }.maxOrNull()
        val asianLow = ltfCandles.filter { isTimeInSession(it.timestamp, config.sessionAsianStart, config.sessionAsianEnd) }.map { it.low }.minOrNull()

        return LiquidityLevels(h1High, h1Low, h4High, h4Low, londonHigh, londonLow, asianHigh, asianLow)
    }

    private fun isTimeInSession(timestamp: Long, startHour: Int, endHour: Int): Boolean {
        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        calendar.timeInMillis = timestamp
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        return if (startHour < endHour) {
            hour in startHour until endHour
        } else {
            // Over midnight
            hour >= startHour || hour < endHour
        }
    }

    private fun checkLiquiditySweep(price: Double, levels: LiquidityLevels): TradeDirection {
        if (levels.h1High != null && price > levels.h1High) return TradeDirection.LONG // Swept high
        if (levels.h4High != null && price > levels.h4High) return TradeDirection.LONG
        if (levels.londonHigh != null && price > levels.londonHigh) return TradeDirection.LONG
        if (levels.asianHigh != null && price > levels.asianHigh) return TradeDirection.LONG

        if (levels.h1Low != null && price < levels.h1Low) return TradeDirection.SHORT // Swept low
        if (levels.h4Low != null && price < levels.h4Low) return TradeDirection.SHORT
        if (levels.londonLow != null && price < levels.londonLow) return TradeDirection.SHORT
        if (levels.asianLow != null && price < levels.asianLow) return TradeDirection.SHORT

        return TradeDirection.NO_TRADE
    }

    data class ReversalResult(val confirmed: Boolean, val price: Double, val label: String)

    private fun findLTFReversal(candles: List<Candle>, sweepDirection: TradeDirection, config: Config): ReversalResult {
        if (candles.size < 5) return ReversalResult(false, 0.0, "")

        val last = candles.last()
        val prev = candles[candles.size - 2]

        if (sweepDirection == TradeDirection.SHORT) { // Swept Low, looking for LONG reversal
            // 1. BOS: Close above previous high
            val recentHigh = candles.takeLast(10).dropLast(1).map { it.high }.maxOrNull() ?: 0.0
            if (last.close > recentHigh) return ReversalResult(true, last.close, "BOS (Long)")

            // 2. 79% Fib Extension (simplified)
            // 3. IFVG (simplified)
        } else if (sweepDirection == TradeDirection.LONG) { // Swept High, looking for SHORT reversal
            // 1. BOS: Close below previous low
            val recentLow = candles.takeLast(10).dropLast(1).map { it.low }.minOrNull() ?: 0.0
            if (last.close < recentLow) return ReversalResult(true, last.close, "BOS (Short)")
        }

        return ReversalResult(false, 0.0, "")
    }

    data class EntryResult(val found: Boolean, val stopLoss: Double, val label: String, val fvg: FvgRange? = null)
    data class FvgRange(val high: Double, val low: Double)

    private fun findEntryContinuation(candles: List<Candle>, sweepDirection: TradeDirection, config: Config): EntryResult {
        if (candles.size < 20) return EntryResult(false, 0.0, "")

        val last = candles.last()
        
        // Equilibrium (50% retracement of the reversal move)
        // Reversal move is from the sweep low/high to the BOS high/low
        val recentHigh = candles.takeLast(20).map { it.high }.maxOrNull() ?: 0.0
        val recentLow = candles.takeLast(20).map { it.low }.minOrNull() ?: 0.0
        val equilibrium = (recentHigh + recentLow) / 2.0

        if (sweepDirection == TradeDirection.SHORT) { // Looking for LONG entry
            // 1. FVG
            for (i in candles.size - 3 downTo 1) {
                val c1 = candles[i-1]
                val c2 = candles[i]
                val c3 = candles[i+1]
                if (c3.low > c1.high) { // Bullish FVG
                    if (last.close > c1.high && last.close < c3.low) {
                        return EntryResult(true, c1.low, "FVG Entry", FvgRange(c3.low, c1.high))
                    }
                }
            }
            
            // 2. Equilibrium: Price hits 50% retracement
            if (last.close <= equilibrium && last.close > recentLow) {
                return EntryResult(true, recentLow, "Equilibrium Entry")
            }
        } else if (sweepDirection == TradeDirection.LONG) { // Looking for SHORT entry
            // 1. FVG
            for (i in candles.size - 3 downTo 1) {
                val c1 = candles[i-1]
                val c2 = candles[i]
                val c3 = candles[i+1]
                if (c3.high < c1.low) { // Bearish FVG
                    if (last.close < c1.low && last.close > c3.high) {
                        return EntryResult(true, c1.high, "FVG Entry", FvgRange(c1.low, c3.high))
                    }
                }
            }

            // 2. Equilibrium: Price hits 50% retracement
            if (last.close >= equilibrium && last.close < recentHigh) {
                return EntryResult(true, recentHigh, "Equilibrium Entry")
            }
        }

        return EntryResult(false, 0.0, "")
    }


    private fun calculateTarget(sweepDirection: TradeDirection, levels: LiquidityLevels): Double {
        return if (sweepDirection == TradeDirection.SHORT) {
            // Target opposite HTF levels
            levels.h1High ?: levels.h4High ?: (levels.londonHigh ?: 0.0)
        } else {
            levels.h1Low ?: levels.h4Low ?: (levels.londonLow ?: 0.0)
        }
    }
    
    fun getVisuals(symbol: String): List<VisualElement> {
        return stateMap[symbol]?.currentVisuals ?: emptyList()
    }
}
