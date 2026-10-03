package com.godzilla.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.godzilla.app.domain.model.Candle

@Composable
fun CandlestickChart(
    candles: List<Candle>,
    modifier: Modifier = Modifier
) {
    if (candles.isEmpty()) return

    val greenColor = Color(0xFF00C805)
    val redColor = Color(0xFFFF3B30)

    Canvas(modifier = modifier.fillMaxWidth().height(250.dp)) {
        val width = size.width
        val height = size.height
        
        val minPrice = candles.minOf { it.low }
        val maxPrice = candles.maxOf { it.high }
        val priceRange = maxPrice - minPrice
        
        val candleWidth = width / candles.size
        val candlePadding = candleWidth * 0.2f
        
        candles.forEachIndexed { index, candle ->
            val x = index * candleWidth + candlePadding / 2
            
            val openY = height - ((candle.open - minPrice) / priceRange * height).toFloat()
            val closeY = height - ((candle.close - minPrice) / priceRange * height).toFloat()
            val highY = height - ((candle.high - minPrice) / priceRange * height).toFloat()
            val lowY = height - ((candle.low - minPrice) / priceRange * height).toFloat()
            
            val isBullish = candle.close >= candle.open
            val color = if (isBullish) greenColor else redColor
            
            // Draw wick
            drawLine(
                color = color,
                start = Offset(x + (candleWidth - candlePadding) / 2, highY),
                end = Offset(x + (candleWidth - candlePadding) / 2, lowY),
                strokeWidth = 1.dp.toPx()
            )
            
            // Draw body
            val bodyHeight = Math.abs(openY - closeY).coerceAtLeast(1f)
            val bodyTop = Math.min(openY, closeY)
            
            drawRect(
                color = color,
                topLeft = Offset(x, bodyTop),
                size = Size(candleWidth - candlePadding, bodyHeight)
            )
        }
    }
}
