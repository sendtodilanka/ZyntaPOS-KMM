package com.zyntasolutions.zyntapos.data.local.db

import app.cash.sqldelight.db.SqlDriver
import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import com.zyntasolutions.zyntapos.domain.port.PasswordHashPort
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import kotlinx.datetime.Clock

/**
 * ZyntaPOS — DatabaseFactory (commonMain)
 *
 * Singleton orchestrator that coordinates the full encrypted database lifecycle:
 *
 * ```
 * DatabaseKeyProvider.getOrCreateKey()
 *       │
 *       ▼
 * DatabaseDriverFactory.createEncryptedDriver(key)
 *   → WAL mode enabled
 *   → SQLCipher key applied
 *       │
 *       ▼
 * DatabaseMigrations.migrateIfNeeded(driver)
 *   → Schema created on first launch
 *   → Forward migrations applied on upgrades
 *       │
 *       ▼
 * ZyntaDatabase(driver) ← ready for use by Repository implementations
 * ```
 *
 * ## Thread Safety
 * [openDatabase] uses Kotlin's `@Volatile` + double-checked locking pattern to
 * guarantee that only ONE [ZyntaDatabase] instance is created per process lifetime,
 * even when called concurrently from multiple coroutines.
 *
 * [openDatabase] is a suspend function — callers MUST invoke it on a background
 * dispatcher (IO). Typically called once at app startup from the Koin `single { ... }`
 * initializer, which handles the dispatcher internally.
 *
 * ## Closing
 * [closeDatabase] is provided for graceful shutdown (process exit / data wipe).
 * After calling it, [openDatabase] will re-initialize from scratch on the next call.
 *
 * @param keyProvider Platform-specific key retrieval (Keystore / PKCS12)
 * @param driverFactory Platform-specific encrypted driver creation
 * @param migrations Schema migration manager
 */
class DatabaseFactory(
    private val keyProvider: DatabaseKeyProvider,
    private val driverFactory: DatabaseDriverFactory,
    private val migrations: DatabaseMigrations,
    private val passwordHasher: PasswordHashPort,
) {

    @Volatile
    private var cachedDriver: SqlDriver? = null

    @Volatile
    private var cachedDatabase: ZyntaDatabase? = null

    /**
     * Opens (or returns the cached) encrypted [ZyntaDatabase].
     *
     * On first call:
     * 1. Retrieves/generates the AES-256 key via [DatabaseKeyProvider]
     * 2. Creates the encrypted [SqlDriver] via [DatabaseDriverFactory]
     * 3. Applies schema migrations via [DatabaseMigrations]
     * 4. Constructs and caches the [ZyntaDatabase]
     *
     * Subsequent calls return the cached instance immediately.
     *
     * @throws com.zyntasolutions.zyntapos.core.result.ZyntaException.DatabaseException
     *   if key retrieval or driver creation fails
     */
    fun openDatabase(): ZyntaDatabase {
        // Fast path — return cached instance without lock acquisition
        cachedDatabase?.let { return it }

        // Slow path — synchronized initialization
        return synchronized(this) {
            cachedDatabase ?: run {
                ZyntaLogger.i(TAG, "Initializing ZyntaDatabase (first open).")

                val key: ByteArray = keyProvider.getOrCreateKey()

                val driver: SqlDriver = driverFactory.createEncryptedDriver(key)
                    .also { cachedDriver = it }

                migrations.migrateIfNeeded(driver)

                ZyntaDatabase(driver).also { db ->
                    cachedDatabase = db
                    seedDefaultAdminIfEmpty(db)
                    ZyntaLogger.i(TAG, "ZyntaDatabase ready — encrypted, WAL, migrations applied.")
                }
            }
        }
    }

    /**
     * Closes the active database connection and clears the cached instance.
     * Safe to call multiple times. After this call, [openDatabase] will
     * re-initialize the database on its next invocation.
     *
     * Typically called during:
     * - App process termination (coroutineScope.invokeOnCompletion)
     * - Data wipe / logout flows that require a fresh database
     */
    fun closeDatabase() {
        synchronized(this) {
            cachedDriver?.close()
            cachedDriver = null
            cachedDatabase = null
            ZyntaLogger.i(TAG, "ZyntaDatabase connection closed and cache cleared.")
        }
    }

    /** Returns `true` if the database has been opened and is currently cached. */
    val isOpen: Boolean get() = cachedDatabase != null

    /**
     * Seeds a default admin account on first launch so the offline-only MVP is usable
     * without a server. No-ops if any user already exists.
     *
     * Default credentials: admin@zentapos.com / admin123
     */
    private fun seedDefaultAdminIfEmpty(db: ZyntaDatabase) {
        try {
            val hasAdmin = db.usersQueries.getUserByEmail("admin@zentapos.com")
                .executeAsOneOrNull() != null
            if (hasAdmin) return

            val now = Clock.System.now().toEpochMilliseconds()
            val passwordHash = passwordHasher.hash("admin123")
            db.usersQueries.insertUser(
                id            = "00000000-0000-0000-0000-000000000001",
                name          = "Admin",
                email         = "admin@zentapos.com",
                password_hash = passwordHash,
                role          = "ADMIN",
                pin_hash      = null,
                store_id      = "default-store",
                is_active     = 1L,
                created_at    = now,
                updated_at    = now,
                sync_status   = "SYNCED",
            )
            ZyntaLogger.i(TAG, "Seeded default admin user (admin@zentapos.com).")
        } catch (e: Exception) {
            ZyntaLogger.e(TAG, "Failed to seed default admin user: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "DatabaseFactory"
    }
}
