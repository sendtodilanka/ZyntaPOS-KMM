package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal providing the current sync display status to any composable in the tree.
 *
 * Set by the root `App()` composable after observing [SyncEngine] and [NetworkMonitor] state.
 * Consumed by [ZyntaSyncStatusIndicator] in the navigation drawer footer.
 *
 * Default value is `null` (sync status not yet available — indicator hidden).
 */
val LocalSyncDisplayStatus = staticCompositionLocalOf<SyncDisplayStatus?> { null }

/**
 * CompositionLocal providing the count of pending sync operations.
 *
 * Set alongside [LocalSyncDisplayStatus] from the root `App()` composable.
 * Default value is `0`.
 */
val LocalSyncPendingCount = staticCompositionLocalOf { 0 }
