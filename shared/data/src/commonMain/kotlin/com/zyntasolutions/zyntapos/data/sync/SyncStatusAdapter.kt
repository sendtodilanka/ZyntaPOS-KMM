package com.zyntasolutions.zyntapos.data.sync

import com.zyntasolutions.zyntapos.domain.port.SyncStatusPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Adapts [SyncEngine] and [NetworkMonitor] to the [SyncStatusPort] domain interface.
 *
 * Registered as a singleton in `dataModule` Koin bindings so the root `App()` composable
 * can observe sync status without importing `:shared:data` types directly.
 */
class SyncStatusAdapter(
    private val syncEngine: SyncEngine,
    private val networkMonitor: NetworkMonitor,
) : SyncStatusPort {

    override val isSyncing: StateFlow<Boolean>
        get() = syncEngine.isSyncing

    override val isNetworkConnected: StateFlow<Boolean>
        get() = networkMonitor.isConnected

    private val _lastSyncFailed = MutableStateFlow(false)
    override val lastSyncFailed: StateFlow<Boolean> = _lastSyncFailed.asStateFlow()

    override val pendingCount: StateFlow<Int>
        get() = syncEngine.pendingCount

    /**
     * Starts observing [SyncEngine.lastSyncResult] to derive [lastSyncFailed].
     * Call once during DI initialization.
     */
    fun startObserving(scope: CoroutineScope) {
        syncEngine.lastSyncResult
            .onEach { result -> _lastSyncFailed.value = result is SyncResult.Failure }
            .launchIn(scope)
        // Initial pending count refresh
        syncEngine.refreshPendingCount()
    }
}
