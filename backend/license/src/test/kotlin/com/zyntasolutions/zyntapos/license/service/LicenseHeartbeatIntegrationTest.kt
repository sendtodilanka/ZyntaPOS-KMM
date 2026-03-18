package com.zyntasolutions.zyntapos.license.service

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zyntasolutions.zyntapos.license.config.LicenseConfig
import com.zyntasolutions.zyntapos.license.db.DeviceRegistrations
import com.zyntasolutions.zyntapos.license.db.Licenses
import com.zyntasolutions.zyntapos.license.models.HeartbeatRequest
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.testcontainers.containers.PostgreSQLContainer
import java.security.KeyPairGenerator
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A1.1 — Integration tests for [LicenseService.heartbeat] with a real PostgreSQL container.
 *
 * Uses a singleton Testcontainers PostgreSQL container started lazily via companion object.
 * Tests cover:
 * - Fresh heartbeat with unique nonce returns a response (nonce recorded)
 * - Duplicate nonce within TTL is rejected (replay protection)
 * - Stale clientTimestamp (>60s old) is rejected
 * - Null nonce/timestamp is accepted (backward compatibility)
 */
class LicenseHeartbeatIntegrationTest {

    companion object {
        private val postgres: PostgreSQLContainer<*> by lazy {
            PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("zyntapos_license_test")
                .withUsername("test")
                .withPassword("test")
                .also { it.start() }
        }

        private val sharedDatabase: Database by lazy {
            Flyway.configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load()
                .migrate()

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
            Database.connect(HikariDataSource(hikariConfig))
        }

        private val testRsaPublicKey = KeyPairGenerator.getInstance("RSA")
            .apply { initialize(2048) }
            .generateKeyPair()
            .public

        val testConfig = LicenseConfig(
            jwtIssuer = "test-issuer",
            jwtAudience = "test-audience",
            jwtPublicKey = testRsaPublicKey,
            gracePeriodDays = 7,
            maxDevicesPerLicense = 100,
            heartbeatIntervalMinutes = 60,
        )

        init {
            Runtime.getRuntime().addShutdownHook(Thread {
                try { postgres.stop() } catch (_: Exception) {}
            })
        }
    }

    @BeforeTest
    fun resetDatabase() {
        TransactionManager.defaultDatabase = sharedDatabase
        transaction(sharedDatabase) {
            exec("TRUNCATE TABLE device_registrations, licenses CASCADE")
        }
    }

    private fun seedLicenseAndDevice(
        licenseKey: String,
        deviceId: String,
        edition: String = "COMMUNITY",
        maxDevices: Int = 5,
        status: String = "ACTIVE",
    ) {
        transaction(sharedDatabase) {
            Licenses.insert {
                it[Licenses.key]                = licenseKey
                it[Licenses.customerId]         = "cust-test"
                it[Licenses.edition]            = edition
                it[Licenses.maxDevices]         = maxDevices
                it[Licenses.status]             = status
                it[Licenses.forceSyncRequested] = false
            }
            DeviceRegistrations.insert {
                it[DeviceRegistrations.id]           = UUID.randomUUID().toString()
                it[DeviceRegistrations.licenseKey]   = licenseKey
                it[DeviceRegistrations.deviceId]     = deviceId
                it[DeviceRegistrations.appVersion]   = "1.0.0"
                it[DeviceRegistrations.lastSeenAt]   = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(30)
                it[DeviceRegistrations.registeredAt] = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
            }
        }
    }

    // ── Fresh heartbeat ──────────────────────────────────────────────────────

    @Test
    fun `heartbeat with valid nonce and recent timestamp returns response`() = runTest {
        val licenseKey = "LK-HEART-${UUID.randomUUID().toString().take(8)}"
        val deviceId = "device-hb-1"
        seedLicenseAndDevice(licenseKey, deviceId)

        val service = LicenseService(testConfig)
        val response = service.heartbeat(
            HeartbeatRequest(
                licenseKey = licenseKey,
                deviceId = deviceId,
                appVersion = "1.0.0",
                nonce = UUID.randomUUID().toString(),
                clientTimestamp = System.currentTimeMillis(),
            )
        )

        assertNotNull(response, "Heartbeat with fresh nonce and timestamp must succeed")
        assertNotNull(response!!.status)
    }

    @Test
    fun `heartbeat with null nonce and null timestamp is accepted (backward compat)`() = runTest {
        val licenseKey = "LK-COMPAT-${UUID.randomUUID().toString().take(8)}"
        val deviceId = "device-compat-1"
        seedLicenseAndDevice(licenseKey, deviceId)

        val service = LicenseService(testConfig)
        val response = service.heartbeat(
            HeartbeatRequest(
                licenseKey = licenseKey,
                deviceId = deviceId,
                appVersion = "1.0.0",
                nonce = null,
                clientTimestamp = null,
            )
        )

        assertNotNull(response, "Null nonce/timestamp must be accepted for backward compatibility")
    }

    // ── Replay protection ────────────────────────────────────────────────────

    @Test
    fun `duplicate nonce within TTL is rejected as replay`() = runTest {
        val licenseKey = "LK-REPLAY-${UUID.randomUUID().toString().take(8)}"
        val deviceId = "device-replay-1"
        seedLicenseAndDevice(licenseKey, deviceId)

        val nonce = UUID.randomUUID().toString()
        val service = LicenseService(testConfig)

        // First heartbeat with this nonce — should succeed
        val resp1 = service.heartbeat(
            HeartbeatRequest(
                licenseKey = licenseKey,
                deviceId = deviceId,
                appVersion = "1.0.0",
                nonce = nonce,
                clientTimestamp = System.currentTimeMillis(),
            )
        )
        assertNotNull(resp1, "First heartbeat with fresh nonce must succeed")

        // Re-seed because the device lastSeenAt was updated and second call needs a new device entry
        // Actually the nonce rejection happens BEFORE the DB query, so we don't need to re-seed
        val resp2 = service.heartbeat(
            HeartbeatRequest(
                licenseKey = licenseKey,
                deviceId = deviceId,
                appVersion = "1.0.0",
                nonce = nonce, // SAME nonce — replay
                clientTimestamp = System.currentTimeMillis(),
            )
        )
        assertNull(resp2, "Duplicate nonce must be rejected as replay")
    }

    // ── Stale timestamp ──────────────────────────────────────────────────────

    @Test
    fun `heartbeat with clientTimestamp older than 60 seconds is rejected`() = runTest {
        val licenseKey = "LK-STALE-${UUID.randomUUID().toString().take(8)}"
        val deviceId = "device-stale-1"
        seedLicenseAndDevice(licenseKey, deviceId)

        val service = LicenseService(testConfig)
        val response = service.heartbeat(
            HeartbeatRequest(
                licenseKey = licenseKey,
                deviceId = deviceId,
                appVersion = "1.0.0",
                nonce = UUID.randomUUID().toString(), // unique — not a replay
                clientTimestamp = System.currentTimeMillis() - 70_000L, // 70s old — stale
            )
        )

        assertNull(response, "Heartbeat with stale timestamp (>60s) must be rejected")
    }
}
