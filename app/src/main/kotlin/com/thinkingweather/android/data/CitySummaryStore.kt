package com.thinkingweather.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import org.json.JSONArray

private val Context.citySummaryPreferences by preferencesDataStore(name = "thinking_weather_city_cards_v1")

internal interface CitySummaryCache {
    suspend fun read(): List<CityWeatherSummary>
    suspend fun save(cards: Collection<CityWeatherSummary>)
}

/** Keeps current-only cards separate from complete cached forecasts. */
internal class CitySummaryStore(context: Context) : CitySummaryCache {
    private val store = context.applicationContext.citySummaryPreferences
    private val cardsKey = stringPreferencesKey("cards")

    override suspend fun read(): List<CityWeatherSummary> {
        val prefs = store.data.catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }.first()
        return decode(prefs[cardsKey])
    }

    override suspend fun save(cards: Collection<CityWeatherSummary>) {
        val bounded = cards.distinctBy { it.city.location }.sortedByDescending(CityWeatherSummary::fetchedAt)
            .take(WeatherStore.MAX_CITIES)
        store.edit { prefs ->
            prefs[cardsKey] = JSONArray().apply { bounded.forEach { put(WeatherJson.encodeCurrentSummary(it)) } }.toString()
        }
    }

    private fun decode(value: String?): List<CityWeatherSummary> = runCatching {
        val array = JSONArray(value ?: "[]")
        (0 until minOf(array.length(), WeatherStore.MAX_CITIES)).mapNotNull { index ->
            runCatching { WeatherJson.cachedCurrentSummary(array.getJSONObject(index)) }.getOrNull()
        }.distinctBy { it.city.location }
    }.getOrDefault(emptyList())
}
