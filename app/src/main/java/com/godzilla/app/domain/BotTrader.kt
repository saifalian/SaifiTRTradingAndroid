package com.godzilla.app.domain

import com.godzilla.app.domain.model.TradeDirection
import com.godzilla.app.domain.model.SimulatedTrade
import com.godzilla.app.domain.model.BotConfig
import com.godzilla.app.domain.model.TradeStatus
import com.godzilla.app.domain.model.TradingDecision
import com.godzilla.app.domain.model.BotInstance
import com.godzilla.app.domain.model.BotState
import com.godzilla.app.domain.model.MarketType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * BotTrader handles simulated trade execution based on strategy signals.
 * It runs inside the app as a backtesting simulation (no real API calls).
 * Each instance is dedicated to a specific trading pair and market type.
 */
class BotTrader(
    private val botId: String,
    private val symbol: String,
    private val marketType: MarketType,
    private val exchange: String,
    private val assetClass: com.godzilla.app.domain.model.AssetClass,
    private val tradingEngine: TradingEngine
) {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var botJob: Job? = null

    private val _state = MutableStateFlow(BotState.STOPPED)
    val state: StateFlow<BotState> = _state.asStateFlow()

    private val _activePositions = MutableStateFlow<List<SimulatedTrade>>(emptyList())
    val activePositions: StateFlow<List<SimulatedTrade>> = _activePositions.asStateFlow()

    private val _currentPosition = MutableStateFlow<SimulatedTrade?>(null)
    val currentPosition: StateFlow<SimulatedTrade?> = _currentPosition.asStateFlow()

    private val _tradeHistory = MutableStateFlow<List<SimulatedTrade>>(emptyList())
    val tradeHistory: StateFlow<List<SimulatedTrade>> = _tradeHistory.asStateFlow()

    private val _completedTrades = MutableStateFlow(0)
    val completedTrades: StateFlow<Int> = _completedTrades.asStateFlow()

    private val _currentCapital = MutableStateFlow(0.0)
    val currentCapital: StateFlow<Double> = _currentCapital.asStateFlow()

    private val _totalProfitLoss = MutableStateFlow(0.0)
    val totalProfitLoss: StateFlow<Double> = _totalProfitLoss.asStateFlow()

    private val _currentUnrealizedPnL = MutableStateFlow(0.0)
    val currentUnrealizedPnL: StateFlow<Double> = _currentUnrealizedPnL.asStateFlow()

    private val _activeStrategyResults = MutableStateFlow<Map<String, TradingDecision>>(emptyMap())
    val activeStrategyResults: StateFlow<Map<String, TradingDecision>> = _activeStrategyResults.asStateFlow()

    private var config: BotConfig = BotConfig()
    private var lastProcessedDirection: TradeDirection? = null

    /** Start the bot with configuration */
    fun startBot(botConfig: BotConfig) {
        if (_state.value == BotState.RUNNING) return
        
        config = botConfig
        _currentCapital.value = config.initialCapital
        _completedTrades.value = 0
        _totalProfitLoss.value = 0.0
        _tradeHistory.value = emptyList()
        _activeStrategyResults.value = emptyMap()
        lastProcessedDirection = null
        
        // Start dedicated data stream for this bot
        tradingEngine.startMonitoringForBot(
            botId = botId,
            exchangeName = exchange,
            symbol = symbol,
            marketType = marketType,
            isAggregate = exchange.equals("Aggregate", ignoreCase = true),
            activeStrategies = config.selectedStrategyIds,
            allowedTimeFrames = config.allowedTimeFrames,
            assetClass = assetClass
        )

        
        _state.value = BotState.RUNNING
        startBotJob()
    }

    private fun openPosition(symbol: String, decision: TradingDecision) {
        if (_activePositions.value.size >= config.maxConcurrentTrades) return

        val price = decision.metrics.midPrice
        val strategyName = "Active Strategy"
        
        val leverage = if (config.useStrategyLeverage) {
            decision.leverage
        } else {
            config.customLeverage
        }
        
        val trade = SimulatedTrade(
            id = "${System.currentTimeMillis()}-${_activePositions.value.size}",
            entryTime = System.currentTimeMillis(),
            exitTime = null,
            symbol = symbol,
            direction = decision.direction,
            leverage = leverage,
            entryPrice = price,
            exitPrice = null,
            positionSize = config.amountPerTrade,
            strategyName = strategyName,
            profitLoss = 0.0,
            profitLossPercent = 0.0,
            status = TradeStatus.OPEN
        )
        
        val newPositions = _activePositions.value + trade
        _activePositions.value = newPositions
        _currentPosition.value = newPositions.lastOrNull() // Show latest
    }

    private fun closePosition(trade: SimulatedTrade, exitPrice: Double) {
        val rawPnl = if (trade.direction == TradeDirection.LONG) {
            (exitPrice - trade.entryPrice) / trade.entryPrice * trade.positionSize * trade.leverage
        } else {
            (trade.entryPrice - exitPrice) / trade.entryPrice * trade.positionSize * trade.leverage
        }
        
        val entryFee = trade.positionSize * config.takerFee
        val exitFee = trade.positionSize * config.takerFee
        val totalFees = entryFee + exitFee
        val pnl = rawPnl - totalFees
        
        val pnlPercent = if (trade.direction == TradeDirection.LONG) {
            ((exitPrice - trade.entryPrice) / trade.entryPrice) * 100 * trade.leverage
        } else {
            ((trade.entryPrice - exitPrice) / trade.entryPrice) * 100 * trade.leverage
        }
        
        val closed = trade.copy(
            exitTime = System.currentTimeMillis(),
            exitPrice = exitPrice,
            profitLoss = pnl,
            rawProfitLoss = rawPnl,
            feesPaid = totalFees,
            profitLossPercent = pnlPercent,
            status = TradeStatus.CLOSED
        )
        
        _currentCapital.value += pnl
        _totalProfitLoss.value += pnl
        _tradeHistory.value = _tradeHistory.value + closed
        _completedTrades.value += 1
        
        val newPositions = _activePositions.value.filter { it.id != trade.id }
        _activePositions.value = newPositions
        _currentPosition.value = newPositions.lastOrNull()
        
        if (newPositions.isEmpty()) {
            lastProcessedDirection = null
        }
    }

    fun pauseBot() {
        if (_state.value == BotState.RUNNING) {
            _state.value = BotState.PAUSED
        }
    }

    fun resumeBot() {
        if (_state.value == BotState.PAUSED) {
            _state.value = BotState.RUNNING
        }
    }

    fun stopBot() {
        _state.value = BotState.STOPPED
        botJob?.cancel()
        
        // Stop dedicated data stream
        tradingEngine.stopMonitoringForBot(botId)
        
        // Close any open position at current market price
        // Close all open positions
        val botState = tradingEngine.getDecisionForBot(botId)
        val marketData = botState?.marketData
        
        if (marketData != null) {
            _activePositions.value.toList().forEach { pos ->
                closePosition(pos, marketData.bestAsk)
            }
        }
        
        botJob = null
    }

    fun restoreState(instance: BotInstance) {
        config = instance.config
        _currentCapital.value = instance.currentCapital
        _completedTrades.value = instance.completedTrades
        _totalProfitLoss.value = instance.totalProfitLoss
        _tradeHistory.value = instance.tradeHistory
        _activePositions.value = instance.activePositions
        _currentPosition.value = instance.currentPosition
        _state.value = if (instance.state == BotState.RUNNING) BotState.PAUSED else instance.state
        
        if (_state.value != BotState.STOPPED) {
            startBotJob()
        }
    }

    fun updateConfig(newConfig: BotConfig) {
        config = newConfig
    }


    private fun startBotJob() {

        botJob?.cancel()
        botJob = scope.launch {
            while (isActive && _state.value != BotState.STOPPED) {
                // Check for pause
                if (_state.value == BotState.PAUSED) {
                    delay(500)
                    continue
                }
                
                // Get decisions from TradingEngine specific to this bot
                val engineState = tradingEngine.getDecisionForBot(botId)
                
                if (engineState == null) {
                    delay(100)
                    continue
                }

                val marketData = engineState.marketData
                

                
                val decision = run {
                    val resultsToConsider = if (config.useDashboardStrategy) {
                        engineState.strategyResults.values.toList()
                    } else {
                        engineState.strategyResults.filter { entry ->
                            config.selectedStrategyIds.any { id -> 
                                entry.key.contains(id.replace("_", " "), ignoreCase = true) 
                            }
                        }.values.toList()
                    }

                    // Update exposed strategy results for UI
                    val relevantMap = engineState.strategyResults.filter { entry ->
                        config.selectedStrategyIds.any { id -> 
                            entry.key.contains(id.replace("_", " "), ignoreCase = true) 
                        }
                    }
                    _activeStrategyResults.value = relevantMap

                    if (resultsToConsider.isEmpty()) null
                    else if (config.requireAllAgree) {
                        val first = resultsToConsider.first()
                        if (resultsToConsider.all { it.direction == first.direction && it.direction != TradeDirection.NO_TRADE }) {
                            first
                        } else null
                    } else {
                        // Any Confirm
                        resultsToConsider.find { it.direction != TradeDirection.NO_TRADE }
                    }
                }
                
                if (decision != null && marketData != null) {
                    val activePos = _activePositions.value.toList() // Copy to avoid concurrent modification
                    
                    // 1. Check Exits for existing positions
                    activePos.forEach { pos ->
                        // Close if signal reverses or becomes NO_TRADE
                        if (decision.direction != pos.direction) {
                            closePosition(pos, decision.metrics.midPrice)
                        }
                    }
                    
                    // 2. Check Entries
                    // Only if we haven't reached max trades
                    if (_activePositions.value.size < config.maxConcurrentTrades) {
                        // Open new position if signal is valid and different from last processed (to avoid spamming same signal)
                        // Note: If we just closed a position, lastProcessedDirection might be null (reset in closePosition if empty)
                        if (decision.direction != TradeDirection.NO_TRADE && 
                            decision.direction != lastProcessedDirection) {
                            openPosition(marketData.symbol, decision)
                            lastProcessedDirection = decision.direction
                        }
                    }
                }
                
                // Check max trades limit
                if (config.maxTrades != null && _completedTrades.value >= config.maxTrades!!) {
                    stopBot()
                }

                // Calculate Unrealized PnL
                if (marketData != null) {
                    val currentPrice = (marketData.bestBid + marketData.bestAsk) / 2.0
                    val totalUnrealized = _activePositions.value.sumOf { pos ->
                        val priceDiff = if (pos.direction == TradeDirection.LONG) {
                            currentPrice - pos.entryPrice
                        } else {
                            pos.entryPrice - currentPrice
                        }
                        (priceDiff / pos.entryPrice) * (pos.positionSize * pos.leverage)
                    }
                    _currentUnrealizedPnL.value = totalUnrealized
                }
                
                delay(250) // Poll every 250ms
            }
        }
    }
}
