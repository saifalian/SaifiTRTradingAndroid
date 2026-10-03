package com.godzilla.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.godzilla.app.domain.model.OrderBookLevel
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun OrderBookView(
    bids: List<OrderBookLevel>,
    asks: List<OrderBookLevel>,
    modifier: Modifier = Modifier
) {
    val greenColor = Color(0xFF00C805)
    val redColor = Color(0xFFFF3B30)
    
    // Precision State
    var selectedPrecision by remember { mutableStateOf(0.01) }
    var showPrecisionMenu by remember { mutableStateOf(false) }
    val precisionOptions = listOf(0.0001, 0.001, 0.01, 0.1, 1.0, 10.0, 50.0, 100.0)

    // Aggregate Data
    val aggregatedBids = remember(bids, selectedPrecision) {
        aggregateLevels(bids, selectedPrecision, true)
    }
    val aggregatedAsks = remember(asks, selectedPrecision) {
        aggregateLevels(asks, selectedPrecision, false)
    }

    // Calculate Dominance
    val totalBidVol = aggregatedBids.sumOf { it.volume }
    val totalAskVol = aggregatedAsks.sumOf { it.volume }
    val totalVol = totalBidVol + totalAskVol
    val bidPct = if (totalVol > 0) (totalBidVol / totalVol).toFloat() else 0.5f
    val askPct = 1f - bidPct

    Column(modifier = modifier.fillMaxWidth()) {
        // Header Row with Title and Precision Selector
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Order Book",
                style = MaterialTheme.typography.titleMedium
            )
            
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showPrecisionMenu = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedPrecision.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select Precision",
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                DropdownMenu(
                    expanded = showPrecisionMenu,
                    onDismissRequest = { showPrecisionMenu = false }
                ) {
                    precisionOptions.forEach { precision ->
                        DropdownMenuItem(
                            text = { Text(precision.toString()) },
                            onClick = {
                                selectedPrecision = precision
                                showPrecisionMenu = false
                            }
                        )
                    }
                }
            }
        }

        // Dominance Bar
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Bids ${(bidPct * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = greenColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Asks ${(askPct * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = redColor,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (totalVol > 0) {
                    Box(
                        modifier = Modifier
                            .weight(bidPct)
                            .fillMaxHeight()
                            .background(greenColor)
                    )
                    Box(
                        modifier = Modifier
                            .weight(askPct)
                            .fillMaxHeight()
                            .background(redColor)
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Text("Price", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text("Amount", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Color.Gray, textAlign = TextAlign.End)
        }

        // Asks (Sells) - Show top 10, reversed so highest is at top
        val displayAsks = aggregatedAsks.take(10).reversed()
        val maxAskVolume = aggregatedAsks.take(10).maxOfOrNull { it.volume } ?: 1.0

        displayAsks.forEach { ask ->
            OrderBookRow(ask, redColor, maxAskVolume, isBid = false, precision = selectedPrecision)
        }

        // Spread / Mid Price
        if (aggregatedBids.isNotEmpty() && aggregatedAsks.isNotEmpty()) {
            val midPrice = (aggregatedBids.first().price + aggregatedAsks.first().price) / 2
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formatPrice(midPrice, selectedPrecision),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Bids (Buys) - Show top 10
        val displayBids = aggregatedBids.take(10)
        val maxBidVolume = aggregatedBids.take(10).maxOfOrNull { it.volume } ?: 1.0

        displayBids.forEach { bid ->
            OrderBookRow(bid, greenColor, maxBidVolume, isBid = true, precision = selectedPrecision)
        }
    }
}

@Composable
fun OrderBookRow(
    level: OrderBookLevel,
    color: Color,
    maxVolume: Double,
    isBid: Boolean,
    precision: Double
) {
    val volumeWidth = (level.volume / maxVolume).toFloat().coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxWidth().height(24.dp)) {
        // Volume Bar Background
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(volumeWidth)
                .align(Alignment.CenterEnd) 
                .background(color.copy(alpha = 0.15f))
        )

        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatPrice(level.price, precision),
                color = color,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = String.format("%.4f", level.volume),
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// Helper to aggregate levels based on precision
private fun aggregateLevels(levels: List<OrderBookLevel>, precision: Double, isBid: Boolean): List<OrderBookLevel> {
    if (levels.isEmpty()) return emptyList()
    
    val grouped = levels.groupBy { 
        val factor = 1.0 / precision
        (it.price * factor).roundToInt() / factor
    }
    
    return grouped.map { (price, groupLevels) ->
        OrderBookLevel(
            price = price,
            volume = groupLevels.sumOf { it.volume }
        )
    }.sortedByDescending { it.price } // Sort by price descending
}

private fun formatPrice(price: Double, precision: Double): String {
    val decimals = when {
        precision >= 1.0 -> 0
        precision >= 0.1 -> 1
        precision >= 0.01 -> 2
        precision >= 0.001 -> 3
        else -> 4
    }
    return "%.${decimals}f".format(price)
}
