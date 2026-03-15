package com.zyntasolutions.zyntapos.api.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)
    private lateinit var dataSource: HikariDataSource

    fun init() {
        val jdbcUrl = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/zyntapos"
        val user = System.getenv("DB_USER") ?: "zyntapos"
        val password = readSecret("DB_PASSWORD_FILE")
            ?: System.getenv("DB_PASSWORD")
            ?: error("DB_PASSWORD_FILE or DB_PASSWORD environment variable must be set")

        // S3-14: Pool sizing is configurable via env vars (mirrors LicenseDatabaseFactory pattern).
        // Defaults are tuned for the API service which handles more concurrent request types than License.
        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = user
            this.password = password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = System.getenv("DB_POOL_MAX")?.toIntOrNull() ?: 10
            minimumIdle = System.getenv("DB_POOL_MIN")?.toIntOrNull() ?: 2
            connectionTimeout = System.getenv("DB_CONNECTION_TIMEOUT_MS")?.toLongOrNull() ?: 30_000L
            idleTimeout = System.getenv("DB_POOL_IDLE_TIMEOUT")?.toLongOrNull() ?: 600_000L
            maxLifetime = 1_800_000L
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            validate()
        }

        dataSource = HikariDataSource(config)
        Database.connect(dataSource)

        // Run Flyway migrations with retry (DB may still be initializing)
        runMigrations(maxRetries = 5, delayMs = 3_000L)

        logger.info("Database initialized and migrations applied")
    }

    private fun runMigrations(maxRetries: Int, delayMs: Long) {
        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                val flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .load()
                // Repair first: updates checksums in flyway_schema_history to match
                // current migration files. Required after V15 was reverted to its
                // original content (new columns moved to V19).
                flyway.repair()
                flyway.migrate()
                return
            } catch (e: Exception) {
                lastException = e
                logger.warn("Flyway migration attempt $attempt/$maxRetries failed: ${e.message}")
                if (attempt < maxRetries) {
                    Thread.sleep(delayMs * attempt)
                }
            }
        }
        logger.error("Flyway migration failed after $maxRetries attempts", lastException)
        throw lastException!!
    }

    fun ping(): Boolean {
        transaction {
            exec("SELECT 1")
        }
        return true
    }

    fun close() {
        if (::dataSource.isInitialized) {
            dataSource.close()
        }
    }

    /** Read a Docker secret from a file path env var, or return null if not set. */
    private fun readSecret(envVar: String): String? {
        val path = System.getenv(envVar) ?: return null
        return try {
            java.io.File(path).readText().trim()
        } catch (_: Exception) {
            null
        }
    }
}
