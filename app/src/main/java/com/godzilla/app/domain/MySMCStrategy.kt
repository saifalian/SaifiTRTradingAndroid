package com.godzilla.app.domain

import com.godzilla.app.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Singleton
class MySMCStrategy @Inject constructor() {

    data class Config(
        val htfTimeFrames: List<TimeFrame> = listOf(TimeFrame.H4),
        val itfTimeFrames: List<TimeFrame> = listOf(TimeFrame.H1),
        val ltfTimeFrames: List<TimeFrame> = listOf(TimeFrame.M1),
        val riskPercent: Double = 0.015, // 1.5% risk
        val leverage: Double = 5.0,
        val zoneLookback: Int = 50,
        val liquidityLookback: Int = 50,
        val displacementMultiplier: Double = 2.0,
        val wickRatio: Double = 0.3
    ) {
        companion object {
            fun fromParameters(params: Map<String, String>): Config {
                return Config(
                    htfTimeFrames = (params["htf_timeframe"] ?: "4h").split(",").filter { it.isNotBlank() }.map { TimeFrame.fromString(it.trim()) },
                    itfTimeFrames = (params["itf_timeframe"] ?: "1h").split(",").filter { it.isNotBlank() }.map { TimeFrame.fromString(it.trim()) },
                    ltfTimeFrames = (params["ltf_timeframe"] ?: "1m").split(",").filter { it.isNotBlank() }.map { TimeFrame.fromString(it.trim()) },
                    riskPercent = (params["risk_percent"]?.toDoubleOrNull() ?: 1.5) / 100.0,
                    leverage = params["leverage"]?.toDoubleOrNull() ?: 5.0,
                    zoneLookback = params["zone_lookback_candles"]?.toIntOrNull() ?: 50,
                    liquidityLookback = params["liquidity_lookback_candles"]?.toIntOrNull() ?: 50,
                    displacementMultiplier = params["displacement_multiplier"]?.toDoubleOrNull() ?: 2.0,
                    wickRatio = params["wick_ratio"]?.toDoubleOrNull() ?: 0.3
                )
            }
        }
    }

    // State to track the setup progression
    data class StrategyState(
        var htfBias: TradeDirection = TradeDirection.NO_TRADE,
        var activeZones: MutableList<Zone> = mutableListOf(),
        var liquidityLevels: List<LiquidityLevel> = emptyList(),
        var bosPoints: List<BOSPoint> = emptyList(),
        var displacementCandles: List<DisplacementCandle> = emptyList(),
        var mitigatedZones: MutableSet<String> = mutableSetOf(),
        var lastSweep: LiquiditySweep? = null,
        var activeTrade: ActiveTrade? = null,
        var lastAnalysisTime: Long = 0,
        var swingHighs: List<SwingPoint> = emptyList(),
        var swingLows: List<SwingPoint> = emptyList()
    )

    data class ActiveTrade(
        val direction: TradeDirection,
        val entryPrice: Double,
        val stopLoss: Double,
        val takeProfit: Double,
        val entryTime: Long
    )

    data class Zone(
        val id: String,
        val type: ZoneType,
        val top: Double,
        val bottom: Double,
        val creationTime: Long,
        val direction: TradeDirection,
        val timeframe: String = ""
    )

    enum class ZoneType {
        ORDER_BLOCK, FVG, LIQUIDITY
    }

    data class LiquidityLevel(
        val price: Double,
        val type: LiquidityType,
        val timeframe: String,
        val timestamp: Long,
        var swept: Boolean = false
    )

    enum class LiquidityType {
        SWING_HIGH, SWING_LOW, SESSION_HIGH, SESSION_LOW
    }

    data class LiquiditySweep(
        val level: LiquidityLevel,
        val sweepTime: Long,
        val sweepPrice: Double
    )

    data class BOSPoint(
        val price: Double,
        val direction: TradeDirection,
        val timestamp: Long
    )

    data class DisplacementCandle(
        val index: Int,
        val direction: TradeDirection,
        val timestamp: Long,
        val bodySize: Double
    )

