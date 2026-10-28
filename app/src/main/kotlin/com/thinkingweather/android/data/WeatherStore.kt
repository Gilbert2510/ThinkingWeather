package com.thinkingweather.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.thinkingweather.android.model.*
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import org.json.JSONArray

private val Context.weatherPreferences by preferencesDataStore(name = "thinking_weather_v1")

internal data class SavedWeatherState(
    val cities: List<City>,
    val selectedCity: City,
    val unit: TemperatureUnit,
    val isDemo: Boolean,
)

/** DataStore atomically persists settings and a small, mode-separated offline cache. */
internal class WeatherStore(context: Context, private val configured: Boolean) {
    private val store = context.applicationContext.weatherPreferences
    private val citiesKey = stringPreferencesKey("cities")
    private val selectedKey = stringPreferencesKey("selected_city")
    private val unitKey = stringPreferencesKey("temperature_unit")
    private val demoKey = booleanPreferencesKey("demo_mode")
    private val cacheKey = stringPreferencesKey("weather_cache")

    suspend fun read(): SavedWeatherState {
        val prefs = store.data.catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }.first()
        val cities = runCatching {
            val list = JSONArray(prefs[citiesKey] ?: "[]")
            normalizeSavedCities((0 until list.length()).map { WeatherJson.city(list.getJSONObject(it)) })
        }.getOrDefault(emptyList()).ifEmpty { DefaultCities.initial }
        return SavedWeatherState(cities, cities.firstOrNull { it.location == prefs[selectedKey] } ?: cities.first(),
            runCatching { TemperatureUnit.valueOf(prefs[unitKey].orEmpty()) }.getOrDefault(TemperatureUnit.CELSIUS),
            prefs[demoKey] ?: !configured)
    }

    suspend fun setCities(cities: List<City>, selected: City) {
        store.edit { prefs ->
            prefs[citiesKey] = JSONArray().apply { normalizeSavedCities(cities).forEach { put(WeatherJson.encodeCity(it)) } }.toString()
            prefs[selectedKey] = selected.location
        }
    }

    suspend fun setUnit(unit: TemperatureUnit) { store.edit { it[unitKey] = unit.name } }
    suspend fun setDemo(enabled: Boolean) { store.edit { it[demoKey] = enabled } }

    suspend fun cached(city: City, isDemo: Boolean): WeatherSnapshot? {
        val prefs = store.data.catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }.first()
        return decodeCache(prefs[cacheKey]).firstOrNull { it.city.location == city.location && it.isDemo == isDemo }
    }

    suspend fun save(snapshot: WeatherSnapshot) {
        store.edit { prefs ->
            val snapshots = (listOf(snapshot) + decodeCache(prefs[cacheKey]))
                .distinctBy { "${it.isDemo}:${it.city.location}" }.take(MAX_CACHED_CITIES)
            prefs[cacheKey] = JSONArray().apply { snapshots.forEach { put(WeatherJson.encodeSnapshot(it)) } }.toString()
        }
    }

    private fun decodeCache(value: String?): List<WeatherSnapshot> = runCatching {
        val array = JSONArray(value ?: "[]")
        (0 until minOf(array.length(), MAX_CACHED_CITIES)).mapNotNull { index ->
            runCatching { WeatherJson.cachedSnapshot(array.getJSONObject(index)) }.getOrNull()
        }
    }.getOrDefault(emptyList())

    companion object {
        const val MAX_CITIES = MAX_SAVED_CITIES
        // At most 30 live and 30 local scenes can coexist without one mode evicting the other immediately.
        const val MAX_CACHED_CITIES = MAX_CITIES * 2
    }
}
