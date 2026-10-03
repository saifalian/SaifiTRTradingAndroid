package com.godzilla.app.ui.bots

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.godzilla.app.domain.BotManager
import com.godzilla.app.domain.model.BotInstance
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BotsViewModel @Inject constructor(
    private val botManager: BotManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BotsUiState())
    val uiState: StateFlow<BotsUiState> = _uiState.asStateFlow()

    init {
        // Observe all bot instances
        viewModelScope.launch {
            botManager.botInstances.collect { bots ->
                _uiState.value = _uiState.value.copy(
                    allBots = bots,
                    cryptoBots = bots.filter { it.assetClass == com.godzilla.app.domain.model.AssetClass.CRYPTO },
                    forexBots = bots.filter { it.assetClass == com.godzilla.app.domain.model.AssetClass.FOREX },
                    stocksBots = bots.filter { it.assetClass == com.godzilla.app.domain.model.AssetClass.STOCKS }
                )
            }
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

    fun toggleBotHistoryDialog(botId: String?) {
        _uiState.value = _uiState.value.copy(selectedBotForHistory = botId)
    }
}

data class BotsUiState(
    val allBots: List<BotInstance> = emptyList(),
    val cryptoBots: List<BotInstance> = emptyList(),
    val forexBots: List<BotInstance> = emptyList(),
    val stocksBots: List<BotInstance> = emptyList(),
    val selectedBotForHistory: String? = null
)