    data class SwingPoint(val price: Double, val time: Long, val isHigh: Boolean)

    private val stateMap = mutableMapOf<String, StrategyState>()

    fun makeDecision(
        symbol: String,
        candlesMap: Map<TimeFrame, List<Candle>>,
        metrics: TradeMetrics,
        config: Config = Config()
    ): TradingDecision {
        val state = stateMap.getOrPut(symbol) { StrategyState() }
        val visuals = mutableListOf<VisualElement>()
        
        // Extract primary candles for basic checks
        val primaryLtf = candlesMap[config.ltfTimeFrames.firstOrNull() ?: TimeFrame.M1] ?: candlesMap[TimeFrame.M1] ?: emptyList()
        val primaryItf = candlesMap[config.itfTimeFrames.firstOrNull() ?: TimeFrame.H1] ?: candlesMap[TimeFrame.H1] ?: emptyList()
        val primaryHtf = candlesMap[config.htfTimeFrames.firstOrNull() ?: TimeFrame.H4] ?: candlesMap[TimeFrame.H4] ?: emptyList()

        if (primaryLtf.isEmpty()) {
            return TradingDecision(TradeDirection.NO_TRADE, 0.0, 0.0, "Waiting for LTF data...", metrics)
        }

        val currentPrice = primaryLtf.last().close

        // --- Step 0: Manage Active Trade ---
        if (state.activeTrade != null) {
            val trade = state.activeTrade!!
            var shouldExit = false
            var exitReason = ""

            // Check TP/SL
            if (trade.direction == TradeDirection.LONG) {
                if (currentPrice >= trade.takeProfit) { shouldExit = true; exitReason = "Take Profit Hit" }
                else if (currentPrice <= trade.stopLoss) { shouldExit = true; exitReason = "Stop Loss Hit" }
            } else {
                if (currentPrice <= trade.takeProfit) { shouldExit = true; exitReason = "Take Profit Hit" }
                else if (currentPrice >= trade.stopLoss) { shouldExit = true; exitReason = "Stop Loss Hit" }
            }

            if (shouldExit) {
                state.activeTrade = null
                return TradingDecision(TradeDirection.NO_TRADE, 0.0, 0.0, exitReason, metrics, visuals)
            } else {
                // Maintain Position - Add visuals
                visuals.add(VisualElement(VisualType.MARKER, price = trade.entryPrice, startTime = trade.entryTime, label = "ENTRY", color = "#FFFFFF"))
                visuals.add(VisualElement(VisualType.HORIZONTAL_LINE, price = trade.stopLoss, startTime = trade.entryTime, label = "SL", color = "#FF0000"))
                visuals.add(VisualElement(VisualType.HORIZONTAL_LINE, price = trade.takeProfit, startTime = trade.entryTime, label = "TP", color = "#00FF00"))
                
                return TradingDecision(
                    direction = trade.direction,
                    leverage = config.leverage,
                    positionSizePct = 100.0,
                    reason = "Holding ${trade.direction} (TP: ${String.format("%.2f", trade.takeProfit)}, SL: ${String.format("%.2f", trade.stopLoss)})",
                    metrics = metrics,
                    visuals = visuals
                )
            }
        }

        // --- Step 1: Determine HTF Bias ---
        val htfResults = config.htfTimeFrames.mapNotNull { tf ->
            candlesMap[tf]?.let { analyzeMarketStructure(it) }
        }
        
        // Bias is LONG if all selected HTF agree on LONG, same for SHORT.
        // If they conflict, we use the first one but mark it as "Weak" or just proceed with primary.
        val primaryBias = htfResults.firstOrNull()?.first ?: TradeDirection.NO_TRADE
        state.htfBias = primaryBias
        
        val allHtfSwings = htfResults.flatMap { it.second }
        state.swingHighs = allHtfSwings.filter { it.isHigh }
        state.swingLows = allHtfSwings.filter { !it.isHigh }

        // Add swing visuals
        allHtfSwings.takeLast(10).forEach { swing ->
            val label = if (swing.isHigh) "HTF H" else "HTF L"
            visuals.add(VisualElement(VisualType.MARKER, price = swing.price, startTime = swing.time, label = label, color = "#888888"))
        }

        val htfLiquidity = config.htfTimeFrames.flatMap { tf ->
            candlesMap[tf]?.let { identifyLiquidity(it, "HTF(${tf.label})", state.swingHighs, state.swingLows, config.liquidityLookback) } ?: emptyList()
        }
        val itfLiquidity = config.itfTimeFrames.flatMap { tf ->
            candlesMap[tf]?.let { identifyLiquidity(it, "ITF(${tf.label})", emptyList(), emptyList(), config.liquidityLookback) } ?: emptyList()
        }
        
        val newLevels = (htfLiquidity + itfLiquidity).distinctBy { it.price }
        state.liquidityLevels = newLevels.map { newLiq ->
            state.liquidityLevels.find { it.price == newLiq.price && it.type == newLiq.type }?.let { existing ->
                newLiq.apply { swept = existing.swept }
            } ?: newLiq
        }

        // Add liquidity visuals
        state.liquidityLevels.forEach { liq ->
            val color = if (liq.swept) "#FFA500" else "#FFFF00" // Orange if swept, Yellow if active
            visuals.add(VisualElement(VisualType.HORIZONTAL_LINE, price = liq.price, startTime = liq.timestamp, label = "${liq.type} (${liq.timeframe})", color = color))
        }

        // --- Step 3: Detect Liquidity Sweeps ---
        // Check for sweeps on any ITF
        var recentSweep: LiquiditySweep? = null
        for (tf in config.itfTimeFrames) {
            val candles = candlesMap[tf] ?: continue
            val sweep = detectSweep(candles, state.liquidityLevels)
            if (sweep != null) {
                recentSweep = sweep
                break
            }
        }
        
        if (recentSweep != null) {
            state.lastSweep = recentSweep
            // Mark sweep visual
            visuals.add(VisualElement(VisualType.MARKER, price = recentSweep.sweepPrice, startTime = recentSweep.sweepTime, label = "SWEEP", color = "#FF00FF"))
        }

        // --- Step 4: Detect Break of Structure (BOS) ---
        // Check for BOS on any ITF
        for (tf in config.itfTimeFrames) {
            val candles = candlesMap[tf] ?: continue
            val bosDetected = detectBOS(candles, state.swingHighs, state.swingLows)
            if (bosDetected != null) {
                state.bosPoints = (state.bosPoints + bosDetected).takeLast(5)
                visuals.add(VisualElement(VisualType.MARKER, price = bosDetected.price, startTime = bosDetected.timestamp, label = "BOS(${tf.label})", color = "#00FFFF"))
            }
        }

        // --- Step 4.5: Refine Bias with BOS fallback ---
        if (state.htfBias == TradeDirection.NO_TRADE && state.bosPoints.isNotEmpty()) {
            state.htfBias = state.bosPoints.last().direction
        }

        // --- Step 5: Detect Displacement ---
        // Check for displacement on any ITF
        val allDisplacements = config.itfTimeFrames.flatMap { tf ->
            candlesMap[tf]?.let { detectDisplacement(it, config) } ?: emptyList()
        }
        state.displacementCandles = allDisplacements.takeLast(3)
        
        allDisplacements.forEach { disp ->
            val color = if (disp.direction == TradeDirection.LONG) "#00FF00" else "#FF0000"
            visuals.add(VisualElement(VisualType.MARKER, price = currentPrice, startTime = disp.timestamp, label = "DISP", color = color))
        }

        // --- Step 6: Identify Zones (OB & FVG) ---
        val allNewZones = config.itfTimeFrames.flatMap { tf ->
            candlesMap[tf]?.let { identifyZones(it, config.zoneLookback, tf.label) } ?: emptyList()
        }
        
        // Add new zones to persistent list
        allNewZones.forEach { newZone ->
            if (state.activeZones.none { it.id == newZone.id }) {
                state.activeZones.add(newZone)
            }
        }

        // Update zone mitigation status
        val zonesToRemove = mutableListOf<String>()
        state.activeZones.forEach { zone ->
            // Check mitigation against all available candles for that timeframe
            val relevantCandles = config.itfTimeFrames.flatMap { tf -> candlesMap[tf] ?: emptyList() }
                .filter { it.timestamp > zone.creationTime }
                .sortedBy { it.timestamp }
            
            if (checkZoneMitigation(zone, relevantCandles)) {
                state.mitigatedZones.add(zone.id)
                zonesToRemove.add(zone.id)
            }
        }
        
        // Keep only unmitigated zones
        state.activeZones.removeAll { zonesToRemove.contains(it.id) }
        
        // Add zone visuals
        state.activeZones.takeLast(10).forEach { zone ->
            val color = if (zone.direction == TradeDirection.LONG) "#00FF00" else "#FF0000"
            val label = "${zone.type} ${zone.timeframe} (${zone.direction})"
            visuals.add(VisualElement(VisualType.RECTANGLE, priceHigh = zone.top, priceLow = zone.bottom, startTime = zone.creationTime, endTime = primaryLtf.last().timestamp, label = label, color = color))
        }

        // --- Step 7: Check for Complete Setup ---
        var potentialEntry = TradeDirection.NO_TRADE
        var entryReason = ""
        var stopLoss = 0.0
        var takeProfit = 0.0

        // Filter zones matching HTF bias
        val relevantZones = state.activeZones.filter { it.direction == state.htfBias }

        // Check if we have all required conditions
        val hasLiquiditySweep = state.lastSweep != null && 
                                (primaryLtf.last().timestamp - state.lastSweep!!.sweepTime) < 12 * 3600000 // Within 12 hours
        val hasDisplacement = state.displacementCandles.isNotEmpty() &&
                              state.displacementCandles.any { it.direction == state.htfBias && (primaryLtf.last().timestamp - it.timestamp) < 3600000 }
        val hasBOS = state.bosPoints.isNotEmpty() && 
                     state.bosPoints.any { it.direction == state.htfBias && (primaryLtf.last().timestamp - it.timestamp) < 3600000 }

        for (zone in relevantZones) {
            if (isPriceInZone(primaryLtf.last(), zone)) {
                // We are in a key zone
                // Check confirmation on ANY LTF
                var confirmation: Confirmation? = null
                for (tf in config.ltfTimeFrames) {
                    val candles = candlesMap[tf] ?: continue
                    val conf = checkLTFConfirmation(candles, zone.direction)
                    if (conf.confirmed) {
                        confirmation = conf
                        break
                    }
                }
                
                if (confirmation?.confirmed == true && hasLiquiditySweep && hasDisplacement) {
                    potentialEntry = zone.direction
                    entryReason = buildString {
                        append("HTF Bias: ${state.htfBias}")
                        append(", Sweep: ${state.lastSweep?.level?.type}")
                        if (hasBOS) append(", BOS Confirmed")
                        append(", Displacement Detected")
                        append(", In ${zone.type}")
                        append(", LTF: ${confirmation.reason}")
                    }
                    
                    // Risk Management
                    stopLoss = if (potentialEntry == TradeDirection.LONG) zone.bottom else zone.top
                    val risk = abs(currentPrice - stopLoss)
                    takeProfit = if (potentialEntry == TradeDirection.LONG) {
                        currentPrice + (risk * 2)
                    } else {
                        currentPrice - (risk * 2)
                    }
                    
                    // Add Entry Visuals
                    visuals.add(VisualElement(VisualType.MARKER, price = currentPrice, startTime = primaryLtf.last().timestamp, label = "ENTRY", color = "#FFFFFF"))
                    visuals.add(VisualElement(VisualType.HORIZONTAL_LINE, price = stopLoss, startTime = primaryLtf.last().timestamp, label = "SL", color = "#FF0000"))
                    visuals.add(VisualElement(VisualType.HORIZONTAL_LINE, price = takeProfit, startTime = primaryLtf.last().timestamp, label = "TP", color = "#00FF00"))
                    
                    // Set Active Trade
                    state.activeTrade = ActiveTrade(potentialEntry, currentPrice, stopLoss, takeProfit, primaryLtf.last().timestamp)
                    
                    break
                }
            }
        }

        val monitoringReason = buildString {
            append("Monitoring: Bias ${state.htfBias}")
            append(", Zones: ${state.activeZones.size}")
            if (hasLiquiditySweep) append(", Sweep ✓") else append(", Sweep ✗")
            if (hasDisplacement) append(", Disp ✓") else append(", Disp ✗")
            if (hasBOS) append(", BOS ✓") else append(", BOS ✗")
            
            val matchingZone = relevantZones.find { isPriceInZone(primaryLtf.last(), it) }
            if (matchingZone != null) append(", In Zone ✓") else append(", Outside Zone")
        }

        return TradingDecision(
            direction = potentialEntry,
            leverage = config.leverage,
            positionSizePct = 100.0,
            reason = entryReason.ifEmpty { monitoringReason },
            metrics = metrics,
            visuals = visuals
        )
    }

