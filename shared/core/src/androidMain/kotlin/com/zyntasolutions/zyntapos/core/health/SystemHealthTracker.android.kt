package com.zyntasolutions.zyntapos.core.health

import android.os.Debug
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Android actual — SystemHealthTracker
//
// Collects:
// - Heap memory via Runtime.getRuntime()
// - Disk usage via StatFs on external storage
// - Database file size via Environment.getDataDirectory()
// - Uptime via SystemClock.elapsedRealtime()
// - CPU count via Runtime.availableProcessors()
// ─────────────────────────────────────────────────────────────────────────────

class AndroidSystemHealthTracker : SystemHealthTracker {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _snapshot = MutableStateFlow(HealthSnapshot())
    override val snapshot: StateFlow<HealthSnapshot> = _snapshot.asStateFlow()

    private var autoRefreshJob: Job? = null

    override suspend fun refresh() {
        val runtime = Runtime.getRuntime()
        val heapMax = runtime.maxMemory()
        val heapTotal = runtime.totalMemory()
        val heapFree = runtime.freeMemory()
        val heapUsed = heapTotal - heapFree
        val heapPercent = if (heapMax > 0) (heapUsed.toFloat() / heapMax * 100f) else 0f

        // Disk
        val stat = StatFs(Environment.getDataDirectory().path)
        val diskTotal = stat.blockSizeLong * stat.blockCountLong
        val diskFree = stat.blockSizeLong * stat.availableBlocksLong
        val diskUsed = diskTotal - diskFree
        val diskPercent = if (diskTotal > 0) (diskUsed.toFloat() / diskTotal * 100f) else 0f

        // Database — scan for the ZyntaPOS SQLite database file
        val dbDir = Environment.getDataDirectory()
        val dbSize = findDatabaseSize(dbDir)

        // Uptime
        val uptimeMs = SystemClock.elapsedRealtime()

        // CPU
        val cpuCount = runtime.availableProcessors()

        // Platform
        val platform = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"

        // Overall status
        val overall = computeOverallStatus(heapPercent, diskPercent, dbSize)

        _snapshot.value = HealthSnapshot(
            heapUsedBytes = heapUsed,
            heapMaxBytes = heapMax,
            heapUsagePercent = heapPercent,
            diskFreeBytes = diskFree,
            diskTotalBytes = diskTotal,
            diskUsagePercent = diskPercent,
            databaseSizeBytes = dbSize,
            databaseStatus = if (dbSize > 0) DatabaseStatus.HEALTHY else DatabaseStatus.UNKNOWN,
            uptimeMillis = uptimeMs,
            availableProcessors = cpuCount,
            platformDescription = platform,
            isNetworkAvailable = true,
            overallStatus = overall,
            lastRefreshedAtMillis = System.currentTimeMillis(),
        )
    }

    override fun startAutoRefresh(intervalMs: Long) {
        autoRefreshJob?.cancel()
        autoRefreshJob = scope.launch {
            while (isActive) {
                refresh()
                delay(intervalMs)
            }
        }
    }

    override fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    private fun findDatabaseSize(dataDir: java.io.File): Long {
        // Typical path: /data/data/<pkg>/databases/zyntapos.db
        // We search for any .db file to be resilient to naming changes
        return try {
            val dbFiles = dataDir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".db") && it.name.contains("zyntapos", ignoreCase = true) }
                .toList()
            dbFiles.sumOf { it.length() }
        } catch (_: Exception) {
            0L
        }
    }

    private fun computeOverallStatus(heapPercent: Float, diskPercent: Float, dbSize: Long): OverallStatus {
        return when {
            heapPercent > 90f || diskPercent > 95f -> OverallStatus.CRITICAL
            heapPercent > 75f || diskPercent > 85f -> OverallStatus.WARNING
            else -> OverallStatus.HEALTHY
        }
    }
}

actual fun createSystemHealthTracker(): SystemHealthTracker = AndroidSystemHealthTracker()
