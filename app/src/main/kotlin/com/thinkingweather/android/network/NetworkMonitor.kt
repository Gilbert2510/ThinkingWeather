package com.thinkingweather.android.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper

/** Registered only while the activity is visible; callback storms are coalesced. */
internal class NetworkMonitor(context: Context, private val onChanged: (NetworkState) -> Unit) {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var started = false
    private var lastDelivered: NetworkState? = null
    private val deliver = Runnable {
        if (started) {
            val current = currentState(manager)
            if (current != lastDelivered) {
                lastDelivered = current
                onChanged(current)
            }
        }
    }
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = schedule()
        override fun onLost(network: Network) = schedule()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = schedule()
    }

    fun start() {
        if (started || manager == null) return
        started = true
        lastDelivered = null
        manager.registerDefaultNetworkCallback(callback, handler)
        schedule()
    }

    fun stop() {
        if (!started) return
        started = false
        handler.removeCallbacks(deliver)
        manager?.unregisterNetworkCallback(callback)
    }

    private fun schedule() {
        handler.removeCallbacks(deliver)
        // Wait briefly for validation and default-network handover before notifying.
        if (started) handler.postDelayed(deliver, 350)
    }

    companion object {
        fun hasInternet(context: Context): Boolean =
            currentState(context.applicationContext.getSystemService(ConnectivityManager::class.java)) != NetworkState.OFFLINE

        private fun currentState(manager: ConnectivityManager?): NetworkState {
            val capabilities = manager?.activeNetwork?.let(manager::getNetworkCapabilities)
                ?: return NetworkState.OFFLINE
            var cellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            // VPN capabilities normally include their underlying transports. For VPNs
            // exposing only TRANSPORT_VPN, use validated underlying networks as a fallback.
            if (!cellular && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val underlying = manager.allNetworks.mapNotNull(manager::getNetworkCapabilities)
                    .filter { !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) }
                cellular = underlying.none { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } &&
                    underlying.any { it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) }
            }
            return networkState(
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED), cellular,
            )
        }
    }
}