    private fun isPriceInZone(candle: Candle, zone: Zone): Boolean {
        val zoneMin = min(zone.top, zone.bottom)
        val zoneMax = max(zone.top, zone.bottom)
        return candle.high >= zoneMin && candle.low <= zoneMax
    }

    // --- Market Structure Analysis ---
    private fun analyzeMarketStructure(candles: List<Candle>): Pair<TradeDirection, List<SwingPoint>> {
        val swings = mutableListOf<SwingPoint>()
        
        // Look for local high/low over 5 candles (2 left, 2 right)
        for (i in 2 until candles.size - 2) {
            val current = candles[i]
            val isHigh = current.high > candles[i-1].high && current.high > candles[i-2].high &&
                         current.high > candles[i+1].high && current.high > candles[i+2].high
            
            val isLow = current.low < candles[i-1].low && current.low < candles[i-2].low &&
                        current.low < candles[i+1].low && current.low < candles[i+2].low
            
            if (isHigh) swings.add(SwingPoint(current.high, current.timestamp, true))
            if (isLow) swings.add(SwingPoint(current.low, current.timestamp, false))
        }

        if (swings.size < 2) return Pair(TradeDirection.NO_TRADE, emptyList())

        // Determine Trend
        val lastHighs = swings.filter { it.isHigh }.takeLast(2)
        val lastLows = swings.filter { !it.isHigh }.takeLast(2)
        
        var trend = TradeDirection.NO_TRADE
        
        if (lastHighs.size >= 2 && lastLows.size >= 2) {
            val h2 = lastHighs.last().price
            val h1 = lastHighs[lastHighs.size - 2].price
            val l2 = lastLows.last().price
            val l1 = lastLows[lastLows.size - 2].price
            
            if (h2 > h1 && l2 > l1) trend = TradeDirection.LONG
            else if (h2 < h1 && l2 < l1) trend = TradeDirection.SHORT
        } else if (lastHighs.size >= 1 && lastLows.size >= 1) {
            // If we only have one of each, we can't confirm a trend of 2 points,
            // but we can see if the last high is above the last low (always true)
            // or compare with current price. Let's stay NO_TRADE and let BOS handle it.
        }

        return Pair(trend, swings)
    }

