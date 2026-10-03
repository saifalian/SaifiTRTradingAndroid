package com.godzilla.app.ui.stocks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.godzilla.app.domain.model.MarketType
import com.godzilla.app.ui.components.InteractiveCandlestickChart
import com.godzilla.app.ui.components.OrderBookView
import com.godzilla.app.ui.main.BotCard
import com.godzilla.app.ui.main.CreateBotDialog
import com.godzilla.app.ui.main.DecisionCard
import com.godzilla.app.ui.main.MetricsCard
import com.godzilla.app.ui.main.PairSelector
import com.godzilla.app.ui.main.TradeHistoryDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StocksScreen(
    viewModel: StocksViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val hiddenBotIds = remember { mutableStateListOf<String>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stocks Dashboard") }
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


            // Trading Bots Section
            Text(
                "Stocks Bots",
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
                Text("Create New Stocks Bot")
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
                            "No stocks bots created yet",
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

            // Trading Pair Selector (Moved to top for better visibility)
            PairSelector(
                currentPair = uiState.symbol,
                suggestions = uiState.pairSuggestions,
                onSearchTextChanged = { viewModel.onSearchTextChanged(it) },
                onPairSelected = { newPair -> viewModel.startMonitoring(uiState.selectedExchange, newPair, uiState.selectedMarketType, uiState.isAggregateMode) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // Exchange Selector
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

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.isLoading && uiState.marketData == null) {
                CircularProgressIndicator()
                Text("Connecting to Market Data...", modifier = Modifier.padding(top = 16.dp))
            } else {
                uiState.marketData?.let { data ->
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
                }

                // Interactive Price Chart
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
                    
                val relevantTrades = remember(uiState.botInstances, uiState.symbol, uiState.selectedMarketType, hiddenBotIds.size) {
                    uiState.botInstances
                        .filter { it.symbol == uiState.symbol && it.marketType == uiState.selectedMarketType }
                        .filter { !hiddenBotIds.contains(it.id) }
                        .flatMap { it.tradeHistory }
                }

                val combinedVisuals = remember(uiState.strategyResults, uiState.botStrategyResults, uiState.hiddenStrategyNames) {
                    val dashboardVisuals = uiState.strategyResults
                        .filter { entry -> 
                            !uiState.hiddenStrategyNames.any { hiddenId -> 
                                entry.key.contains(hiddenId.replace("_", " "), ignoreCase = true) 
                            }
                        }
                        .values.flatMap { it.visuals }

                    val botVisuals = uiState.botStrategyResults
                        .filter { entry ->
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

                if (uiState.errorMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "Unknown error",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Order Book
                uiState.marketData?.let { data ->
                    OrderBookView(
                        bids = data.bids,
                        asks = data.asks,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }

                    // Active Strategy Results
                    if (uiState.allActiveStrategyIds.isNotEmpty()) {
                        Text(
                            "Active Strategies",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                        
                        uiState.activeStrategyIds.forEach { strategyId ->
                            val isHidden = uiState.hiddenStrategyNames.contains(strategyId)
                            if (!isHidden) {
                                val decision = uiState.strategyResults.entries.find { 
                                    it.key.contains(strategyId.replace("_", " "), ignoreCase = true) 
                                }
                                
                                val botMetadata = uiState.botStrategyMetadata[strategyId]
                                
                                if (decision != null) {
                                    DecisionCard(
                                        decision = decision.value,
                                        title = decision.key,
                                        onToggleVisibility = { viewModel.toggleStrategyVisibility(strategyId) },
                                        botMetadata = botMetadata
                                    )
                                } else {
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

                    if (uiState.strategyResults.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        MetricsCard(uiState)
                    }
            }
        }
    }
}
