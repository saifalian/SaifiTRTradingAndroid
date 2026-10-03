package com.godzilla.app.domain

import com.godzilla.app.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class BacktestEngine @Inject constructor(
    private val stableStrategy: StableOrderBookStrategy,
    private val garchModel: GarchModel,
    private val tradingCalculator: TradingCalculator
) {

    fun runBacktest(
        config: BacktestConfig,
        candles: List<Candle>
    ): BacktestResult {
        if (candles.isEmpty()) {
            return createEmptyResult(config)
        }

        var currentCapital = config.initialCapital
        val trades = mutableListOf<TradeRecord>()
        var activePosition: TradeRecord? = null
        
        // Simulate market data feed from candles
        // Note: Strategies usually need MarketData (OrderBook), but we only have Candles.
        // We will approximate MarketData from Candle data for backtesting purposes.
        // This is a limitation: strategies relying heavily on OrderBook imbalance might not be perfectly accurate
        // on pure candle data unless we simulate the book or have historical book data.
        // For StableOrderBookStrategy, we might need to simulate some metrics or accept this limitation.
        
        // We will iterate through candles.
        // For each candle, we create a dummy MarketData.
        
        var maxCapital = currentCapital
        var maxDrawdown = 0.0

        for (i in 50 until candles.size) { // Start after some warmup
            val candle = candles[i]
            val prevCandles = candles.subList(0, i + 1)
            
            // Approximate MarketData from Candle
            // Best Bid/Ask ~ Close price
            val marketData = MarketData(
                symbol = config.symbol,
                bestBid = candle.close,
                bestAsk = candle.close,
                bids = emptyList(), // Missing deep book data
                asks = emptyList()
            )
            
            // Calculate Volatility for metrics
            // (Simplified: re-calculating every step is slow, but accurate for simulation)
            // In a real optimized backtester we'd optimize this.
            val returns = prevCandles.takeLast(50).mapIndexedNotNull { index, c ->
                if (index > 0) kotlin.math.ln(c.close / prevCandles.takeLast(50)[index - 1].close) else null
            }
            val volatility = if (returns.isNotEmpty()) garchModel.calculateRollingStdDev(returns) else 0.0
            
            // Generate Decision
            val decision = when (config.strategyId) {
                "stable_orderbook" -> {
                    // We need to construct a config for the strategy
                    val stratConfig = StableOrderBookStrategy.Config(
                        leverageMode = StableOrderBookStrategy.LeverageMode.BALANCED, // Default
                        manualLeverageOverride = !config.useStrategyLeverage,
                        manualLeverageValue = config.userLeverage
                    )
                    // Note: StableStrategy relies on OrderBook. Without real bids/asks, it might not trigger.
                    // We might need to mock imbalance or use a different strategy for candle-only backtest.
                    // For now, let's proceed. If the strategy checks bids/asks size, it might fail to trigger.
                    stableStrategy.makeDecision(marketData, stratConfig)
                }
                else -> TradingDecision(
                    direction = TradeDirection.NO_TRADE,
                    leverage = 1.0,
                    positionSizePct = 0.0,
                    reason = "Strategy not supported",
                    metrics = TradeMetrics(
                        midPrice = 0.0,
                        spread = 0.0,
                        relativeSpread = 0.0,
                        imbalance = 0.0,
                        volatility = 0.0
                    )
                )
            }

            // Execute Logic
            val price = candle.close
            
            // Check Exit
            if (activePosition != null) {
                val p = activePosition
                // Simple Take Profit / Stop Loss simulation if strategy doesn't explicitly exit
                // Or if strategy says reverse/exit
                
                val shouldExit = decision.direction != TradeDirection.NO_TRADE && decision.direction != p.direction
                // Or if strategy explicitly says NO_TRADE but we are in position? 
                // Usually strategies return NO_TRADE when holding.
                // Let's assume strategy returns opposite direction to close/flip.
                
                // For simplicity in this basic version:
                // If we have a position and strategy says opposite -> Close & Reverse
                // If strategy says same -> Hold/Add (we'll just hold)
                
                if (shouldExit) {
                    // Close Position
                    val exitPrice = price
                    val rawPnl = if (p.direction == TradeDirection.LONG) {
                        (exitPrice - p.entryPrice) * p.quantity
                    } else {
                        (p.entryPrice - exitPrice) * p.quantity
                    }
                    
                    val exitFee = (exitPrice * p.quantity) * (config.feePercent / 100.0)
                    val netPnl = rawPnl - exitFee
                    
                    currentCapital += (p.investedAmount + netPnl)
                    
                    trades.add(p.copy(
                        exitTime = candle.timestamp,
                        exitPrice = exitPrice,
                        fee = p.fee + exitFee,
                        profitLoss = netPnl,
                        profitLossPercent = (netPnl / p.investedAmount) * 100.0,
                        exitReason = "Strategy Signal"
                    ))
                    
                    activePosition = null
                }
            }
            
            // Check Entry
            if (activePosition == null && decision.direction != TradeDirection.NO_TRADE) {
                val leverage = if (config.useStrategyLeverage && decision.leverage > 0) {
                    decision.leverage
                } else {
                    config.userLeverage
                }
                
                val investAmount = config.amountPerOrder
                if (currentCapital >= investAmount) {
                    val positionSize = investAmount * leverage
                    val quantity = positionSize / price
                    val entryFee = positionSize * (config.feePercent / 100.0)
                    
                    currentCapital -= investAmount // Deduct capital (margin)
                    currentCapital -= entryFee // Deduct fee immediately
                    
                    activePosition = TradeRecord(
                        entryTime = candle.timestamp,
                        exitTime = 0,
                        symbol = config.symbol,
                        direction = decision.direction,
                        entryPrice = price,
                        exitPrice = 0.0,
                        quantity = quantity,
                        leverage = leverage,
                        investedAmount = investAmount,
                        fee = entryFee,
                        profitLoss = 0.0,
                        profitLossPercent = 0.0,
                        exitReason = ""
                    )
                }
            }
            
            // Track Max Drawdown
            if (currentCapital > maxCapital) maxCapital = currentCapital
            val drawdown = (maxCapital - currentCapital) / maxCapital
            if (drawdown > maxDrawdown) maxDrawdown = drawdown
        }
        
        // Close any open position at the end
        if (activePosition != null) {
            val p = activePosition!!
            val lastPrice = candles.last().close
            val rawPnl = if (p.direction == TradeDirection.LONG) {
                (lastPrice - p.entryPrice) * p.quantity
            } else {
                (p.entryPrice - lastPrice) * p.quantity
            }
            val exitFee = (lastPrice * p.quantity) * (config.feePercent / 100.0)
            val netPnl = rawPnl - exitFee
            
            currentCapital += (p.investedAmount + netPnl)
            
            trades.add(p.copy(
                exitTime = candles.last().timestamp,
                exitPrice = lastPrice,
                fee = p.fee + exitFee,
                profitLoss = netPnl,
                profitLossPercent = (netPnl / p.investedAmount) * 100.0,
                exitReason = "End of Backtest"
            ))
        }

        val totalProfitLoss = currentCapital - config.initialCapital
        val winningTrades = trades.count { it.profitLoss > 0 }
        val losingTrades = trades.count { it.profitLoss <= 0 }
        val winRate = if (trades.isNotEmpty()) (winningTrades.toDouble() / trades.size) * 100.0 else 0.0
        val totalFees = trades.sumOf { it.fee }

        return BacktestResult(
            config = config,
            totalProfitLoss = totalProfitLoss,
            finalCapital = currentCapital,
            winRate = winRate,
            totalTrades = trades.size,
            winningTrades = winningTrades,
            losingTrades = losingTrades,
            totalFeesPaid = totalFees,
            maxDrawdown = maxDrawdown * 100.0,
            tradeHistory = trades.sortedByDescending { it.entryTime }
        )
    }

    private fun createEmptyResult(config: BacktestConfig): BacktestResult {
        return BacktestResult(
            config = config,
            totalProfitLoss = 0.0,
            finalCapital = config.initialCapital,
            winRate = 0.0,
            totalTrades = 0,
            winningTrades = 0,
            losingTrades = 0,
            totalFeesPaid = 0.0,
            maxDrawdown = 0.0,
            tradeHistory = emptyList()
        )
    }
}
