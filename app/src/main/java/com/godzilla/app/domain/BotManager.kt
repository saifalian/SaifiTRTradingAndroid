package com.godzilla.app.domain

import com.godzilla.app.data.local.UserPreferences
import com.godzilla.app.domain.model.BotConfig
import com.godzilla.app.domain.model.BotInstance
import com.godzilla.app.domain.model.BotState
import com.godzilla.app.domain.model.MarketType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BotManager manages multiple BotTrader instances.
 * Each bot can trade a different pair and market type independently.
 */
@Singleton
class BotManager @Inject constructor(
    private val tradingEngine: TradingEngine,
    private val userPreferences: UserPreferences
) {
    companion object {
        const val MAX_BOTS = 10
    }

    private val activeBots = mutableMapOf<String, BotTrader>()
    private val _botInstances = MutableStateFlow<List<BotInstance>>(emptyList())
    val botInstances: StateFlow<List<BotInstance>> = _botInstances.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default)
    private val gson = Gson()

    init {
        loadBots()
    }

    /**
     * Create a new bot instance
     */
    fun createBot(
        name: String,
        symbol: String,
        marketType: MarketType,
        exchange: String,
        assetClass: com.godzilla.app.domain.model.AssetClass = com.godzilla.app.domain.model.AssetClass.CRYPTO,
        config: BotConfig,
        isAggregateMode: Boolean = false
    ): Result<String> {
        if (_botInstances.value.size >= MAX_BOTS) {
            return Result.failure(Exception("Maximum number of bots ($MAX_BOTS) reached"))
        }

        val botId = UUID.randomUUID().toString()
        val botName = name.ifBlank { 
            if (isAggregateMode) "$symbol ${marketType.name} AGG Bot" 
            else "$symbol ${marketType.name} Bot" 
        }

        val instance = BotInstance(
            id = botId,
            name = botName,
            symbol = symbol,
            marketType = marketType,
            exchange = exchange,
            assetClass = assetClass,
            isAggregateMode = isAggregateMode,
            config = config,
            currentCapital = config.initialCapital
        )

        val botTrader = BotTrader(
            botId = botId,
            symbol = symbol,
            marketType = marketType,
            exchange = exchange,
            assetClass = assetClass,
            tradingEngine = tradingEngine
        )

        activeBots[botId] = botTrader
        _botInstances.value = _botInstances.value + instance

        // Observe bot state changes
        observeBotState(botId, botTrader)
        saveBots()

        return Result.success(botId)
    }

    /**
     * Start a bot
     */
    fun startBot(botId: String): Result<Unit> {
        val bot = activeBots[botId] ?: return Result.failure(Exception("Bot not found"))
        val instance = _botInstances.value.find { it.id == botId }
            ?: return Result.failure(Exception("Bot instance not found"))

        // Ensure TradingEngine is monitoring this bot's pair
        tradingEngine.startMonitoring(
            instance.exchange,
            instance.symbol,
            instance.marketType,
            instance.isAggregateMode,
            instance.assetClass
        )

        bot.startBot(instance.config)
        updateBotState(botId, BotState.RUNNING)

        return Result.success(Unit)
    }

    /**
     * Stop a bot
     */
    fun stopBot(botId: String): Result<Unit> {
        val bot = activeBots[botId] ?: return Result.failure(Exception("Bot not found"))
        bot.stopBot()
        updateBotState(botId, BotState.STOPPED)
        return Result.success(Unit)
    }

    /**
     * Pause a bot
     */
    fun pauseBot(botId: String): Result<Unit> {
        val bot = activeBots[botId] ?: return Result.failure(Exception("Bot not found"))
        bot.pauseBot()
        updateBotState(botId, BotState.PAUSED)
        return Result.success(Unit)
    }

    /**
     * Resume a bot
     */
    fun resumeBot(botId: String): Result<Unit> {
        val bot = activeBots[botId] ?: return Result.failure(Exception("Bot not found"))
        val instance = _botInstances.value.find { it.id == botId }
            ?: return Result.failure(Exception("Bot instance not found"))

        // Ensure TradingEngine is monitoring this bot's pair
        tradingEngine.startMonitoring(
            instance.exchange,
            instance.symbol,
            instance.marketType,
            instance.isAggregateMode,
            instance.assetClass
        )

        bot.resumeBot()
        updateBotState(botId, BotState.RUNNING)
        return Result.success(Unit)
    }

    /**
     * Delete a bot
     */
    fun deleteBot(botId: String): Result<Unit> {
        val bot = activeBots[botId]
        if (bot != null) {
            bot.stopBot()
            activeBots.remove(botId)
        }

        _botInstances.value = _botInstances.value.filter { it.id != botId }
        saveBots()
        return Result.success(Unit)
    }

    /**
     * Update a bot's configuration
     */
    fun updateBotConfig(botId: String, name: String, config: BotConfig): Result<Unit> {
        val bot = activeBots[botId] ?: return Result.failure(Exception("Bot not found"))
        
        bot.updateConfig(config)
        updateBotInstance(botId) { it.copy(name = name, config = config) }
        
        return Result.success(Unit)
    }

    /**
     * Get a specific bot instance
     */
    fun getBot(botId: String): BotInstance? {
        return _botInstances.value.find { it.id == botId }
    }

    /**
     * Observe bot state changes and update the instance
     */
    private fun observeBotState(botId: String, botTrader: BotTrader) {
        scope.launch {
            botTrader.state.collect { state ->
                updateBotState(botId, state)
            }
        }

        scope.launch {
            botTrader.currentCapital.collect { capital ->
                updateBotInstance(botId) { it.copy(currentCapital = capital) }
            }
        }

        scope.launch {
            botTrader.totalProfitLoss.collect { pnl ->
                updateBotInstance(botId) { it.copy(totalProfitLoss = pnl) }
            }
        }

        scope.launch {
            botTrader.completedTrades.collect { count ->
                updateBotInstance(botId) { it.copy(completedTrades = count) }
            }
        }

        scope.launch {
            botTrader.currentPosition.collect { position ->
                updateBotInstance(botId) { it.copy(currentPosition = position) }
            }
        }

        scope.launch {
            botTrader.tradeHistory.collect { history ->
                updateBotInstance(botId) { it.copy(tradeHistory = history) }
            }
        }

        scope.launch {
            botTrader.activeStrategyResults.collect { results ->
                updateBotInstance(botId) { it.copy(activeStrategyResults = results) }
            }
        }

        scope.launch {
            botTrader.currentUnrealizedPnL.collect { pnl ->
                updateBotInstance(botId) { it.copy(currentUnrealizedPnL = pnl) }
            }
        }

        scope.launch {
            botTrader.activePositions.collect { positions ->
                updateBotInstance(botId) { it.copy(activePositions = positions) }
            }
        }
    }

    private fun updateBotState(botId: String, state: BotState) {
        updateBotInstance(botId) { it.copy(state = state, lastUpdated = System.currentTimeMillis()) }
    }

    private fun updateBotInstance(botId: String, update: (BotInstance) -> BotInstance) {
        _botInstances.value = _botInstances.value.map { instance ->
            if (instance.id == botId) update(instance) else instance
        }
        saveBots()
    }

    private fun saveBots() {
        val json = gson.toJson(_botInstances.value)
        userPreferences.saveBots(json)
    }

    private fun loadBots() {
        val json = userPreferences.getBots() ?: return
        try {
            val type = object : TypeToken<List<BotInstance>>() {}.type
            val bots: List<BotInstance> = gson.fromJson(json, type)
            
            _botInstances.value = bots
            
            // Recreate BotTrader instances
            bots.forEach { instance ->
                val botTrader = BotTrader(
                    botId = instance.id,
                    symbol = instance.symbol,
                    marketType = instance.marketType,
                    exchange = instance.exchange,
                    assetClass = instance.assetClass,
                    tradingEngine = tradingEngine
                )
                
                // Restore state to BotTrader
                botTrader.restoreState(instance)
                
                activeBots[instance.id] = botTrader
                observeBotState(instance.id, botTrader)

                // Ensure engine monitors this pair if bot is active (PAUSED or RUNNING)
                if (instance.state != BotState.STOPPED) {
                    tradingEngine.startMonitoring(
                        instance.exchange,
                        instance.symbol,
                        instance.marketType,
                        instance.isAggregateMode,
                        instance.assetClass
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Stop all bots (e.g., when app is closing)
     */
    fun stopAllBots() {
        activeBots.values.forEach { it.stopBot() }
    }
}

