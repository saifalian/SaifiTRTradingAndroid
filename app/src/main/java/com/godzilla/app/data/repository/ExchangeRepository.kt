package com.godzilla.app.data.repository

import com.godzilla.app.domain.model.Candle
import com.godzilla.app.domain.model.MarketData
import com.godzilla.app.domain.model.MarketType
import kotlinx.coroutines.flow.Flow

interface ExchangeRepository {
    val exchangeName: String
    fun getMarketData(symbol: String, marketType: MarketType): Flow<MarketData>
    suspend fun getHistoricalReturns(symbol: String, marketType: MarketType, limit: Int): List<Double>
    suspend fun getKLines(symbol: String, marketType: MarketType, interval: com.godzilla.app.domain.model.TimeFrame, limit: Int): List<Candle>
}
