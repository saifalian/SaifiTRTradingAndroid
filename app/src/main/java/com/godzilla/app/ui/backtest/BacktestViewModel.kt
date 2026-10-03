package com.godzilla.app.ui.backtest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.godzilla.app.data.repository.BacktestRepository
import com.godzilla.app.domain.BacktestEngine
import com.godzilla.app.domain.TradingEngine
import com.godzilla.app.domain.model.BacktestConfig
import com.godzilla.app.domain.model.BacktestResult
import com.godzilla.app.domain.model.TimeFrame
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BacktestViewModel @Inject constructor(
    private val backtestEngine: BacktestEngine,
    private val backtestRepository: BacktestRepository,
    private val tradingEngine: TradingEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(BacktestUiState())
    val uiState: StateFlow<BacktestUiState> = _uiState.asStateFlow()

    init {
        loadResults()
    }

    private fun loadResults() {
        viewModelScope.launch {
            val results = backtestRepository.getAllResults()
            _uiState.value = _uiState.value.copy(history = results)
        }
    }

    fun updateConfig(config: BacktestConfig) {
        _uiState.value = _uiState.value.copy(config = config)
    }

    fun runBacktest() {
        val currentState = _uiState.value
        if (currentState.isRunning) return

        _uiState.value = currentState.copy(isRunning = true, errorMessage = null)

        viewModelScope.launch {
            try {
                // Get candles from TradingEngine
                // Note: We are using the currently loaded candles in the engine.
                // Ideally, we might want to fetch a specific range, but for now we use what's available.
                val candles = tradingEngine.cryptoState.value.candles
                
                if (candles.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isRunning = false,
                        errorMessage = "No candle data available. Please load a chart first."
                    )
                    return@launch
                }

                val result = backtestEngine.runBacktest(currentState.config, candles)
                backtestRepository.saveResult(result)
                
                val updatedHistory = backtestRepository.getAllResults()
                
                _uiState.value = _uiState.value.copy(
                    isRunning = false,
                    lastResult = result,
                    history = updatedHistory,
                    selectedTab = 1 // Switch to Results tab
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRunning = false,
                    errorMessage = "Backtest failed: ${e.message}"
                )
            }
        }
    }
    
    fun deleteResult(resultId: String) {
        viewModelScope.launch {
            backtestRepository.deleteResult(resultId)
            loadResults()
        }
    }
    
    fun selectResult(result: BacktestResult?) {
        _uiState.value = _uiState.value.copy(selectedResult = result)
    }
}

data class BacktestUiState(
    val config: BacktestConfig = BacktestConfig(),
    val isRunning: Boolean = false,
    val errorMessage: String? = null,
    val lastResult: BacktestResult? = null,
    val history: List<BacktestResult> = emptyList(),
    val selectedTab: Int = 0,
    val selectedResult: BacktestResult? = null
)
