package com.zyntasolutions.zyntapos.license.service

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import com.zyntasolutions.zyntapos.license.db.AdminAuditLog
import com.zyntasolutions.zyntapos.license.db.DeviceRegistrations
import com.zyntasolutions.zyntapos.license.db.Licenses
import com.zyntasolutions.zyntapos.license.models.AdminCreateLicenseRequest
import com.zyntasolutions.zyntapos.license.models.AdminUpdateLicenseRequest
import kotlinx.coroutines.test.runTest
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.testcontainers.containers.PostgreSQLContainer
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
 * C8: Integration tests for [AdminLicenseService] CRUD operations with real PostgreSQL.
 *
 * Tests cover:
 * - Create license: valid input, key generation, audit log
 * - Update license: extend expiry, change tier, forceSync, clearExpiry
 * - Revoke license: status change to REVOKED, audit log
 * - Device deregistration: single device removal, audit log
 * - License stats aggregation: active/expired/revoked/suspended counts, byEdition
 * - List with filters: status, edition, search, pagination
 * - Get license with devices
 * - Audit log creation on all admin actions
 */
class AdminLicenseServiceIntegrationTest {

    companion object {
        private val postgres: PostgreSQLContainer<*> by lazy {
            PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("zyntapos_license_admin_test")
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

        private const val ADMIN_ID = "admin-test-001"

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
            exec("TRUNCATE TABLE admin_audit_log, device_registrations, licenses CASCADE")
        }
    }