    // --- Liquidity Identification ---
    private fun identifyLiquidity(
        candles: List<Candle>,
        timeframe: String,
        swingHighs: List<SwingPoint>,
        swingLows: List<SwingPoint>,
        lookback: Int
    ): List<LiquidityLevel> {
        val liquidity = mutableListOf<LiquidityLevel>()
        
        // Add swing highs/lows as liquidity
        swingHighs.takeLast(3).forEach {
            liquidity.add(LiquidityLevel(it.price, LiquidityType.SWING_HIGH, timeframe, it.time))
        }
        swingLows.takeLast(3).forEach {
            liquidity.add(LiquidityLevel(it.price, LiquidityType.SWING_LOW, timeframe, it.time))
        }
        
        // Add session high/low (last 24 candles if M1, or proportional)
        if (candles.size > 10) {
            val recentCandles = candles.takeLast(min(lookback, candles.size))
            val sessionHigh = recentCandles.maxByOrNull { it.high }
            val sessionLow = recentCandles.minByOrNull { it.low }
            
            if (sessionHigh != null) {
                liquidity.add(LiquidityLevel(sessionHigh.high, LiquidityType.SESSION_HIGH, timeframe, sessionHigh.timestamp))
            }
            if (sessionLow != null) {
                liquidity.add(LiquidityLevel(sessionLow.low, LiquidityType.SESSION_LOW, timeframe, sessionLow.timestamp))
            }
        }
        
        return liquidity
    }

