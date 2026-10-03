package com.godzilla.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.godzilla.app.domain.model.BotState
import com.godzilla.app.domain.model.MarketType
import com.godzilla.app.domain.model.TradeDirection
import com.godzilla.app.domain.model.TradingDecision
import com.godzilla.app.ui.components.InteractiveCandlestickChart
import com.godzilla.app.ui.components.OrderBookView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val hiddenBotIds = remember { mutableStateListOf<String>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saifi TR") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Aggregate Mode Button
            Button(
                onClick = { viewModel.startMonitoring(uiState.selectedExchange, uiState.symbol, uiState.selectedMarketType, !uiState.isAggregateMode) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isAggregateMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            ) {
                Text(if (uiState.isAggregateMode) "AGGREGATE MODE: ON" else "TURN ON AGGREGATE MODE")
            }

            // Trading Bots Section
            Text(
                "Trading Bots",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Create New Bot Button
            OutlinedButton(
                onClick = { viewModel.toggleBotConfigDialog(true) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Bot")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create New Bot")
            }

            // Bot List
            if (uiState.botInstances.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "No bots created yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Create your first bot to start automated trading",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                uiState.botInstances.forEach { bot ->
                    BotCard(
                        bot = bot,
                        isTradeVisible = !hiddenBotIds.contains(bot.id),
                        onToggleTradeVisibility = {
                            if (hiddenBotIds.contains(bot.id)) {
                                hiddenBotIds.remove(bot.id)
                            } else {
                                hiddenBotIds.add(bot.id)
                            }
                        },
                        onStartClick = { viewModel.startBot(bot.id) },
                        onStopClick = { viewModel.stopBot(bot.id) },
                        onPauseClick = { viewModel.pauseBot(bot.id) },
                        onResumeClick = { viewModel.resumeBot(bot.id) },
                        onDeleteClick = { viewModel.deleteBot(bot.id) },
                        onEditClick = { viewModel.toggleBotEditDialog(bot.id) },
                        onViewHistoryClick = { viewModel.toggleBotHistoryDialog(bot.id) },

                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )
                }
            }

            // Create Bot Dialog
            if (uiState.showBotConfigDialog) {
                CreateBotDialog(
                    availableExchanges = uiState.availableExchanges,
                    currentSymbol = uiState.symbol,
                    currentExchange = uiState.selectedExchange,
                    currentMarketType = uiState.selectedMarketType,
                    onDismiss = { viewModel.toggleBotConfigDialog(false) },
                    onCreate = { name, symbol, marketType, exchange, config, isAggregateMode ->
                        viewModel.createBot(name, symbol, marketType, exchange, config, isAggregateMode)
                        viewModel.toggleBotConfigDialog(false)
                    }
                )
            }

            // Edit Bot Dialog
            uiState.selectedBotForEdit?.let { botId ->
                val bot = uiState.botInstances.find { it.id == botId }
                if (bot != null) {
                    CreateBotDialog(
                        availableExchanges = uiState.availableExchanges,
                        existingBot = bot,
                        onDismiss = { viewModel.toggleBotEditDialog(null) },
                        onCreate = { name, _, _, _, config, _ ->
                            viewModel.updateBot(botId, name, config)
                            viewModel.toggleBotEditDialog(null)
                        }
                    )
                }
            }


            // Trade History Dialog
            uiState.selectedBotForHistory?.let { botId ->
                val bot = uiState.botInstances.find { it.id == botId }
                if (bot != null) {
                    TradeHistoryDialog(
                        bot = bot,
                        onDismiss = { viewModel.toggleBotHistoryDialog(null) }
                    )
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator()
                Text("Connecting...", modifier = Modifier.padding(top = 16.dp))
            } else {
                uiState.marketData?.let { data ->
                    // Exchange Selector (Disabled in Aggregate Mode)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.availableExchanges.forEach { exchange ->
                            FilterChip(
                                selected = uiState.selectedExchange == exchange && !uiState.isAggregateMode,
                                onClick = { viewModel.startMonitoring(exchange, uiState.symbol, uiState.selectedMarketType, false) },
                                label = { Text(exchange) },
                                enabled = !uiState.isAggregateMode
                            )
                        }
                    }

                    // Market Type Tabs
                    TabRow(
                        selectedTabIndex = if (uiState.selectedMarketType == MarketType.SPOT) 0 else 1,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Tab(
                            selected = uiState.selectedMarketType == MarketType.SPOT,
                            onClick = { viewModel.startMonitoring(uiState.selectedExchange, uiState.symbol, MarketType.SPOT, uiState.isAggregateMode) },
                            text = { Text("SPOT") }
                        )
                        Tab(
                            selected = uiState.selectedMarketType == MarketType.FUTURES,
                            onClick = { viewModel.startMonitoring(uiState.selectedExchange, uiState.symbol, MarketType.FUTURES, uiState.isAggregateMode) },
                            text = { Text("FUTURES") }
                        )
                    }

                    // Trading Pair Selector
                    PairSelector(
                        currentPair = data.symbol,
                        suggestions = uiState.pairSuggestions,
                        onSearchTextChanged = { viewModel.onSearchTextChanged(it) },
                        onPairSelected = { newPair -> viewModel.startMonitoring(uiState.selectedExchange, newPair, uiState.selectedMarketType, uiState.isAggregateMode) }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = if (uiState.isAggregateMode) {
                            "AGGREGATE (${uiState.mergedExchangesCount} Exchanges) ${uiState.selectedMarketType} : ${data.symbol}"
                        } else {
                            "${uiState.selectedExchange} ${uiState.selectedMarketType} : ${data.symbol}"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = String.format("%.2f", (data.bestBid + data.bestAsk) / 2),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // Interactive Price Chart with time frame selector
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Price Chart",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                        
                    // Filter trades for current symbol and market type
                    val relevantTrades = remember(uiState.botInstances, uiState.symbol, uiState.selectedMarketType, hiddenBotIds.size) {
                        uiState.botInstances
                            .filter { it.symbol == uiState.symbol && it.marketType == uiState.selectedMarketType }
                            .filter { !hiddenBotIds.contains(it.id) }
                            .flatMap { it.tradeHistory }
                    }

                    // Only show visuals from dashboard strategies, not bot strategies
                    val combinedVisuals = remember(uiState.strategyResults, uiState.botStrategyResults, uiState.hiddenStrategyNames) {
                        val dashboardVisuals = uiState.strategyResults
                            .filter { entry -> 
                                // Filter out if ANY hidden ID matches this strategy name
                                !uiState.hiddenStrategyNames.any { hiddenId -> 
                                    entry.key.contains(hiddenId.replace("_", " "), ignoreCase = true) 
                                }
                            }
                            .values.flatMap { it.visuals }

                        val botVisuals = uiState.botStrategyResults
                            .filter { entry ->
                                // Filter hidden bot strategies
                                !uiState.hiddenStrategyNames.contains(entry.key)
                            }
                            .values.flatMap { it.visuals }

                        dashboardVisuals + botVisuals
                    }

                    InteractiveCandlestickChart(
                        candles = uiState.candles,
                        trades = relevantTrades,
                        showTradeMarkers = true,
                        selectedTimeFrame = uiState.selectedTimeFrame,
                        onTimeFrameChange = { viewModel.changeTimeFrame(it) },
                        isLoading = uiState.isLoading,
                        errorMessage = uiState.errorMessage,
                        visuals = combinedVisuals,
                        modifier = Modifier.padding(vertical = 8.dp),
                        isMarketOpen = uiState.isMarketOpen,
                        chartType = uiState.chartType,
                        onToggleChartType = { viewModel.toggleChartType() }
                    )


                    Spacer(modifier = Modifier.height(24.dp))

                    // Order Book
                    OrderBookView(
                        bids = data.bids,
                        asks = data.asks,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )

                    // Active Strategy Results
                    if (uiState.allActiveStrategyIds.isNotEmpty()) {
                        Text(
                            "Active Strategies",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                        
                        // We display strategies from the dashboard's results AND potentially placeholder cards for bot strategies
                        // if they aren't in the dashboard's results map (which they won't be if they are running in independent contexts).
                        // However, the user wants to see them.
                        // The `strategyResults` map in `MainUiState` currently only comes from `TradingEngine.state`, which is the dashboard context.
                        // We need to fetch the results for the bot strategies too.
                        // But `MainViewModel` doesn't aggregate results yet, only IDs.
                        // For now, let's just show the ones available in `strategyResults` which covers the dashboard.
                        // For bot strategies, we might need a different UI or update `MainViewModel` to aggregate results.
                        // Given the complexity, let's stick to showing what's in `strategyResults` for now, 
                        // BUT we should ensure that if a bot is running a strategy that is ALSO active on the dashboard, it shows up.
                        
                        // Wait, the user requirement was: "when i add stretgy in bot it means its running so if its running it should be in active stretgy in setting season i am not seeing it"
                        // This implies they want to see the strategy card.
                        // If the bot is running independently, its results are in `botState`.
                        // We need to aggregate those results in `MainViewModel` if we want them here.
                        // I missed that step in `MainViewModel`.
                        // Let's do a quick fix: Iterate `allActiveStrategyIds`. 
                        // If it's in `strategyResults`, show it.
                        // If not (it's a bot-only strategy), we can't show a decision card because we don't have the decision data here yet.
                        // We can show a placeholder or just the name.
                        // For this step, let's just iterate `allActiveStrategyIds` and show what we have.
                        
                        
                        // Only show dashboard strategies (not bot strategies)
                        uiState.activeStrategyIds.forEach { strategyId ->
                            val isHidden = uiState.hiddenStrategyNames.contains(strategyId)
                            if (!isHidden) {
                                // Only check dashboard results
                                val decision = uiState.strategyResults.entries.find { 
                                    it.key.contains(strategyId.replace("_", " "), ignoreCase = true) 
                                }
                                
                                // Get bot metadata if this strategy is running on a bot
                                val botMetadata = uiState.botStrategyMetadata[strategyId]
                                
                                if (decision != null) {
                                    // Show full decision card with bot metadata if available
                                    DecisionCard(
                                        decision = decision.value,
                                        title = decision.key,
                                        onToggleVisibility = { viewModel.toggleStrategyVisibility(strategyId) },
                                        botMetadata = botMetadata
                                    )
                                } else {
                                    // Fallback if no decision found (shouldn't happen if logic is correct)
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = strategyId.replace("_", " ").split(" ").joinToString(" ") { 
                                                            it.replaceFirstChar { c -> c.uppercase() } 
                                                        },
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    if (botMetadata != null) {
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = "🤖 ${botMetadata.botName} • ${botMetadata.symbol}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                IconButton(onClick = { viewModel.toggleStrategyVisibility(strategyId) }) {
                                                    Icon(
                                                        imageVector = Icons.Default.Visibility,
                                                        contentDescription = "Hide Strategy",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Waiting for data...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }


                        // Show hidden strategies as chips to allow unhiding
                        if (uiState.hiddenStrategyNames.isNotEmpty()) {
                            Text(
                                "Hidden Strategies (Click to show)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                uiState.hiddenStrategyNames.forEach { name ->
                                    AssistChip(
                                        onClick = { viewModel.toggleStrategyVisibility(name) },
                                        label = { Text(name) },
                                        leadingIcon = { Icon(Icons.Default.VisibilityOff, null, modifier = Modifier.size(16.dp)) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // Metrics (using first available decision metrics for now, or could be per strategy)
                    if (uiState.strategyResults.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        MetricsCard(uiState)
                    }
                }
            }
        }
    }
}

@Composable
fun DecisionCard(
    decision: TradingDecision,
    title: String? = null,
    onToggleVisibility: (() -> Unit)? = null,
    botMetadata: com.godzilla.app.ui.main.BotStrategyMetadata? = null
) {
    val color = when (decision.direction) {
        TradeDirection.LONG -> Color(0xFF006400)
        TradeDirection.SHORT -> Color.Red
        TradeDirection.NO_TRADE -> Color.Gray
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (title != null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Display bot metadata if available
                    if (botMetadata != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "🤖 ${botMetadata.botName} • ${botMetadata.symbol}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (onToggleVisibility != null) {
                    IconButton(onClick = onToggleVisibility) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Hide Strategy",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = decision.direction.name,
                style = MaterialTheme.typography.displayMedium,
                color = color,
                fontWeight = FontWeight.Black
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Leverage: ${String.format("%.1fx", decision.leverage)}",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Size: ${String.format("%.1f%%", decision.positionSizePct)}",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = decision.reason,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun MetricsCard(state: MainUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Market Metrics", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            state.decision?.metrics?.let { metrics ->
                MetricRow("Spread", String.format("%.2f", metrics.spread))
                MetricRow("Rel Spread", String.format("%.4f%%", metrics.relativeSpread * 100))
                MetricRow("Imbalance", String.format("%.2f", metrics.imbalance))
                MetricRow("Volatility", String.format("%.4f", metrics.volatility))
            }
        }
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PairSelector(
    currentPair: String,
    suggestions: List<String>,
    onSearchTextChanged: (String) -> Unit,
    onPairSelected: (String) -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Current Pair: $currentPair",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Column {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { 
                        searchText = it.uppercase()
                        onSearchTextChanged(searchText)
                        errorMessage = ""
                    },
                    placeholder = { Text("Enter pair name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(),
                    singleLine = true,
                    isError = errorMessage.isNotEmpty(),
                    trailingIcon = {
                        if (searchText.isNotEmpty()) {
                            IconButton(onClick = { 
                                searchText = ""
                                onSearchTextChanged("")
                            }) {
                                Text("✕")
                            }
                        }
                    }
                )

                if (suggestions.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column {
                            suggestions.forEach { suggestion ->
                                DropdownMenuItem(
                                    text = { Text(suggestion) },
                                    onClick = {
                                        onPairSelected(suggestion)
                                        searchText = ""
                                        onSearchTextChanged("")
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
