package com.godzilla.app.di

import android.content.Context
import com.godzilla.app.data.local.UserPreferences
import com.godzilla.app.domain.GarchModel
import com.godzilla.app.domain.TradingCalculator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideUserPreferences(@ApplicationContext context: Context): UserPreferences {
        return UserPreferences(context)
    }

    @Provides
    @Singleton
    fun provideGarchModel(): GarchModel {
        return GarchModel()
    }

    @Provides
    @Singleton
    fun provideTradingCalculator(garchModel: GarchModel): TradingCalculator {
        return TradingCalculator(garchModel)
    }
}
