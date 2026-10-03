package com.godzilla.app.ui.main

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.godzilla.app.domain.model.BotConfig
import com.godzilla.app.domain.model.BotInstance
import com.godzilla.app.domain.model.BotState
import com.godzilla.app.domain.model.MarketType
import com.godzilla.app.domain.model.StrategyTemplates
import com.godzilla.app.domain.model.TradeDirection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BotCard(
    bot: BotInstance,
    isTradeVisible: Boolean = true,
    onToggleTradeVisibility: () -> Unit = {},
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onEditClick: () -> Unit,
    onViewHistoryClick: () -> Unit,

    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = when (bot.state) {
                BotState.RUNNING -> MaterialTheme.colorScheme.primaryContainer
                BotState.PAUSED -> MaterialTheme.colorScheme.tertiaryContainer
                BotState.STOPPED -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with name and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            bot.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        IconButton(
                            onClick = onEditClick,
                            modifier = Modifier.size(32.dp).padding(start = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Settings",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                    }

                    Text(
                        if (bot.isAggregateMode) 
                            "${bot.symbol} • ${bot.marketType.name} • AGGREGATE"
                        else 
                            "${bot.symbol} • ${bot.marketType.name} • ${bot.exchange}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val strategyText = if (bot.config.useDashboardStrategy) {
                        "Dashboard Default"
                    } else {
                        val mode = if (bot.config.requireAllAgree) "All Agree" else "Any Confirm"
                        "${bot.config.selectedStrategyIds.size} Strats ($mode)"
                    }
                    Text(
                        "Strategy: $strategyText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                    
                    val tfText = if (bot.config.allowedTimeFrames.isEmpty()) {
                        "All Timeframes"
                    } else {
                        bot.config.allowedTimeFrames.joinToString(", ") { it.label }
                    }
                    Text(
                        "Timeframes: $tfText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                    )

                }
                
                // Status badge and Visibility Toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleTradeVisibility) {
                        Icon(
                            imageVector = if (isTradeVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (isTradeVisible) "Hide Trades" else "Show Trades",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = when (bot.state) {
                            BotState.RUNNING -> Color(0xFF006400)
                            BotState.PAUSED -> Color(0xFFFFA500)
                            BotState.STOPPED -> Color.Gray
                        }
                    ) {
                        Text(
                            text = bot.state.name,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Capital",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "$${String.format("%.2f", bot.currentCapital)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "P&L",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${if (bot.totalProfitLoss >= 0) "+" else ""}${String.format("%.2f", bot.totalProfitLoss)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (bot.totalProfitLoss >= 0) Color(0xFF006400) else Color.Red
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Trades",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (bot.config.maxTrades != null) "${bot.completedTrades} / ${bot.config.maxTrades}" else "${bot.completedTrades} / ∞",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Active Positions
            if (bot.activePositions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Active Positions (${bot.activePositions.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    bot.activePositions.forEach { pos ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            color = if (pos.direction == TradeDirection.LONG)
                                Color(0xFF006400).copy(alpha = 0.2f) else Color.Red.copy(alpha = 0.2f)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "${pos.direction.name} @ ${String.format("%.2f", pos.entryPrice)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                // Calculate individual PnL for display
                                // Note: This is approximate as we don't have live price here easily without calculating it again
                                // But we can use the aggregate PnL if it's the only position, or just show direction/entry.
                                // Or we can rely on the aggregate PnL shown above.
                                // Let's just show Entry Price and Size.
                                Text(
                                    "$${String.format("%.0f", pos.positionSize)} x${pos.leverage}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
                
                // Aggregate Live PnL (Already shown in header, but maybe emphasize here?)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                     val pnl = bot.currentUnrealizedPnL
                     Text(
                        "Total Unrealized: ${if (pnl >= 0) "+" else ""}${String.format("%.2f", pnl)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (pnl >= 0) Color(0xFF006400) else Color(0xFF8B0000)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Control buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (bot.state) {
                    BotState.STOPPED -> {
                        Button(
                            onClick = onStartClick,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Start")
                        }
                        IconButton(onClick = onDeleteClick) {
                            Icon(Icons.Default.Delete, "Delete")
                        }
                    }
                    BotState.RUNNING -> {
                        Button(
                            onClick = onPauseClick,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Text("Pause")
                        }
                        Button(
                            onClick = onStopClick,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Stop")
                        }
                    }
                    BotState.PAUSED -> {
                        Button(
                            onClick = onResumeClick,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Resume")
                        }
                        Button(
                            onClick = onStopClick,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Stop")
                        }
                    }
                }
            }

            // View History button
            if (bot.tradeHistory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onViewHistoryClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📊 View Trade History (${bot.tradeHistory.size})")
                }
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateBotDialog(
    availableExchanges: List<String>,
    currentSymbol: String = "BTCUSDT",
    currentExchange: String = "Binance",
    currentMarketType: MarketType = MarketType.SPOT,
    existingBot: BotInstance? = null,
    onDismiss: () -> Unit,
    onCreate: (String, String, MarketType, String, BotConfig, Boolean) -> Unit
) {
    val allStrategies = remember { StrategyTemplates.getAllTemplates() }
    var botName by remember { mutableStateOf(existingBot?.name ?: "") }
    var symbol by remember { mutableStateOf(existingBot?.symbol ?: currentSymbol) }
    var marketType by remember { mutableStateOf(existingBot?.marketType ?: currentMarketType) }
    var exchange by remember { mutableStateOf(existingBot?.exchange ?: currentExchange) }
    var isAggregateMode by remember { mutableStateOf(existingBot?.isAggregateMode ?: false) }
    
    var initialCapital by remember { mutableStateOf(existingBot?.config?.initialCapital?.toString() ?: "10000") }
    var amountPerTrade by remember { mutableStateOf(existingBot?.config?.amountPerTrade?.toString() ?: "100") }
    var maxTradesText by remember { mutableStateOf(existingBot?.config?.maxTrades?.toString() ?: "") }
    var unlimitedTrades by remember { mutableStateOf(existingBot?.config?.maxTrades == null) }
    
    var concurrentTradesMode by remember { mutableStateOf(if ((existingBot?.config?.maxConcurrentTrades ?: 1) > 1) "Multiple" else "Single") }
    var maxConcurrentTradesText by remember { mutableStateOf(existingBot?.config?.maxConcurrentTrades?.toString() ?: "3") }

    var useStrategyLeverage by remember { mutableStateOf(existingBot?.config?.useStrategyLeverage ?: true) }
    var customLeverage by remember { mutableStateOf(existingBot?.config?.customLeverage?.toString() ?: "1.0") }
    var makerFee by remember { mutableStateOf(((existingBot?.config?.makerFee ?: 0.0002) * 100).toString()) }
    var takerFee by remember { mutableStateOf(((existingBot?.config?.takerFee ?: 0.0005) * 100).toString()) }
    
    val selectedStrategies = remember { 
        mutableStateListOf<String>().apply { 
            existingBot?.config?.selectedStrategyIds?.let { addAll(it) } 
        } 
    }
    var requireAllAgree by remember { mutableStateOf(existingBot?.config?.requireAllAgree ?: false) }
    
    var accessAllTimeFrames by remember { mutableStateOf(existingBot?.config?.allowedTimeFrames?.isEmpty() ?: true) }
    val selectedTimeFrames = remember { 
        mutableStateListOf<com.godzilla.app.domain.model.TimeFrame>().apply {
            existingBot?.config?.allowedTimeFrames?.let { addAll(it) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingBot != null) "Edit Bot Settings" else "Create New Bot") },

        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Bot name
                OutlinedTextField(
                    value = botName,
                    onValueChange = { botName = it },
                    label = { Text("Bot Name (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("$symbol ${marketType.name} Bot") }
                )

                // Symbol
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it.uppercase() },
                    label = { Text("Trading Pair") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("Currently monitoring: $currentSymbol", style = MaterialTheme.typography.bodySmall) }
                )

                // Market Type
                Text("Market Type", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = marketType == MarketType.SPOT,
                        onClick = { marketType = MarketType.SPOT },
                        label = { Text("SPOT") }
                    )
                    FilterChip(
                        selected = marketType == MarketType.FUTURES,
                        onClick = { marketType = MarketType.FUTURES },
                        label = { Text("FUTURES") }
                    )
                }

                HorizontalDivider()

                // Exchange selection
                Text("Exchange", style = MaterialTheme.typography.labelMedium)
                
                // Aggregate mode checkbox
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isAggregateMode,
                        onCheckedChange = { isAggregateMode = it }
                    )
                    Text("Use Aggregate Mode (Merge all exchanges)")
                }

                // Show exchange selection only if NOT in aggregate mode
                if (!isAggregateMode) {
                    if (availableExchanges.size > 1) {
                        Text("Select Exchange", style = MaterialTheme.typography.labelSmall)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            availableExchanges.forEach { ex ->
                                FilterChip(
                                    selected = exchange == ex,
                                    onClick = { exchange = ex },
                                    label = { Text(ex) }
                                )
                            }
                        }
                    } else {
                        Text(
                            "Exchange: $exchange",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // Show info when aggregate mode is enabled
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            "ℹ️ Bot will merge data from all ${availableExchanges.size} exchanges",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                HorizontalDivider()

                // Capital settings
                OutlinedTextField(
                    value = initialCapital,
                    onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) initialCapital = it },
                    label = { Text("Initial Capital ($)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = amountPerTrade,
                    onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) amountPerTrade = it },
                    label = { Text("Amount Per Trade ($)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Max trades
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = unlimitedTrades, onCheckedChange = { unlimitedTrades = it })
                    Text("Unlimited Trades")
                }

                if (!unlimitedTrades) {
                    OutlinedTextField(
                        value = maxTradesText,
                        onValueChange = { if (it.all { c -> c.isDigit() }) maxTradesText = it },
                        label = { Text("Max Trades") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                HorizontalDivider()

                // Concurrent Trades
                Text("Concurrent Trades", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = concurrentTradesMode == "Single", onClick = { concurrentTradesMode = "Single" })
                    Text("Single Trade (Wait for Close)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = concurrentTradesMode == "Multiple", onClick = { concurrentTradesMode = "Multiple" })
                    Text("Multiple Trades")
                }
                
                if (concurrentTradesMode == "Multiple") {
                    OutlinedTextField(
                        value = maxConcurrentTradesText,
                        onValueChange = { if (it.all { c -> c.isDigit() }) maxConcurrentTradesText = it },
                        label = { Text("Max Concurrent Trades") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                HorizontalDivider()

                // Leverage
                Text("Leverage", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = useStrategyLeverage, onClick = { useStrategyLeverage = true })
                    Text("Use Strategy's Leverage")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !useStrategyLeverage, onClick = { useStrategyLeverage = false })
                    Text("Custom Leverage")
                }

                if (!useStrategyLeverage) {
                    OutlinedTextField(
                        value = customLeverage,
                        onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) customLeverage = it },
                        label = { Text("Leverage") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                HorizontalDivider()

                // Fees
                Text("Trading Fees (%)", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = makerFee,
                    onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) makerFee = it },
                    label = { Text("Maker Fee (%)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = takerFee,
                    onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) takerFee = it },
                    label = { Text("Taker Fee (%)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                HorizontalDivider()


                // Strategy Selection
                Text("Trading Strategy", style = MaterialTheme.typography.labelMedium)
                
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Select Strategies:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    allStrategies.forEach { strategy ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedStrategies.contains(strategy.id),
                                onCheckedChange = { checked ->
                                    if (checked) selectedStrategies.add(strategy.id) else selectedStrategies.remove(strategy.id)
                                }
                            )
                            Text(strategy.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    
                    if (selectedStrategies.size > 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text("Agreement Mode:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = requireAllAgree, onClick = { requireAllAgree = true })
                            Text("All Must Agree", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = !requireAllAgree, onClick = { requireAllAgree = false })
                            Text("At Least One Must Agree (Any Confirm)", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                HorizontalDivider()

                // Timeframe Selection
                Text("Timeframe Access", style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = accessAllTimeFrames, onCheckedChange = { accessAllTimeFrames = it })
                        Text("Access All Timeframes", style = MaterialTheme.typography.bodyMedium)
                    }
                    
                    if (!accessAllTimeFrames) {
                        Column(
                            modifier = Modifier.padding(start = 24.dp, top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            com.godzilla.app.domain.model.TimeFrame.values().forEach { tf ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = selectedTimeFrames.contains(tf),
                                        onCheckedChange = { checked ->
                                            if (checked) selectedTimeFrames.add(tf) else selectedTimeFrames.remove(tf)
                                        }
                                    )
                                    Text(tf.label, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }


            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val config = BotConfig(
                        initialCapital = initialCapital.toDoubleOrNull() ?: 10000.0,
                        amountPerTrade = amountPerTrade.toDoubleOrNull() ?: 100.0,
                        maxTrades = if (unlimitedTrades) null else maxTradesText.toIntOrNull(),
                        maxConcurrentTrades = if (concurrentTradesMode == "Single") 1 else (maxConcurrentTradesText.toIntOrNull() ?: 1),
                        useStrategyLeverage = useStrategyLeverage,
                        customLeverage = customLeverage.toDoubleOrNull() ?: 1.0,
                        makerFee = (makerFee.toDoubleOrNull() ?: 0.02) / 100.0,
                        takerFee = (takerFee.toDoubleOrNull() ?: 0.05) / 100.0,
                        useDashboardStrategy = false,
                        selectedStrategyIds = selectedStrategies.toList(),
                        requireAllAgree = requireAllAgree,
                        allowedTimeFrames = if (accessAllTimeFrames) emptyList() else selectedTimeFrames.toList()
                    )

                    onCreate(botName, symbol, marketType, exchange, config, isAggregateMode)
                },
                enabled = symbol.isNotBlank() &&
                         initialCapital.toDoubleOrNull() != null &&
                         amountPerTrade.toDoubleOrNull() != null &&
                         selectedStrategies.isNotEmpty()
            ) {
                Text(if (existingBot != null) "Save Changes" else "Create & Start")
            }
        },

        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeHistoryDialog(
    bot: BotInstance,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${bot.name} - Trade History") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (bot.tradeHistory.isEmpty()) {
                    Text("No trades yet", style = MaterialTheme.typography.bodyMedium)
                } else {
                    bot.tradeHistory.reversed().forEach { trade ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (trade.profitLoss >= 0)
                                    Color(0xFF006400).copy(alpha = 0.1f) else Color.Red.copy(alpha = 0.1f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${trade.direction.name} • ${trade.leverage}x",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            "Net: ${if (trade.profitLoss >= 0) "+" else ""}${String.format("%.2f", trade.profitLoss)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (trade.profitLoss >= 0) Color(0xFF006400) else Color.Red
                                        )
                                        Text(
                                            "Gross: ${if (trade.rawProfitLoss >= 0) "+" else ""}${String.format("%.2f", trade.rawProfitLoss)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "Fees: -${String.format("%.2f", trade.feesPaid)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Entry: ${String.format("%.2f", trade.entryPrice)} @ ${dateFormat.format(Date(trade.entryTime))}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                trade.exitPrice?.let { exitPrice ->
                                    trade.exitTime?.let { exitTime ->
                                        Text(
                                            "Exit: ${String.format("%.2f", exitPrice)} @ ${dateFormat.format(Date(exitTime))}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                Text(
                                    "Position: $${String.format("%.2f", trade.positionSize)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