    // --- Sweep Detection ---
    private fun detectSweep(
        candles: List<Candle>,
        liquidityLevels: List<LiquidityLevel>
    ): LiquiditySweep? {
        if (candles.size < 3) return null
        
        val last = candles.last()
        val prev = candles[candles.size - 2]
        
        // Check for sweep: wick beyond liquidity, then reversal
        for (liq in liquidityLevels.filter { !it.swept }) {
            val isHighSweep = liq.type == LiquidityType.SWING_HIGH || liq.type == LiquidityType.SESSION_HIGH
            val isLowSweep = liq.type == LiquidityType.SWING_LOW || liq.type == LiquidityType.SESSION_LOW
            
            // Bullish sweep: wick below liquidity low, then close above
            if (isLowSweep) {
                if (last.low < liq.price && last.close > liq.price && prev.close > liq.price) {
                    liq.swept = true
                    return LiquiditySweep(liq, last.timestamp, last.low)
                }
            }
            
            // Bearish sweep: wick above liquidity high, then close below
            if (isHighSweep) {
                if (last.high > liq.price && last.close < liq.price && prev.close < liq.price) {
                    liq.swept = true
                    return LiquiditySweep(liq, last.timestamp, last.high)
                }
            }
        }
        
        return null
    }

    // --- BOS Detection ---
    private fun detectBOS(
        candles: List<Candle>,
        swingHighs: List<SwingPoint>,
        swingLows: List<SwingPoint>
    ): BOSPoint? {
        if (candles.size < 2) return null
        
        val last = candles.last()
        
        // Bullish BOS: Close above previous swing high
        if (swingHighs.isNotEmpty()) {
            val lastSwingHigh = swingHighs.last().price
            if (last.close > lastSwingHigh) {
                return BOSPoint(lastSwingHigh, TradeDirection.LONG, last.timestamp)
            }
        }
        
        // Bearish BOS: Close below previous swing low
        if (swingLows.isNotEmpty()) {
            val lastSwingLow = swingLows.last().price
            if (last.close < lastSwingLow) {
                return BOSPoint(lastSwingLow, TradeDirection.SHORT, last.timestamp)
            }
        }
        
        return null
    }

