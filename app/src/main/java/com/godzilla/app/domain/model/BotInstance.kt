package com.godzilla.app.domain.model

data class BotInstance(
    val id: String,
    val name: String, // User-friendly name like "BTC SPOT Bot"
    val symbol: String, // e.g., "BTCUSDT"
    val marketType: MarketType,
    val exchange: String, // e.g., "Binance"
    val assetClass: AssetClass = AssetClass.CRYPTO,
    val isAggregateMode: Boolean = false, // true = merge all exchanges
    val config: BotConfig,
    val state: BotState = BotState.STOPPED,
    val currentCapital: Double = config.initialCapital,
    val totalProfitLoss: Double = 0.0,
    val currentUnrealizedPnL: Double = 0.0,
    val completedTrades: Int = 0,
    val activePositions: List<SimulatedTrade> = emptyList(),
    val currentPosition: SimulatedTrade? = activePositions.firstOrNull(), // Backward compatibility
    val tradeHistory: List<SimulatedTrade> = emptyList(),
    val activeStrategyResults: Map<String, TradingDecision> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis()
)

enum class BotState {
    STOPPED, RUNNING, PAUSED
}
