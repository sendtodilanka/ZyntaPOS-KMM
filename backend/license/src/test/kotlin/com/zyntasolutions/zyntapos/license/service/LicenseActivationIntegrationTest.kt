package com.zyntasolutions.zyntapos.license.service

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zyntasolutions.zyntapos.license.config.LicenseConfig
import com.zyntasolutions.zyntapos.license.db.DeviceRegistrations
import com.zyntasolutions.zyntapos.license.db.Licenses
import com.zyntasolutions.zyntapos.license.models.ActivateRequest
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B4: Integration tests for [LicenseService.activate] with a real PostgreSQL container.
 *
 * Uses the same singleton Testcontainers pattern as [LicenseHeartbeatIntegrationTest].
 * Tests cover the full activation flow through Exposed + Flyway migrations:
 * - Happy path: new device activation succeeds
 * - Re-activation: same device re-activates on the same license
 * - Device limit: activation denied when all slots are occupied by OTHER devices
 * - Unknown license key: activate returns null (not found)
 * - SUSPENDED license: isValid=false, errorCode=LICENSE_SUSPENDED
 * - REVOKED license: isValid=false, errorCode=LICENSE_REVOKED
 * - Expired past grace period: isValid=false, errorCode=LICENSE_EXPIRED
 * - Within grace period: activation allowed (isValid=true)
 */
class LicenseActivationIntegrationTest {