    // --- Displacement Detection ---
    private fun detectDisplacement(candles: List<Candle>, config: Config): List<DisplacementCandle> {
        if (candles.size < 10) return emptyList()
        
        val displacement = mutableListOf<DisplacementCandle>()
        
        // Calculate average body size
        val recentCandles = candles.takeLast(20)
        val avgBodySize = recentCandles.map { abs(it.close - it.open) }.average()
        
        // Look for displacement in recent candles
        candles.takeLast(10).forEachIndexed { idx, candle ->
            val bodySize = abs(candle.close - candle.open)
            val upperWick = candle.high - max(candle.close, candle.open)
            val lowerWick = min(candle.close, candle.open) - candle.low
            val totalWick = upperWick + lowerWick
            
            // Displacement criteria: Large body, small wicks
            if (bodySize > avgBodySize * config.displacementMultiplier && totalWick < bodySize * config.wickRatio) {
                val direction = if (candle.close > candle.open) TradeDirection.LONG else TradeDirection.SHORT
                displacement.add(DisplacementCandle(candles.size - 10 + idx, direction, candle.timestamp, bodySize))
            }
        }
        
        return displacement
    }

    // --- Zone Identification ---
    private fun identifyZones(candles: List<Candle>, lookbackLimit: Int, tfLabel: String): List<Zone> {
        val zones = mutableListOf<Zone>()
        
        val lookback = min(lookbackLimit, candles.size - 3)
        
        for (i in candles.size - lookback until candles.size - 2) {
            val c1 = candles[i]
            val c2 = candles[i+1]
            val c3 = candles[i+2]
            
            // Fair Value Gaps
            if (c3.low > c1.high) {
                val id = "FVG_LONG_${tfLabel}_${c2.timestamp}"
                zones.add(Zone(id, ZoneType.FVG, c3.low, c1.high, c2.timestamp, TradeDirection.LONG, tfLabel))
            }
            if (c3.high < c1.low) {
                val id = "FVG_SHORT_${tfLabel}_${c2.timestamp}"
                zones.add(Zone(id, ZoneType.FVG, c1.low, c3.high, c2.timestamp, TradeDirection.SHORT, tfLabel))
            }
            
            // Order Blocks
            if (c3.low > c1.high && c1.close < c1.open) {
                val id = "OB_LONG_${tfLabel}_${c1.timestamp}"
                zones.add(Zone(id, ZoneType.ORDER_BLOCK, c1.high, c1.low, c1.timestamp, TradeDirection.LONG, tfLabel))
            }
            if (c3.high < c1.low && c1.close > c1.open) {
                val id = "OB_SHORT_${tfLabel}_${c1.timestamp}"
                zones.add(Zone(id, ZoneType.ORDER_BLOCK, c1.high, c1.low, c1.timestamp, TradeDirection.SHORT, tfLabel))
            }
        }
        
        return zones.takeLast(10)
    }

