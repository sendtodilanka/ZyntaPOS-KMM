package com.zyntasolutions.zyntapos.data.local.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger  // :shared:core via :shared:domain api dep
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Properties

/**
 * ZyntaPOS — DatabaseDriverFactory (jvmMain actual)
 *
 * Creates an AES-256 encrypted SQLite driver for the Desktop (JVM) target.
 *
 * ## SQLCipher on JVM — Native Library Requirement
 * Standard `org.xerial:sqlite-jdbc` does NOT support SQLCipher. To activate
 * full encryption, the production Desktop distribution MUST include the native
 * SQLCipher shared library alongside the JVM JAR:
 * - **macOS:** `libsqlcipher.dylib` (arm64 + x86_64 universal)
 * - **Linux:**  `libsqlcipher.so`
 * - **Windows:** `sqlcipher.dll`
 *
 * These native libs can be obtained from the official SQLCipher build or via the
 * `net.zetetic:sqlcipher-android` AAR (extract jni/ folder for each ABI and repackage
 * as desktop natives). At runtime, set `-Dorg.xerial.sqlite.lib.path=/path/to/natives`.
 *
 * ## Key Application Strategy
 * SQLCipher requires the PRAGMA key to be issued immediately after the JDBC connection
 * is established, before any schema or data operations. This factory creates the driver
 * WITHOUT schema auto-management (`schema = null`), applies the key PRAGMA, verifies
 * decryption success, then enables WAL — before returning to [DatabaseFactory] for
 * schema migration.
 *
 * ## WAL Mode
 * WAL is enabled post-key-application for concurrent read/write access across
 * multiple coroutine dispatchers in the Desktop POS session.
 *
 * ## Koin Injection
 * ```kotlin
 * single { DatabaseDriverFactory(appDataDirectory()) }
 * ```
 *
 * @param appDataDir Absolute path to the application data directory where the
 *   encrypted database file will be created/opened. E.g. `~/.zyntapos/data/`
 */
actual class DatabaseDriverFactory(private val appDataDir: String) {

    actual fun createEncryptedDriver(key: ByteArray): SqlDriver {
        require(key.size == 32) {
            "SQLCipher key MUST be exactly 32 bytes (256-bit AES). Received ${key.size} bytes."
        }

        ZyntaLogger.d(TAG, "Opening encrypted JVM SQLite database at: $appDataDir")

        // Ensure the data directory exists before opening the database
        Files.createDirectories(Paths.get(appDataDir))
        val dbFilePath = "$appDataDir/$DB_FILE_NAME"

        // Open a raw JDBC connection — schema = null so we control the lifecycle
        val driver = JdbcSqliteDriver(
            url = "jdbc:sqlite:$dbFilePath",
            properties = Properties().apply { put("foreign_keys", "true") },
        )

        // ─────────────────────────────────────────────────────────────────
        // STEP 1: Apply SQLCipher AES-256 key (hex-encoded, raw key mode)
        //   - Must be the FIRST operation after connection; no other query
        //     may execute until key is set on an encrypted database.
        //   - "x'...'" prefix tells SQLCipher this is a raw hex key (not
        //     a passphrase subject to PBKDF2 derivation), ensuring identical
        //     key semantics with the Android SupportFactory path.
        // ─────────────────────────────────────────────────────────────────
        val keyHex = key.joinToString(separator = "") { byte -> "%02x".format(byte) }
        driver.execute(
            identifier = null,
            sql = "PRAGMA key = \"x'$keyHex'\";",
            parameters = 0,
        )

        // STEP 2: Verify decryption — a failed key returns an empty/corrupt result.
        // This throws if the database is not accessible with the provided key.
        try {
            driver.execute(identifier = null, sql = "SELECT count(*) FROM sqlite_master;", parameters = 0)
            ZyntaLogger.d(TAG, "SQLCipher key verification passed — database decrypted successfully.")
        } catch (e: Exception) {
            driver.close()
            throw IllegalStateException(
                "SQLCipher key verification failed. The database may be corrupt or the key is incorrect.",
                e,
            )
        }

        // STEP 3: Enable WAL for concurrent POS read/write access
        driver.execute(identifier = null, sql = "PRAGMA journal_mode=WAL;", parameters = 0)

        // STEP 4: Tune SQLCipher page cache for POS workloads (product catalog scans)
        driver.execute(identifier = null, sql = "PRAGMA cache_size=-8000;", parameters = 0)  // ~8 MB cache
        driver.execute(identifier = null, sql = "PRAGMA busy_timeout=5000;", parameters = 0)  // 5s timeout

        ZyntaLogger.i(TAG, "JVM encrypted DB opened at '$dbFilePath' (WAL, 8MB cache).")
        return driver
    }

    private companion object {
        const val TAG = "DatabaseDriverFactory"
        const val DB_FILE_NAME = "zyntapos_encrypted.db"
    }
}
