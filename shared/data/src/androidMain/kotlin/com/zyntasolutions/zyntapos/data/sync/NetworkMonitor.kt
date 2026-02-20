package com.zyntasolutions.zyntapos.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android implementation of [NetworkMonitor].
 *
 * Uses [ConnectivityManager.NetworkCallback] to emit real-time connectivity
 * changes to [isConnected]. The initial state is read synchronously from
 * the active network before the callback is registered.
 *
 * @param context Application context (provided by Koin androidContext()).
 */
actual class NetworkMonitor(private val context: Context) {

    private val log = Logger.withTag("NetworkMonitor-Android")
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isConnected = MutableStateFlow(readCurrentState())
    actual val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            log.d { "Network available: $network" }
            _isConnected.value = true
        }

        override fun onLost(network: Network) {
            // Re-check if any other usable network remains before declaring offline
            _isConnected.value = readCurrentState()
            log.d { "Network lost: $network — connected=${_isConnected.value}" }
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val usable = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (!usable) _isConnected.value = false
        }
    }

    actual fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
        log.d { "NetworkCallback registered" }
    }

    actual fun stop() {
        runCatching { cm.unregisterNetworkCallback(callback) }
        log.d { "NetworkCallback unregistered" }
    }

    private fun readCurrentState(): Boolean {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    actual companion object {
        actual const val CHECK_INTERVAL_MS: Long = 10_000L
        actual const val PING_HOST: String = "8.8.8.8"
    }
}
