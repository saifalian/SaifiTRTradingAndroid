@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH")

package com.godzilla.app.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.godzilla.app.domain.model.Candle
import com.godzilla.app.domain.model.TimeFrame
import kotlin.math.max
import kotlin.math.min

@SuppressLint("UnusedBoxWithConstraintsScope", "UseKtx", "DefaultLocale")
@Composable
fun InteractiveCandlestickChart(
    candles: List<Candle>,
    selectedTimeFrame: TimeFrame,
    onTimeFrameChange: (TimeFrame) -> Unit,
    modifier: Modifier = Modifier,
    trades: List<com.godzilla.app.domain.model.SimulatedTrade> = emptyList(),
    showTradeMarkers: Boolean = true,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    visuals: List<com.godzilla.app.domain.model.VisualElement> = emptyList(),
    isMarketOpen: Boolean = true,
    chartType: com.godzilla.app.domain.model.ChartType = com.godzilla.app.domain.model.ChartType.CANDLESTICK,
    onToggleChartType: () -> Unit = {}
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var isUserScrolling by remember { mutableStateOf(false) }
    
    // Reset scroll when timeframe changes
    LaunchedEffect(selectedTimeFrame) {
        isUserScrolling = false
        offsetX = 0f // Will be recalculated to end
    }
    
    val greenColor = Color(0xFF00C805)
    val redColor = Color(0xFFFF3B30)
    val gridColor = Color.Gray.copy(alpha = 0.2f)
    val lineChartColor = MaterialTheme.colorScheme.primary
    
    // Calculate price direction for current price line color - use derived values as keys for proper reactivity
    val lastCandleClose = candles.lastOrNull()?.close ?: 0.0
    val candleCount = candles.size
    val priceColor = remember(lastCandleClose, candleCount) {
        if (candles.size >= 2) {
            val lastCandle = candles.last()
            val previousCandle = candles[candles.size - 2]
            if (lastCandle.close >= previousCandle.close) greenColor else redColor
        } else {
            Color.Yellow
        }
    }
    
    Column(modifier = modifier.fillMaxWidth()) {
        // Top Control Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time frame selector
            TimeFrameSelector(
                selectedTimeFrame = selectedTimeFrame,
                onTimeFrameChange = onTimeFrameChange,
                modifier = Modifier.weight(1f)
            )
            
            // Chart Type Toggle
            IconButton(onClick = onToggleChartType) {
                Icon(
                    imageVector = if (chartType == com.godzilla.app.domain.model.ChartType.CANDLESTICK) 
                        androidx.compose.material.icons.Icons.AutoMirrored.Filled.ShowChart // Line icon
                    else 
                        androidx.compose.material.icons.Icons.Default.BarChart, // Bar/Candle icon
                    contentDescription = "Toggle Chart Type",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        if (candles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (errorMessage != null) {
                        Text(
                            "⚠️ $errorMessage",
                            color = Color.Red,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else if (isLoading) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Loading chart data...", color = Color.Gray)
                    } else {
                        Text("No chart data available", color = Color.Gray)
                    }
                }
            }
            return@Column
        }
        
        val textMeasurer = rememberTextMeasurer()
        
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            val width = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
            
            // Calculate layout parameters
            val candleWidth = 15f * scale
            val candleSpacing = 5f * scale
            val totalContentWidth = candles.size * (candleWidth + candleSpacing)
            val minOffset = -(totalContentWidth - width + 100f).coerceAtLeast(0f)
            val maxOffset = 100f // Allow some padding on the left
            
            // Auto-scroll to end if user hasn't scrolled
            LaunchedEffect(candles.size, width, scale, isUserScrolling) {
                if (!isUserScrolling) {
                    offsetX = minOffset
                }
            }

            
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            isUserScrolling = true
                            scale *= zoom
                            scale = scale.coerceIn(0.5f, 5f)
                            
                            offsetX += pan.x
                            offsetX = offsetX.coerceIn(minOffset, maxOffset)
                        }
                    }
            ) {
                val paddingLeft = 40f
                val paddingRight = 120f
                val paddingTop = 40f
                val paddingBottom = 40f
                
                val chartHeight = size.height - paddingTop - paddingBottom
                
                // Clip content to chart area to prevent drawing outside bounds
                drawContext.canvas.save()
                drawContext.transform.clipRect(
                    left = paddingLeft,
                    top = paddingTop,
                    right = size.width - paddingRight,
                    bottom = size.height - paddingBottom
                )

                // Calculate price range based on VISIBLE candles
                val visibleCandles = getVisibleCandles(candles, offsetX, size.width, scale)
                
                println("📊 Chart Debug: Total candles=${candles.size}, Visible=${visibleCandles.size}, offsetX=$offsetX, width=$width, scale=$scale")
                
                if (visibleCandles.isNotEmpty()) {
                    val maxPrice = visibleCandles.maxOf { it.high }
                    val minPrice = visibleCandles.minOf { it.low }
                    val priceRange = maxPrice - minPrice
                    
                    println("📊 Price Range: min=$minPrice, max=$maxPrice, range=$priceRange")
                    
                    if (priceRange > 0) {
                        // Draw grid
                        drawGrid(size.width, size.height, paddingLeft, paddingRight, paddingTop, paddingBottom, gridColor, minPrice, maxPrice, textMeasurer)
                        
                        // Draw candles or line
                        drawContext.canvas.save()
                        drawContext.transform.clipRect(
                            left = paddingLeft,
                            top = paddingTop,
                            right = size.width - paddingRight,
                            bottom = size.height - paddingBottom
                        )

                        if (chartType == com.godzilla.app.domain.model.ChartType.LINE) {
                            // Draw Line Chart
                            val path = androidx.compose.ui.graphics.Path()
                            var isFirstPoint = true
                            
                            candles.forEachIndexed { index, candle ->
                                val x = paddingLeft + offsetX + (index * (candleWidth + candleSpacing)) + candleWidth / 2
                                
                                // Optimization: Skip points far outside visible area, but keep one before and after for continuity
                                if (x < -100 || x > size.width + 100) return@forEachIndexed
                                
                                val y = paddingTop + ((maxPrice - candle.close) / priceRange * chartHeight).toFloat()
                                
                                if (isFirstPoint) {
                                    path.moveTo(x, y)
                                    isFirstPoint = false
                                } else {
                                    path.lineTo(x, y)
                                }
                            }
                            
                            drawPath(
                                path = path,
                                color = lineChartColor,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                            )
                            
                        } else {
                            // Draw Candlestick Chart
                            candles.forEachIndexed { index, candle ->
                                val x = paddingLeft + offsetX + (index * (candleWidth + candleSpacing))
                                
                                // Only draw visible candles (optimization)
                                if (x + candleWidth < 0 || x > size.width) return@forEachIndexed
                                
                                val openY = paddingTop + ((maxPrice - candle.open) / priceRange * chartHeight).toFloat()
                                val closeY = paddingTop + ((maxPrice - candle.close) / priceRange * chartHeight).toFloat()
                                val highY = paddingTop + ((maxPrice - candle.high) / priceRange * chartHeight).toFloat()
                                val lowY = paddingTop + ((maxPrice - candle.low) / priceRange * chartHeight).toFloat()
                                
                                val isBullish = candle.close >= candle.open
                                val candleColor = if (isBullish) greenColor else redColor
                                
                                // Draw wick
                                drawLine(
                                    color = candleColor,
                                    start = Offset(x + candleWidth / 2, highY),
                                    end = Offset(x + candleWidth / 2, lowY),
                                    strokeWidth = 1.5f
                                )
                                
                                // Draw body
                                val bodyTop = min(openY, closeY)
                                val bodyBottom = max(openY, closeY)
                                val bodyHeight = (bodyBottom - bodyTop).coerceAtLeast(1f)
                                
                                drawRect(
                                    color = candleColor,
                                    topLeft = Offset(x, bodyTop),
                                    size = Size(candleWidth, bodyHeight)
                                )

                                // Draw Trade Markers
                                if (showTradeMarkers && trades.isNotEmpty()) {
                                    // Find trades that happened during this candle
                                    // Candle duration depends on timeframe
                                    val candleDuration = selectedTimeFrame.minutes * 60 * 1000L
                                    
                                    val tradesInCandle = trades.filter { 
                                        it.entryTime >= candle.timestamp && it.entryTime < (candle.timestamp + candleDuration)
                                    }

                                    tradesInCandle.forEach { trade ->
                                        val markerX = x + candleWidth / 2
                                        val markerSize = 20f
                                        
                                        if (trade.direction == com.godzilla.app.domain.model.TradeDirection.LONG) {
                                            // Green Arrow UP below candle
                                            val markerY = lowY + 10f
                                            val path = androidx.compose.ui.graphics.Path().apply {
                                                moveTo(markerX, markerY)
                                                lineTo(markerX - markerSize / 2, markerY + markerSize)
                                                lineTo(markerX + markerSize / 2, markerY + markerSize)
                                                close()
                                            }
                                            drawPath(path, greenColor)
                                        } else {
                                            // Red Arrow DOWN above candle
                                            val markerY = highY - 10f
                                            val path = androidx.compose.ui.graphics.Path().apply {
                                                moveTo(markerX, markerY)
                                                lineTo(markerX - markerSize / 2, markerY - markerSize)
                                                lineTo(markerX + markerSize / 2, markerY - markerSize)
                                                close()
                                            }
                                            drawPath(path, redColor)
                                        }
                                    }
                                }
                            }
                        }
                        
                        drawContext.canvas.restore()
                        
                        // Draw Strategy Visuals
                        visuals.forEach { visual ->
                            val color = try {
                                Color(android.graphics.Color.parseColor(visual.color))
                            } catch (_: Exception) {
                                Color.Gray
                            }

                            when (visual.type) {
                                com.godzilla.app.domain.model.VisualType.HORIZONTAL_LINE -> {
                                    visual.price?.let { price ->
                                        val y = paddingTop + ((maxPrice - price) / priceRange * chartHeight).toFloat()
                                        if (y in paddingTop..(size.height - paddingBottom)) {
                                            val startX = if (visual.startTime != null) {
                                                val index = candles.indexOfFirst { it.timestamp == visual.startTime }
                                                if (index != -1) {
                                                    paddingLeft + offsetX + (index * (candleWidth + candleSpacing)) + candleWidth / 2
                                                } else {
                                                    if (visual.startTime < (candles.firstOrNull()?.timestamp ?: 0)) paddingLeft else size.width - paddingRight
                                                }
                                            } else paddingLeft

                                            val endX = if (visual.endTime != null) {
                                                val index = candles.indexOfFirst { it.timestamp == visual.endTime }
                                                if (index != -1) {
                                                    paddingLeft + offsetX + (index * (candleWidth + candleSpacing)) + candleWidth / 2
                                                } else {
                                                    if (visual.endTime > (candles.lastOrNull()?.timestamp ?: 0)) size.width - paddingRight else paddingLeft
                                                }
                                            } else size.width - paddingRight

                                            if (startX < endX) {
                                                drawLine(
                                                    color = color,
                                                    start = Offset(startX, y),
                                                    end = Offset(endX, y),
                                                    strokeWidth = 2f,
                                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                                )
                                                val textX = max(paddingLeft, startX) + 10f
                                                if (textX < size.width - paddingRight) {
                                                    drawText(
                                                        textMeasurer = textMeasurer,
                                                        text = visual.label,
                                                        topLeft = Offset(textX, y - 15f),
                                                        style = TextStyle(color = color, fontSize = 10.sp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                com.godzilla.app.domain.model.VisualType.RECTANGLE -> {
                                    if (visual.priceHigh != null && visual.priceLow != null) {
                                        val topY = paddingTop + ((maxPrice - visual.priceHigh) / priceRange * chartHeight).toFloat()
                                        val bottomY = paddingTop + ((maxPrice - visual.priceLow) / priceRange * chartHeight).toFloat()
                                        
                                        if (topY <= (size.height - paddingBottom) && bottomY >= paddingTop) {
                                            val startX = if (visual.startTime != null) {
                                                val index = candles.indexOfFirst { it.timestamp == visual.startTime }
                                                if (index != -1) {
                                                    paddingLeft + offsetX + (index * (candleWidth + candleSpacing)) + candleWidth / 2
                                                } else {
                                                    if (visual.startTime < (candles.firstOrNull()?.timestamp ?: 0)) paddingLeft else size.width - paddingRight
                                                }
                                            } else paddingLeft

                                            val endX = if (visual.endTime != null) {
                                                val index = candles.indexOfFirst { it.timestamp == visual.endTime }
                                                if (index != -1) {
                                                    paddingLeft + offsetX + (index * (candleWidth + candleSpacing)) + candleWidth / 2
                                                } else {
                                                    if (visual.endTime > (candles.lastOrNull()?.timestamp ?: 0)) size.width - paddingRight else paddingLeft
                                                }
                                            } else size.width - paddingRight

                                            if (startX < endX) {
                                                drawRect(
                                                    color = color.copy(alpha = 0.2f),
                                                    topLeft = Offset(startX, topY),
                                                    size = Size(endX - startX, bottomY - topY)
                                                )
                                                
                                                if (topY < size.height - paddingBottom) {
                                                    val textX = max(paddingLeft, startX) + 10f
                                                    if (textX < size.width - paddingRight) {
                                                        drawText(
                                                            textMeasurer = textMeasurer,
                                                            text = visual.label,
                                                            topLeft = Offset(textX, topY + 5f),
                                                            style = TextStyle(color = color, fontSize = 10.sp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                com.godzilla.app.domain.model.VisualType.MARKER -> {
                                    visual.price?.let { price ->
                                        val y = paddingTop + ((maxPrice - price) / priceRange * chartHeight).toFloat()
                                        if (y in paddingTop..(size.height - paddingBottom)) {
                                            val x = if (visual.startTime != null) {
                                                // Find candle with matching timestamp
                                                val index = candles.indexOfFirst { it.timestamp == visual.startTime }
                                                if (index != -1) {
                                                    paddingLeft + offsetX + (index * (candleWidth + candleSpacing)) + candleWidth / 2
                                                } else {
                                                    // Try to find approximate position if exact match fails (e.g. different timeframe)
                                                    // For now, just hide if not found to avoid clutter
                                                    -1f
                                                }
                                            } else {
                                                size.width / 2 // Fallback to center
                                            }

                                            if (x != -1f && x >= paddingLeft && x <= size.width - paddingRight) {
                                                drawCircle(
                                                    color = color,
                                                    radius = 10f,
                                                    center = Offset(x, y)
                                                )
                                                val textX = x + 15f
                                                if (textX < size.width - paddingRight) {
                                                    drawText(
                                                        textMeasurer = textMeasurer,
                                                        text = visual.label,
                                                        topLeft = Offset(textX, y - 10f),
                                                        style = TextStyle(color = color, fontSize = 10.sp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Restore canvas from initial clipping before drawing current price line
                drawContext.canvas.restore()
                
                // Draw current price line and label (outside clipping context)
                if (visibleCandles.isNotEmpty()) {
                    val maxPrice = visibleCandles.maxOf { it.high }
                    val minPrice = visibleCandles.minOf { it.low }
                    val priceRange = maxPrice - minPrice
                    
                    if (priceRange > 0) {
                        val lastCandle = candles.lastOrNull()
                        if (lastCandle != null) {
                            val currentPriceY = paddingTop + ((maxPrice - lastCandle.close) / priceRange * chartHeight).toFloat()
                            
                            // Only draw if within bounds
                            if (currentPriceY in paddingTop..(size.height - paddingBottom)) {
                                drawLine(
                                    color = priceColor,
                                    start = Offset(paddingLeft, currentPriceY),
                                    end = Offset(size.width - paddingRight, currentPriceY),
                                    strokeWidth = 2f,
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                )
                                
                                // Price label on the right side (outside chart area)
                                val priceText = if (lastCandle.close < 10) String.format("%.5f", lastCandle.close) else String.format("%.2f", lastCandle.close)
                                
                                val textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                val textLayoutResult = textMeasurer.measure(priceText, textStyle)
                                
                                val labelWidth = (textLayoutResult.size.width + 20f).coerceAtLeast(paddingRight - 10f)
                                val labelHeight = textLayoutResult.size.height + 12f
                                val labelX = size.width - paddingRight + 5f
                                
                                // Draw price box
                                drawRoundRect(
                                    color = priceColor,
                                    topLeft = Offset(labelX, currentPriceY - labelHeight / 2),
                                    size = Size(labelWidth, labelHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                                )
                                
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = priceText,
                                    topLeft = Offset(
                                        labelX + (labelWidth - textLayoutResult.size.width) / 2,
                                        currentPriceY - textLayoutResult.size.height / 2
                                    ),
                                    style = textStyle
                                )
                            }
                        }
                    }
                }
            }
            
            // Chart info overlay
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Candles: ${candles.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                
                if (!isMarketOpen) {
                    Text(
                        "MARKET CLOSED",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.Red
                    )
                }
                
                if (isUserScrolling || scale != 1f) {
                    TextButton(onClick = { 
                        scale = 1f
                        isUserScrolling = false
                        // offsetX will be updated by LaunchedEffect
                    }) {
                        Text("Reset View", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun TimeFrameSelector(
    selectedTimeFrame: TimeFrame,
    onTimeFrameChange: (TimeFrame) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(TimeFrame.entries) { timeFrame ->
            FilterChip(
                selected = timeFrame == selectedTimeFrame,
                onClick = { onTimeFrameChange(timeFrame) },
                label = { Text(timeFrame.label) }
            )
        }
    }
}

private fun getVisibleCandles(
    candles: List<Candle>,
    offsetX: Float,
    width: Float,
    scale: Float
): List<Candle> {
    val candleWidth = 15f * scale
    val candleSpacing = 5f * scale
    val totalCandleWidth = candleWidth + candleSpacing
    
    val startIndex = max(0, (-offsetX / totalCandleWidth).toInt() - 5)
    val endIndex = min(candles.size, ((width - offsetX) / totalCandleWidth).toInt() + 5)
    
    return if (startIndex < endIndex) {
        candles.subList(startIndex, endIndex)
    } else {
        emptyList()
    }
}

private fun DrawScope.drawGrid(
    width: Float,
    height: Float,
    paddingLeft: Float,
    paddingRight: Float,
    paddingTop: Float,
    paddingBottom: Float,
    gridColor: Color,
    minPrice: Double,
    maxPrice: Double,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val gridLines = 5
    val priceRange = maxPrice - minPrice
    val chartHeight = height - paddingTop - paddingBottom
    
    // Detect if we should use more decimals (Forex)
    val format = if (maxPrice < 10) "%.5f" else "%.2f"
    
    // Horizontal price lines
    for (i in 0..gridLines) {
        val y = paddingTop + (chartHeight / gridLines * i)
        val price = maxPrice - (priceRange / gridLines * i)
        
        drawLine(
            color = gridColor,
            start = Offset(paddingLeft, y),
            end = Offset(width - paddingRight, y),
            strokeWidth = 1f
        )
        
        // Price labels on the right
        drawText(
            textMeasurer = textMeasurer,
            text = String.format(format, price),
            topLeft = Offset(width - paddingRight + 5f, y - 10f),
            style = TextStyle(
                color = Color.Gray,
                fontSize = 10.sp
            )
        )
    }
}
