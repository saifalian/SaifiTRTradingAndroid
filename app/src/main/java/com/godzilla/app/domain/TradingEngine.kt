package com.godzilla.app.domain

import com.godzilla.app.data.local.UserPreferences
import com.godzilla.app.data.repository.ExchangeRepository
import com.godzilla.app.domain.model.AssetClass
import com.godzilla.app.domain.model.Candle
import com.godzilla.app.domain.model.MarketData
import com.godzilla.app.domain.model.MarketType
import com.godzilla.app.domain.model.TimeFrame
import com.godzilla.app.domain.model.TradingDecision
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.math.ln

@Singleton
class TradingEngine @Inject constructor(
    private val cryptoRepositories: List<@JvmSuppressWildcards ExchangeRepository>,
    @Named("ForexRepositories") private val forexRepositories: List<@JvmSuppressWildcards ExchangeRepository>,
    @Named("StocksRepositories") private val stocksRepositories: List<@JvmSuppressWildcards ExchangeRepository>,
    private val calculator: TradingCalculator,
    private val garchModel: GarchModel,
    private val userPreferences: UserPreferences,
    private val stableStrategy: StableOrderBookStrategy,
    private val liquidityStrategy: LiquidityDrawStrategy,
    private val mySMCStrategy: MySMCStrategy
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Separate states for each asset class
    private val _cryptoState = MutableStateFlow(EngineState(availableExchanges = cryptoRepositories.map { it.exchangeName }))
    val cryptoState: StateFlow<EngineState> = _cryptoState.asStateFlow()

    private val _forexState = MutableStateFlow(EngineState(availableExchanges = forexRepositories.map { it.exchangeName }, symbol = "EURUSD"))
    val forexState: StateFlow<EngineState> = _forexState.asStateFlow()

    private val _stocksState = MutableStateFlow(EngineState(availableExchanges = stocksRepositories.map { it.exchangeName }, symbol = "AAPL"))
    val stocksState: StateFlow<EngineState> = _stocksState.asStateFlow()

    // Map to track polling jobs for each asset class
    private val pollingJobs = mutableMapOf<AssetClass, MutableList<Job>>()
    
    // Internal data storage per asset class
    private val exchangeDataMap = mutableMapOf<AssetClass, MutableMap<String, MarketData>>()
    private val exchangeVolatilityMap = mutableMapOf<AssetClass, MutableMap<String, Double>>()
    private val exchangeCandlesMap = mutableMapOf<AssetClass, MutableMap<String, MutableMap<TimeFrame, List<Candle>>>>()
    
    private val botStates = mutableMapOf<String, MutableStateFlow<EngineState>>()
    private val botJobs = mutableMapOf<String, MutableList<Job>>()
    
    private var maxCandles: Int = userPreferences.getConfig("max_candles", "500").toIntOrNull() ?: 500

    init {
        _cryptoState.value = _cryptoState.value.copy(availableExchanges = cryptoRepositories.map { it.exchangeName })
        _forexState.value = _forexState.value.copy(availableExchanges = forexRepositories.map { it.exchangeName })
        _stocksState.value = _stocksState.value.copy(availableExchanges = stocksRepositories.map { it.exchangeName })

        // Initialize default monitoring if needed, or wait for UI to request it
        if (_cryptoState.value.selectedExchange.isEmpty() && cryptoRepositories.isNotEmpty()) {
            startMonitoring(cryptoRepositories.first().exchangeName, "BTCUSDT", MarketType.SPOT, false, AssetClass.CRYPTO)
        }
    }

    private fun getStateFlow(assetClass: AssetClass): MutableStateFlow<EngineState> {
        return when (assetClass) {
            AssetClass.CRYPTO -> _cryptoState
            AssetClass.FOREX -> _forexState
            AssetClass.STOCKS, AssetClass.INDICES, AssetClass.COMMODITIES -> _stocksState // Grouping stocks/indices for now or separate if needed
        }
    }

    private fun getRepositories(assetClass: AssetClass): List<ExchangeRepository> {
        return when (assetClass) {
            AssetClass.CRYPTO -> cryptoRepositories
            AssetClass.FOREX -> forexRepositories
            AssetClass.STOCKS, AssetClass.INDICES, AssetClass.COMMODITIES -> stocksRepositories
        }
    }

    fun updateMaxCandles(value: Int) {
        maxCandles = value.coerceIn(50, 1000)
        // Restart monitoring for all active contexts
        listOf(AssetClass.CRYPTO, AssetClass.FOREX, AssetClass.STOCKS).forEach { assetClass ->
            val currentState = getStateFlow(assetClass).value
            if (currentState.selectedExchange.isNotEmpty()) {
                startMonitoring(
                    currentState.selectedExchange,
                    currentState.symbol,
                    currentState.selectedMarketType,
                    currentState.isAggregateMode,
                    assetClass
                )
            }
        }
    }

    fun updateActiveStrategies(strategies: List<String>, assetClass: AssetClass) {
        val stateFlow = getStateFlow(assetClass)
        stateFlow.value = stateFlow.value.copy(activeStrategyIds = strategies)
        val currentData = stateFlow.value.marketData
        val currentMetrics = stateFlow.value.decision?.metrics
        if (currentData != null && currentMetrics != null) {
            updateDecision(currentData, currentMetrics, assetClass)
        }
    }

    fun startMonitoring(exchangeName: String, symbol: String, marketType: MarketType, isAggregate: Boolean, assetClass: AssetClass) {
        stopMonitoring(assetClass)
        
        val stateFlow = getStateFlow(assetClass)
        stateFlow.value = stateFlow.value.copy(
            selectedExchange = exchangeName,
            selectedMarketType = marketType,
            symbol = symbol,
            isAggregateMode = isAggregate,
            isLoading = true,
            errorMessage = null,
            isMarketOpen = MarketHours.isMarketOpen(assetClass)
        )

        val allRepos = getRepositories(assetClass)
        val targetRepos = if (isAggregate) {
            allRepos
        } else {
            allRepos.filter { it.exchangeName == exchangeName }
        }

        val assetScope = scope // Can use a specific scope per asset if needed

        assetScope.launch {
            try {
                val requiredTfs = getRequiredTimeFrames(stateFlow.value.activeStrategyIds, stateFlow.value.selectedTimeFrame)
                
                // Initialize maps for this asset class
                val assetCandlesMap = exchangeCandlesMap.getOrPut(assetClass) { mutableMapOf() }
                val assetVolMap = exchangeVolatilityMap.getOrPut(assetClass) { mutableMapOf() }
                val assetDataMap = exchangeDataMap.getOrPut(assetClass) { mutableMapOf() }

                val candleFetchJobs = targetRepos.flatMap { repo ->
                    requiredTfs.map { tf ->
                        async {
                            try {
                                // Fetch maxCandles for all required timeframes to support deep lookbacks
                                val candles = repo.getKLines(symbol, marketType, tf, maxCandles)
                                if (candles.isNotEmpty()) {
                                    synchronized(assetCandlesMap) {
                                        val repoMap = assetCandlesMap.getOrPut(repo.exchangeName) { mutableMapOf() }
                                        repoMap[tf] = candles
                                    }
                                    
                                    if (tf == stateFlow.value.selectedTimeFrame) {
                                        val returns = candles.mapIndexedNotNull { index, candle ->
                                            if (index > 0 && candles[index - 1].close > 0) ln(candle.close / candles[index - 1].close) else null
                                        }
                                        val vol = garchModel.calculateRollingStdDev(returns)
                                        synchronized(assetVolMap) { assetVolMap[repo.exchangeName] = vol }
                                    }
                                }
                            } catch (e: Exception) { 
                                println("⚠️ Error fetching candles for $symbol on ${repo.exchangeName}: ${e.message}")
                            }
                        }
                    }
                }
                candleFetchJobs.awaitAll()
                
                withContext(Dispatchers.Main) {
                    val allCandles = mutableMapOf<TimeFrame, List<Candle>>()
                    requiredTfs.forEach { tf ->
                        val candlesList = synchronized(assetCandlesMap) {
                            assetCandlesMap.values.mapNotNull { it[tf] }
                        }
                        if (candlesList.isNotEmpty()) {
                            val merged = calculator.mergeCandles(candlesList)
                            allCandles[tf] = merged.takeLast(maxCandles)
                        }
                    }

                    val finalCandles = allCandles[stateFlow.value.selectedTimeFrame] ?: emptyList()
                    stateFlow.value = stateFlow.value.copy(
                        mergedExchangesCount = targetRepos.size,
                        candles = finalCandles,
                        candlesH1 = allCandles[TimeFrame.H1] ?: emptyList(),
                        candlesH4 = allCandles[TimeFrame.H4] ?: emptyList(),
                        allCandles = allCandles,
                        isLoading = false,
                        errorMessage = if (finalCandles.isEmpty()) "No data available for $symbol. Check if the symbol is correct (e.g., EURUSD or AAPL)." else null
                    )
                }
                
                val jobs = pollingJobs.getOrPut(assetClass) { mutableListOf() }
                
                targetRepos.forEach { repo ->
                    val vol = assetVolMap[repo.exchangeName] ?: 0.0
                    val job = scope.launch {
                        try {
                            repo.getMarketData(symbol, marketType).collect { marketData ->
                                synchronized(assetDataMap) {
                                    assetDataMap[repo.exchangeName] = marketData
                                    if (isAggregate) {
                                        processAggregateData(symbol, assetClass)
                                    } else {
                                        processMarketData(marketData, vol, assetClass)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            println("❌ Error polling from ${repo.exchangeName}: ${e.message}")
                        }
                    }
                    jobs.add(job)
                }
                
                // Start strategy execution loop
                val strategyJob = startStrategyLoop(assetClass)
                jobs.add(strategyJob)
                
                val candleRefreshJob = scope.launch {
                    // Dynamic refresh interval: 30% of timeframe duration, min 10s, max 60s
                    val initialTimeFrame = stateFlow.value.selectedTimeFrame
                    val initialDelayMs = (initialTimeFrame.minutes * 60 * 1000 * 0.3).toLong().coerceIn(10000L, 60000L)
                    delay(initialDelayMs)
                    while (isActive) {
                        try {
                            val currentTimeFrame = stateFlow.value.selectedTimeFrame
                            val refreshDelayMs = (currentTimeFrame.minutes * 60 * 1000 * 0.3).toLong().coerceIn(10000L, 60000L)
                            
                            targetRepos.forEach { repo ->
                                async {
                                    try {
                                        val freshCandles = repo.getKLines(symbol, marketType, currentTimeFrame, maxCandles)
                                        if (freshCandles.isNotEmpty()) {
                                            synchronized(assetCandlesMap) {
                                                val repoMap = assetCandlesMap.getOrPut(repo.exchangeName) { mutableMapOf() }
                                                repoMap[currentTimeFrame] = freshCandles
                                            }
                                        }
                                    } catch (e: Exception) {
                                        println("⚠️ Error refreshing candles: ${e.message}")
                                    }
                                }
                            }
                            
                            withContext(Dispatchers.Main) {
                                if (!isAggregate) {
                                    val fresh = assetCandlesMap[exchangeName]?.get(currentTimeFrame) ?: emptyList()
                                    stateFlow.value = stateFlow.value.copy(candles = fresh)
                                } else {
                                    refreshMergedCandles(assetClass)
                                }
                            }
                            
                            delay(refreshDelayMs)
                        } catch (e: Exception) {
                            println("❌ Error in candle refresh: ${e.message}")
                        }
                    }
                }
                jobs.add(candleRefreshJob)
                
                // Start market status check loop
                val marketStatusJob = scope.launch {
                    while (isActive) {
                        val isOpen = MarketHours.isMarketOpen(assetClass)
                        val currentState = getStateFlow(assetClass).value
                        if (currentState.isMarketOpen != isOpen) {
                            getStateFlow(assetClass).value = currentState.copy(isMarketOpen = isOpen)
                        }
                        delay(60000) // Check every minute
                    }
                }
                jobs.add(marketStatusJob)
                
            } catch (e: Exception) {
                println("❌ Fatal error in startMonitoring: ${e.message}")
                stateFlow.value = stateFlow.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load data: ${e.message}"
                )
            }
        }
    }

    fun changeTimeFrame(timeFrame: TimeFrame, assetClass: AssetClass) {
        val stateFlow = getStateFlow(assetClass)
        stateFlow.value = stateFlow.value.copy(selectedTimeFrame = timeFrame)
        val currentState = stateFlow.value
        startMonitoring(
            currentState.selectedExchange,
            currentState.symbol,
            currentState.selectedMarketType,
            currentState.isAggregateMode,
            assetClass
        )
    }
    
    private fun getRequiredTimeFrames(activeStrategyIds: List<String>, selectedTimeFrame: TimeFrame): Set<TimeFrame> {
        val tfs = mutableSetOf(selectedTimeFrame, TimeFrame.H1, TimeFrame.H4)
        if (activeStrategyIds.contains("my_smc")) {
            val params = com.godzilla.app.domain.model.StrategyTemplates.MY_SMC.parameters.keys.associateWith { key ->
                userPreferences.getStrategyParameter("my_smc", key, com.godzilla.app.domain.model.StrategyTemplates.MY_SMC.parameters[key] ?: "")
            }
            val config = MySMCStrategy.Config.fromParameters(params)
            tfs.addAll(config.htfTimeFrames)
            tfs.addAll(config.itfTimeFrames)
            tfs.addAll(config.ltfTimeFrames)
        }
        return tfs
    }

    private suspend fun refreshMergedCandles(assetClass: AssetClass) {
        val stateFlow = getStateFlow(assetClass)
        val requiredTfs = getRequiredTimeFrames(stateFlow.value.activeStrategyIds, stateFlow.value.selectedTimeFrame)
        val allCandles = mutableMapOf<TimeFrame, List<Candle>>()
        val assetCandlesMap = exchangeCandlesMap[assetClass] ?: return
        
        requiredTfs.forEach { tf ->
            val candlesList = assetCandlesMap.values.mapNotNull { it[tf] }
            if (candlesList.isNotEmpty()) {
                val merged = calculator.mergeCandles(candlesList)
                allCandles[tf] = merged.takeLast(maxCandles)
            }
        }

        stateFlow.value = stateFlow.value.copy(
            candles = allCandles[stateFlow.value.selectedTimeFrame] ?: emptyList(),
            candlesH1 = allCandles[TimeFrame.H1] ?: emptyList(),
            candlesH4 = allCandles[TimeFrame.H4] ?: emptyList(),
            allCandles = allCandles
        )
    }

    private fun processMarketData(data: MarketData, volatility: Double, assetClass: AssetClass) {
        val stateFlow = getStateFlow(assetClass)
        val metrics = calculator.calculateMetrics(data, volatility)
        
        val currentCandles = stateFlow.value.candles.toMutableList()
        val updatedAllCandles = stateFlow.value.allCandles.toMutableMap()
        val currentPrice = (data.bestBid + data.bestAsk) / 2.0
        val currentTimeMs = System.currentTimeMillis()
        val timeframeDurationMs = stateFlow.value.selectedTimeFrame.minutes * 60 * 1000L
        
        if (currentCandles.isNotEmpty()) {
            val lastCandle = currentCandles.last()
            val candleEndTime = lastCandle.timestamp + timeframeDurationMs
            
            if (currentTimeMs >= candleEndTime) {
                // Time for a new candle - create one starting at the expected timestamp
                val newCandleTimestamp = lastCandle.timestamp + timeframeDurationMs
                val newCandle = Candle(
                    timestamp = newCandleTimestamp,
                    open = currentPrice,
                    high = currentPrice,
                    low = currentPrice,
                    close = currentPrice,
                    volume = 0.0
                )
                currentCandles.add(newCandle)
                // Keep list size manageable
                if (currentCandles.size > maxCandles) {
                    currentCandles.removeAt(0)
                }
            } else {
                // Update existing last candle
                val updatedCandle = lastCandle.copy(
                    close = currentPrice,
                    high = maxOf(lastCandle.high, currentPrice),
                    low = minOf(lastCandle.low, currentPrice)
                )
                currentCandles[currentCandles.lastIndex] = updatedCandle
            }
            updatedAllCandles[stateFlow.value.selectedTimeFrame] = currentCandles
        }
        
        stateFlow.value = stateFlow.value.copy(
            candles = currentCandles,
            allCandles = updatedAllCandles,
            marketData = data
        )
        
        // updateDecision(data, metrics, assetClass) - Removed synchronous call
    }

    private fun processAggregateData(symbol: String, assetClass: AssetClass) {
        val assetDataMap = exchangeDataMap[assetClass] ?: return
        val allData = assetDataMap.values.toList()
        if (allData.isEmpty()) return

        val totalBids = allData.flatMap { it.bids }.sortedByDescending { it.price }
        val totalAsks = allData.flatMap { it.asks }.sortedBy { it.price }
        
        val avgBestBid = allData.map { it.bestBid }.average()
        val avgBestAsk = allData.map { it.bestAsk }.average()
        
        val assetVolMap = exchangeVolatilityMap[assetClass]
        val avgVolatility = assetVolMap?.values?.average() ?: 0.0

        val aggregateData = MarketData(
            symbol = symbol,
            bestBid = avgBestBid,
            bestAsk = avgBestAsk,
            bids = totalBids,
            asks = totalAsks
        )

        val stateFlow = getStateFlow(assetClass)
        val currentCandles = stateFlow.value.candles.toMutableList()
        val updatedAllCandles = stateFlow.value.allCandles.toMutableMap()
        val currentPrice = (avgBestBid + avgBestAsk) / 2.0
        val currentTimeMs = System.currentTimeMillis()
        val timeframeDurationMs = stateFlow.value.selectedTimeFrame.minutes * 60 * 1000L
        
        if (currentCandles.isNotEmpty()) {
            val lastCandle = currentCandles.last()
            val candleEndTime = lastCandle.timestamp + timeframeDurationMs
            
            if (currentTimeMs >= candleEndTime) {
                // Time for a new candle
                val newCandleTimestamp = lastCandle.timestamp + timeframeDurationMs
                val newCandle = Candle(
                    timestamp = newCandleTimestamp,
                    open = currentPrice,
                    high = currentPrice,
                    low = currentPrice,
                    close = currentPrice,
                    volume = 0.0
                )
                currentCandles.add(newCandle)
                if (currentCandles.size > maxCandles) {
                    currentCandles.removeAt(0)
                }
            } else {
                // Update existing last candle
                val updatedCandle = lastCandle.copy(
                    close = currentPrice,
                    high = maxOf(lastCandle.high, currentPrice),
                    low = minOf(lastCandle.low, currentPrice)
                )
                currentCandles[currentCandles.lastIndex] = updatedCandle
            }
            updatedAllCandles[stateFlow.value.selectedTimeFrame] = currentCandles
        }
        
        stateFlow.value = stateFlow.value.copy(
            candles = currentCandles,
            allCandles = updatedAllCandles,
            marketData = aggregateData,
            mergedExchangesCount = allData.size
        )
        // updateDecision(aggregateData, metrics, assetClass) - Removed synchronous call
    }

    private fun updateDecision(data: MarketData, metrics: com.godzilla.app.domain.model.TradeMetrics, assetClass: AssetClass) {
        val stateFlow = getStateFlow(assetClass)
        val results = mutableMapOf<String, TradingDecision>()

        if (stateFlow.value.activeStrategyIds.contains("godzilla_garch")) {
            val config = TradingCalculator.Config(
                imbalanceThreshold = userPreferences.getConfig("imbalance_threshold", "0.3").toDouble(),
                maxRelativeSpread = userPreferences.getConfig("max_spread", "0.005").toDouble(),
                maxVolatility = userPreferences.getConfig("max_vol", "0.05").toDouble()
            )
            results["Godzilla GARCH"] = calculator.makeDecision(metrics, config)
        }

        if (stateFlow.value.activeStrategyIds.contains("stable_orderbook_basic")) {
            val config = StableOrderBookStrategy.Config(
                imbalanceEnterThreshold = userPreferences.getConfig("imbalance_enter", "0.3").toDouble(),
                imbalanceExitThreshold = userPreferences.getConfig("imbalance_exit", "0.1").toDouble(),
                confirmationTimeSeconds = userPreferences.getConfig("confirmation_time", "5").toLong(),
                cooldownTimeSeconds = userPreferences.getConfig("cooldown_time", "30").toLong(),
                leverageMode = StableOrderBookStrategy.LeverageMode.BALANCED,
                manualLeverageOverride = false
            )
            results["Stable Order-Book (Basic)"] = stableStrategy.makeDecision(data, config)
        }

        if (stateFlow.value.activeStrategyIds.contains("stable_orderbook")) {
            val modeString = userPreferences.getConfig("leverage_mode", "BALANCED")
            val leverageMode = try {
                StableOrderBookStrategy.LeverageMode.valueOf(modeString)
            } catch (e: Exception) {
                StableOrderBookStrategy.LeverageMode.BALANCED
            }

            val config = StableOrderBookStrategy.Config(
                imbalanceEnterThreshold = userPreferences.getConfig("imbalance_enter", "0.3").toDouble(),
                imbalanceExitThreshold = userPreferences.getConfig("imbalance_exit", "0.1").toDouble(),
                confirmationTimeSeconds = userPreferences.getConfig("confirmation_time", "5").toLong(),
                cooldownTimeSeconds = userPreferences.getConfig("cooldown_time", "30").toLong(),
                maxUserLeverageCap = userPreferences.getConfig("max_user_leverage", "10.0").toDouble(),
                riskPerTrade = userPreferences.getConfig("risk_per_trade", "0.02").toDouble(),
                leverageMode = leverageMode,
                manualLeverageOverride = userPreferences.getConfig("manual_leverage", "false").toBoolean(),
                manualLeverageValue = userPreferences.getConfig("manual_leverage_value", "1.0").toDouble()
            )
            results["Stable Order-Book (Leverage)"] = stableStrategy.makeDecision(data, config)
        }

        if (stateFlow.value.activeStrategyIds.contains("liquidity_draw")) {
            results["Liquidity Draw (SMC)"] = liquidityStrategy.makeDecision(
                symbol = stateFlow.value.symbol,
                ltfCandles = stateFlow.value.candles,
                h1Candles = stateFlow.value.candlesH1,
                h4Candles = stateFlow.value.candlesH4
            )
        }

        if (stateFlow.value.activeStrategyIds.contains("my_smc")) {
            val params = com.godzilla.app.domain.model.StrategyTemplates.MY_SMC.parameters.keys.associateWith { key ->
                userPreferences.getStrategyParameter("my_smc", key, com.godzilla.app.domain.model.StrategyTemplates.MY_SMC.parameters[key] ?: "")
            }
            val config = MySMCStrategy.Config.fromParameters(params)

            results["MY SMC"] = mySMCStrategy.makeDecision(
                symbol = stateFlow.value.symbol,
                candlesMap = stateFlow.value.allCandles,
                metrics = metrics,
                config = config
            )
        }

        val firstDecision = results.values.firstOrNull()
        
        stateFlow.value = stateFlow.value.copy(
            isLoading = false,
            // marketData = data, // Already updated in processMarketData
            decision = firstDecision,
            strategyResults = results
        )
    }

    private fun startStrategyLoop(assetClass: AssetClass): Job {
        return scope.launch(Dispatchers.Default) {
            while (isActive) {
                try {
                    val state = getStateFlow(assetClass).value
                    if (state.marketData != null && !state.isLoading && state.activeStrategyIds.isNotEmpty()) {
                        val vol = exchangeVolatilityMap[assetClass]?.let { volMap ->
                            synchronized(volMap) {
                                if (volMap.isEmpty()) 0.0 
                                else volMap.values.average().let { if (it.isNaN()) 0.0 else it }
                            }
                        } ?: 0.0
                        val metrics = calculator.calculateMetrics(state.marketData, vol)
                        updateDecision(state.marketData, metrics, assetClass)
                    }
                } catch (e: Exception) {
                    println("❌ Error in strategy loop: ${e.message}")
                }
                delay(250) // Throttle to 250ms
            }
        }
    }
    
    fun getLatestDecision(assetClass: AssetClass): TradingDecision? {
        return getStateFlow(assetClass).value.decision
    }
    
    fun getCurrentMarketData(assetClass: AssetClass): MarketData? {
        return getStateFlow(assetClass).value.marketData
    }

    private fun stopMonitoring(assetClass: AssetClass) {
        pollingJobs[assetClass]?.forEach { it.cancel() }
        pollingJobs[assetClass]?.clear()
        
        exchangeDataMap[assetClass]?.clear()
        exchangeVolatilityMap[assetClass]?.clear()
        exchangeCandlesMap[assetClass]?.clear()
        
        val stateFlow = getStateFlow(assetClass)
        stateFlow.value = stateFlow.value.copy(
            isLoading = false, 
            mergedExchangesCount = 0, 
            candles = emptyList(),
            errorMessage = null
        )
    }

    fun startMonitoringForBot(botId: String, exchangeName: String, symbol: String, marketType: MarketType, isAggregate: Boolean, activeStrategies: List<String>, allowedTimeFrames: List<TimeFrame>, assetClass: AssetClass) {
        stopMonitoringForBot(botId)
        
        val botStateFlow = MutableStateFlow(EngineState(
            selectedExchange = exchangeName,
            selectedMarketType = marketType,
            symbol = symbol,
            isAggregateMode = isAggregate,
            activeStrategyIds = activeStrategies,
            isLoading = true
        ))
        botStates[botId] = botStateFlow
        
        val jobs = mutableListOf<Job>()
        botJobs[botId] = jobs

        val allRepos = getRepositories(assetClass)
        val targetRepos = if (isAggregate) {
            allRepos
        } else {
            allRepos.filter { it.exchangeName == exchangeName }
        }

        val botScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        
        val botCandlesMap = mutableMapOf<String, MutableMap<TimeFrame, List<Candle>>>()
        val botVolatilityMap = mutableMapOf<String, Double>()
        
        val monitorJob = botScope.launch {
            try {
                val requiredTfs = getRequiredTimeFrames(activeStrategies, TimeFrame.M1) // Default to M1 for bots base
                
                val candleFetchJobs = targetRepos.flatMap { repo ->
                    requiredTfs.map { tf ->
                        async {
                            try {
                                val candles = repo.getKLines(symbol, marketType, tf, if (tf == TimeFrame.M1) maxCandles else 100)
                                if (candles.isNotEmpty()) {
                                    synchronized(botCandlesMap) {
                                        val repoMap = botCandlesMap.getOrPut(repo.exchangeName) { mutableMapOf() }
                                        repoMap[tf] = candles
                                    }
                                    
                                    if (tf == TimeFrame.M1 || (allowedTimeFrames.isNotEmpty() && tf == allowedTimeFrames.first())) {
                                        val returns = candles.mapIndexedNotNull { index, candle ->
                                            if (index > 0 && candles[index - 1].close > 0) ln(candle.close / candles[index - 1].close) else null
                                        }
                                        val vol = garchModel.calculateRollingStdDev(returns)
                                        synchronized(botVolatilityMap) { botVolatilityMap[repo.exchangeName] = vol }
                                    }
                                }
                            } catch (e: Exception) {}
                        }
                    }
                }
                
                candleFetchJobs.awaitAll()
                
                val allCandles = mutableMapOf<TimeFrame, List<Candle>>()
                requiredTfs.forEach { tf ->
                    val candlesList = botCandlesMap.values.mapNotNull { it[tf] }
                    if (candlesList.isNotEmpty()) {
                        allCandles[tf] = calculator.mergeCandles(candlesList)
                    }
                }

                botStateFlow.value = botStateFlow.value.copy(
                    candles = allCandles[TimeFrame.M1] ?: emptyList(),
                    candlesH1 = allCandles[TimeFrame.H1] ?: emptyList(),
                    candlesH4 = allCandles[TimeFrame.H4] ?: emptyList(),
                    allCandles = allCandles,
                    isLoading = false
                )

                targetRepos.forEach { repo ->
                    val vol = botVolatilityMap[repo.exchangeName] ?: 0.0
                    val job = botScope.launch {
                        try {
                            repo.getMarketData(symbol, marketType).collect { marketData ->
                                val currentState = botStateFlow.value
                                val metrics = calculator.calculateMetrics(marketData, vol)
                                
                                val currentCandles = currentState.candles.toMutableList()
                                val currentPrice = (marketData.bestBid + marketData.bestAsk) / 2.0
                                val currentTimeMs = System.currentTimeMillis()
                                val timeframeDurationMs = TimeFrame.M1.minutes * 60 * 1000L
                                
                                if (currentCandles.isNotEmpty()) {
                                    val lastCandle = currentCandles.last()
                                    val candleEndTime = lastCandle.timestamp + timeframeDurationMs
                                    
                                    if (currentTimeMs >= candleEndTime) {
                                        // Time for a new candle
                                        val newCandleTimestamp = lastCandle.timestamp + timeframeDurationMs
                                        val newCandle = Candle(
                                            timestamp = newCandleTimestamp,
                                            open = currentPrice,
                                            high = currentPrice,
                                            low = currentPrice,
                                            close = currentPrice,
                                            volume = 0.0
                                        )
                                        currentCandles.add(newCandle)
                                        if (currentCandles.size > maxCandles) {
                                            currentCandles.removeAt(0)
                                        }
                                    } else {
                                        val updatedCandle = lastCandle.copy(
                                            close = currentPrice,
                                            high = maxOf(lastCandle.high, currentPrice),
                                            low = minOf(lastCandle.low, currentPrice)
                                        )
                                        currentCandles[currentCandles.lastIndex] = updatedCandle
                                    }
                                }
                                
                                val updatedAllCandles = currentState.allCandles.toMutableMap()
                                updatedAllCandles[TimeFrame.M1] = currentCandles
                                
                                // Strategy execution moved to separate loop

                                botStateFlow.value = currentState.copy(
                                    marketData = marketData,
                                    candles = currentCandles,
                                    allCandles = updatedAllCandles
                                    // strategyResults updated by separate loop
                                )
                            }
                        } catch (e: Exception) {
                            println("❌ Fatal error in bot monitoring: ${e.message}")
                        }
                    }
                    jobs.add(job)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        jobs.add(monitorJob)
        
        // Start bot strategy loop
        val botStrategyJob = botScope.launch(Dispatchers.Default) {
            while (isActive) {
                try {
                    val currentState = botStateFlow.value
                    if (currentState.marketData != null && currentState.activeStrategyIds.isNotEmpty()) {
                        val vol = synchronized(botVolatilityMap) {
                            if (botVolatilityMap.isEmpty()) 0.0 
                            else botVolatilityMap.values.average().let { if (it.isNaN()) 0.0 else it }
                        }
                        val metrics = calculator.calculateMetrics(currentState.marketData, vol)
                        
                        val results = mutableMapOf<String, TradingDecision>()
                        
                        if (currentState.activeStrategyIds.contains("godzilla_garch")) {
                             val config = TradingCalculator.Config(
                                imbalanceThreshold = userPreferences.getConfig("imbalance_threshold", "0.3").toDouble(),
                                maxRelativeSpread = userPreferences.getConfig("max_spread", "0.005").toDouble(),
                                maxVolatility = userPreferences.getConfig("max_vol", "0.05").toDouble()
                            )
                            results["Godzilla GARCH"] = calculator.makeDecision(metrics, config)
                        }
                        
                        if (currentState.activeStrategyIds.contains("liquidity_draw")) {
                            results["Liquidity Draw (SMC)"] = liquidityStrategy.makeDecision(
                                symbol = symbol,
                                ltfCandles = currentState.candles,
                                h1Candles = currentState.candlesH1,
                                h4Candles = currentState.candlesH4
                            )
                        }

                        if (currentState.activeStrategyIds.contains("my_smc")) {
                            val params = com.godzilla.app.domain.model.StrategyTemplates.MY_SMC.parameters.keys.associateWith { key ->
                                userPreferences.getStrategyParameter("my_smc", key, com.godzilla.app.domain.model.StrategyTemplates.MY_SMC.parameters[key] ?: "")
                            }
                            val config = MySMCStrategy.Config.fromParameters(params)

                            results["MY SMC"] = mySMCStrategy.makeDecision(
                                symbol = symbol,
                                candlesMap = currentState.allCandles,
                                metrics = metrics,
                                config = config
                            )
                        }

                        botStateFlow.value = currentState.copy(
                            strategyResults = results
                        )
                    }
                } catch (e: Exception) {
                    println("❌ Error in bot strategy loop: ${e.message}")
                }
                delay(250) // Throttle bot strategy to 250ms
            }
        }
        jobs.add(botStrategyJob)
    }

    fun stopMonitoringForBot(botId: String) {
        botJobs[botId]?.forEach { it.cancel() }
        botJobs.remove(botId)
        botStates.remove(botId)
    }

    fun getDecisionForBot(botId: String): EngineState? {
        return botStates[botId]?.value
    }
}

data class EngineState(
    val selectedExchange: String = "",
    val selectedMarketType: MarketType = MarketType.SPOT,
    val selectedTimeFrame: TimeFrame = TimeFrame.M1,
    val isAggregateMode: Boolean = false,
    val availableExchanges: List<String> = emptyList(),
    val symbol: String = "BTCUSDT",
    val isLoading: Boolean = false,
    val marketData: MarketData? = null,
    val decision: TradingDecision? = null,
    val mergedExchangesCount: Int = 0,
    val candles: List<Candle> = emptyList(),
    val candlesH1: List<Candle> = emptyList(),
    val candlesH4: List<Candle> = emptyList(),
    val allCandles: Map<TimeFrame, List<Candle>> = emptyMap(),
    val activeStrategyIds: List<String> = emptyList(),
    val strategyResults: Map<String, TradingDecision> = emptyMap(),
    val errorMessage: String? = null,
    val isMarketOpen: Boolean = true
)
