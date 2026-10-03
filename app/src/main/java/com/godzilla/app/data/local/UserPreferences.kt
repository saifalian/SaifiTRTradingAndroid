package com.godzilla.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKey(exchange: String, key: String) {
        sharedPreferences.edit().putString("${exchange}_api_key", key).apply()
    }

    fun getApiKey(exchange: String): String? {
        return sharedPreferences.getString("${exchange}_api_key", null)
    }

    fun saveApiSecret(exchange: String, secret: String) {
        sharedPreferences.edit().putString("${exchange}_api_secret", secret).apply()
    }

    fun getApiSecret(exchange: String): String? {
        return sharedPreferences.getString("${exchange}_api_secret", null)
    }

    // Config Settings
    fun saveConfig(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    fun getConfig(key: String, defaultValue: String): String {
        return sharedPreferences.getString(key, defaultValue) ?: defaultValue
    }

    // Active Strategies
    fun saveActiveStrategies(strategyIds: Set<String>) {
        sharedPreferences.edit().putStringSet("active_strategies", strategyIds).apply()
    }

    fun getActiveStrategies(): Set<String> {
        return sharedPreferences.getStringSet("active_strategies", emptySet()) ?: emptySet()
    }

    // Bot Persistence
    fun saveBots(botsJson: String) {
        sharedPreferences.edit().putString("saved_bots", botsJson).apply()
    }

    fun getBots(): String? {
        return sharedPreferences.getString("saved_bots", null)
    }

    // Strategy Parameters
    fun saveStrategyParameter(strategyId: String, paramKey: String, value: String) {
        sharedPreferences.edit().putString("strategy_${strategyId}_${paramKey}", value).apply()
    }

    fun getStrategyParameter(strategyId: String, paramKey: String, defaultValue: String): String {
        return sharedPreferences.getString("strategy_${strategyId}_${paramKey}", defaultValue) ?: defaultValue
    }

    // System Settings
    fun savePriceUpdateDelay(delayMs: Long) {
        sharedPreferences.edit().putLong("price_update_delay", delayMs).apply()
    }

    fun getPriceUpdateDelay(): Long {
        return sharedPreferences.getLong("price_update_delay", 500L)
    }
}
