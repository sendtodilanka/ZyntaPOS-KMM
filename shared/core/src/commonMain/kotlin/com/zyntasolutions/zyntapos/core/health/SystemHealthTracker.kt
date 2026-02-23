package com.zyntasolutions.zyntapos.core.health

import kotlinx.coroutines.flow.StateFlow

// ─────────────────────────────────────────────────────────────────────────────
// SystemHealthTracker — Platform-specific system diagnostics provider
//
// Collects runtime metrics (memory, disk, CPU, database, uptime) and exposes
// them as a reactive StateFlow for the System Health screen under Settings.
//
// Android  → ActivityManager, StatFs, Runtime, DatabaseUtils
// Desktop  → Runtime, ManagementFactory, File, NIO
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Snapshot of current system health metrics.
 *
 * All memory/disk values are in bytes. Percentages are 0.0–100.0.
 */
data class HealthSnapshot(
    // ── Memory ───────────────────────────────────────────────────────────────
    val heapUsedBytes: Long = 0L,
    val heapMaxBytes: Long = 0L,
    val heapUsagePercent: Float = 0f,

    // ── Disk ─────────────────────────────────────────────────────────────────
    val diskFreeBytes: Long = 0L,
    val diskTotalBytes: Long = 0L,
    val diskUsagePercent: Float = 0f,

    // ── Database ─────────────────────────────────────────────────────────────
    val databaseSizeBytes: Long = 0L,
    val databaseStatus: DatabaseStatus = DatabaseStatus.UNKNOWN,

    // ── Runtime ──────────────────────────────────────────────────────────────
    val uptimeMillis: Long = 0L,
    val availableProcessors: Int = 0,
    val platformDescription: String = "",

    // ── Network ──────────────────────────────────────────────────────────────
    val isNetworkAvailable: Boolean = false,

    // ── Overall ──────────────────────────────────────────────────────────────
    val overallStatus: OverallStatus = OverallStatus.UNKNOWN,
    val lastRefreshedAtMillis: Long = 0L,
)

/** Database operational status. */
enum class DatabaseStatus {
    HEALTHY,
    DEGRADED,
    ERROR,
    UNKNOWN,
}

/** Aggregate system health level. */
enum class OverallStatus {
    HEALTHY,
    WARNING,
    CRITICAL,
    UNKNOWN,
}

/**
 * Platform-specific system health diagnostics provider.
 *
 * Implementations collect OS-level metrics and expose a [StateFlow] of
 * [HealthSnapshot] that the UI layer observes. Call [refresh] to trigger
 * a manual re-collection; call [startAutoRefresh] for periodic updates.
 */
interface SystemHealthTracker {

    /** Current health snapshot, updated on [refresh] or auto-refresh ticks. */
    val snapshot: StateFlow<HealthSnapshot>

    /** Trigger an immediate collection of all metrics. */
    suspend fun refresh()

    /** Start periodic background refresh at [intervalMs] intervals. */
    fun startAutoRefresh(intervalMs: Long = 30_000L)

    /** Stop periodic background refresh. */
    fun stopAutoRefresh()
}

/**
 * Returns the platform-specific [SystemHealthTracker] implementation.
 *
 * - **Android:** Uses [ActivityManager], [StatFs], [Runtime].
 * - **Desktop:** Uses [Runtime], [ManagementFactory], [File].
 */
expect fun createSystemHealthTracker(): SystemHealthTracker
