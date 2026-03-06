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
        val password = readSecret("DB_PASSWORD_FILE") ?: System.getenv("DB_PASSWORD") ?: "zyntapos"

        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = user
            this.password = password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
            minimumIdle = 2
            connectionTimeout = 30_000L
            idleTimeout = 600_000L
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
                Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .load()
                    .migrate()
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
