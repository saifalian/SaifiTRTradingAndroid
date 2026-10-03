package com.godzilla.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.godzilla.app.ui.theme.SaifiTRTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SaifiTRTheme {
                MainAppNavigation()
            }
        }
    }
}

@Composable
fun MainAppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.CurrencyBitcoin, contentDescription = "Crypto") },
                    label = { Text("Crypto") },
                    selected = currentRoute == "main",
                    onClick = {
                        if (currentRoute != "main") {
                            navController.navigate("main") {
                                popUpTo("main") { inclusive = true }
                            }
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.AttachMoney, contentDescription = "Forex") },
                    label = { Text("Forex") },
                    selected = currentRoute == "forex",
                    onClick = {
                        if (currentRoute != "forex") {
                            navController.navigate("forex") {
                                popUpTo("main")
                            }
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "Stocks") },
                    label = { Text("Stocks") },
                    selected = currentRoute == "stocks",
                    onClick = {
                        if (currentRoute != "stocks") {
                            navController.navigate("stocks") {
                                popUpTo("main")
                            }
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.SmartToy, contentDescription = "Bots") },
                    label = { Text("Bots") },
                    selected = currentRoute == "bots",
                    onClick = {
                        if (currentRoute != "bots") {
                            navController.navigate("bots") {
                                popUpTo("main")
                            }
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = currentRoute == "settings",
                    onClick = {
                        if (currentRoute != "settings") {
                            navController.navigate("settings") {
                                popUpTo("main")
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "main",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("main") {
                val mainViewModel: com.godzilla.app.ui.main.MainViewModel = hiltViewModel()
                val settingsViewModel: com.godzilla.app.ui.settings.SettingsViewModel = hiltViewModel()
                
                // Sync active strategies to main view model
                val settingsState by settingsViewModel.uiState.collectAsState()
                LaunchedEffect(settingsState.activeStrategiesWithInfo) {
                    mainViewModel.updateActiveStrategies(
                        settingsState.activeStrategiesWithInfo.map { it.strategy.id }
                    )
                }
                
                com.godzilla.app.ui.main.MainScreen(viewModel = mainViewModel)
            }
            composable("forex") {
                val forexViewModel: com.godzilla.app.ui.forex.ForexViewModel = hiltViewModel()
                val settingsViewModel: com.godzilla.app.ui.settings.SettingsViewModel = hiltViewModel()
                
                val settingsState by settingsViewModel.uiState.collectAsState()
                LaunchedEffect(settingsState.activeStrategiesWithInfo) {
                    forexViewModel.updateActiveStrategies(
                        settingsState.activeStrategiesWithInfo.map { it.strategy.id }
                    )
                }
                
                com.godzilla.app.ui.forex.ForexScreen(viewModel = forexViewModel)
            }
            composable("stocks") {
                val stocksViewModel: com.godzilla.app.ui.stocks.StocksViewModel = hiltViewModel()
                val settingsViewModel: com.godzilla.app.ui.settings.SettingsViewModel = hiltViewModel()
                
                val settingsState by settingsViewModel.uiState.collectAsState()
                LaunchedEffect(settingsState.activeStrategiesWithInfo) {
                    stocksViewModel.updateActiveStrategies(
                        settingsState.activeStrategiesWithInfo.map { it.strategy.id }
                    )
                }
                
                com.godzilla.app.ui.stocks.StocksScreen(viewModel = stocksViewModel)
            }
            composable("bots") {
                com.godzilla.app.ui.bots.BotsScreen()
            }
            composable("manual") {
                com.godzilla.app.ui.manual.ManualModeScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("backtest") {
                com.godzilla.app.ui.backtest.BacktestScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("settings") {
                com.godzilla.app.ui.settings.SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToManual = {
                        navController.navigate("manual") {
                            popUpTo("main")
                        }
                    },
                    onNavigateToBacktest = {
                        navController.navigate("backtest") {
                            popUpTo("main")
                        }
                    }
                )
            }
        }
    }
}
