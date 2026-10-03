package com.godzilla.app.ui.stocks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.godzilla.app.data.local.UserPreferences
import com.godzilla.app.domain.BotManager
import com.godzilla.app.domain.TradingEngine
import com.godzilla.app.domain.model.AssetClass
import com.godzilla.app.domain.model.BotConfig
import com.godzilla.app.domain.model.BotInstance
import com.godzilla.app.domain.model.BotState
import com.godzilla.app.domain.model.MarketType
import com.godzilla.app.ui.main.BotStrategyMetadata
import com.godzilla.app.ui.main.MainUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StocksViewModel @Inject constructor(
    private val tradingEngine: TradingEngine,
    private val botManager: BotManager,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val popularPairs = listOf(
        "AAPL", "TSLA", "NVDA", "AMD", "MSFT", "GOOGL", "AMZN", "META", "SPX", "NDX", "DJI"
    )

    private val _uiState = MutableStateFlow(MainUiState(symbol = "AAPL"))
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        // Load saved active strategies
        val savedStrategies = userPreferences.getActiveStrategies().toList()
        tradingEngine.updateActiveStrategies(savedStrategies, AssetClass.STOCKS)
        
        // Start monitoring default Stock ONLY if not already monitoring
        val currentState = tradingEngine.stocksState.value
        if (currentState.selectedExchange.isEmpty() || currentState.symbol.isEmpty()) {
            val defaultExchange = if (currentState.availableExchanges.isNotEmpty()) {
                currentState.availableExchanges.first()
            } else {
                "Yahoo Finance"
            }
            startMonitoring(defaultExchange, "AAPL", MarketType.SPOT, false)
        }
        
        // Observe engine state
        viewModelScope.launch {
            tradingEngine.stocksState.collect { engineState ->
                _uiState.value = _uiState.value.copy(
                    selectedExchange = engineState.selectedExchange,
                    selectedMarketType = engineState.selectedMarketType,
                    selectedTimeFrame = engineState.selectedTimeFrame,
                    isAggregateMode = engineState.isAggregateMode,
                    symbol = engineState.symbol,
                    isLoading = engineState.isLoading,
                    marketData = engineState.marketData,
                    decision = engineState.decision,
                    mergedExchangesCount = engineState.mergedExchangesCount,
                    candles = engineState.candles,
                    activeStrategyIds = engineState.activeStrategyIds,
                    strategyResults = engineState.strategyResults,
                    errorMessage = engineState.errorMessage,
                    availableExchanges = engineState.availableExchanges,
                    isMarketOpen = engineState.isMarketOpen
                )
                
                recalculateBotStrategies(_uiState.value.botInstances)
            }
        }
        
        // Observe bot instances
        viewModelScope.launch {
            botManager.botInstances.collect { bots ->
                // Filter for Stocks bots
                val stocksBots = bots.filter { it.assetClass == AssetClass.STOCKS }
                recalculateBotStrategies(stocksBots)
            }
        }
    }

    private fun recalculateBotStrategies(bots: List<BotInstance>) {
        val currentSymbol = _uiState.value.symbol
        val currentExchange = _uiState.value.selectedExchange
        val isAggregate = _uiState.value.isAggregateMode
        
        val relevantBots = bots
            .filter { it.state == BotState.RUNNING }
            .filter { bot ->
                if (isAggregate) {
                    bot.symbol == currentSymbol && bot.exchange.equals("Aggregate", ignoreCase = true)
                } else {
                    bot.symbol == currentSymbol && bot.exchange == currentExchange
                }
            }

        val botStrategies = relevantBots
            .flatMap { it.config.selectedStrategyIds }
            .distinct()
        
        val botResults = relevantBots
            .flatMap { it.activeStrategyResults.entries }
            .associate { it.key to it.value }

        val botStrategyMetadata = bots
            .filter { it.state == BotState.RUNNING }
            .flatMap { bot ->
                bot.config.selectedStrategyIds.map { strategyId ->
                    strategyId to BotStrategyMetadata(
                        botName = bot.name,
                        symbol = bot.symbol,
                        botId = bot.id
                    )
                }
            }
            .toMap()

        _uiState.value = _uiState.value.copy(
            botInstances = bots,
            allActiveStrategyIds = (_uiState.value.activeStrategyIds + botStrategies).distinct(),
            botStrategyResults = botResults,
            botStrategyMetadata = botStrategyMetadata
        )
    }

    fun updateActiveStrategies(strategies: List<String>) {
        tradingEngine.updateActiveStrategies(strategies, AssetClass.STOCKS)
    }

    fun onSearchTextChanged(text: String) {
        val suggestions = if (text.isBlank()) {
            emptyList()
        } else {
            popularPairs.filter { it.startsWith(text, ignoreCase = true) }
        }
        _uiState.value = _uiState.value.copy(pairSuggestions = suggestions)
    }

    fun changeTimeFrame(timeFrame: com.godzilla.app.domain.model.TimeFrame) {
        tradingEngine.changeTimeFrame(timeFrame, AssetClass.STOCKS)
    }

    fun startMonitoring(exchangeName: String, symbol: String, marketType: MarketType, isAggregate: Boolean) {
        _uiState.value = _uiState.value.copy(pairSuggestions = emptyList())
        tradingEngine.startMonitoring(exchangeName, symbol, marketType, isAggregate, AssetClass.STOCKS)
    }
    
    fun createBot(
        name: String,
        symbol: String,
        marketType: MarketType,
        exchange: String,
        config: BotConfig,
        isAggregateMode: Boolean = false,
        autoStart: Boolean = true
    ) {
        viewModelScope.launch {
            val result = botManager.createBot(
                name = name,
                symbol = symbol,
                marketType = marketType,
                exchange = exchange,
                assetClass = AssetClass.STOCKS,
                config = config,
                isAggregateMode = isAggregateMode
            )
            result.onSuccess { botId ->
                if (autoStart) {
                    botManager.startBot(botId)
                }
            }
        }
    }
    
    fun updateBot(botId: String, name: String, config: BotConfig) {
        viewModelScope.launch {
            botManager.updateBotConfig(botId, name, config)
        }
    }
    
    fun startBot(botId: String) {
        viewModelScope.launch { botManager.startBot(botId) }
    }
    
    fun stopBot(botId: String) {
        viewModelScope.launch { botManager.stopBot(botId) }
    }
    
    fun pauseBot(botId: String) {
        viewModelScope.launch { botManager.pauseBot(botId) }
    }
    
    fun resumeBot(botId: String) {
        viewModelScope.launch { botManager.resumeBot(botId) }
    }
    
    fun deleteBot(botId: String) {
        viewModelScope.launch { botManager.deleteBot(botId) }
    }
    
    fun toggleBotConfigDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showBotConfigDialog = show)
    }
    
    fun toggleBotHistoryDialog(botId: String?) {
        _uiState.value = _uiState.value.copy(selectedBotForHistory = botId)
    }

    fun toggleBotEditDialog(botId: String?) {
        _uiState.value = _uiState.value.copy(selectedBotForEdit = botId)
    }

    fun toggleStrategyVisibility(strategyName: String) {
        val currentHidden = _uiState.value.hiddenStrategyNames
        val newHidden = if (currentHidden.contains(strategyName)) {
            currentHidden - strategyName
        } else {
            currentHidden + strategyName
        }
        _uiState.value = _uiState.value.copy(hiddenStrategyNames = newHidden)
    }

    fun toggleChartType() {
        val currentType = _uiState.value.chartType
        val newType = if (currentType == com.godzilla.app.domain.model.ChartType.CANDLESTICK) {
            com.godzilla.app.domain.model.ChartType.LINE
        } else {
            com.godzilla.app.domain.model.ChartType.CANDLESTICK
        }
        _uiState.value = _uiState.value.copy(chartType = newType)
    }
}
