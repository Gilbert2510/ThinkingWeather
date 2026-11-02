package com.thinkingweather.android.data

import org.junit.Assert.*
import org.junit.Test

class WeatherRepositoryTest {
    @Test fun acceptsOnlyAnUncredentialledHttpsBaseAndKeepsAnEncodedPrefix() {
        assertEquals("https://weather.example.com/gateway%20one/", WeatherRepository.validateBaseUrl("https://weather.example.com/gateway%20one").toString())
    }

    @Test fun rejectsCleartextLocalhostEmulatorAliasesAndUnconfiguredAddresses() {
        listOf("", "not a URL", "http://weather.example.com", "http://127.0.0.1:8080", "http://10.0.2.2:8080").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { WeatherRepository.validateBaseUrl(value) }
        }
    }

    @Test fun credentialsQueriesAndFragmentsCannotBecomePartOfAPublicApiEndpoint() {
        listOf("https://user:secret@weather.example.com", "https://weather.example.com?key=x", "https://weather.example.com/#token").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { WeatherRepository.validateBaseUrl(value) }
        }
    }
}
