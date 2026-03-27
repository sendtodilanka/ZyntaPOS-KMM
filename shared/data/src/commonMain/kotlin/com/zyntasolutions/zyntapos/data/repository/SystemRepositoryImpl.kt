package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
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
 * Uses SQLite PRAGMA queries via the raw [SqlDriver] for accurate database
 * size reporting and incremental vacuum. Memory stats come from [Runtime].
 *
 * @param db            The encrypted [ZyntaDatabase] singleton.
 * @param driver        The [SqlDriver] for raw PRAGMA execution.
 * @param appVersion    Version string (default "1.0.0").
 * @param buildNumber   Integer build number (default 1).
 * @param networkOnline Latest network connectivity state (injected by caller).
 */
class SystemRepositoryImpl(
    private val db: ZyntaDatabase,
    private val driver: SqlDriver,
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

            // Compute actual DB size via PRAGMA page_count * page_size
            val pageSize = pragmaLong("page_size")
            val pageCount = pragmaLong("page_count")
            val dbSizeBytes = pageSize * pageCount

            SystemHealth(
                databaseSizeBytes = dbSizeBytes,
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
            // Row counts via COUNT(*) for efficiency (avoids loading all rows into memory)
            val tableStats = buildList {
                add(TableStats("products", countTable("products")))
                add(TableStats("orders", countTable("orders")))
                add(TableStats("customers", countTable("customers")))
                add(TableStats("categories", countTable("categories")))
                add(TableStats("pending_operations", countTable("pending_operations")))
                add(TableStats("audit_entries", countTable("audit_entries")))
            }
            val totalRows = tableStats.sumOf { it.rowCount }
            val pageSize = pragmaLong("page_size")
            val pageCount = pragmaLong("page_count")
            val freePages = pragmaLong("freelist_count")

            DatabaseStats(
                totalRows = totalRows,
                tables = tableStats,
                sizeBytes = pageSize * pageCount,
                walSizeBytes = freePages * pageSize,
            )
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "getDatabaseStats failed", cause = t)) },
        )
    }

    override suspend fun vacuumDatabase(): Result<PurgeResult> = withContext(Dispatchers.IO) {
        runCatching {
            val startTime = Clock.System.now().toEpochMilliseconds()

            // Measure free pages before vacuum
            val freePagesBefore = pragmaLong("freelist_count")
            val pageSize = pragmaLong("page_size")

            // Run incremental vacuum to reclaim unused pages
            driver.execute(null, "PRAGMA incremental_vacuum", 0)

            val freePagesAfter = pragmaLong("freelist_count")
            val pagesFreed = freePagesBefore - freePagesAfter
            val bytesFreed = pagesFreed * pageSize

            val duration = Clock.System.now().toEpochMilliseconds() - startTime
            PurgeResult(bytesFreed = bytesFreed, durationMs = duration, success = true)
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
                val freePagesBefore = pragmaLong("freelist_count")
                val pageSize = pragmaLong("page_size")

                db.transaction {
                    // Prune completed sync operations older than cutoff
                    db.sync_queueQueries.pruneSynced(cutoff)

                    // Purge old audit log entries beyond retention window
                    driver.execute(null, "DELETE FROM audit_entries WHERE timestamp < $cutoff", 0)
                }

                // Run incremental vacuum after deletes to reclaim space
                driver.execute(null, "PRAGMA incremental_vacuum", 0)
                val freePagesAfter = pragmaLong("freelist_count")
                val bytesFreed = (freePagesBefore - freePagesAfter) * pageSize

                val duration = Clock.System.now().toEpochMilliseconds() - startTime
                PurgeResult(bytesFreed = bytesFreed, durationMs = duration, success = true)
            }.fold(
                onSuccess = { Result.Success(it) },
                onFailure = { t ->
                    Result.Error(DatabaseException(t.message ?: "purgeExpiredData failed", cause = t))
                },
            )
        }

    /** Executes a PRAGMA query and returns the first column as Long. */
    private fun pragmaLong(pragma: String): Long {
        return driver.executeQuery(
            identifier = null,
            sql = "PRAGMA $pragma",
            mapper = { cursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
            },
            parameters = 0,
        ).value
    }

    /** Returns COUNT(*) for a table via raw SQL. */
    private fun countTable(tableName: String): Long {
        return driver.executeQuery(
            identifier = null,
            sql = "SELECT COUNT(*) FROM $tableName",
            mapper = { cursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
            },
            parameters = 0,
        ).value
    }
}