    private fun seedLicense(
        licenseKey: String,
        status: String = "ACTIVE",
        edition: String = "STARTER",
        maxDevices: Int = 5,
        expiresAt: OffsetDateTime? = null,
        customerId: String = "cust-admin-test",
        customerName: String = "Admin Test Customer",
    ) {
        transaction(sharedDatabase) {
            Licenses.insert {
                it[key] = licenseKey
                it[Licenses.customerId] = customerId
                it[Licenses.customerName] = customerName
                it[Licenses.edition] = edition
                it[Licenses.maxDevices] = maxDevices
                it[Licenses.status] = status
                it[issuedAt] = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30)
                it[Licenses.expiresAt] = expiresAt
                it[forceSyncRequested] = false
                it[createdAt] = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30)
                it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30)
            }
        }
    }

    private fun seedDevice(licenseKey: String, deviceId: String, rowId: String = UUID.randomUUID().toString()) {
        transaction(sharedDatabase) {
            DeviceRegistrations.insert {
                it[id] = rowId
                it[DeviceRegistrations.licenseKey] = licenseKey
                it[DeviceRegistrations.deviceId] = deviceId
                it[deviceName] = "Device $deviceId"
                it[appVersion] = "1.0.0"
                it[osVersion] = "Android 14"
                it[lastSeenAt] = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1)
                it[registeredAt] = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
            }
        }
    }

    private fun getAuditLogs(action: String? = null): List<Map<String, Any?>> {
        return transaction(sharedDatabase) {
            var query = AdminAuditLog.selectAll()
            if (action != null) {
                query = query.adjustWhere { AdminAuditLog.action eq action }
            }
            query.map { row ->
                mapOf(
                    "adminId" to row[AdminAuditLog.adminId],
                    "action" to row[AdminAuditLog.action],
                    "licenseKey" to row[AdminAuditLog.licenseKey],
                    "details" to row[AdminAuditLog.details],
                )
            }
        }
    }

    // ── Create License ──────────────────────────────────────────────────────

    @Test
    fun `createLicense creates license with correct fields`() = runTest {
        val service = AdminLicenseService()

        val result = service.createLicense(
            AdminCreateLicenseRequest(
                customerId = "cust-new-001",
                customerName = "New Customer",
                edition = "PROFESSIONAL",
                maxDevices = 3,
                expiresAt = "2027-12-31T00:00:00Z",
            ),
            ADMIN_ID
        )

        assertEquals("PROFESSIONAL", result.edition)
        assertEquals(3, result.maxDevices)
        assertEquals("cust-new-001", result.customerId)
        assertEquals("New Customer", result.customerName)
        assertNotNull(result.key, "Generated key should not be null")
        assertTrue(result.key.length == 19, "Key should be XXXX-XXXX-XXXX-XXXX (19 chars)")
        assertNotNull(result.expiresAt, "expiresAt should be set")
        assertEquals("ACTIVE", result.status)
    }

    @Test
    fun `createLicense normalizes edition to uppercase`() = runTest {
        val service = AdminLicenseService()

        val result = service.createLicense(
            AdminCreateLicenseRequest(
                customerId = "cust-case-001",
                edition = "enterprise",
                maxDevices = 10,
            ),
            ADMIN_ID
        )

        assertEquals("ENTERPRISE", result.edition, "Edition should be uppercased")
    }

    @Test
    fun `createLicense with null expiresAt creates perpetual license`() = runTest {
        val service = AdminLicenseService()

        val result = service.createLicense(
            AdminCreateLicenseRequest(
                customerId = "cust-perp-001",
                edition = "STARTER",
                maxDevices = 1,
                expiresAt = null,
            ),
            ADMIN_ID
        )

        assertNull(result.expiresAt, "Perpetual license should have null expiresAt")
    }

    @Test
    fun `createLicense writes audit log with CREATE_LICENSE action`() = runTest {
        val service = AdminLicenseService()

        service.createLicense(
            AdminCreateLicenseRequest(
                customerId = "cust-audit-001",
                edition = "STARTER",
                maxDevices = 2,
            ),
            ADMIN_ID
        )

        val logs = getAuditLogs("CREATE_LICENSE")
        assertEquals(1, logs.size, "Should have exactly one CREATE_LICENSE audit log")
        assertEquals(ADMIN_ID, logs[0]["adminId"])
        assertTrue(
            (logs[0]["details"] as String).contains("edition=STARTER"),
            "Audit details should contain edition"
        )
        assertTrue(
            (logs[0]["details"] as String).contains("maxDevices=2"),
            "Audit details should contain maxDevices"
        )
    }

    // ── Update License ──────────────────────────────────────────────────────

    @Test
    fun `updateLicense extends expiry date`() = runTest {
        val key = "LK-UPEXP-${UUID.randomUUID().toString().take(8)}"
        val originalExpiry = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30)
        seedLicense(key, expiresAt = originalExpiry)

        val service = AdminLicenseService()
        val newExpiry = "2028-06-30T00:00:00Z"
        val result = service.updateLicense(
            key,
            AdminUpdateLicenseRequest(expiresAt = newExpiry),
            ADMIN_ID
        )

        assertNotNull(result, "Update should return the updated license")
        assertNotNull(result!!.expiresAt, "expiresAt should be set")
        assertTrue(
            result.expiresAt!!.contains("2028"),
            "expiresAt should reflect the new date"
        )
    }

    @Test
    fun `updateLicense changes edition tier`() = runTest {
        val key = "LK-CHED-${UUID.randomUUID().toString().take(8)}"
        seedLicense(key, edition = "STARTER")

        val service = AdminLicenseService()
        val result = service.updateLicense(
            key,
            AdminUpdateLicenseRequest(edition = "ENTERPRISE"),
            ADMIN_ID
        )

        assertNotNull(result)
        assertEquals("ENTERPRISE", result!!.edition, "Edition should be updated to ENTERPRISE")
    }

    @Test
    fun `updateLicense changes maxDevices`() = runTest {
        val key = "LK-CHMD-${UUID.randomUUID().toString().take(8)}"
        seedLicense(key, maxDevices = 2)

        val service = AdminLicenseService()
        val result = service.updateLicense(
            key,
            AdminUpdateLicenseRequest(maxDevices = 10),
            ADMIN_ID
        )

        assertNotNull(result)
        assertEquals(10, result!!.maxDevices, "maxDevices should be updated to 10")
    }

    @Test
    fun `updateLicense sets forceSync flag`() = runTest {
        val key = "LK-FSUP-${UUID.randomUUID().toString().take(8)}"
        seedLicense(key)

        val service = AdminLicenseService()
        service.updateLicense(
            key,
            AdminUpdateLicenseRequest(forceSync = true),
            ADMIN_ID
        )

        transaction(sharedDatabase) {
            val license = Licenses.selectAll().where { Licenses.key eq key }.single()
            assertTrue(
                license[Licenses.forceSyncRequested],
                "forceSyncRequested should be true after update"
            )
        }
    }

    @Test
    fun `updateLicense clears expiry when clearExpiry=true`() = runTest {
        val key = "LK-CLEXP-${UUID.randomUUID().toString().take(8)}"
        seedLicense(key, expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30))

        val service = AdminLicenseService()
        val result = service.updateLicense(
            key,
            AdminUpdateLicenseRequest(clearExpiry = true),
            ADMIN_ID
        )

        assertNotNull(result)
        assertNull(result!!.expiresAt, "expiresAt should be null after clearExpiry=true")
    }

    @Test
    fun `updateLicense returns null for unknown key`() = runTest {
        val service = AdminLicenseService()
        val result = service.updateLicense(
            "UNKNOWN-KEY-0000",
            AdminUpdateLicenseRequest(edition = "ENTERPRISE"),
            ADMIN_ID
        )

        assertNull(result, "Update of unknown key should return null")
    }

    @Test
    fun `updateLicense writes audit log with UPDATE_LICENSE action`() = runTest {
        val key = "LK-AUDIT-${UUID.randomUUID().toString().take(8)}"
        seedLicense(key)

        val service = AdminLicenseService()
        service.updateLicense(
            key,
            AdminUpdateLicenseRequest(edition = "PROFESSIONAL", maxDevices = 8),
            ADMIN_ID
        )

        val logs = getAuditLogs("UPDATE_LICENSE")
        assertEquals(1, logs.size)
        assertEquals(ADMIN_ID, logs[0]["adminId"])
        assertEquals(key, logs[0]["licenseKey"])
        val details = logs[0]["details"] as String
        assertTrue(details.contains("edition=PROFESSIONAL"), "Details should include edition change")
        assertTrue(details.contains("maxDevices=8"), "Details should include maxDevices change")
    }

    @Test
    fun `updateLicense changes status to SUSPENDED`() = runTest {
        val key = "LK-SUSP-${UUID.randomUUID().toString().take(8)}"
        seedLicense(key, status = "ACTIVE")

        val service = AdminLicenseService()
        val result = service.updateLicense(
            key,
            AdminUpdateLicenseRequest(status = "SUSPENDED"),
            ADMIN_ID
        )

        assertNotNull(result)
        assertEquals("SUSPENDED", result!!.status)
    }

    // ── Revoke License ──────────────────────────────────────────────────────

    @Test
    fun `revokeLicense sets status to REVOKED`() = runTest {
        val key = "LK-REVOK-${UUID.randomUUID().toString().take(8)}"
        seedLicense(key, status = "ACTIVE")

        val service = AdminLicenseService()
        val revoked = service.revokeLicense(key, ADMIN_ID)

        assertTrue(revoked, "Revoke should return true for existing license")

        transaction(sharedDatabase) {
            val license = Licenses.selectAll().where { Licenses.key eq key }.single()
            assertEquals("REVOKED", license[Licenses.status])
        }
    }

    @Test
    fun `revokeLicense returns false for unknown key`() = runTest {
        val service = AdminLicenseService()
        val revoked = service.revokeLicense("UNKNOWN-KEY-0000", ADMIN_ID)
        assertFalse(revoked, "Revoke of unknown key should return false")
    }

    @Test
    fun `revokeLicense writes audit log with REVOKE_LICENSE action`() = runTest {
        val key = "LK-RAUD-${UUID.randomUUID().toString().take(8)}"
        seedLicense(key)

        val service = AdminLicenseService()
        service.revokeLicense(key, ADMIN_ID)

        val logs = getAuditLogs("REVOKE_LICENSE")
        assertEquals(1, logs.size)
        assertEquals(ADMIN_ID, logs[0]["adminId"])
        assertEquals(key, logs[0]["licenseKey"])
    }

    @Test
    fun `revokeLicense does not write audit log for unknown key`() = runTest {
        val service = AdminLicenseService()
        service.revokeLicense("UNKNOWN-0000", ADMIN_ID)

        val logs = getAuditLogs("REVOKE_LICENSE")
        assertEquals(0, logs.size, "No audit log for failed revoke")
    }

    // ── Device Deregistration ───────────────────────────────────────────────

    @Test
    fun `deregisterDevice removes single device`() = runTest {
        val key = "LK-DEREG-${UUID.randomUUID().toString().take(8)}"
        val rowId = UUID.randomUUID().toString()
        seedLicense(key)
        seedDevice(key, "device-001", rowId)
        seedDevice(key, "device-002")

        val service = AdminLicenseService()
        val removed = service.deregisterDevice(key, rowId, ADMIN_ID)

        assertTrue(removed, "Deregister should return true for existing device")

        transaction(sharedDatabase) {
            val remaining = DeviceRegistrations.selectAll()
                .where { DeviceRegistrations.licenseKey eq key }
                .count()
            assertEquals(1L, remaining, "Only one device should remain after deregistration")
        }
    }

    @Test
    fun `deregisterDevice returns false for unknown device`() = runTest {
        val key = "LK-DRNF-${UUID.randomUUID().toString().take(8)}"
        seedLicense(key)

        val service = AdminLicenseService()
        val removed = service.deregisterDevice(key, "nonexistent-device-id", ADMIN_ID)

        assertFalse(removed, "Deregister of unknown device should return false")
    }

    @Test
    fun `deregisterDevice writes audit log with DEREGISTER_DEVICE action`() = runTest {
        val key = "LK-DRAUD-${UUID.randomUUID().toString().take(8)}"
        val rowId = UUID.randomUUID().toString()
        seedLicense(key)
        seedDevice(key, "device-aud", rowId)

        val service = AdminLicenseService()
        service.deregisterDevice(key, rowId, ADMIN_ID)

        val logs = getAuditLogs("DEREGISTER_DEVICE")
        assertEquals(1, logs.size)
        assertEquals(ADMIN_ID, logs[0]["adminId"])
        assertEquals(key, logs[0]["licenseKey"])
        assertTrue(
            (logs[0]["details"] as String).contains(rowId),
            "Audit details should contain device row ID"
        )
    }

    // ── License Stats Aggregation ───────────────────────────────────────────

    @Test
    fun `getStats returns correct counts for mixed statuses`() = runTest {
        seedLicense("LK-S1", status = "ACTIVE", edition = "STARTER")
        seedLicense("LK-S2", status = "ACTIVE", edition = "STARTER")
        seedLicense("LK-S3", status = "ACTIVE", edition = "PROFESSIONAL")
        seedLicense("LK-S4", status = "EXPIRED", edition = "STARTER")
        seedLicense("LK-S5", status = "REVOKED", edition = "ENTERPRISE")
        seedLicense("LK-S6", status = "SUSPENDED", edition = "PROFESSIONAL")

        val service = AdminLicenseService()
        val stats = service.getStats()

        assertEquals(6, stats.total)
        assertEquals(3, stats.active)
        assertEquals(1, stats.expired)
        assertEquals(1, stats.revoked)
        assertEquals(1, stats.suspended)
    }

    @Test
    fun `getStats counts expiringSoon correctly`() = runTest {
        val in10Days = OffsetDateTime.now(ZoneOffset.UTC).plusDays(10)
        val in30Days = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30)

        seedLicense("LK-ES1", status = "ACTIVE", expiresAt = in10Days)  // expiring soon
        seedLicense("LK-ES2", status = "ACTIVE", expiresAt = in30Days)  // not expiring soon
        seedLicense("LK-ES3", status = "ACTIVE", expiresAt = null)      // perpetual — not expiring soon
        seedLicense("LK-ES4", status = "EXPIRED", expiresAt = in10Days) // expired status — not counted

        val service = AdminLicenseService()
        val stats = service.getStats()

        assertEquals(1, stats.expiringSoon, "Only ACTIVE licenses within 14 days should count")
    }

    @Test
    fun `getStats byEdition map is correct`() = runTest {
        seedLicense("LK-E1", edition = "STARTER")
        seedLicense("LK-E2", edition = "STARTER")
        seedLicense("LK-E3", edition = "PROFESSIONAL")
        seedLicense("LK-E4", edition = "ENTERPRISE")

        val service = AdminLicenseService()
        val stats = service.getStats()

        assertEquals(2, stats.byEdition["STARTER"])
        assertEquals(1, stats.byEdition["PROFESSIONAL"])
        assertEquals(1, stats.byEdition["ENTERPRISE"])
    }

    @Test
    fun `getStats returns zeros when no licenses exist`() = runTest {
        val service = AdminLicenseService()
        val stats = service.getStats()

        assertEquals(0, stats.total)
        assertEquals(0, stats.active)
        assertEquals(0, stats.expired)
        assertEquals(0, stats.revoked)
        assertEquals(0, stats.suspended)
        assertEquals(0, stats.expiringSoon)
        assertTrue(stats.byEdition.isEmpty())
    }

    // ── List Licenses with filters ──────────────────────────────────────────

    @Test
    fun `listLicenses returns paginated results`() = runTest {
        for (i in 1..15) {
            seedLicense("LK-LIST-$i", customerId = "cust-list-$i")
        }

        val service = AdminLicenseService()
        val page0 = service.listLicenses(page = 0, size = 10, status = null, edition = null, search = null)

        assertEquals(10, page0.data.size)
        assertEquals(15, page0.total)
        assertEquals(2, page0.totalPages)
        assertEquals(0, page0.page)
        assertEquals(10, page0.size)
    }

    @Test
    fun `listLicenses page 2 returns remaining items`() = runTest {
        for (i in 1..15) {
            seedLicense("LK-PG2-$i", customerId = "cust-pg2-$i")
        }

        val service = AdminLicenseService()
        val page1 = service.listLicenses(page = 1, size = 10, status = null, edition = null, search = null)

        assertEquals(5, page1.data.size, "Second page should have remaining 5 items")
        assertEquals(15, page1.total)
    }

    @Test
    fun `listLicenses filters by status`() = runTest {
        seedLicense("LK-FSTAT-1", status = "ACTIVE")
        seedLicense("LK-FSTAT-2", status = "ACTIVE")
        seedLicense("LK-FSTAT-3", status = "REVOKED")

        val service = AdminLicenseService()
        val result = service.listLicenses(page = 0, size = 20, status = "ACTIVE", edition = null, search = null)

        assertEquals(2, result.total, "Should only return ACTIVE licenses")
        result.data.forEach { license ->
            // Note: computedStatus may differ from DB status (EXPIRING_SOON override)
            assertTrue(
                license.status in listOf("ACTIVE", "EXPIRING_SOON"),
                "Filtered licenses should have ACTIVE or EXPIRING_SOON computed status"
            )
        }
    }

    @Test
    fun `listLicenses filters by edition`() = runTest {
        seedLicense("LK-FED-1", edition = "STARTER")
        seedLicense("LK-FED-2", edition = "PROFESSIONAL")
        seedLicense("LK-FED-3", edition = "STARTER")

        val service = AdminLicenseService()
        val result = service.listLicenses(page = 0, size = 20, status = null, edition = "STARTER", search = null)

        assertEquals(2, result.total, "Should only return STARTER licenses")
    }

    @Test
    fun `listLicenses filters by search term in customerId`() = runTest {
        seedLicense("LK-SRCH-1", customerId = "acme-corp-001")
        seedLicense("LK-SRCH-2", customerId = "beta-inc-002")
        seedLicense("LK-SRCH-3", customerId = "acme-corp-003")

        val service = AdminLicenseService()
        val result = service.listLicenses(page = 0, size = 20, status = null, edition = null, search = "acme")

        assertEquals(2, result.total, "Search for 'acme' should match 2 licenses")
    }

    @Test
    fun `listLicenses search is case-insensitive`() = runTest {
        seedLicense("LK-CASE-1", customerId = "UPPER-CASE-CUST")

        val service = AdminLicenseService()
        val result = service.listLicenses(page = 0, size = 20, status = null, edition = null, search = "upper-case")

        assertEquals(1, result.total, "Search should be case-insensitive")
    }

    // ── Get License with Devices ────────────────────────────────────────────

    @Test
    fun `getLicense returns license with devices`() = runTest {
        val key = "LK-GETD-${UUID.randomUUID().toString().take(8)}"
        seedLicense(key, edition = "ENTERPRISE", maxDevices = 10)
        seedDevice(key, "device-a")
        seedDevice(key, "device-b")

        val service = AdminLicenseService()
        val result = service.getLicense(key)

        assertNotNull(result)
        assertEquals("ENTERPRISE", result!!.license.edition)
        assertEquals(2, result.devices.size)
        assertEquals(2, result.license.activeDevices)
    }

    @Test
    fun `getLicense returns null for unknown key`() = runTest {
        val service = AdminLicenseService()
        val result = service.getLicense("UNKNOWN-KEY")

        assertNull(result, "getLicense should return null for unknown key")
    }

    @Test
    fun `getLicense returns license with empty device list when no devices registered`() = runTest {
        val key = "LK-NODEV-${UUID.randomUUID().toString().take(8)}"
        seedLicense(key)

        val service = AdminLicenseService()
        val result = service.getLicense(key)

        assertNotNull(result)
        assertTrue(result!!.devices.isEmpty(), "Device list should be empty")
        assertEquals(0, result.license.activeDevices)
    }

    // ── Get Devices ─────────────────────────────────────────────────────────

    @Test
    fun `getDevices returns all devices for a license`() = runTest {
        val key = "LK-GDEV-${UUID.randomUUID().toString().take(8)}"
        seedLicense(key)
        seedDevice(key, "dev-1")
        seedDevice(key, "dev-2")
        seedDevice(key, "dev-3")

        val service = AdminLicenseService()
        val devices = service.getDevices(key)

        assertNotNull(devices)
        assertEquals(3, devices!!.size)
    }

    @Test
    fun `getDevices returns null for unknown license`() = runTest {
        val service = AdminLicenseService()
        val devices = service.getDevices("UNKNOWN-KEY")

        assertNull(devices, "getDevices should return null for unknown license")
    }

    @Test
    fun `getDevices returns empty list when license has no devices`() = runTest {
        val key = "LK-NDEV-${UUID.randomUUID().toString().take(8)}"
        seedLicense(key)

        val service = AdminLicenseService()
        val devices = service.getDevices(key)

        assertNotNull(devices)
        assertTrue(devices!!.isEmpty(), "Should return empty list, not null")
    }

    // ── Dynamic status computation in toAdminLicense ────────────────────────

    @Test
    fun `getLicense computes EXPIRING_SOON for ACTIVE license within 14 days`() = runTest {
        val key = "LK-DYNST-${UUID.randomUUID().toString().take(8)}"
        val in10Days = OffsetDateTime.now(ZoneOffset.UTC).plusDays(10)
        seedLicense(key, status = "ACTIVE", expiresAt = in10Days)

        val service = AdminLicenseService()
        val result = service.getLicense(key)

        assertNotNull(result)
        assertEquals(
            "EXPIRING_SOON",
            result!!.license.status,
            "Dynamic status should be EXPIRING_SOON within 14 days"
        )
    }

    @Test
    fun `getLicense computes EXPIRED for ACTIVE license past expiry`() = runTest {
        val key = "LK-DYNEX-${UUID.randomUUID().toString().take(8)}"
        val pastExpiry = OffsetDateTime.now(ZoneOffset.UTC).minusDays(5)
        seedLicense(key, status = "ACTIVE", expiresAt = pastExpiry)

        val service = AdminLicenseService()
        val result = service.getLicense(key)

        assertNotNull(result)
        assertEquals(
            "EXPIRED",
            result!!.license.status,
            "Dynamic status should be EXPIRED when past expiry"
        )
    }

    @Test
    fun `getLicense preserves REVOKED status regardless of expiry`() = runTest {
        val key = "LK-DYNRV-${UUID.randomUUID().toString().take(8)}"
        val futureExpiry = OffsetDateTime.now(ZoneOffset.UTC).plusDays(90)
        seedLicense(key, status = "REVOKED", expiresAt = futureExpiry)

        val service = AdminLicenseService()
        val result = service.getLicense(key)

        assertNotNull(result)
        assertEquals(
            "REVOKED",
            result!!.license.status,
            "REVOKED status should be preserved regardless of expiry"
        )
    }
}