    private fun checkZoneMitigation(zone: Zone, candles: List<Candle>): Boolean {
        // Check if price has fully broken through the zone
        for (candle in candles) {
            if (zone.direction == TradeDirection.LONG) {
                // Bullish zone mitigated if price closes below zone bottom
                if (candle.close < zone.bottom) return true
            } else {
                // Bearish zone mitigated if price closes above zone top
                if (candle.close > zone.top) return true
            }
        }
        
        return false
    }

    // --- LTF Confirmation ---
    data class Confirmation(val confirmed: Boolean, val reason: String)

    private fun checkLTFConfirmation(candles: List<Candle>, direction: TradeDirection): Confirmation {
        if (candles.size < 3) return Confirmation(false, "")
        
        val last = candles.last()
        val prev = candles[candles.size - 2]
        
        if (direction == TradeDirection.LONG) {
            // Bullish Engulfing
            val isBullishEngulfing = prev.close < prev.open && last.close > last.open &&
                                     last.close > prev.open && last.open < prev.close
            
            if (isBullishEngulfing) return Confirmation(true, "Bullish Engulfing")
            
            // Pin Bar (Hammer)
            val bodySize = abs(last.close - last.open)
            val lowerWick = min(last.close, last.open) - last.low
            if (lowerWick > bodySize * 2) return Confirmation(true, "Bullish Pin Bar")
            
        } else {
            // Bearish Engulfing
            val isBearishEngulfing = prev.close > prev.open && last.close < last.open &&
                                     last.close < prev.open && last.open > prev.close
            
            if (isBearishEngulfing) return Confirmation(true, "Bearish Engulfing")

            // Pin Bar (Shooting Star)
            val bodySize = abs(last.close - last.open)
            val upperWick = last.high - max(last.close, last.open)
            if (upperWick > bodySize * 2) return Confirmation(true, "Bearish Pin Bar")
        }
        
        return Confirmation(false, "")
    }
}
