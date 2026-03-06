package com.zyntasolutions.zyntapos.license.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

object LicenseDatabaseFactory {
    private val logger = LoggerFactory.getLogger(LicenseDatabaseFactory::class.java)
    private lateinit var dataSource: HikariDataSource

    fun init() {
        val jdbcUrl = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/zyntapos"
        val user = System.getenv("DB_USER") ?: "zyntapos"
        val password = readSecret("DB_PASSWORD_FILE")
            ?: System.getenv("DB_PASSWORD")
            ?: error("DB_PASSWORD_FILE or DB_PASSWORD environment variable must be set")

        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = user
            this.password = password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 5
            minimumIdle = 1
            connectionTimeout = 30_000L
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            validate()
        }

        dataSource = HikariDataSource(config)
        Database.connect(dataSource)

        // Run Flyway migrations with retry (DB may still be initializing)
        runMigrations(maxRetries = 5, delayMs = 3_000L)

        logger.info("License server database initialized")
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
        transaction { exec("SELECT 1") }
        return true
    }

    private fun readSecret(envVar: String): String? {
        val path = System.getenv(envVar) ?: return null
        return try { java.io.File(path).readText().trim() } catch (_: Exception) { null }
    }
}
