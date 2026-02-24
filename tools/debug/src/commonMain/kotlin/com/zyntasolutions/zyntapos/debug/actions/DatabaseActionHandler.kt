package com.zyntasolutions.zyntapos.debug.actions

import app.cash.sqldelight.db.QueryResult
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.db.DatabaseFactory

/**
 * Abstracts low-level database operations for the Database tab.
 *
 * Uses [DatabaseFactory] directly — a deliberate exception to the
 * "depend only on domain interfaces" rule, justified because this is
 * debug-only tooling that inherently requires data-layer access.
 */
interface DatabaseActionHandler {
    /** Returns a map of table name → approximate row count. */
    suspend fun getTableRowCounts(): Result<Map<String, Long>>

    /**
     * Truncates all core business tables (DELETE FROM, not DROP TABLE).
     * Schema is preserved; FK constraints are disabled during truncation.
     * IRREVERSIBLE — only callable after typed-word confirmation.
     */
    suspend fun resetDatabase(): Result<Unit>

    /** Runs SQLite VACUUM to reclaim unused pages. */
    suspend fun vacuum(): Result<Unit>

    /** Returns 0 — file size not accessible from SQLDelight driver. */
    suspend fun getDatabaseFileSizeKb(): Result<Long>
}

/**
 * Default implementation backed by [DatabaseFactory].
 */
class DatabaseActionHandlerImpl(
    private val databaseFactory: DatabaseFactory,
) : DatabaseActionHandler {

    // Ordered to respect FK constraints (children before parents)
    private val truncateOrder = listOf(
        "order_items", "orders", "cash_movements", "cash_registers",
        "stock_adjustments", "products", "categories", "suppliers",
        "customers", "sync_operations", "audit_entries", "settings",
        "users",
    )

    override suspend fun getTableRowCounts(): Result<Map<String, Long>> {
        return try {
            val driver = databaseFactory.openDriver()
            val counts = mutableMapOf<String, Long>()
            val tables = listOf(
                "users", "products", "categories", "orders", "order_items",
                "customers", "suppliers", "cash_movements", "sync_operations",
                "audit_entries",
            )
            tables.forEach { table ->
                runCatching {
                    val count = driver.executeQuery(
                        identifier = null,
                        sql = "SELECT COUNT(*) FROM $table",
                        mapper = { cursor ->
                            QueryResult.Value(
                                if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L
                            )
                        },
                        parameters = 0,
                        binders = null,
                    ).value
                    counts[table] = count
                }
            }
            Result.Success(counts)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to read table counts: ${e.message}"))
        }
    }

    override suspend fun resetDatabase(): Result<Unit> {
        return try {
            val driver = databaseFactory.openDriver()
            driver.execute(null, "PRAGMA foreign_keys = OFF", 0, null)
            truncateOrder.forEach { table ->
                runCatching { driver.execute(null, "DELETE FROM $table", 0, null) }
            }
            driver.execute(null, "PRAGMA foreign_keys = ON", 0, null)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Database reset failed: ${e.message}"))
        }
    }

    override suspend fun vacuum(): Result<Unit> {
        return try {
            databaseFactory.openDriver().execute(null, "VACUUM", 0, null)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(DatabaseException("VACUUM failed: ${e.message}"))
        }
    }

    override suspend fun getDatabaseFileSizeKb(): Result<Long> =
        Result.Success(0L)
}
