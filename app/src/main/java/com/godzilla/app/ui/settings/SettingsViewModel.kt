package com.godzilla.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.godzilla.app.domain.model.StrategyTemplates
import com.godzilla.app.domain.model.TradingStrategy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: com.godzilla.app.data.local.UserPreferences,
    private val tradingEngine: com.godzilla.app.domain.TradingEngine,
    private val botManager: com.godzilla.app.domain.BotManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadStrategies()
        
        // Observe bot instances to update strategies when bots change
        viewModelScope.launch {
            botManager.botInstances.collect {
                loadStrategies()
            }
        }
    }

    private fun loadStrategies() {
        viewModelScope.launch {
            val allStrategies = StrategyTemplates.getAllTemplates().map { template ->
                // Load saved parameters
                val savedParams = template.parameters.keys.associateWith { key ->
                    userPreferences.getStrategyParameter(template.id, key, template.parameters[key] ?: "")
                }
                template.copy(parameters = savedParams)
            }
            
            val savedActiveIds = userPreferences.getActiveStrategies()
            
            // Get strategies from running bots
            val botStrategies = botManager.botInstances.value
                .filter { it.state == com.godzilla.app.domain.model.BotState.RUNNING }
                .flatMap { bot -> 
                    bot.config.selectedStrategyIds.map { strategyId ->
                        StrategyInfo(strategyId, bot.name, bot.id, bot.symbol, isFromBot = true)
                    }
                }
            
            // Combine dashboard and bot strategies
            val allActiveIds = (savedActiveIds + botStrategies.map { it.strategyId }).distinct()
            
            val activeStrategiesWithInfo = allStrategies.filter { allActiveIds.contains(it.id) }
                .map { strategy ->
                    val isDashboard = savedActiveIds.contains(strategy.id)
                    val botInfos = botStrategies.filter { it.strategyId == strategy.id }
                    ActiveStrategyInfo(
                        strategy = strategy.copy(
                            isActive = true,
                            // Add source info to description
                            description = buildString {
                                append(strategy.description)
                                append("\n\n")
                                if (isDashboard && botInfos.isNotEmpty()) {
                                    append("📊 Dashboard + 🤖 Bots:\n")
                                    botInfos.forEach { 
                                        append("  • ${it.botName} (${it.symbol})\n")
                                    }
                                } else if (isDashboard) {
                                    append("📊 Dashboard Only")
                                } else if (botInfos.isNotEmpty()) {
                                    append("🤖 Bot Only:\n")
                                    botInfos.forEach { 
                                        append("  • ${it.botName} (${it.symbol})\n")
                                    }
                                }
                            }
                        ),
                        isDashboard = isDashboard,
                        botIds = botInfos.map { it.botId }
                    )
                }
            
            val availableStrategies = allStrategies.filter { !allActiveIds.contains(it.id) }
            
            _uiState.value = _uiState.value.copy(
                availableStrategies = availableStrategies,
                activeStrategiesWithInfo = activeStrategiesWithInfo
            )
        }
    }

    fun updateStrategyParameter(strategyId: String, key: String, value: String) {
        userPreferences.saveStrategyParameter(strategyId, key, value)
        loadStrategies()
    }

    fun toggleStrategy(strategy: TradingStrategy) {
        val currentActiveWithInfo = _uiState.value.activeStrategiesWithInfo
        val currentAvailable = _uiState.value.availableStrategies.toMutableList()
        
        if (currentActiveWithInfo.any { it.strategy.id == strategy.id }) {
            // Deactivating - only remove from dashboard (not from bots)
            val savedActiveIds = userPreferences.getActiveStrategies().toMutableSet()
            savedActiveIds.remove(strategy.id)
            userPreferences.saveActiveStrategies(savedActiveIds)
            
            // Move to available if not used by any bot
            val strategyInfo = currentActiveWithInfo.find { it.strategy.id == strategy.id }
            if (strategyInfo?.botIds?.isEmpty() == true) {
                currentAvailable.add(strategy.copy(isActive = false))
            }
        } else {
            // Activating - add to dashboard
            currentAvailable.removeAll { it.id == strategy.id }
            val savedActiveIds = userPreferences.getActiveStrategies().toMutableSet()
            savedActiveIds.add(strategy.id)
            userPreferences.saveActiveStrategies(savedActiveIds)
        }
        
        // Reload to update the UI
        loadStrategies()
    }

    fun isStrategyActive(strategyId: String): Boolean {
        return _uiState.value.activeStrategiesWithInfo.any { it.strategy.id == strategyId }
    }

    fun getMaxCandles(): Int {
        return userPreferences.getConfig("max_candles", "200").toIntOrNull() ?: 200
    }

    fun setMaxCandles(value: Int) {
        userPreferences.saveConfig("max_candles", value.toString())
        tradingEngine.updateMaxCandles(value)
    }

    fun getPriceUpdateDelay(): Long {
        return userPreferences.getPriceUpdateDelay()
    }

    fun setPriceUpdateDelay(delayMs: Long) {
        userPreferences.savePriceUpdateDelay(delayMs)
    }
}

data class SettingsUiState(
    val availableStrategies: List<TradingStrategy> = emptyList(),
    val activeStrategiesWithInfo: List<ActiveStrategyInfo> = emptyList()
)

data class ActiveStrategyInfo(
    val strategy: TradingStrategy,
    val isDashboard: Boolean,
    val botIds: List<String>
)

private data class StrategyInfo(
    val strategyId: String,
    val botName: String,
    val botId: String,
    val symbol: String,
    val isFromBot: Boolean
)
