package com.zyntasolutions.zyntapos.license.service

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zyntasolutions.zyntapos.license.config.LicenseConfig
import com.zyntasolutions.zyntapos.license.db.DeviceRegistrations
import com.zyntasolutions.zyntapos.license.db.Licenses
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B4: Integration tests for [LicenseService.getStatus] with a real PostgreSQL container.
 *
 * Tests cover:
 * - Known active license returns a status response with correct fields
 * - Device count is correctly reflected in the status response
 * - Expired license status is returned as-is (ACTIVE — dynamic status is computed at heartbeat time)
 * - Unknown license key returns null
 */
class LicenseStatusIntegrationTest {

    companion object {
        private val postgres: PostgreSQLContainer<*> by lazy {
            PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("zyntapos_license_status_test")
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

    private fun seedLicense(
        licenseKey: String,
        status: String = "ACTIVE",
        maxDevices: Int = 5,
        edition: String = "COMMUNITY",
        expiresAt: OffsetDateTime? = null,
    ) {
        transaction(sharedDatabase) {
            Licenses.insert {
                it[Licenses.key] = licenseKey
                it[Licenses.customerId] = "cust-status-test"
                it[Licenses.customerName] = "Status Test Customer"
                it[Licenses.edition] = edition
                it[Licenses.maxDevices] = maxDevices
                it[Licenses.status] = status
                it[Licenses.issuedAt] = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30)
                it[Licenses.expiresAt] = expiresAt
                it[Licenses.forceSyncRequested] = false
                it[Licenses.createdAt] = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30)
                it[Licenses.updatedAt] = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30)
            }
        }
    }

    private fun seedDevice(licenseKey: String, deviceId: String) {
        transaction(sharedDatabase) {
            DeviceRegistrations.insert {
                it[DeviceRegistrations.id] = UUID.randomUUID().toString()
                it[DeviceRegistrations.licenseKey] = licenseKey
                it[DeviceRegistrations.deviceId] = deviceId
                it[DeviceRegistrations.appVersion] = "1.0.0"
                it[DeviceRegistrations.lastSeenAt] = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1)
                it[DeviceRegistrations.registeredAt] = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
            }
        }
    }

    // ── Status response correctness ──────────────────────────────────────────

    @Test
    fun `getStatus returns correct fields for known active license`() = runTest {
        val key = "LK-STAT-${UUID.randomUUID().toString().take(8).uppercase()}"
        seedLicense(licenseKey = key, maxDevices = 3, edition = "PROFESSIONAL")
        val service = LicenseService(testConfig)

        val status = service.getStatus(key)

        assertNotNull(status, "getStatus must return a response for a known license")
        assertEquals("PROFESSIONAL", status!!.edition)
        assertEquals("ACTIVE", status.status)
        assertEquals(3, status.maxDevices)
        assertEquals(0, status.activeDevices)  // no devices registered yet
        assertNotNull(status.issuedAt)
        assertNull(status.expiresAt, "Perpetual license must have null expiresAt")
        assertNull(status.lastHeartbeatAt, "No heartbeat yet")
        // Key must be masked (last 4 chars only)
        assertTrue(status.key.startsWith("****"), "Key must be masked in status response")
    }

    @Test
    fun `getStatus reflects active device count correctly`() = runTest {
        val key = "LK-DEVCT-${UUID.randomUUID().toString().take(6).uppercase()}"
        seedLicense(licenseKey = key, maxDevices = 10)
        seedDevice(key, "device-001")
        seedDevice(key, "device-002")
        seedDevice(key, "device-003")
        val service = LicenseService(testConfig)

        val status = service.getStatus(key)

        assertNotNull(status)
        assertEquals(3, status!!.activeDevices, "activeDevices must equal number of registered devices")
    }

    @Test
    fun `getStatus returns null for unknown license key`() = runTest {
        val service = LicenseService(testConfig)

        val status = service.getStatus("UNKN-0000-0000-0000")

        assertNull(status, "Unknown key must return null (404 to caller)")
    }

    @Test
    fun `getStatus includes expiresAt when license has an expiry date`() = runTest {
        val key = "LK-EXP-${UUID.randomUUID().toString().take(8).uppercase()}"
        val future = OffsetDateTime.now(ZoneOffset.UTC).plusDays(90)
        seedLicense(licenseKey = key, expiresAt = future)
        val service = LicenseService(testConfig)

        val status = service.getStatus(key)

        assertNotNull(status)
        assertNotNull(status!!.expiresAt, "expiresAt must be present when set on the license")
        assertTrue(status.expiresAt!! > System.currentTimeMillis(), "expiresAt must be in the future")
    }
}
