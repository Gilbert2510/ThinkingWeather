package com.thinkingweather.android.data

import org.junit.Assert.*
import org.junit.Test

class WeatherRepositoryTest {
    @Test fun acceptsReleaseHttps() {
        val result = WeatherRepository.validateBaseUrl("https://weather.example.com/gateway", false)
        assertEquals("https://weather.example.com/gateway/", result.toString())
    }

    @Test(expected = IllegalArgumentException::class) fun releaseRejectsHttpEvenForLocalhost() {
        WeatherRepository.validateBaseUrl("http://localhost:8080", false)
    }

    @Test(expected = IllegalArgumentException::class) fun debugRejectsUnencryptedRemoteServer() {
        WeatherRepository.validateBaseUrl("http://weather.example.com", true)
    }

    @Test fun emulatorDebugMayUseLocalGateway() {
        assertEquals("10.0.2.2", WeatherRepository.validateBaseUrl("http://10.0.2.2:8080", true).host)
    }

    @Test fun emulatorLoopbackUsesComputerAliasAndPreservesPortAndEncodedPath() {
        for (host in listOf("localhost", "127.0.0.1", "[::1]")) {
            val original = WeatherRepository.validateBaseUrl("http://$host:9182/gateway%20one", true)
            val result = WeatherRepository.gatewayForDevice(original, debug = true, emulator = true)
            assertEquals("10.0.2.2", result.host)
            assertEquals(9182, result.port)
            assertEquals("/gateway%20one/", result.encodedPath)
            assertEquals("http", result.scheme)
        }
    }

    @Test fun physicalPhoneRetainsLoopbackForUsbForwarding() {
        val original = WeatherRepository.validateBaseUrl("http://127.0.0.1:8080/gateway", true)
        assertSame(original, WeatherRepository.gatewayForDevice(original, debug = true, emulator = false))
    }

    @Test fun httpsAndReleaseGatewaysAreNeverRemapped() {
        for (host in listOf("localhost", "127.0.0.1", "weather.example.com")) {
            val original = WeatherRepository.validateBaseUrl("https://$host/gateway", false)
            assertSame(original, WeatherRepository.gatewayForDevice(original, debug = true, emulator = true))
            assertSame(original, WeatherRepository.gatewayForDevice(original, debug = false, emulator = true))
        }
    }

    @Test fun explicitEmulatorAliasIsNotRebuilt() {
        val original = WeatherRepository.validateBaseUrl("http://10.0.2.2:8080", true)
        assertSame(original, WeatherRepository.gatewayForDevice(original, debug = true, emulator = true))
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsEmbeddedCredentials() {
        WeatherRepository.validateBaseUrl("https://user:secret@weather.example.com", false)
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsBaseUrlWithQuery() {
        WeatherRepository.validateBaseUrl("https://weather.example.com?token=anything", false)
    }
}
