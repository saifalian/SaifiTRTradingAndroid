package com.godzilla.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.godzilla.app.data.local.UserPreferences
import com.godzilla.app.domain.BotManager
import com.godzilla.app.domain.TradingEngine
import com.godzilla.app.domain.model.AssetClass
import com.godzilla.app.domain.model.BotConfig
import com.godzilla.app.domain.model.BotInstance
import com.godzilla.app.domain.model.Candle
import com.godzilla.app.domain.model.MarketData
import com.godzilla.app.domain.model.MarketType
import com.godzilla.app.domain.model.TradingDecision
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val tradingEngine: TradingEngine,
    private val botManager: BotManager,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val popularPairs = listOf(
        "BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT", "XRPUSDT", "ADAUSDT", "DOGEUSDT", "AVAXUSDT", "DOTUSDT", "MATICUSDT",
        "LINKUSDT", "UNIUSDT", "LTCUSDT", "BCHUSDT", "SHIBUSDT", "TRXUSDT", "NEARUSDT", "FILUSDT", "ATOMUSDT", "ETCUSDT"
    )

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        // Load saved active strategies
        val savedStrategies = userPreferences.getActiveStrategies().toList()
        tradingEngine.updateActiveStrategies(savedStrategies, AssetClass.CRYPTO)
        
        // Observe engine state
        viewModelScope.launch {
            tradingEngine.cryptoState.collect { engineState ->
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
                
                // Re-calculate bot strategies with new symbol/exchange
                recalculateBotStrategies(_uiState.value.botInstances)
            }
        }
        
        // Observe bot instances
        viewModelScope.launch {
            botManager.botInstances.collect { bots ->
                // Filter for Crypto bots only
                val cryptoBots = bots.filter { it.assetClass == AssetClass.CRYPTO }
                recalculateBotStrategies(cryptoBots)
            }
        }
    }

    private fun recalculateBotStrategies(bots: List<BotInstance>) {
        val currentSymbol = _uiState.value.symbol
        val currentExchange = _uiState.value.selectedExchange
        val isAggregate = _uiState.value.isAggregateMode
        
        // Filter bots that match the current symbol and exchange for display on main screen
        val relevantBots = bots
            .filter { it.state == com.godzilla.app.domain.model.BotState.RUNNING }
            .filter { bot ->
                if (isAggregate) {
                    bot.symbol == currentSymbol && bot.exchange.equals("Aggregate", ignoreCase = true)
                } else {
                    bot.symbol == currentSymbol && bot.exchange == currentExchange
                }
            }

        // Get strategies from relevant bots (only those matching current pair)
        val botStrategies = relevantBots
            .flatMap { it.config.selectedStrategyIds }
            .distinct()
        
        val botResults = relevantBots
            .flatMap { it.activeStrategyResults.entries }
            .associate { it.key to it.value }

        // Build metadata map for all running bot strategies (for display purposes)
        val botStrategyMetadata = bots
            .filter { it.state == com.godzilla.app.domain.model.BotState.RUNNING }
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
        tradingEngine.updateActiveStrategies(strategies, AssetClass.CRYPTO)
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
        tradingEngine.changeTimeFrame(timeFrame, AssetClass.CRYPTO)
    }

    fun startMonitoring(exchangeName: String, symbol: String, marketType: MarketType, isAggregate: Boolean) {
        // Clear suggestions when a pair is selected
        _uiState.value = _uiState.value.copy(pairSuggestions = emptyList())
        tradingEngine.startMonitoring(exchangeName, symbol, marketType, isAggregate, AssetClass.CRYPTO)
    }
    
    // Bot control methods
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
            // Explicitly using positional arguments to avoid any ambiguity
            val result = botManager.createBot(
                name,
                symbol,
                marketType,
                exchange,
                AssetClass.CRYPTO,
                config,
                isAggregateMode
            )
            result.onSuccess { botId ->
                if (autoStart) {
                    botManager.startBot(botId)
                }
            }
            result.onFailure { error ->
                // TODO: Show error to user
                println("Error creating bot: ${error.message}")
            }
        }
    }
    
    fun updateBot(
        botId: String,
        name: String,
        config: BotConfig
    ) {
        viewModelScope.launch {
            botManager.updateBotConfig(botId, name, config)
        }
    }
    
    fun startBot(botId: String) {

        viewModelScope.launch {
            botManager.startBot(botId)
        }
    }
    
    fun stopBot(botId: String) {
        viewModelScope.launch {
            botManager.stopBot(botId)
        }
    }
    
    fun pauseBot(botId: String) {
        viewModelScope.launch {
            botManager.pauseBot(botId)
        }
    }
    
    fun resumeBot(botId: String) {
        viewModelScope.launch {
            botManager.resumeBot(botId)
        }
    }
    
    fun deleteBot(botId: String) {
        viewModelScope.launch {
            botManager.deleteBot(botId)
        }
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


data class MainUiState(
    val selectedExchange: String = "Binance",
    val selectedMarketType: MarketType = MarketType.SPOT,
    val selectedTimeFrame: com.godzilla.app.domain.model.TimeFrame = com.godzilla.app.domain.model.TimeFrame.M1,
    val isAggregateMode: Boolean = false,
    val availableExchanges: List<String> = emptyList(),
    val symbol: String = "BTCUSDT",
    val isLoading: Boolean = false,
    val marketData: MarketData? = null,
    val decision: TradingDecision? = null,
    val pairSuggestions: List<String> = emptyList(),
    val mergedExchangesCount: Int = 0,
    val candles: List<Candle> = emptyList(),
    val activeStrategyIds: List<String> = emptyList(),
    val strategyResults: Map<String, TradingDecision> = emptyMap(),
    val errorMessage: String? = null,
    // Bot state
    val botInstances: List<BotInstance> = emptyList(),
    val showBotConfigDialog: Boolean = false,
    val selectedBotForHistory: String? = null, // Bot ID for showing history dialog
    val selectedBotForEdit: String? = null, // Bot ID for showing edit dialog
    val hiddenStrategyNames: Set<String> = emptySet(),
    val allActiveStrategyIds: List<String> = emptyList(),
    val botStrategyResults: Map<String, TradingDecision> = emptyMap(),
    // Map of strategy ID to bot metadata (bot name and symbol)
    val botStrategyMetadata: Map<String, BotStrategyMetadata> = emptyMap(),
    val isMarketOpen: Boolean = true,
    val chartType: com.godzilla.app.domain.model.ChartType = com.godzilla.app.domain.model.ChartType.CANDLESTICK
)

data class BotStrategyMetadata(
    val botName: String,
    val symbol: String,
    val botId: String
)
