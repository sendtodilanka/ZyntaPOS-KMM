package com.zyntasolutions.zyntapos.api.test

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.junit.jupiter.api.BeforeEach

/**
 * Base class for repository integration tests that need a real PostgreSQL
 * database with Flyway migrations applied.
 *
 * Uses Testcontainers to spin up a single shared PostgreSQL instance.
 * Each test gets a clean database state via table truncation.
 */
@Testcontainers
abstract class AbstractIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("zyntapos_api_test")
            .withUsername("test")
            .withPassword("test")

        @Volatile
        private var initialized = false

        @Volatile
        private lateinit var sharedDatabase: Database
    }

    protected val database: Database get() = sharedDatabase

    @BeforeEach
    fun baseSetup() {
        if (!initialized) {
            // Run Flyway migrations first
            Flyway.configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load()
                .migrate()

            // Connect Exposed via HikariCP (matches production DatabaseFactory pattern)
            val hikariConfig = HikariConfig().apply {
                jdbcUrl = postgres.jdbcUrl
                username = postgres.username
                password = postgres.password
                driverClassName = "org.postgresql.Driver"
                maximumPoolSize = 5
                minimumIdle = 1
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_READ_COMMITTED"
                validate()
            }
            sharedDatabase = Database.connect(HikariDataSource(hikariConfig))
            initialized = true
        }

        // Ensure Exposed uses our test database for newSuspendedTransaction calls
        TransactionManager.defaultDatabase = sharedDatabase

        cleanTables()
    }

    /**
     * Truncates all application tables so each test starts with a clean slate.
     * Uses CASCADE to handle FK constraints.
     */
    private fun cleanTables() {
        transaction(sharedDatabase) {
            exec(
                """
                TRUNCATE TABLE
                    password_reset_tokens,
                    admin_sessions,
                    admin_audit_log,
                    pos_sessions,
                    sync_queue,
                    products,
                    users,
                    admin_users,
                    stores
                CASCADE
                """.trimIndent()
            )
        }
    }
}
