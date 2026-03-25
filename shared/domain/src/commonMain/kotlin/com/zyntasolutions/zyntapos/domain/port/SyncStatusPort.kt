package com.zyntasolutions.zyntapos.domain.port

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain port exposing sync engine state to the presentation layer.
 *
 * Implemented by [SyncEngine] in `:shared:data`. Consumed by the root `App()`
 * composable in `:composeApp` to drive the [ZyntaSyncStatusIndicator] in the
 * navigation drawer footer.
 *
 * This port exists because `:composeApp:commonMain` cannot depend on `:shared:data`
 * directly (`:shared:data` is only available in platform-specific source sets).
 */
interface SyncStatusPort {
    /** True when a sync cycle (push/pull) is actively running. */
    val isSyncing: StateFlow<Boolean>

    /** True when the device has a usable network connection. */
    val isNetworkConnected: StateFlow<Boolean>

    /** True when the last sync cycle ended in failure. */
    val lastSyncFailed: StateFlow<Boolean>

    /** Number of PENDING operations in the sync outbox queue. */
    val pendingCount: StateFlow<Int>

    /**
     * Emits the number of conflicts detected whenever a sync cycle completes with
     * one or more conflicts. Consumers should show a non-blocking toast on each emission.
     */
    val newConflictCount: SharedFlow<Int>

    /**
     * Emits `Unit` whenever a sync cycle finishes (success or failure).
     * Dashboard and report screens collect this to trigger a silent data refresh without
     * showing a loading spinner, giving real-time KPI updates when connectivity is good.
     */
    val onSyncComplete: SharedFlow<Unit>
}
