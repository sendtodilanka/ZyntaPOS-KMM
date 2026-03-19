package com.zyntasolutions.zyntapos.api.test

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.testcontainers.containers.PostgreSQLContainer
import org.junit.jupiter.api.BeforeEach

/**
 * Base class for repository integration tests that need a real PostgreSQL
 * database with Flyway migrations applied.
 *
 * Uses a lazy singleton Testcontainers instance started once per JVM session.
 * The @Testcontainers/@Container annotation pattern is intentionally avoided:
 * it stops and restarts the container after each test class, changing the
 * dynamic port mapping. The initialized flag was never reset, so subsequent
 * classes kept a stale HikariCP pool pointing to the dead old port, causing
 * ConnectException on every test after the first class.
 *
 * Each test gets a clean database state via table truncation in cleanTables().
 */
abstract class AbstractIntegrationTest {

    companion object {
        val postgres: PostgreSQLContainer<*> by lazy {
            PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("zyntapos_api_test")
                .withUsername("test")
                .withPassword("test")
                .also { it.start() }
        }

        init {
            Runtime.getRuntime().addShutdownHook(Thread { postgres.stop() })
        }

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
