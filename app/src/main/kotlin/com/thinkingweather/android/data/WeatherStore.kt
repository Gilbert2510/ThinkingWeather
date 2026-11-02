package com.thinkingweather.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
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
    val locatedCityLocation: String? = null,
)

/** Persistence boundary shared by production DataStore and isolated test memory stores. */
internal interface WeatherPreferences {
    suspend fun read(): SavedWeatherState
    suspend fun setCities(cities: List<City>, selected: City, locatedCityLocation: String?)
    suspend fun setUnit(unit: TemperatureUnit)
    suspend fun cached(city: City): WeatherSnapshot?
    suspend fun cachedAll(): List<WeatherSnapshot>
    suspend fun save(snapshot: WeatherSnapshot)
}

/** DataStore atomically persists preferences and a bounded real-weather cache. */
internal class WeatherStore(context: Context) : WeatherPreferences {
    private val store = context.applicationContext.weatherPreferences
    private val citiesKey = stringPreferencesKey("cities")
    private val selectedKey = stringPreferencesKey("selected_city")
    private val unitKey = stringPreferencesKey("temperature_unit")
    private val cacheKey = stringPreferencesKey("weather_cache")
    private val locatedCityKey = stringPreferencesKey("located_city")

    override suspend fun read(): SavedWeatherState {
        val prefs = store.data.catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }.first()
        val cities = runCatching {
            val list = JSONArray(prefs[citiesKey] ?: "[]")
            normalizeSavedCities((0 until list.length()).map { WeatherJson.city(list.getJSONObject(it)) })
        }.getOrDefault(emptyList()).ifEmpty { DefaultCities.initial }
        return SavedWeatherState(cities, cities.firstOrNull { it.location == prefs[selectedKey] } ?: cities.first(),
            runCatching { TemperatureUnit.valueOf(prefs[unitKey].orEmpty()) }.getOrDefault(TemperatureUnit.CELSIUS),
            prefs[locatedCityKey]?.takeIf { key -> cities.any { it.location == key } })
    }

    /** Persist selection and the last resolved city in one transaction; no delayed marker-only write. */
    override suspend fun setCities(cities: List<City>, selected: City, locatedCityLocation: String?) {
        store.edit { prefs ->
            val normalized = normalizeSavedCities(cities)
            prefs[citiesKey] = JSONArray().apply { normalized.forEach { put(WeatherJson.encodeCity(it)) } }.toString()
            prefs[selectedKey] = selected.location
            if (locatedCityLocation != null && normalized.any { it.location == locatedCityLocation })
                prefs[locatedCityKey] = locatedCityLocation
            else prefs.remove(locatedCityKey)
        }
    }

    override suspend fun setUnit(unit: TemperatureUnit) { store.edit { it[unitKey] = unit.name } }

    override suspend fun cached(city: City): WeatherSnapshot? {
        val prefs = store.data.catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }.first()
        return decodeCache(prefs[cacheKey]).firstOrNull { it.city.location == city.location }
    }

    /** City cards warm their cache in one read/parse, rather than once for every saved city. */
    override suspend fun cachedAll(): List<WeatherSnapshot> {
        val prefs = store.data.catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }.first()
        return decodeCache(prefs[cacheKey])
    }

    override suspend fun save(snapshot: WeatherSnapshot) {
        store.edit { prefs ->
            val snapshots = (listOf(snapshot) + decodeCache(prefs[cacheKey]))
                .distinctBy { it.city.location }.take(MAX_CACHED_CITIES)
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
        const val MAX_CACHED_CITIES = MAX_CITIES
    }
}
