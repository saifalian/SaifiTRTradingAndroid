package com.godzilla.app.domain

import com.godzilla.app.domain.model.AssetClass
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

object MarketHours {
    private val EST_ZONE = ZoneId.of("America/New_York")

    fun isMarketOpen(assetClass: AssetClass): Boolean {
        val now = ZonedDateTime.now(EST_ZONE)
        
        return when (assetClass) {
            AssetClass.CRYPTO -> true // Crypto is 24/7
            
            AssetClass.FOREX -> {
                // Forex opens Sunday 5PM EST, closes Friday 5PM EST
                val day = now.dayOfWeek
                val hour = now.hour
                
                when (day) {
                    DayOfWeek.FRIDAY -> hour < 17
                    DayOfWeek.SATURDAY -> false
                    DayOfWeek.SUNDAY -> hour >= 17
                    else -> true // Mon-Thu open 24h
                }
            }
            
            AssetClass.STOCKS, AssetClass.INDICES -> {
                // US Stocks: Mon-Fri 9:30 AM - 4:00 PM EST
                val day = now.dayOfWeek
                val hour = now.hour
                val minute = now.minute
                
                if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                    return false
                }
                
                // 9:30 AM to 4:00 PM (16:00)
                val timeInMinutes = hour * 60 + minute
                val openTime = 9 * 60 + 30 // 9:30
                val closeTime = 16 * 60 // 16:00
                
                timeInMinutes in openTime until closeTime
            }
            
            AssetClass.COMMODITIES -> {
                // Simplified Commodities (similar to Forex but with breaks)
                // For now, treat same as Forex for simplicity unless specified
                val day = now.dayOfWeek
                val hour = now.hour
                
                when (day) {
                    DayOfWeek.FRIDAY -> hour < 17
                    DayOfWeek.SATURDAY -> false
                    DayOfWeek.SUNDAY -> hour >= 17
                    else -> true
                }
            }
        }
    }
}
