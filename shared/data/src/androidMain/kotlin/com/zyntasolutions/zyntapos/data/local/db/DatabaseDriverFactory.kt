package com.zyntasolutions.zyntapos.data.local.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.zyntasolutions.zyntapos.core.logger.ZentaLogger
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

/**
 * ZyntaPOS — DatabaseDriverFactory (androidMain actual)
 *
 * Creates an AES-256-CBC encrypted SQLite driver via SQLCipher for Android.
 *
 * ## Implementation Details
 * - Uses [net.sqlcipher.database.SupportFactory] as the `SupportSQLiteOpenHelper.Factory`
 *   injected into [AndroidSqliteDriver]. This is the official SQLDelight 2.x integration
 *   path for `net.zetetic:sqlcipher-android`.
 * - We pass the raw 32-byte key (not a passphrase string) via [SQLiteDatabase.getBytes]
 *   to bypass PBKDF2 derivation and maintain symmetric key parity with the JVM actual.
 * - WAL mode is enabled post-connection for concurrent read/write access between
 *   the active POS checkout flow and the background WorkManager sync worker.
 *
 * ## Security Note
 * The [key] bytes come from [DatabaseKeyProvider]'s envelope-decryption path —
 * they are the unwrapped raw AES-256 encryption key, never stored in plaintext on disk.
 *
 * ## Koin Injection
 * ```kotlin
 * single { DatabaseDriverFactory(androidContext()) }
 * ```
 *
 * @param context Application [Context] required by [AndroidSqliteDriver]
 */
actual class DatabaseDriverFactory(private val context: Context) {

    actual fun createEncryptedDriver(key: ByteArray): SqlDriver {
        require(key.size == 32) {
            "SQLCipher key MUST be exactly 32 bytes (256-bit AES). Received ${key.size} bytes."
        }

        ZentaLogger.d(TAG, "Opening encrypted Android SQLite database with SQLCipher.")

        // Convert raw key bytes → char array → SQLCipher-ready passphrase ByteArray
        // Using SQLiteDatabase.getBytes() avoids the PBKDF2 derivation round-trip,
        // treating the 32-byte value directly as the SQLCipher key material.
        val passphrase: ByteArray = SQLiteDatabase.getBytes(
            key.map { it.toInt().toChar() }.toCharArray()
        )
        val factory = SupportFactory(passphrase)

        // AndroidSqliteDriver → SupportFactory intercepts openOrCreateDatabase calls
        // → SQLCipher applies AES-256-CBC encryption transparently on every page I/O
        val driver = AndroidSqliteDriver(
            schema  = com.zyntasolutions.zyntapos.db.ZyntaDatabase.Schema,
            context = context,
            name    = DB_FILE_NAME,
            factory = factory,
        )

        // WAL mode: concurrent readers + single writer — essential for POS throughput
        driver.execute(identifier = null, sql = "PRAGMA journal_mode=WAL;", parameters = 0)

        // Tune cache for POS product-catalogue workloads
        driver.execute(identifier = null, sql = "PRAGMA cache_size=-8000;", parameters = 0)  // 8 MB cache

        ZentaLogger.i(TAG, "Android encrypted DB opened successfully (WAL mode, 8 MB cache).")
        return driver
    }

    private companion object {
        const val TAG         = "DatabaseDriverFactory"
        const val DB_FILE_NAME = "zyntapos_encrypted.db"
    }
}
