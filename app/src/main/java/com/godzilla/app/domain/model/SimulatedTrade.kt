package com.godzilla.app.domain.model

data class SimulatedTrade(
    val id: String,
    val entryTime: Long,
    val exitTime: Long?,
    val symbol: String,
    val direction: TradeDirection,
    val leverage: Double,
    val entryPrice: Double,
    val exitPrice: Double?,
    val positionSize: Double,
    val strategyName: String,
    val profitLoss: Double,
    val rawProfitLoss: Double = 0.0,
    val feesPaid: Double = 0.0,
    val profitLossPercent: Double,
    val status: TradeStatus
)

enum class TradeStatus {
    OPEN, CLOSED
}

data class BotConfig(
    val initialCapital: Double = 10000.0,
    val maxTrades: Int? = null, // null = unlimited
    val amountPerTrade: Double = 100.0,
    val useStrategyLeverage: Boolean = true, // true = use strategy's leverage, false = use custom
    val customLeverage: Double = 1.0,
    val makerFee: Double = 0.0002, // 0.02% default
    val takerFee: Double = 0.0005,  // 0.05% default
    val useDashboardStrategy: Boolean = true,
    val selectedStrategyIds: List<String> = emptyList(),
    val requireAllAgree: Boolean = false, // true = All Agree, false = Any Confirm
    val allowedTimeFrames: List<TimeFrame> = emptyList(), // empty = All
    val maxConcurrentTrades: Int = 1 // Default to 1 (Sequential)
)

