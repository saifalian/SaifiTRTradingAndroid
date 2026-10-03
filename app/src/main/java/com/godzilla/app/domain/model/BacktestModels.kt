package com.godzilla.app.domain.model

import java.time.Instant

data class BacktestConfig(
    val initialCapital: Double = 1000.0,
    val amountPerOrder: Double = 100.0,
    val feePercent: Double = 0.1, // 0.1%
    val userLeverage: Double = 1.0,
    val useStrategyLeverage: Boolean = true,
    val strategyId: String = "stable_orderbook",
    val symbol: String = "BTCUSDT",
    val timeFrame: TimeFrame = TimeFrame.M1
)

data class BacktestResult(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val config: BacktestConfig,
    val totalProfitLoss: Double,
    val finalCapital: Double,
    val winRate: Double,
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val totalFeesPaid: Double,
    val maxDrawdown: Double,
    val tradeHistory: List<TradeRecord>
)

data class TradeRecord(
    val entryTime: Long,
    val exitTime: Long,
    val symbol: String,
    val direction: TradeDirection,
    val entryPrice: Double,
    val exitPrice: Double,
    val quantity: Double,
    val leverage: Double,
    val investedAmount: Double,
    val fee: Double,
    val profitLoss: Double,
    val profitLossPercent: Double,
    val exitReason: String
)
