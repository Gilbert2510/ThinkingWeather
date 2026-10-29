package com.thinkingweather.android.data

import com.thinkingweather.android.model.DefaultCities
import org.junit.Assert.*
import org.junit.Test

class CityEditingTest {
    private val cities = DefaultCities.initial

    @Test fun batchDeletingEveryCityIsRejectedAtomically() {
        val plan = planCityRemoval(cities, cities.map { it.location }.toSet(), cities[1])
        assertTrue(plan.blocked)
        assertEquals(cities, plan.cities)
        assertEquals(cities[1], plan.selectedCity)
    }

    @Test fun deletingTheCurrentCitySelectsTheFirstRemainingCity() {
        val plan = planCityRemoval(cities, setOf(cities[0].location), cities[0])
        assertFalse(plan.blocked)
        assertEquals(cities.drop(1), plan.cities)
        assertEquals(cities[1], plan.selectedCity)
    }

    @Test fun removingOtherCitiesPreservesTheCurrentSelection() {
        val plan = planCityRemoval(cities, setOf(cities[0].location, "unknown"), cities[2])
        assertEquals(cities[2], plan.selectedCity)
        assertEquals(cities.drop(1), plan.cities)
    }

    @Test fun reorderIgnoresUnknownAndDuplicateIdentitiesAndRetainsNewCities() {
        val ordered = orderedSavedCities(cities, listOf(cities[2].location, "unknown", cities[2].location, cities[0].location))
        assertEquals(listOf(cities[2], cities[0], cities[1]), ordered)
    }
}
