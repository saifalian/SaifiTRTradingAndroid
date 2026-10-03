package com.godzilla.app.domain.model

data class TradingStrategy(
    val id: String,
    val name: String,
    val description: String,
    val isActive: Boolean = false,
    val parameters: Map<String, String> = emptyMap()
)

// Built-in strategies
object StrategyTemplates {
    val GODZILLA_GARCH = TradingStrategy(
        id = "godzilla_garch",
        name = "Godzilla GARCH Strategy",
        description = "Advanced order book imbalance analysis with GARCH volatility modeling. Provides LONG/SHORT signals with dynamic leverage and position sizing.",
        parameters = mapOf(
            "imbalance_threshold" to "0.3",
            "max_spread" to "0.005",
            "max_volatility" to "0.05"
        )
    )
    
    val STABLE_ORDERBOOK_BASIC = TradingStrategy(
        id = "stable_orderbook_basic",
        name = "Stable Order-Book (Basic)",
        description = "Stable strategy based on order-book imbalance and volatility. Simple configuration without advanced leverage controls.",
        parameters = mapOf(
            "imbalance_enter" to "0.3",
            "imbalance_exit" to "0.1",
            "confirmation_time" to "5",
            "cooldown_time" to "30"
        )
    )

    val STABLE_ORDERBOOK = TradingStrategy(
        id = "stable_orderbook",
        name = "Stable Order-Book (Leverage)",
        description = "Stable, human-usable strategy based on order-book imbalance and volatility. Includes configurable risk-based leverage.",
        parameters = mapOf(
            "imbalance_enter" to "0.3",
            "imbalance_exit" to "0.1",
            "confirmation_time" to "5",
            "cooldown_time" to "30",
            "max_user_leverage" to "10.0",
            "risk_per_trade" to "0.02",
            "leverage_mode" to "BALANCED", // CONSERVATIVE, BALANCED, AGGRESSIVE
            "manual_leverage" to "false",
            "manual_leverage_value" to "1.0"
        )
    )
    
    val LIQUIDITY_DRAW = TradingStrategy(
        id = "liquidity_draw",
        name = "Liquidity Draw (SMC)",
        description = "Smart Money Concepts strategy identifying HTF liquidity sweeps (1H/4H/Sessions) and LTF reversals (BOS/FVG) for high-probability entries.",
        parameters = mapOf(
            "ltf_timeframe" to "1m",
            "htf_timeframe1" to "1h",
            "htf_timeframe2" to "4h"
        )
    )
    
    val MY_SMC = TradingStrategy(
        id = "my_smc",
        name = "MY SMC",
        description = "Smart Money Concept strategy. Tracks Market Structure (HTF), Liquidity Zones/OB/FVG (ITF), and confirms entries with LTF patterns.",
        parameters = mapOf(
            "risk_percent" to "1.5",
            "leverage" to "5.0",
            "htf_timeframe" to "4h",
            "itf_timeframe" to "1h",
            "ltf_timeframe" to "1m",
            "zone_lookback_candles" to "50",
            "liquidity_lookback_candles" to "50",
            "displacement_multiplier" to "2.0",
            "wick_ratio" to "0.3"
        )
    )

    fun getAllTemplates() = listOf(
        GODZILLA_GARCH,
        STABLE_ORDERBOOK_BASIC,
        STABLE_ORDERBOOK,
        LIQUIDITY_DRAW,
        MY_SMC
    )

}
