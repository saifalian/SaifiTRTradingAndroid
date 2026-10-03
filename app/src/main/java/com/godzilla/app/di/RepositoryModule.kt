package com.godzilla.app.di

import com.godzilla.app.data.repository.ExchangeRepository
import com.godzilla.app.data.repository.YahooFinanceRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    @Named("ForexRepositories")
    fun provideForexRepositories(yahoo: YahooFinanceRepository): List<ExchangeRepository> {
        return listOf(yahoo)
    }

    @Provides
    @Singleton
    @Named("StocksRepositories")
    fun provideStocksRepositories(yahoo: YahooFinanceRepository): List<ExchangeRepository> {
        return listOf(yahoo)
    }
}
