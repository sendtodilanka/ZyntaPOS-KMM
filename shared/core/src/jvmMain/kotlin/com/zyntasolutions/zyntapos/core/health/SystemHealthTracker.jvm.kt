package com.zyntasolutions.zyntapos.core.health

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
import java.io.File
import java.lang.management.ManagementFactory

// ─────────────────────────────────────────────────────────────────────────────
// Desktop (JVM) actual — SystemHealthTracker
//
// Collects:
// - Heap memory via Runtime.getRuntime()
// - Disk usage via File roots
// - Database file size in ~/.zyntapos/
// - JVM uptime via ManagementFactory.getRuntimeMXBean()
// - CPU count via Runtime.availableProcessors()
// - OS info via system properties
// ─────────────────────────────────────────────────────────────────────────────

class JvmSystemHealthTracker : SystemHealthTracker {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _snapshot = MutableStateFlow(HealthSnapshot())
    override val snapshot: StateFlow<HealthSnapshot> = _snapshot.asStateFlow()

    private var autoRefreshJob: Job? = null

    private val appDataDir = File(System.getProperty("user.home"), ".zyntapos")

    override suspend fun refresh() {
        val runtime = Runtime.getRuntime()
        val heapMax = runtime.maxMemory()
        val heapTotal = runtime.totalMemory()
        val heapFree = runtime.freeMemory()
        val heapUsed = heapTotal - heapFree
        val heapPercent = if (heapMax > 0) (heapUsed.toFloat() / heapMax * 100f) else 0f

        // Disk — use the root of the app data directory
        val diskRoot = appDataDir.let { if (it.exists()) it else File(System.getProperty("user.home")) }
        val diskTotal = diskRoot.totalSpace
        val diskFree = diskRoot.usableSpace
        val diskUsed = diskTotal - diskFree
        val diskPercent = if (diskTotal > 0) (diskUsed.toFloat() / diskTotal * 100f) else 0f

        // Database — find .db files in ~/.zyntapos/
        val dbSize = findDatabaseSize()

        // Database status
        val dbStatus = when {
            dbSize > 0 -> DatabaseStatus.HEALTHY
            appDataDir.exists() -> DatabaseStatus.DEGRADED
            else -> DatabaseStatus.UNKNOWN
        }

        // JVM uptime
        val uptimeMs = ManagementFactory.getRuntimeMXBean().uptime

        // CPU
        val cpuCount = runtime.availableProcessors()

        // Platform description
        val osName = System.getProperty("os.name", "Unknown OS")
        val osArch = System.getProperty("os.arch", "")
        val javaVersion = System.getProperty("java.version", "?")
        val platform = "$osName $osArch (Java $javaVersion)"

        // Overall status
        val overall = computeOverallStatus(heapPercent, diskPercent, dbStatus)

        _snapshot.value = HealthSnapshot(
            heapUsedBytes = heapUsed,
            heapMaxBytes = heapMax,
            heapUsagePercent = heapPercent,
            diskFreeBytes = diskFree,
            diskTotalBytes = diskTotal,
            diskUsagePercent = diskPercent,
            databaseSizeBytes = dbSize,
            databaseStatus = dbStatus,
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

    private fun findDatabaseSize(): Long {
        return try {
            if (!appDataDir.exists()) return 0L
            appDataDir.walkTopDown()
                .filter { it.isFile && it.extension == "db" }
                .sumOf { it.length() }
        } catch (_: Exception) {
            0L
        }
    }

    private fun computeOverallStatus(
        heapPercent: Float,
        diskPercent: Float,
        dbStatus: DatabaseStatus,
    ): OverallStatus {
        return when {
            heapPercent > 90f || diskPercent > 95f || dbStatus == DatabaseStatus.ERROR -> OverallStatus.CRITICAL
            heapPercent > 75f || diskPercent > 85f || dbStatus == DatabaseStatus.DEGRADED -> OverallStatus.WARNING
            else -> OverallStatus.HEALTHY
        }
    }
}

actual fun createSystemHealthTracker(): SystemHealthTracker = JvmSystemHealthTracker()
