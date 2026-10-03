package com.godzilla.app.domain.model

data class OrderBookLevel(
    val price: Double,
    val volume: Double
)

data class MarketData(
    val symbol: String,
    val bestBid: Double,
    val bestAsk: Double,
    val bids: List<OrderBookLevel>, // Top K levels
    val asks: List<OrderBookLevel>, // Top K levels
    val timestamp: Long = System.currentTimeMillis()
)

data class TradingDecision(
    val direction: TradeDirection,
    val leverage: Double,
    val positionSizePct: Double,
    val reason: String,
    val metrics: TradeMetrics,
    val visuals: List<VisualElement> = emptyList()
)

data class VisualElement(
    val type: VisualType,
    val price: Double? = null,
    val priceHigh: Double? = null,
    val priceLow: Double? = null,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val label: String,
    val color: String // Hex color
)

enum class VisualType {
    HORIZONTAL_LINE,
    RECTANGLE,
    MARKER
}

data class TradeMetrics(
    val midPrice: Double,
    val spread: Double,
    val relativeSpread: Double,
    val imbalance: Double,
    val volatility: Double
)

enum class TradeDirection {
    LONG, SHORT, NO_TRADE
}

enum class ChartType {
    CANDLESTICK,
    LINE
}

