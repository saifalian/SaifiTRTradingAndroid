package com.godzilla.app.domain

import com.godzilla.app.domain.model.MarketData
import com.godzilla.app.domain.model.TradeDirection
import com.godzilla.app.domain.model.TradeMetrics
import com.godzilla.app.domain.model.TradingDecision
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sqrt

@Singleton
class StableOrderBookStrategy @Inject constructor(
    private val garchModel: GarchModel
) {

    enum class LeverageMode(val factor: Double) {
        CONSERVATIVE(0.5),
        BALANCED(1.0),
        AGGRESSIVE(2.0)
    }

    data class Config(
        // Core Strategy
        val orderBookDepth: Int = 20,
        val imbalanceEnterThreshold: Double = 0.3,
        val imbalanceExitThreshold: Double = 0.1,
        val smoothingPeriod: Int = 5,
        val confirmationTimeSeconds: Long = 5,
        val cooldownTimeSeconds: Long = 30,
        val trendEmaLength: Int = 50,
        val maxRelativeSpread: Double = 0.005,
        val maxAllowedVolatility: Double = 0.05,
        val minConfidence: Double = 70.0,

        // Leverage & Risk Control
        val maxExchangeLeverage: Double = 20.0,
        val maxUserLeverageCap: Double = 10.0,
        val riskPerTrade: Double = 0.02, // 2%
        val volatilityRiskMultiplier: Double = 2.0, // k_v
        val leverageMode: LeverageMode = LeverageMode.BALANCED,
        val manualLeverageOverride: Boolean = false,
        val manualLeverageValue: Double = 1.0
    )

    data class State(
        var smoothedImbalance: Double = 0.0,
        var lastPrice: Double = 0.0,
        var lastVolatility: Double = 0.001,
        var trendEma: Double = 0.0,
        
        var lastTradeTime: Long = 0,
        var potentialSignalDirection: TradeDirection = TradeDirection.NO_TRADE,
        var signalStartTime: Long = 0,
        
        var currentDirection: TradeDirection = TradeDirection.NO_TRADE
    )

    private val stateMap = mutableMapOf<String, State>()

    fun makeDecision(marketData: MarketData, config: Config): TradingDecision {
        val state = stateMap.getOrPut(marketData.symbol) { State() }
        val currentTime = System.currentTimeMillis()
        val decisionReason = StringBuilder()

        // --- CORE CALCULATIONS ---
        val midPrice = (marketData.bestBid + marketData.bestAsk) / 2.0
        val spread = marketData.bestAsk - marketData.bestBid
        val relativeSpread = if (midPrice > 0) spread / midPrice else 0.0

        val depth = min(config.orderBookDepth, min(marketData.bids.size, marketData.asks.size))
        val volBid = marketData.bids.take(depth).sumOf { it.volume }
        val volAsk = marketData.asks.take(depth).sumOf { it.volume }
        val totalVol = volBid + volAsk

        val rawImbalance = if (totalVol > 0) (volBid - volAsk) / totalVol else 0.0

        val alpha = 2.0 / (config.smoothingPeriod + 1.0)
        state.smoothedImbalance = alpha * rawImbalance + (1 - alpha) * state.smoothedImbalance

        val returns = if (state.lastPrice > 0) ln(midPrice / state.lastPrice) else 0.0
        val volatility = garchModel.calculateNextVolatility(midPrice, state.lastPrice, state.lastVolatility)
        state.lastVolatility = volatility
        state.lastPrice = midPrice

        val trendAlpha = 2.0 / (config.trendEmaLength + 1.0)
        state.trendEma = if (state.trendEma == 0.0) midPrice else (trendAlpha * midPrice + (1 - trendAlpha) * state.trendEma)

        val metrics = TradeMetrics(
            midPrice = midPrice,
            spread = spread,
            relativeSpread = relativeSpread,
            imbalance = state.smoothedImbalance,
            volatility = volatility
        )

        // --- DECISION LOGIC ---

        // 1️⃣ HARD FILTERS
        if (relativeSpread > config.maxRelativeSpread) {
            return TradingDecision(TradeDirection.NO_TRADE, 0.0, 0.0, "Spread too high", metrics)
        }
        if (volatility > config.maxAllowedVolatility) {
            return TradingDecision(TradeDirection.NO_TRADE, 0.0, 0.0, "Volatility too high", metrics)
        }
        
        val timeSinceLastTrade = (currentTime - state.lastTradeTime) / 1000
        if (state.currentDirection == TradeDirection.NO_TRADE && timeSinceLastTrade < config.cooldownTimeSeconds) {
             return TradingDecision(TradeDirection.NO_TRADE, 0.0, 0.0, "Cooldown active (${config.cooldownTimeSeconds - timeSinceLastTrade}s)", metrics)
        }

        // 2️⃣ DIRECTION WITH HYSTERESIS
        var proposedDirection = state.currentDirection

        if (state.currentDirection == TradeDirection.NO_TRADE) {
            if (state.smoothedImbalance > config.imbalanceEnterThreshold) {
                proposedDirection = TradeDirection.LONG
            } else if (state.smoothedImbalance < -config.imbalanceEnterThreshold) {
                proposedDirection = TradeDirection.SHORT
            }
        } else if (state.currentDirection == TradeDirection.LONG) {
            if (state.smoothedImbalance < config.imbalanceExitThreshold) {
                proposedDirection = TradeDirection.NO_TRADE
            }
        } else if (state.currentDirection == TradeDirection.SHORT) {
            if (state.smoothedImbalance > -config.imbalanceExitThreshold) {
                proposedDirection = TradeDirection.NO_TRADE
            }
        }

        // 3️⃣ TIME CONFIRMATION
        var finalDirection = state.currentDirection
        
        if (proposedDirection != state.currentDirection) {
            if (proposedDirection != state.potentialSignalDirection) {
                state.potentialSignalDirection = proposedDirection
                state.signalStartTime = currentTime
                decisionReason.append("Validating signal... ")
            } else {
                val signalDuration = (currentTime - state.signalStartTime) / 1000
                if (signalDuration >= config.confirmationTimeSeconds) {
                    finalDirection = proposedDirection
                    state.lastTradeTime = currentTime
                    state.currentDirection = finalDirection
                    state.potentialSignalDirection = TradeDirection.NO_TRADE
                } else {
                    decisionReason.append("Confirming ${proposedDirection} (${signalDuration}/${config.confirmationTimeSeconds}s)... ")
                }
            }
        } else {
            state.potentialSignalDirection = TradeDirection.NO_TRADE
        }
        
        if (finalDirection == state.currentDirection && finalDirection != TradeDirection.NO_TRADE) {
            decisionReason.append("Holding ${finalDirection}. ")
        }

        // 4️⃣ CONFIDENCE SCORE
        val w1 = 60.0
        val w2 = 20.0
        val w3 = 20.0
        
        val normImbalance = min(1.0, abs(state.smoothedImbalance) / config.imbalanceEnterThreshold)
        val normSpread = min(1.0, 1.0 - (relativeSpread / config.maxRelativeSpread))
        val normVol = min(1.0, 1.0 - (volatility / config.maxAllowedVolatility))
        
        val confidence = (w1 * normImbalance) + (w2 * normSpread) + (w3 * normVol)
        
        if (finalDirection != TradeDirection.NO_TRADE && confidence < config.minConfidence) {
             if (state.currentDirection == TradeDirection.NO_TRADE) {
                 return TradingDecision(TradeDirection.NO_TRADE, 0.0, 0.0, "Low confidence (${String.format("%.1f", confidence)}%)", metrics)
             }
        }

        // 🔒 LEVERAGE & SIZE (RISK-BASED)
        val finalLeverage: Double
        
        if (config.manualLeverageOverride) {
            finalLeverage = min(config.manualLeverageValue, config.maxExchangeLeverage)
            decisionReason.append("Lev: ${String.format("%.1fx", finalLeverage)} (Manual). ")
        } else {
            // Move_risk = k_v * sigma
            val moveRisk = config.volatilityRiskMultiplier * volatility
            
            // Leverage_risk = R / Move_risk
            val leverageRisk = if (moveRisk > 0) config.riskPerTrade / moveRisk else 1.0
            
            // Apply Mode Factor
            val modeAdjustedLeverage = leverageRisk * config.leverageMode.factor
            
            // Final Cap
            finalLeverage = min(
                modeAdjustedLeverage,
                min(config.maxExchangeLeverage, config.maxUserLeverageCap)
            )
            
            decisionReason.append("Lev: ${String.format("%.1fx", finalLeverage)} (${config.leverageMode}). ")
        }
        
        // Position Size propto 1/sigma
        val baselineVol = 0.01
        val positionSizePct = min(100.0, (baselineVol / (if(volatility > 0) volatility else 0.001)) * 100.0)

        // Final Output
        if (finalDirection != TradeDirection.NO_TRADE) {
            decisionReason.append("Conf: ${String.format("%.0f", confidence)}%. ")
            if (state.smoothedImbalance > 0) decisionReason.append("Buyers dominant. ") else decisionReason.append("Sellers dominant. ")
        } else {
            decisionReason.append("WAIT. ")
        }

        return TradingDecision(
            direction = finalDirection,
            leverage = finalLeverage,
            positionSizePct = positionSizePct,
            reason = decisionReason.toString().trim(),
            metrics = metrics
        )
    }
    
    fun resetState(symbol: String) {
        stateMap.remove(symbol)
    }
}
