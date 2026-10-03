package com.godzilla.app.domain

import com.godzilla.app.domain.model.MarketData
import com.godzilla.app.domain.model.TradeDirection
import com.godzilla.app.domain.model.TradeMetrics
import com.godzilla.app.domain.model.TradingDecision
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.min

class TradingCalculator @Inject constructor(
    private val garchModel: GarchModel
) {

    // Configuration Parameters (Can be injected or passed via settings)
    data class Config(
        val imbalanceThreshold: Double = 0.3, // theta_I
        val maxRelativeSpread: Double = 0.005, // phi_max (0.5%)
        val maxVolatility: Double = 0.05,      // sigma_max
        val exchangeMaxLeverage: Double = 20.0,
        val smoothingPeriod: Int = 1 // 1 = no smoothing
    )

    fun calculateMetrics(marketData: MarketData, currentVolatility: Double): TradeMetrics {
        val midPrice = (marketData.bestBid + marketData.bestAsk) / 2.0
        val spread = marketData.bestAsk - marketData.bestBid
        val relativeSpread = if (midPrice > 0) spread / midPrice else 0.0

        val volBid = marketData.bids.sumOf { it.volume }
        val volAsk = marketData.asks.sumOf { it.volume }
        val totalVol = volBid + volAsk

        val imbalance = if (totalVol > 0) {
            (volBid - volAsk) / totalVol
        } else {
            0.0
        }

        return TradeMetrics(
            midPrice = midPrice,
            spread = spread,
            relativeSpread = relativeSpread,
            imbalance = imbalance,
            volatility = currentVolatility
        )
    }

    fun makeDecision(metrics: TradeMetrics, config: Config): TradingDecision {
        val decisionReason = StringBuilder()
        var direction = TradeDirection.NO_TRADE

        // 1. Safety Filters
        if (metrics.relativeSpread > config.maxRelativeSpread) {
            decisionReason.append("Spread too high (${String.format("%.4f%%", metrics.relativeSpread * 100)}). ")
            return TradingDecision(TradeDirection.NO_TRADE, 0.0, 0.0, decisionReason.toString(), metrics)
        }

        if (metrics.volatility > config.maxVolatility) {
            decisionReason.append("Volatility too high (${String.format("%.4f", metrics.volatility)}). ")
            return TradingDecision(TradeDirection.NO_TRADE, 0.0, 0.0, decisionReason.toString(), metrics)
        }

        if (abs(metrics.imbalance) < config.imbalanceThreshold) {
            decisionReason.append("Imbalance weak (${String.format("%.2f", metrics.imbalance)}). ")
            return TradingDecision(TradeDirection.NO_TRADE, 0.0, 0.0, decisionReason.toString(), metrics)
        }

        // 2. Direction Decision
        if (metrics.imbalance > config.imbalanceThreshold) {
            direction = TradeDirection.LONG
            decisionReason.append("Buyers dominant (Imbalance: ${String.format("%.2f", metrics.imbalance)}). ")
        } else if (metrics.imbalance < -config.imbalanceThreshold) {
            direction = TradeDirection.SHORT
            decisionReason.append("Sellers dominant (Imbalance: ${String.format("%.2f", metrics.imbalance)}). ")
        }

        // 3. Leverage & Size
        // MaxLeverage = min(L_exchange, 1 + 1/sigma)
        // Avoid division by zero
        val safeVol = if (metrics.volatility < 0.0001) 0.0001 else metrics.volatility
        val riskBasedLeverage = 1.0 + (1.0 / safeVol)
        val finalLeverage = min(config.exchangeMaxLeverage, riskBasedLeverage)

        // Position Size Proportional to 1/sigma (Normalized? Let's just output a raw score for now or cap at 100%)
        // If vol is 1% (0.01), size score is 100. If vol is 10% (0.1), size score is 10.
        // Let's normalize such that 1% vol = 100% size (baseline).
        val baselineVol = 0.01
        val positionSizePct = min(100.0, (baselineVol / safeVol) * 100.0)

        return TradingDecision(
            direction = direction,
            leverage = finalLeverage,
            positionSizePct = positionSizePct,
            reason = decisionReason.toString().trim(),
            metrics = metrics
        )
    }

    fun mergeCandles(candleLists: List<List<com.godzilla.app.domain.model.Candle>>): List<com.godzilla.app.domain.model.Candle> {
        if (candleLists.isEmpty()) return emptyList()
        if (candleLists.size == 1) return candleLists[0]

        val timestampToCandles = mutableMapOf<Long, MutableList<com.godzilla.app.domain.model.Candle>>()
        for (list in candleLists) {
            for (candle in list) {
                timestampToCandles.getOrPut(candle.timestamp) { mutableListOf() }.add(candle)
            }
        }

        val sortedTimestamps = timestampToCandles.keys.sorted()
        val merged = sortedTimestamps.map { ts ->
            val candlesAtTs = timestampToCandles[ts]!!
            com.godzilla.app.domain.model.Candle(
                timestamp = ts,
                open = candlesAtTs.map { it.open }.average(),
                high = candlesAtTs.map { it.high }.average(),
                low = candlesAtTs.map { it.low }.average(),
                close = candlesAtTs.map { it.close }.average(),
                volume = candlesAtTs.map { it.volume }.sum()
            )
        }
        return merged
    }
}
