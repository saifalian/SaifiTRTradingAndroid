package com.godzilla.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.godzilla.app.domain.model.TradingStrategy

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToManual: () -> Unit = {},
    onNavigateToBacktest: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var editingStrategy by remember { mutableStateOf<TradingStrategy?>(null) }

    if (editingStrategy != null) {
        val defaultStrategy = com.godzilla.app.domain.model.StrategyTemplates.getAllTemplates().find { it.id == editingStrategy!!.id }
        val defaultParams = defaultStrategy?.parameters ?: emptyMap()

        StrategyEditDialog(
            strategy = editingStrategy!!,
            defaultParameters = defaultParams,
            onDismiss = { editingStrategy = null },
            onSave = { newParams ->
                newParams.forEach { (key, value) ->
                    viewModel.updateStrategyParameter(editingStrategy!!.id, key, value)
                }
                editingStrategy = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Strategies") }
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
            // Manual Trading Button
            Button(
                onClick = onNavigateToManual,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Manual Trading",
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Manual Trading Mode", style = MaterialTheme.typography.titleMedium)
            }

            // Backtesting Button
            Button(
                onClick = onNavigateToBacktest,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Backtesting",
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Strategy Backtester", style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Chart Settings Section
            Text(
                "Chart Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            var maxCandlesText by remember { mutableStateOf(viewModel.getMaxCandles().toString()) }

            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Maximum Candles",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Number of historical candles to display (50-1000)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = maxCandlesText,
                            onValueChange = { 
                                if (it.all { char -> char.isDigit() } || it.isEmpty()) {
                                    maxCandlesText = it
                                }
                            },
                            label = { Text("Max Candles") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        
                        Button(
                            onClick = {
                                val value = maxCandlesText.toIntOrNull()
                                if (value != null && value in 50..1000) {
                                    viewModel.setMaxCandles(value)
                                } else {
                                    maxCandlesText = viewModel.getMaxCandles().toString()
                                }
                            }
                        ) {
                            Text("Apply")
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Data Update Settings
            Text(
                "Data Update Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            var priceUpdateDelayText by remember { mutableStateOf(viewModel.getPriceUpdateDelay().toString()) }

            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Price Update Delay",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Delay between price updates in milliseconds. Higher values mean slower updates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = priceUpdateDelayText,
                            onValueChange = { 
                                if (it.all { char -> char.isDigit() } || it.isEmpty()) {
                                    priceUpdateDelayText = it
                                }
                            },
                            label = { Text("Delay (ms)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            supportingText = {
                                val delay = priceUpdateDelayText.toLongOrNull() ?: 0L
                                Text("${delay / 1000.0} seconds")
                            }
                        )
                        
                        Button(
                            onClick = {
                                val value = priceUpdateDelayText.toLongOrNull()
                                if (value != null && value >= 100) {
                                    viewModel.setPriceUpdateDelay(value)
                                } else {
                                    priceUpdateDelayText = viewModel.getPriceUpdateDelay().toString()
                                }
                            },
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {
                            Text("Apply")
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Active Strategies Section
            Text(
                "Active Strategies (${uiState.activeStrategiesWithInfo.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (uiState.activeStrategiesWithInfo.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        "No active strategies. Select from available strategies below.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.activeStrategiesWithInfo.forEach { strategyInfo ->
                        ActiveStrategyCard(
                            strategyInfo = strategyInfo,
                            onRemove = { viewModel.toggleStrategy(strategyInfo.strategy) },
                            onNavigateToBot = { botId -> 
                                // Navigate back to main screen - the bot will be visible there
                                onNavigateBack()
                            },
                            onEdit = { editingStrategy = strategyInfo.strategy }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Spacer(modifier = Modifier.height(8.dp))

            // Dashboard Strategies Section
            Text(
                "Dashboard Strategies",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            
            Text(
                "Select strategies to run on the main dashboard. Bot strategies are configured separately in Bot Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.availableStrategies.forEach { strategy ->
                    StrategyCard(
                        strategy = strategy,
                        isActive = viewModel.isStrategyActive(strategy.id),
                        onToggle = { viewModel.toggleStrategy(strategy) },
                        onEdit = { editingStrategy = strategy }
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveStrategyCard(
    strategyInfo: ActiveStrategyInfo,
    onRemove: () -> Unit,
    onNavigateToBot: (String) -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Active",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp).padding(end = 4.dp)
                    )
                    Text(
                        strategyInfo.strategy.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    IconButton(onClick = onEdit, modifier = Modifier.size(24.dp).padding(start = 4.dp)) {
                        Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Text(
                    strategyInfo.strategy.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            // Show different buttons based on strategy source
            when {
                // Bot-only strategy: Show "View Bot" button
                !strategyInfo.isDashboard && strategyInfo.botIds.isNotEmpty() -> {
                    Button(
                        onClick = { onNavigateToBot(strategyInfo.botIds.first()) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("View Bot")
                    }
                }
                // Dashboard + Bot: Show both buttons
                strategyInfo.isDashboard && strategyInfo.botIds.isNotEmpty() -> {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = { onNavigateToBot(strategyInfo.botIds.first()) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("View Bot", style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = onRemove,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Remove", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                // Dashboard-only: Show "Remove" button
                else -> {
                    Button(
                        onClick = onRemove,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Remove")
                    }
                }
            }
        }
    }
}

@Composable
fun StrategyCard(
    strategy: TradingStrategy,
    isActive: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        strategy.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onEdit, modifier = Modifier.size(24.dp).padding(start = 4.dp)) {
                        Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    strategy.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
                
                // Display parameters
                if (strategy.parameters.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    strategy.parameters.entries.take(2).forEach { (key, value) ->
                        Text(
                            "$key: $value",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            Button(
                onClick = onToggle,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isActive) "Deactivate" else "Activate")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StrategyEditDialog(
    strategy: TradingStrategy,
    defaultParameters: Map<String, String>,
    onDismiss: () -> Unit,
    onSave: (Map<String, String>) -> Unit
) {
    var parameters by remember { mutableStateOf(strategy.parameters) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${strategy.name}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                parameters.forEach { (key, value) ->
                    if (key.endsWith("_timeframe")) {
                        Text(
                            key.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() },
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        val selectedTimeframes = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            com.godzilla.app.domain.model.TimeFrame.entries.forEach { tf ->
                                val isChecked = selectedTimeframes.contains(tf.label)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            val newList = if (checked) {
                                                (selectedTimeframes + tf.label).distinct()
                                            } else {
                                                selectedTimeframes - tf.label
                                            }
                                            parameters = parameters.toMutableMap().apply { put(key, newList.joinToString(",")) }
                                        }
                                    )
                                    Text(tf.label, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { newValue ->
                                parameters = parameters.toMutableMap().apply { put(key, newValue) }
                            },
                            label = { Text(key.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedButton(
                    onClick = { parameters = defaultParameters },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Refresh, "Reset")
                    Spacer(Modifier.width(8.dp))
                    Text("Reset to Defaults")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(parameters) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
