package com.godzilla.app.ui.bots

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.godzilla.app.ui.main.BotCard
import com.godzilla.app.ui.main.TradeHistoryDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotsScreen(
    viewModel: BotsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val hiddenBotIds = remember { mutableStateListOf<String>() }
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SmartToy, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("All Bots")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Tab Row for filtering by asset class
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("All (${uiState.allBots.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Crypto (${uiState.cryptoBots.size})") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Forex (${uiState.forexBots.size})") }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Stocks (${uiState.stocksBots.size})") }
                )
            }

            // Bot List
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                val botsToShow = when (selectedTab) {
                    0 -> uiState.allBots
                    1 -> uiState.cryptoBots
                    2 -> uiState.forexBots
                    3 -> uiState.stocksBots
                    else -> uiState.allBots
                }

                if (botsToShow.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.SmartToy,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No bots found",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Create bots from the Crypto, Forex, or Stocks tabs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    botsToShow.forEach { bot ->
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
                            onEditClick = { /* Edit not available in this view */ },
                            onViewHistoryClick = { viewModel.toggleBotHistoryDialog(bot.id) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        )
                    }
                }
            }

            // Trade History Dialog
            uiState.selectedBotForHistory?.let { botId ->
                val bot = uiState.allBots.find { it.id == botId }
                if (bot != null) {
                    TradeHistoryDialog(
                        bot = bot,
                        onDismiss = { viewModel.toggleBotHistoryDialog(null) }
                    )
                }
            }
        }
    }
}
