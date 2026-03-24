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
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
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
 * C3: Expanded integration tests for [LicenseService.heartbeat].
 *
 * Covers gaps not addressed in [LicenseHeartbeatIntegrationTest]:
 * - forceSync flag: reads true, resets to false after heartbeat
 * - Device info update: appVersion and osVersion are updated on heartbeat
 * - Status recalculation: ACTIVE, EXPIRING_SOON, GRACE_PERIOD, EXPIRED
 * - Unregistered device returns null
 * - lastHeartbeatAt is updated on the license row
 */
class LicenseHeartbeatExpandedIntegrationTest {

    companion object {
        private val postgres: PostgreSQLContainer<*> by lazy {
            PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("zyntapos_license_hb_expanded_test")
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
        status: String = "ACTIVE",
        expiresAt: OffsetDateTime? = null,
        forceSyncRequested: Boolean = false,
    ) {
        transaction(sharedDatabase) {
            Licenses.insert {
                it[Licenses.key] = licenseKey
                it[customerId] = "cust-hb-expanded"
                it[edition] = "PROFESSIONAL"
                it[maxDevices] = 5
                it[Licenses.status] = status
                it[issuedAt] = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30)
                it[Licenses.expiresAt] = expiresAt
                it[Licenses.forceSyncRequested] = forceSyncRequested
                it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30)
                it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30)
            }
            DeviceRegistrations.insert {
                it[id] = UUID.randomUUID().toString()
                it[DeviceRegistrations.licenseKey] = licenseKey
                it[DeviceRegistrations.deviceId] = deviceId
                it[appVersion] = "1.0.0"
                it[osVersion] = "Android 13"
                it[lastSeenAt] = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(30)
                it[registeredAt] = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
            }
        }
    }

    // ── forceSync flag ────────────────────────────────────────────────────────

    @Test
    fun `heartbeat returns forceSync=true when flag is set on license`() = runTest {
        val key = "LK-FSYNC-${UUID.randomUUID().toString().take(8)}"
        val deviceId = "device-fsync-1"
        seedLicenseAndDevice(key, deviceId, forceSyncRequested = true)

        val service = LicenseService(testConfig)
        val response = service.heartbeat(
            HeartbeatRequest(
                licenseKey = key,
                deviceId = deviceId,
                appVersion = "1.0.0",
            )
        )

        assertNotNull(response, "Heartbeat should succeed")
        assertTrue(response!!.forceSync, "forceSync should be true when flag is set in DB")
    }

    @Test
    fun `heartbeat resets forceSync flag after reading it`() = runTest {
        val key = "LK-FSRST-${UUID.randomUUID().toString().take(8)}"
        val deviceId = "device-fsrst-1"
        seedLicenseAndDevice(key, deviceId, forceSyncRequested = true)

        val service = LicenseService(testConfig)

        // First heartbeat — should get forceSync=true
        val resp1 = service.heartbeat(
            HeartbeatRequest(licenseKey = key, deviceId = deviceId, appVersion = "1.0.0")
        )
        assertNotNull(resp1)
        assertTrue(resp1!!.forceSync, "First heartbeat should see forceSync=true")

        // Second heartbeat — flag should be reset to false
        val resp2 = service.heartbeat(
            HeartbeatRequest(licenseKey = key, deviceId = deviceId, appVersion = "1.0.0")
        )
        assertNotNull(resp2)
        assertFalse(resp2!!.forceSync, "Second heartbeat should see forceSync=false (reset)")
    }

    @Test
    fun `heartbeat returns forceSync=false when flag is not set`() = runTest {
        val key = "LK-NOFS-${UUID.randomUUID().toString().take(8)}"
        val deviceId = "device-nofs-1"
        seedLicenseAndDevice(key, deviceId, forceSyncRequested = false)

        val service = LicenseService(testConfig)
        val response = service.heartbeat(
            HeartbeatRequest(licenseKey = key, deviceId = deviceId, appVersion = "1.0.0")
        )

        assertNotNull(response)
        assertFalse(response!!.forceSync, "forceSync should be false when not set")
    }

    // ── Device info update ──────────────────────────────────────────────────

    @Test
    fun `heartbeat updates appVersion and osVersion on device`() = runTest {
        val key = "LK-DEVUP-${UUID.randomUUID().toString().take(8)}"
        val deviceId = "device-devup-1"
        seedLicenseAndDevice(key, deviceId)

        val service = LicenseService(testConfig)
        service.heartbeat(
            HeartbeatRequest(
                licenseKey = key,
                deviceId = deviceId,
                appVersion = "2.0.0",
                osVersion = "Android 14",
            )
        )

        // Verify device row was updated
        transaction(sharedDatabase) {
            val device = DeviceRegistrations.selectAll().where {
                (DeviceRegistrations.licenseKey eq key) and
                    (DeviceRegistrations.deviceId eq deviceId)
            }.single()

            assertEquals("2.0.0", device[DeviceRegistrations.appVersion])
            assertEquals("Android 14", device[DeviceRegistrations.osVersion])
        }
    }

    @Test
    fun `heartbeat updates lastSeenAt on device`() = runTest {
        val key = "LK-LSEEN-${UUID.randomUUID().toString().take(8)}"
        val deviceId = "device-lseen-1"
        seedLicenseAndDevice(key, deviceId)

        val beforeHeartbeat = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1)

        val service = LicenseService(testConfig)
        service.heartbeat(
            HeartbeatRequest(licenseKey = key, deviceId = deviceId, appVersion = "1.0.0")
        )

        transaction(sharedDatabase) {
            val device = DeviceRegistrations.selectAll().where {
                (DeviceRegistrations.licenseKey eq key) and
                    (DeviceRegistrations.deviceId eq deviceId)
            }.single()

            val lastSeenAt = device[DeviceRegistrations.lastSeenAt]
            assertTrue(
                lastSeenAt.isAfter(beforeHeartbeat),
                "lastSeenAt should be updated to current time"
            )
        }
    }

    @Test
    fun `heartbeat updates lastHeartbeatAt on license`() = runTest {
        val key = "LK-LHBAT-${UUID.randomUUID().toString().take(8)}"
        val deviceId = "device-lhbat-1"
        seedLicenseAndDevice(key, deviceId)

        val service = LicenseService(testConfig)
        service.heartbeat(
            HeartbeatRequest(licenseKey = key, deviceId = deviceId, appVersion = "1.0.0")
        )

        transaction(sharedDatabase) {
            val license = Licenses.selectAll().where { Licenses.key eq key }.single()
            assertNotNull(
                license[Licenses.lastHeartbeatAt],
                "lastHeartbeatAt should be set after heartbeat"
            )
        }
    }

    // ── Status recalculation ────────────────────────────────────────────────

    @Test
    fun `heartbeat returns ACTIVE status for non-expiring license`() = runTest {
        val key = "LK-HSTAT-${UUID.randomUUID().toString().take(8)}"
        val deviceId = "device-hstat-1"
        seedLicenseAndDevice(key, deviceId, expiresAt = null)

        val service = LicenseService(testConfig)
        val response = service.heartbeat(
            HeartbeatRequest(licenseKey = key, deviceId = deviceId, appVersion = "1.0.0")
        )

        assertNotNull(response)
        assertEquals("ACTIVE", response!!.status)
        assertNull(response.expiresAt, "Perpetual license should have null expiresAt")
        assertNull(response.daysUntilExpiry, "Perpetual license should have null daysUntilExpiry")
    }

    @Test
    fun `heartbeat returns EXPIRING_SOON when within 7 days of expiry`() = runTest {
        val key = "LK-EXPSN-${UUID.randomUUID().toString().take(8)}"
        val deviceId = "device-expsn-1"
        val expiresIn5Days = OffsetDateTime.now(ZoneOffset.UTC).plusDays(5)
        seedLicenseAndDevice(key, deviceId, expiresAt = expiresIn5Days)

        val service = LicenseService(testConfig)
        val response = service.heartbeat(
            HeartbeatRequest(licenseKey = key, deviceId = deviceId, appVersion = "1.0.0")
        )

        assertNotNull(response)
        assertEquals("EXPIRING_SOON", response!!.status)
        assertNotNull(response.daysUntilExpiry)
        assertTrue(response.daysUntilExpiry!! in 4..5, "Should be ~5 days until expiry")
    }

    @Test
    fun `heartbeat returns GRACE_PERIOD when expired 3 days ago`() = runTest {
        val key = "LK-GRACE-${UUID.randomUUID().toString().take(8)}"
        val deviceId = "device-grace-1"
        val expiredRecently = OffsetDateTime.now(ZoneOffset.UTC).minusDays(3)
        seedLicenseAndDevice(key, deviceId, expiresAt = expiredRecently)

        val service = LicenseService(testConfig)
        val response = service.heartbeat(
            HeartbeatRequest(licenseKey = key, deviceId = deviceId, appVersion = "1.0.0")
        )

        assertNotNull(response)
        assertEquals("GRACE_PERIOD", response!!.status)
    }

    @Test
    fun `heartbeat returns EXPIRED when past grace period`() = runTest {
        val key = "LK-EXPIR-${UUID.randomUUID().toString().take(8)}"
        val deviceId = "device-expir-1"
        val expiredLongAgo = OffsetDateTime.now(ZoneOffset.UTC).minusDays(10)
        seedLicenseAndDevice(key, deviceId, expiresAt = expiredLongAgo)

        val service = LicenseService(testConfig)
        val response = service.heartbeat(
            HeartbeatRequest(licenseKey = key, deviceId = deviceId, appVersion = "1.0.0")
        )

        assertNotNull(response)
        assertEquals("EXPIRED", response!!.status)
    }

    @Test
    fun `heartbeat returns SUSPENDED for suspended license`() = runTest {
        val key = "LK-HSUS-${UUID.randomUUID().toString().take(8)}"
        val deviceId = "device-hsus-1"
        seedLicenseAndDevice(key, deviceId, status = "SUSPENDED")

        val service = LicenseService(testConfig)
        val response = service.heartbeat(
            HeartbeatRequest(licenseKey = key, deviceId = deviceId, appVersion = "1.0.0")
        )

        assertNotNull(response)
        assertEquals("SUSPENDED", response!!.status)
    }

    @Test
    fun `heartbeat returns REVOKED for revoked license`() = runTest {
        val key = "LK-HREV-${UUID.randomUUID().toString().take(8)}"
        val deviceId = "device-hrev-1"
        seedLicenseAndDevice(key, deviceId, status = "REVOKED")

        val service = LicenseService(testConfig)
        val response = service.heartbeat(
            HeartbeatRequest(licenseKey = key, deviceId = deviceId, appVersion = "1.0.0")
        )

        assertNotNull(response)
        assertEquals("REVOKED", response!!.status)
    }

    // ── Unregistered device ─────────────────────────────────────────────────

    @Test
    fun `heartbeat returns null for unregistered device`() = runTest {
        val key = "LK-UNREG-${UUID.randomUUID().toString().take(8)}"
        val deviceId = "device-unreg-registered"
        seedLicenseAndDevice(key, deviceId)

        val service = LicenseService(testConfig)
        val response = service.heartbeat(
            HeartbeatRequest(
                licenseKey = key,
                deviceId = "device-NOT-registered",
                appVersion = "1.0.0"
            )
        )

        assertNull(response, "Heartbeat for unregistered device should return null")
    }

    @Test
    fun `heartbeat returns null for unknown license key`() = runTest {
        val service = LicenseService(testConfig)
        val response = service.heartbeat(
            HeartbeatRequest(
                licenseKey = "UNKNOWN-KEY-0000",
                deviceId = "device-any",
                appVersion = "1.0.0"
            )
        )

        assertNull(response, "Heartbeat for unknown license key should return null")
    }
}
