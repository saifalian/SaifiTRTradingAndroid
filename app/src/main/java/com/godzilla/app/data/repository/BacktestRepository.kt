package com.godzilla.app.data.repository

import android.content.Context
import com.godzilla.app.domain.model.BacktestResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BacktestRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val fileName = "backtest_results.json"

    suspend fun saveResult(result: BacktestResult) {
        withContext(Dispatchers.IO) {
            val currentResults = getAllResults().toMutableList()
            currentResults.add(0, result) // Add new result to the top
            saveList(currentResults)
        }
    }

    suspend fun getAllResults(): List<BacktestResult> {
        return withContext(Dispatchers.IO) {
            val file = File(context.filesDir, fileName)
            if (!file.exists()) {
                return@withContext emptyList()
            }
            try {
                val json = file.readText()
                val type = object : TypeToken<List<BacktestResult>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    suspend fun deleteResult(resultId: String) {
        withContext(Dispatchers.IO) {
            val currentResults = getAllResults().toMutableList()
            currentResults.removeAll { it.id == resultId }
            saveList(currentResults)
        }
    }

    private fun saveList(list: List<BacktestResult>) {
        val file = File(context.filesDir, fileName)
        val json = gson.toJson(list)
        file.writeText(json)
    }
}