    companion object {
        private val postgres: PostgreSQLContainer<*> by lazy {
            PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("zyntapos_license_activation_test")
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

    /**
     * Seeds a license row into the DB, with optional expiry.
     * Returns the generated license key.
     */
    private fun seedLicense(
        status: String = "ACTIVE",
        maxDevices: Int = 5,
        edition: String = "COMMUNITY",
        expiresAt: OffsetDateTime? = null,
        licenseKey: String = "LK-ACT-${UUID.randomUUID().toString().take(8).uppercase()}",
    ): String {
        transaction(sharedDatabase) {
            Licenses.insert {
                it[Licenses.key] = licenseKey
                it[Licenses.customerId] = "cust-${UUID.randomUUID().toString().take(6)}"
                it[Licenses.customerName] = "Test Customer"
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
        return licenseKey
    }

    /**
     * Seeds an additional device for an existing license (pre-occupies a slot).
     */
    private fun seedDevice(licenseKey: String, deviceId: String) {
        transaction(sharedDatabase) {
            DeviceRegistrations.insert {
                it[DeviceRegistrations.id] = UUID.randomUUID().toString()
                it[DeviceRegistrations.licenseKey] = licenseKey
                it[DeviceRegistrations.deviceId] = deviceId
                it[DeviceRegistrations.deviceName] = "Existing Device"
                it[DeviceRegistrations.appVersion] = "1.0.0"
                it[DeviceRegistrations.lastSeenAt] = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1)
                it[DeviceRegistrations.registeredAt] = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
            }
        }
    }

    // ── Happy path ───────────────────────────────────────────────────────────

    @Test
    fun `new device activation succeeds for active license`() = runTest {
        val key = seedLicense()
        val service = LicenseService(testConfig)

        val response = service.activate(
            ActivateRequest(
                licenseKey = key,
                deviceId = "device-new-001",
                deviceName = "POS Terminal 1",
                appVersion = "1.0.0",
                osVersion = "Android 14",
            )
        )

        assertNotNull(response, "Activation must return a response for a valid license")
        assertTrue(response!!.isValid, "New device on active license must be valid")
        assertEquals(1, response.activeDevices)
        assertNull(response.errorCode, "No errorCode on success")
    }

    @Test
    fun `re-activation of same device succeeds and does not consume extra slot`() = runTest {
        val key = seedLicense(maxDevices = 1)
        seedDevice(key, "device-existing-001")  // pre-register this device

        val service = LicenseService(testConfig)

        // Re-activate the SAME device — should succeed (otherDevices count excludes itself)
        val response = service.activate(
            ActivateRequest(
                licenseKey = key,
                deviceId = "device-existing-001",
                appVersion = "1.0.1",  // updated version
                osVersion = "Android 14",
            )
        )

        assertNotNull(response)
        assertTrue(response!!.isValid, "Re-activation must succeed even at maxDevices=1")
    }

    // ── Device limit ─────────────────────────────────────────────────────────

    @Test
    fun `activation denied when device limit is reached by other devices`() = runTest {
        val key = seedLicense(maxDevices = 2)
        // Pre-register 2 DIFFERENT devices to fill all slots
        seedDevice(key, "device-slot-1")
        seedDevice(key, "device-slot-2")

        val service = LicenseService(testConfig)

        val response = service.activate(
            ActivateRequest(
                licenseKey = key,
                deviceId = "device-new-denied",  // new device not yet registered
                appVersion = "1.0.0",
            )
        )

        assertNotNull(response)
        assertFalse(response!!.isValid, "New device must be denied when all slots are occupied")
        assertEquals("DEVICE_LIMIT_REACHED", response.errorCode)
        assertEquals(2, response.activeDevices)
    }

    // ── Unknown key ──────────────────────────────────────────────────────────

    @Test
    fun `activate returns null for unknown license key`() = runTest {
        val service = LicenseService(testConfig)

        val response = service.activate(
            ActivateRequest(
                licenseKey = "XXXX-XXXX-XXXX-0000",  // not in DB
                deviceId = "device-any",
                appVersion = "1.0.0",
            )
        )

        assertNull(response, "Unknown license key must return null (404 to caller)")
    }

    // ── License status gates ─────────────────────────────────────────────────

    @Test
    fun `activate returns isValid=false for SUSPENDED license`() = runTest {
        val key = seedLicense(status = "SUSPENDED")
        val service = LicenseService(testConfig)

        val response = service.activate(
            ActivateRequest(licenseKey = key, deviceId = "device-sus", appVersion = "1.0.0")
        )

        assertNotNull(response)
        assertFalse(response!!.isValid)
        assertEquals("LICENSE_SUSPENDED", response.errorCode)
    }

    @Test
    fun `activate returns isValid=false for REVOKED license`() = runTest {
        val key = seedLicense(status = "REVOKED")
        val service = LicenseService(testConfig)

        val response = service.activate(
            ActivateRequest(licenseKey = key, deviceId = "device-rev", appVersion = "1.0.0")
        )

        assertNotNull(response)
        assertFalse(response!!.isValid)
        assertEquals("LICENSE_REVOKED", response.errorCode)
    }

    // ── Expiry handling ──────────────────────────────────────────────────────

    @Test
    fun `activate denied when license is expired past grace period`() = runTest {
        // Expired 10 days ago, grace period is 7 days → past grace
        val expired = OffsetDateTime.now(ZoneOffset.UTC).minusDays(10)
        val key = seedLicense(expiresAt = expired)
        val service = LicenseService(testConfig)

        val response = service.activate(
            ActivateRequest(licenseKey = key, deviceId = "device-exp", appVersion = "1.0.0")
        )

        assertNotNull(response)
        assertFalse(response!!.isValid, "Past-grace-period expiry must deny activation")
        assertEquals("LICENSE_EXPIRED", response.errorCode)
    }

    @Test
    fun `activate allowed when license is expired but within grace period`() = runTest {
        // Expired 3 days ago, grace period is 7 days → still within grace
        val recentlyExpired = OffsetDateTime.now(ZoneOffset.UTC).minusDays(3)
        val key = seedLicense(expiresAt = recentlyExpired)
        val service = LicenseService(testConfig)

        val response = service.activate(
            ActivateRequest(licenseKey = key, deviceId = "device-grace", appVersion = "1.0.0")
        )

        assertNotNull(response)
        assertTrue(
            response!!.isValid,
            "Activation within grace period must succeed (isValid=true)"
        )
    }
}
