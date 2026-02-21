package com.zyntasolutions.zyntapos.data.local.db

import app.cash.sqldelight.db.SqlDriver

/**
 * ZyntaPOS — DatabaseDriverFactory (commonMain expect)
 *
 * Platform-agnostic factory contract for creating an encrypted SQLite [SqlDriver].
 * Each platform provides its own `actual` implementation that:
 *  - Android: uses [net.sqlcipher.database.SupportFactory] + [AndroidSqliteDriver]
 *  - Desktop (JVM): uses [app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver]
 *    with SQLCipher PRAGMA key applied before any schema operations.
 *
 * The factory does NOT manage schema creation — that responsibility belongs to
 * [DatabaseFactory], which calls [createEncryptedDriver] and then passes the
 * resulting driver to [DatabaseMigrations].
 *
 * ## SQLCipher Key Protocol
 * The [key] parameter MUST be a 256-bit (32-byte) raw AES key, sourced exclusively
 * from [DatabaseKeyProvider]. Never hard-code or log this value.
 *
 * ## WAL Mode
 * Both actuals MUST enable WAL (`PRAGMA journal_mode=WAL`) after the key is set
 * and before returning the driver. WAL provides concurrent read/write performance
 * critical for POS real-time inventory updates during active checkout sessions.
 *
 * @see DatabaseKeyProvider for secure key retrieval
 * @see DatabaseFactory for lifecycle orchestration
 */
expect class DatabaseDriverFactory {

    /**
     * Creates and returns an AES-256 encrypted [SqlDriver] with WAL mode enabled.
     *
     * This call MUST be invoked on a background dispatcher (IO). The operation
     * may involve disk I/O for database file creation and key derivation.
     *
     * @param key 32-byte AES-256 key from [DatabaseKeyProvider.getOrCreateKey]
     * @return an open, encrypted, WAL-enabled [SqlDriver] ready for schema operations
     * @throws IllegalStateException if the key is not exactly 32 bytes
     * @throws DatabaseException if the driver cannot be opened (bad key, disk full, etc.)
     */
    fun createEncryptedDriver(key: ByteArray): SqlDriver
}
