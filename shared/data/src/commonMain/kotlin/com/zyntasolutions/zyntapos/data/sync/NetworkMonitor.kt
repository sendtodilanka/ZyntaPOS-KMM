package com.zyntasolutions.zyntapos.data.sync

import kotlinx.coroutines.flow.StateFlow

/**
 * ZyntaPOS — Network connectivity monitor (expect/actual).
 *
 * Provides a platform-agnostic [StateFlow] of the current network reachability.
 *
 * ## Platform implementations
 * - **Android** (`androidMain`): [ConnectivityManager.NetworkCallback] → emits on every
 *   network gain/loss event. Initialised immediately with the current network state.
 * - **Desktop** (`jvmMain`): Periodic [java.net.InetAddress.isReachable] check against
 *   the configured host every [NetworkMonitorConstants.CHECK_INTERVAL_MS].
 *
 * Bind via Koin `dataModule` (Step 3.4.6):
 * ```kotlin
 * single { NetworkMonitor(...) }
 * ```
 */
expect class NetworkMonitor {

    /**
     * Emits `true` when at least one usable network interface is available,
     * `false` when the device is offline.
     */
    val isConnected: StateFlow<Boolean>

    /** Starts background monitoring. Call once at application startup. */
    fun start()

    /** Stops background monitoring and releases OS resources. Call on application exit. */
    fun stop()

    companion object {
        /** Interval between reachability checks (ms). Desktop: active polling; Android: unused. */
        val CHECK_INTERVAL_MS: Long

        /** Host used for Desktop ICMP ping reachability checks. */
        val PING_HOST: String
    }
}

/**
 * Platform-agnostic constants for [NetworkMonitor] implementations.
 * Defined outside `expect class` to avoid initializer restrictions on expect declarations.
 */
object NetworkMonitorConstants {
    /** Interval between reachability checks on Desktop (ms). */
    const val CHECK_INTERVAL_MS: Long = 10_000L

    /** Host used for Desktop ping checks. */
    const val PING_HOST: String = "8.8.8.8"
}
