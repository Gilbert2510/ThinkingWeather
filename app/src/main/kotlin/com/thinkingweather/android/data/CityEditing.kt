package com.thinkingweather.android.data

import com.thinkingweather.android.model.City

/** Reordering accepts only saved identities, and keeps cities added while the editor was open. */
internal fun orderedSavedCities(cities: List<City>, order: List<String>): List<City> {
    val saved = cities.associateBy(City::location)
    return (order.mapNotNull(saved::get) + cities).distinctBy(City::location)
}

internal data class CityRemovalPlan(val cities: List<City>, val selectedCity: City, val blocked: Boolean)

/** A batch is atomic: selecting every city never silently removes only some of them. */
internal fun planCityRemoval(cities: List<City>, selection: Set<String>, current: City): CityRemovalPlan {
    val remaining = cities.filterNot { it.location in selection }
    if (remaining.isEmpty()) return CityRemovalPlan(cities, current, blocked = true)
    return CityRemovalPlan(remaining, remaining.firstOrNull { it.location == current.location } ?: remaining.first(), blocked = false)
}
