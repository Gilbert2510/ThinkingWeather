package com.thinkingweather.android.data

import com.thinkingweather.android.model.City
import com.thinkingweather.android.model.WeatherSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** A current-only city card. It is never substituted for a complete forecast. */
data class CityWeatherSummary(
    val city: City,
    val temp: String,
    val text: String,
    val icon: String,
    val fetchedAt: Long,
    val isStale: Boolean = false,
    val sources: List<String> = emptyList(),
    val licenses: List<String> = emptyList(),
) {
    fun isFresh(now: Long): Boolean = !isStale && now - fetchedAt in 0 until FRESH_MS

    companion object {
        const val FRESH_MS = 120_000L
        fun from(snapshot: WeatherSnapshot): CityWeatherSummary = CityWeatherSummary(
            snapshot.city, snapshot.now.temp, snapshot.now.text, snapshot.now.icon, snapshot.fetchedAt,
            snapshot.isStale, snapshot.sources, snapshot.licenses,
        )
    }
}

/** One failure cannot cancel other cards. Cancellation still closes in-flight requests. */
internal class CitySummaryLoader(
    private val fetch: suspend (City) -> CityWeatherSummary,
    private val parallelism: Int = 2,
) {
    init { require(parallelism in 1..3) }

    suspend fun load(cities: List<City>, onResult: (City, Result<CityWeatherSummary>) -> Unit) = coroutineScope {
        val permits = Semaphore(parallelism)
        cities.distinctBy(City::location).forEach { city ->
            launch {
                permits.withPermit {
                    val result = try { Result.success(fetch(city)) }
                    catch (error: CancellationException) { throw error }
                    catch (error: Exception) { Result.failure(error) }
                    onResult(city, result)
                }
            }
        }
    }
}

internal fun shouldWaitForLongerCityQuery(query: String): Boolean =
    query.length == 1 && (query[0] in 'a'..'z' || query[0] in 'A'..'Z')

internal data class CitySearchNotice(val message: String, val offline: Boolean)

internal fun citySearchNotice(error: Exception, noUsableNetwork: Boolean): CitySearchNotice = when {
    error is WeatherOfflineException || noUsableNetwork -> CitySearchNotice("天气服务不可用，请稍后重试", true)
    else -> CitySearchNotice("暂时无法搜索城市，请稍后重试。", false)
}
