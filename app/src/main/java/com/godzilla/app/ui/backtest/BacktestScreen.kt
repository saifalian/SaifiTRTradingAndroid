package com.godzilla.app.ui.backtest

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.godzilla.app.domain.model.BacktestConfig
import com.godzilla.app.domain.model.BacktestResult
import com.godzilla.app.domain.model.TradeDirection
import com.godzilla.app.domain.model.TradeRecord
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacktestScreen(
    onNavigateBack: () -> Unit,
    viewModel: BacktestViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableStateOf(0) }
    
    // Sync tab selection with ViewModel if it changes (e.g. after run)
    LaunchedEffect(uiState.selectedTab) {
        if (uiState.selectedTab != selectedTabIndex && uiState.selectedTab != 0) {
            selectedTabIndex = uiState.selectedTab
        }
    }

    if (uiState.selectedResult != null) {
        BacktestDetailScreen(
            result = uiState.selectedResult!!,
            onBack = { viewModel.selectResult(null) }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backtesting") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Configuration") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Results") }
                )
            }

            when (selectedTabIndex) {
                0 -> ConfigTab(uiState, viewModel)
                1 -> ResultsTab(uiState, viewModel)
            }
        }
    }
}

@Composable
fun ConfigTab(uiState: BacktestUiState, viewModel: BacktestViewModel) {
    val config = uiState.config
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Simulation Parameters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        
        OutlinedTextField(
            value = config.initialCapital.toString(),
            onValueChange = { it.toDoubleOrNull()?.let { v -> viewModel.updateConfig(config.copy(initialCapital = v)) } },
            label = { Text("Initial Capital ($)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = config.amountPerOrder.toString(),
            onValueChange = { it.toDoubleOrNull()?.let { v -> viewModel.updateConfig(config.copy(amountPerOrder = v)) } },
            label = { Text("Amount Per Order ($)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = config.feePercent.toString(),
            onValueChange = { it.toDoubleOrNull()?.let { v -> viewModel.updateConfig(config.copy(feePercent = v)) } },
            label = { Text("Fee per Order (%)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider()
        
        Text("Leverage Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = config.useStrategyLeverage,
                onCheckedChange = { viewModel.updateConfig(config.copy(useStrategyLeverage = it)) }
            )
            Text("Use Strategy Leverage (Dynamic)")
        }
        
        if (!config.useStrategyLeverage) {
            OutlinedTextField(
                value = config.userLeverage.toString(),
                onValueChange = { it.toDoubleOrNull()?.let { v -> viewModel.updateConfig(config.copy(userLeverage = v)) } },
                label = { Text("Manual Leverage (x)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        if (uiState.errorMessage != null) {
            Text(uiState.errorMessage!!, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = { viewModel.runBacktest() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isRunning
        ) {
            if (uiState.isRunning) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Running Simulation...")
            } else {
                Text("RUN BACKTEST")
            }
        }
        
        Text(
            "Note: Simulation uses currently loaded chart candles. Ensure you have sufficient history loaded.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ResultsTab(uiState: BacktestUiState, viewModel: BacktestViewModel) {
    if (uiState.history.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No backtest results yet.")
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.history) { result ->
                ResultCard(result, onDelete = { viewModel.deleteResult(result.id) }, onClick = { viewModel.selectResult(result) })
            }
        }
    }
}

@Composable
fun ResultCard(result: BacktestResult, onDelete: () -> Unit, onClick: () -> Unit) {
    val dateFormat = SimpleDateFormat("MMM dd HH:mm", Locale.getDefault())
    val pnlColor = if (result.totalProfitLoss >= 0) Color.Green else Color.Red
    
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dateFormat.format(Date(result.timestamp)), style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Total P&L", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "${String.format("%.2f", result.totalProfitLoss)}$",
                        style = MaterialTheme.typography.titleLarge,
                        color = pnlColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Win Rate", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "${String.format("%.1f", result.winRate)}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "Trades: ${result.totalTrades} | Drawdown: ${String.format("%.2f", result.maxDrawdown)}%",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BacktestDetailScreen(result: BacktestResult, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backtest Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Performance Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailRow("Initial Capital", "$${result.config.initialCapital}")
                    DetailRow("Final Capital", "$${String.format("%.2f", result.finalCapital)}")
                    DetailRow("Total P&L", "$${String.format("%.2f", result.totalProfitLoss)}", if (result.totalProfitLoss >= 0) Color.Green else Color.Red)
                    DetailRow("Total Fees", "$${String.format("%.2f", result.totalFeesPaid)}")
                    DetailRow("Max Drawdown", "${String.format("%.2f", result.maxDrawdown)}%")
                    DetailRow("Win Rate", "${String.format("%.1f", result.winRate)}% (${result.winningTrades}/${result.totalTrades})")
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Trade History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            result.tradeHistory.forEach { trade ->
                TradeHistoryItem(trade)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, color: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun TradeHistoryItem(trade: TradeRecord) {
    val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    val pnlColor = if (trade.profitLoss >= 0) Color.Green else Color.Red
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${trade.direction} ${trade.symbol}",
                    fontWeight = FontWeight.Bold,
                    color = if (trade.direction == TradeDirection.LONG) Color.Green else Color.Red
                )
                Text(dateFormat.format(Date(trade.entryTime)), style = MaterialTheme.typography.bodySmall)
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Lev: ${String.format("%.1f", trade.leverage)}x")
                Text("Invested: $${String.format("%.0f", trade.investedAmount)}")
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Fee: $${String.format("%.2f", trade.fee)}")
                Text(
                    "P&L: $${String.format("%.2f", trade.profitLoss)} (${String.format("%.2f", trade.profitLossPercent)}%)",
                    fontWeight = FontWeight.Bold,
                    color = pnlColor
                )
            }
        }
    }
}
