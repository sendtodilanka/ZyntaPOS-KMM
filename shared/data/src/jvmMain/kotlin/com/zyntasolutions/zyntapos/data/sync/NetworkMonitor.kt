package com.zyntasolutions.zyntapos.data.sync

import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.InetAddress

/**
 * Desktop (JVM) implementation of [NetworkMonitor].
 *
 * Periodically checks internet reachability via [InetAddress.isReachable] against
 * [PING_HOST] every [CHECK_INTERVAL_MS] milliseconds on the [Dispatchers.IO] thread pool.
 *
 * Starts immediately with a synchronous initial check so callers don't have to wait
 * one full interval before getting the first state.
 */
actual class NetworkMonitor {

    private val log = ZyntaLogger.forModule("NetworkMonitor-Desktop")

    private val _isConnected = MutableStateFlow(checkReachable())
    actual val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null

    actual fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            log.d("Desktop reachability polling started (interval: ${CHECK_INTERVAL_MS}ms)")
            while (true) {
                delay(CHECK_INTERVAL_MS)
                val reachable = checkReachable()
                if (reachable != _isConnected.value) {
                    _isConnected.value = reachable
                    log.d("Connectivity changed → connected=$reachable")
                }
            }
        }
    }

    actual fun stop() {
        job?.cancel()
        job = null
        log.d("Desktop reachability polling stopped")
    }

    private fun checkReachable(): Boolean = try {
        InetAddress.getByName(PING_HOST).isReachable(3_000)
    } catch (e: Exception) {
        log.d("Reachability check failed: ${e.message}")
        false
    }

    actual companion object {
        actual const val CHECK_INTERVAL_MS: Long = 10_000L
        actual const val PING_HOST: String = "8.8.8.8"
    }
}
