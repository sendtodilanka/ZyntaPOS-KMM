package com.zyntasolutions.zyntapos.data.local.db

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import com.zyntasolutions.zyntapos.db.ZyntaDatabase

/**
 * ZyntaPOS — DatabaseMigrations (commonMain)
 *
 * Version-safe schema migration manager for the ZyntaPOS encrypted SQLite database.
 *
 * ## Migration Strategy
 * SQLDelight 2.x tracks the schema version in SQLite's `user_version` PRAGMA.
 * On each app launch, [migrateIfNeeded] compares:
 * - **current DB version** (`PRAGMA user_version`) — the live on-disk version
 * - **compiled schema version** ([ZyntaDatabase.Schema.version]) — the version in code
 *
 * | Scenario | Action |
 * |----------|--------|
 * | DB version = 0 (new DB) | Call `Schema.create(driver)` to build full schema |
 * | DB version < Schema version | Call `Schema.migrate(driver, from, to, callbacks)` |
 * | DB version = Schema version | No-op — database is current |
 * | DB version > Schema version | **Error** — downgrade is not supported; throw [IllegalStateException] |
 *
 * ## Migration Files
 * SQL migration scripts live in `shared/data/src/commonMain/sqldelight/migrations/`
 * as numbered files (`1.sqm`, `2.sqm`, etc.), processed by the SQLDelight Gradle plugin
 * at compile time into the [ZyntaDatabase.Schema] migration graph.
 *
 * ## Rollback Safety
 * - Migrations are forward-only. There is no downgrade path.
 * - Each migration is wrapped in a SQLite `BEGIN EXCLUSIVE TRANSACTION` by SQLDelight
 *   to guarantee atomic migration (partial migration = none applied on crash).
 * - After migration, `user_version` is atomically updated within the same transaction.
 *
 * ## Post-Migration Callbacks
 * [afterVersionCallbacks] provides hooks for data transformations that cannot be
 * expressed in pure SQL (e.g., encrypting a newly added PII column). Add entries here
 * when needed as new migrations land in Phase 2+.
 *
 * ## Usage
 * ```kotlin
 * val migrations = DatabaseMigrations()
 * migrations.migrateIfNeeded(driver)  // Called by DatabaseFactory before serving ZyntaDatabase
 * ```
 */
class DatabaseMigrations {

    /**
     * Inspects the live database version and applies the appropriate schema action.
     *
     * This function is idempotent for the case where DB version == Schema version.
     * It MUST be called before any [ZyntaDatabase] queries are executed.
     *
     * @param driver An open [SqlDriver] with the encryption key already applied
     *   (i.e., after [DatabaseDriverFactory.createEncryptedDriver] returns)
     * @throws IllegalStateException if the on-disk schema version is GREATER than
     *   the compiled schema version (indicates a downgrade attempt)
     */
    fun migrateIfNeeded(driver: SqlDriver) {
        val schema: SqlSchema<QueryResult.Value<Unit>> = ZyntaDatabase.Schema
        val currentVersion = getSchemaVersion(driver)
        val targetVersion  = schema.version

        ZyntaLogger.d(TAG, "Schema check — disk: v$currentVersion, compiled: v$targetVersion")

        when {
            currentVersion == 0L -> {
                // Brand-new database: create all tables from the compiled schema
                ZyntaLogger.i(TAG, "Creating ZyntaPOS schema at version $targetVersion (first launch).")
                schema.create(driver)
                setSchemaVersion(driver, targetVersion)
                ZyntaLogger.i(TAG, "Schema v$targetVersion created successfully.")
            }

            currentVersion < targetVersion -> {
                // Upgrade path: apply incremental migrations from currentVersion → targetVersion
                ZyntaLogger.i(TAG, "Migrating schema: v$currentVersion → v$targetVersion")
                schema.migrate(
                    driver  = driver,
                    oldVersion = currentVersion,
                    newVersion = targetVersion,
                    *afterVersionCallbacks,
                )
                ZyntaLogger.i(TAG, "Migration to v$targetVersion completed successfully.")
            }

            currentVersion == targetVersion -> {
                // No-op: database is already at the correct version
                ZyntaLogger.d(TAG, "Schema is current at v$currentVersion — no migration needed.")
            }

            else -> {
                // currentVersion > targetVersion: downgrade is not supported
                throw IllegalStateException(
                    "Database schema version mismatch: on-disk v$currentVersion is NEWER than " +
                    "compiled v$targetVersion. Downgrades are not supported. " +
                    "Please upgrade the application or restore from a compatible backup."
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // PRAGMA user_version helpers
    // ─────────────────────────────────────────────────────────────────

    private fun getSchemaVersion(driver: SqlDriver): Long {
        return driver.executeQuery(
            identifier = null,
            sql = "PRAGMA user_version;",
            mapper = { cursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
            },
            parameters = 0,
        ).value
    }

    private fun setSchemaVersion(driver: SqlDriver, version: Long) {
        // user_version must be set via a raw PRAGMA (cannot use bound parameters)
        driver.execute(identifier = null, sql = "PRAGMA user_version = $version;", parameters = 0)
    }

    // ─────────────────────────────────────────────────────────────────
    // Post-migration Kotlin callbacks
    // Add entries here for data transforms that cannot be expressed in SQL.
    // Example: encrypting a newly added PII column after schema upgrade.
    // ─────────────────────────────────────────────────────────────────

    private val afterVersionCallbacks: Array<AfterVersion> = arrayOf(
        // Phase 1: No post-migration data transforms required.
        // Phase 2+ entries will follow the pattern:
        // AfterVersion(1) { driver ->
        //     ZyntaLogger.i(TAG, "Running post-v1 data transform: encrypting email column.")
        //     // ... batch encryption via AES-256-GCM EncryptionManager ...
        // }
    )

    private companion object {
        const val TAG = "DatabaseMigrations"
    }
}
