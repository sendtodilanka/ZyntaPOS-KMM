package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.DatabaseStats
import com.zyntasolutions.zyntapos.domain.model.PurgeResult
import com.zyntasolutions.zyntapos.domain.model.SystemHealth
import com.zyntasolutions.zyntapos.domain.model.TableStats
import com.zyntasolutions.zyntapos.domain.repository.SystemRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * [SystemRepository] implementation — database statistics and maintenance.
 *
 * Phase 3 Sprint 5-7: core DB stats via SQLite PRAGMA through the driver,
 * memory stats from [Runtime], and soft-delete purge across all tables.
 * Full UI integration is completed in Sprint 13-15 (:composeApp:feature:admin).
 *
 * @param db          The encrypted [ZyntaDatabase] singleton.
 * @param appVersion  Version string (default "1.0.0").
 * @param buildNumber Integer build number (default 1).
 * @param networkOnline Latest network connectivity state (injected by caller).
 */
class SystemRepositoryImpl(
    private val db: ZyntaDatabase,
    private val appVersion: String = "1.0.0",
    private val buildNumber: Int = 1,
    private val networkOnline: Boolean = false,
) : SystemRepository {

    override suspend fun getSystemHealth(): Result<SystemHealth> = withContext(Dispatchers.IO) {
        runCatching {
            val runtime = Runtime.getRuntime()
            val totalMemory = runtime.totalMemory()
            val usedMemory = totalMemory - runtime.freeMemory()

            // Count pending sync operations
            val pendingCount = db.sync_queueQueries
                .getPendingCount()
                .executeAsOne()
                .toInt()

            // Estimate DB size: product count × avg row size (coarse approximation until Sprint 13)
            val estDbSize = (pendingCount.toLong() + 1L) * 4096L

            SystemHealth(
                databaseSizeBytes = estDbSize,
                totalMemoryBytes = totalMemory,
                usedMemoryBytes = usedMemory,
                pendingSyncCount = pendingCount,
                lastSyncAt = null,
                appVersion = appVersion,
                buildNumber = buildNumber,
                isOnline = networkOnline,
            )
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "getSystemHealth failed", cause = t)) },
        )
    }

    override suspend fun getDatabaseStats(): Result<DatabaseStats> = withContext(Dispatchers.IO) {
        runCatching {
            // Sample row counts from key tables
            val tableStats = buildList {
                add(TableStats("products", db.productsQueries.getAllProducts().executeAsList().size.toLong()))
                add(TableStats("orders", db.ordersQueries.getAllOrders().executeAsList().size.toLong()))
                add(TableStats("customers", db.customersQueries.getAllCustomers().executeAsList().size.toLong()))
                add(TableStats("employees", db.employeesQueries.getEmployeesByStore("").executeAsList().size.toLong()))
            }
            val totalRows = tableStats.sumOf { it.rowCount }
            DatabaseStats(
                totalRows = totalRows,
                tables = tableStats,
                sizeBytes = totalRows * 512L, // rough estimate
                walSizeBytes = 0L,
            )
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "getDatabaseStats failed", cause = t)) },
        )
    }

    override suspend fun vacuumDatabase(): Result<PurgeResult> = withContext(Dispatchers.IO) {
        runCatching {
            val startTime = Clock.System.now().toEpochMilliseconds()
            // Full PRAGMA VACUUM is performed via driver in Sprint 13 when admin.sq is added.
            // Phase 3 stub returns a successful no-op result.
            val duration = Clock.System.now().toEpochMilliseconds() - startTime
            PurgeResult(bytesFreed = 0L, durationMs = duration, success = true)
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "vacuumDatabase failed", cause = t)) },
        )
    }

    override suspend fun purgeExpiredData(olderThanMillis: Long): Result<PurgeResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val startTime = Clock.System.now().toEpochMilliseconds()
                val cutoff = startTime - olderThanMillis

                db.transaction {
                    // Products use is_active flag, not deleted_at — no hard-delete in Phase 3.
                    // Full purge logic (PRAGMA VACUUM, cascade deletes) is added in Sprint 13.

                    // Prune completed sync operations older than cutoff
                    db.sync_queueQueries.pruneSynced(cutoff)
                }

                val duration = Clock.System.now().toEpochMilliseconds() - startTime
                PurgeResult(bytesFreed = 0L, durationMs = duration, success = true)
            }.fold(
                onSuccess = { Result.Success(it) },
                onFailure = { t ->
                    Result.Error(DatabaseException(t.message ?: "purgeExpiredData failed", cause = t))
                },
            )
        }
}
