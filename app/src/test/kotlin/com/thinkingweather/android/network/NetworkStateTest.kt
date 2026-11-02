package com.thinkingweather.android.network

import org.junit.Assert.*
import org.junit.Test

class NetworkStateTest {
    @Test fun noInternetRouteOrACaptivePortalIsOffline() {
        assertEquals(NetworkState.OFFLINE, networkState(true, true, false))
        assertEquals(NetworkState.OFFLINE, networkState(false, false, true))
        assertEquals("当前未连接网络", networkNotice(NetworkState.OFFLINE))
    }

    @Test fun availableCellularUsesTheShortMobileDataNoticeWithoutRequiringValidation() {
        assertEquals(NetworkState.CELLULAR, networkState(true, false, true))
        assertEquals("请注意，当前正在使用移动流量", networkNotice(NetworkState.CELLULAR))
        assertEquals(NetworkState.OTHER, networkState(true, false, false))
        assertNull(networkNotice(NetworkState.OTHER))
    }
}
