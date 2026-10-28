package com.thinkingweather.android.network

import org.junit.Assert.*
import org.junit.Test

class NetworkStateTest {
    @Test fun wifiWithoutValidatedInternetIsOffline() {
        assertEquals(NetworkState.OFFLINE, networkState(hasInternet = true, validated = false, usesCellular = false))
        assertEquals(NetworkState.OFFLINE, networkState(hasInternet = false, validated = true, usesCellular = true))
    }

    @Test fun cellularAndNonCellularInternetHaveDistinctNotices() {
        assertEquals(NetworkState.CELLULAR, networkState(true, true, true))
        assertEquals("请注意，当前正在使用移动流量", networkNotice(NetworkState.CELLULAR))
        assertEquals("当前未连接网络", networkNotice(NetworkState.OFFLINE))
        assertEquals(NetworkState.OTHER, networkState(true, true, false))
        assertNull(networkNotice(NetworkState.OTHER))
    }

    @Test fun emulatorAliasOnPhysicalPhoneGivesActionableDiagnosis() {
        val error = DevelopmentGateway.configurationError("10.0.2.2", emulator = false)
        assertTrue(error.orEmpty().contains("仅供 Android 模拟器"))
        assertTrue(error.orEmpty().contains("adb reverse"))
        assertNull(DevelopmentGateway.configurationError("10.0.2.2", emulator = true))
        assertNull(DevelopmentGateway.configurationError("127.0.0.1", emulator = false))
    }

    @Test fun localGatewayRecognitionDoesNotAllowOtherPrivateOrRemoteHosts() {
        for (host in listOf("127.0.0.1", "localhost", "::1", "[::1]", "10.0.2.2")) {
            assertTrue(host, DevelopmentGateway.isLocalHost(host))
        }
        for (host in listOf("192.168.1.10", "10.0.2.3", "localhost.example.com", "example.com")) {
            assertFalse(host, DevelopmentGateway.isLocalHost(host))
        }
        assertFalse(DevelopmentGateway.isLocalHost(null))
    }

    @Test fun emulatorDetectionDoesNotTreatOrdinaryPhysicalPhoneAsEmulator() {
        assertTrue(DevelopmentGateway.isEmulator("google/sdk_gphone64", "sdk_gphone64_x86_64", "ranchu", "sdk_gphone64"))
        assertFalse(DevelopmentGateway.isEmulator("vivo/PD2305", "V2307", "qcom", "PD2305"))
    }

    @Test fun connectionAdviceMatchesTheActualDeviceRoute() {
        val emulator = DevelopmentGateway.connectionFailure("10.0.2.2", 9182, emulator = true)
        assertTrue(emulator.contains("直连电脑"))
        assertTrue(emulator.contains("9182"))
        assertFalse(emulator.contains("adb reverse"))
        val phone = DevelopmentGateway.connectionFailure("127.0.0.1", 9182, emulator = false)
        assertTrue(phone.contains("adb reverse tcp:9182 tcp:9182"))
    }
}
