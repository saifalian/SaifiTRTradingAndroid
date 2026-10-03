package com.godzilla.app.ui.manual

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.godzilla.app.domain.TradingCalculator
import com.godzilla.app.domain.model.MarketData
import com.godzilla.app.domain.model.OrderBookLevel
import com.godzilla.app.ui.main.DecisionCard
import com.godzilla.app.ui.main.MainUiState
import com.godzilla.app.ui.main.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualModeScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    var bidPrice by remember { mutableStateOf("") }
    var askPrice by remember { mutableStateOf("") }
    var bidVol by remember { mutableStateOf("") }
    var askVol by remember { mutableStateOf("") }
    var volatility by remember { mutableStateOf("0.01") } // Default 1%

    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manual Mode") },
                navigationIcon = {
                    Button(onClick = onNavigateBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = bidPrice,
                onValueChange = { bidPrice = it },
                label = { Text("Best Bid Price") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = askPrice,
                onValueChange = { askPrice = it },
                label = { Text("Best Ask Price") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = bidVol,
                onValueChange = { bidVol = it },
                label = { Text("Total Bid Volume") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = askVol,
                onValueChange = { askVol = it },
                label = { Text("Total Ask Volume") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = volatility,
                onValueChange = { volatility = it },
                label = { Text("Volatility (0.01 = 1%)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val bPrice = bidPrice.toDoubleOrNull() ?: 0.0
                    val aPrice = askPrice.toDoubleOrNull() ?: 0.0
                    val bVol = bidVol.toDoubleOrNull() ?: 0.0
                    val aVol = askVol.toDoubleOrNull() ?: 0.0
                    val vol = volatility.toDoubleOrNull() ?: 0.01

                    // Create dummy market data from manual inputs
                    val manualData = MarketData(
                        symbol = "MANUAL",
                        bestBid = bPrice,
                        bestAsk = aPrice,
                        bids = listOf(OrderBookLevel(bPrice, bVol)),
                        asks = listOf(OrderBookLevel(aPrice, aVol))
                    )
                    
                    // We can reuse the ViewModel logic or just call calculator directly here.
                    // For simplicity, let's assume we want to see the result in the same UI state structure
                    // But ViewModel is tied to repository polling.
                    // Let's just do a quick calculation here or add a method to ViewModel.
                    // Ideally, we add 'calculateManual(data, vol)' to ViewModel.
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            ) {
                Text("Calculate Decision")
            }

            // Placeholder for result
            Text("Result will appear here (logic to be connected)")
        }
    }
}
