package com.zyntasolutions.zyntapos.domain.usecase.license

import com.zyntasolutions.zyntapos.domain.model.LicenseStatus
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeLicenseRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the three license use cases:
 * - [ActivateLicenseUseCase]
 * - [GetLicenseStatusUseCase]
 * - [SendHeartbeatUseCase]
 *
 * All tests use [FakeLicenseRepository] — no real network or database access.
 */
class LicenseUseCasesTest {

    // ─── ActivateLicenseUseCase ───────────────────────────────────────────────

    @Test
    fun `activate_succeeds_with_valid_key`() = runTest {
        val repo = FakeLicenseRepository()
        val useCase = ActivateLicenseUseCase(repo)

        val result = useCase(
            licenseKey = "ABCD-1234-EFGH-5678",
            deviceId = "device-001",
            deviceName = "ZyntaPOS Terminal 1",
            appVersion = "1.0.0",
            osVersion = "Android 14",
        )

        assertTrue(result.isSuccess, "Activation should succeed with valid key")
        val license = result.getOrNull()
        assertNotNull(license)
        assertEquals("ABCD-1234-EFGH-5678", license.key)
        assertEquals("device-001", license.deviceId)
        assertEquals(LicenseStatus.ACTIVE, license.status)
    }

    @Test
    fun `activate_delegates_key_to_repository`() = runTest {
        val repo = FakeLicenseRepository()
        val useCase = ActivateLicenseUseCase(repo)

        useCase(
            licenseKey = "TEST-KEY-0000-0001",
            deviceId = "dev-42",
            deviceName = null,
            appVersion = "1.0.0",
            osVersion = null,
        )

        assertTrue(repo.activateCalled)
        assertEquals("TEST-KEY-0000-0001", repo.lastActivatedKey)
        assertEquals("dev-42", repo.lastActivatedDeviceId)
    }

    @Test
    fun `activate_persists_license_locally`() = runTest {
        val repo = FakeLicenseRepository()
        val useCase = ActivateLicenseUseCase(repo)

        useCase(
            licenseKey = "ABCD-0000-0000-0001",
            deviceId = "device-001",
            deviceName = "Terminal 1",
            appVersion = "1.0.0",
            osVersion = null,
        )

        assertNotNull(repo.storedLicense, "License should be stored after successful activation")
    }

    @Test
    fun `activate_returns_failure_when_repository_fails`() = runTest {
        val repo = FakeLicenseRepository().apply { shouldFailActivation = true }
        val useCase = ActivateLicenseUseCase(repo)

        val result = useCase(
            licenseKey = "INVALID-KEY",
            deviceId = "device-001",
            deviceName = null,
            appVersion = "1.0.0",
            osVersion = null,
        )

        assertTrue(result.isFailure, "Activation should fail when repository fails")
        assertNull(repo.storedLicense, "No license should be stored on failure")
    }

    @Test
    fun `activate_accepts_null_optional_fields`() = runTest {
        val repo = FakeLicenseRepository()
        val useCase = ActivateLicenseUseCase(repo)

        val result = useCase(
            licenseKey = "NULL-OPTS-TEST-0001",
            deviceId = "device-002",
            deviceName = null,
            appVersion = "1.0.0",
            osVersion = null,
        )

        assertTrue(result.isSuccess)
    }

    // ─── GetLicenseStatusUseCase ──────────────────────────────────────────────

    @Test
    fun `getLicenseStatus_returns_null_when_never_activated`() = runTest {
        val repo = FakeLicenseRepository()
        val useCase = GetLicenseStatusUseCase(repo)

        val license = useCase()

        assertNull(license, "Should return null before any activation")
    }

    @Test
    fun `getLicenseStatus_returns_cached_license_after_activation`() = runTest {
        val repo = FakeLicenseRepository()
        val activateUseCase = ActivateLicenseUseCase(repo)
        val statusUseCase = GetLicenseStatusUseCase(repo)

        activateUseCase(
            licenseKey = "GET-STATUS-TEST-0001",
            deviceId = "dev-01",
            deviceName = "Terminal 1",
            appVersion = "1.0.0",
            osVersion = null,
        )

        val license = statusUseCase()
        assertNotNull(license)
        assertEquals("GET-STATUS-TEST-0001", license.key)
        assertEquals(LicenseStatus.ACTIVE, license.status)
    }

    @Test
    fun `getLicenseStatus_returns_null_after_clear`() = runTest {
        val repo = FakeLicenseRepository()
        val activateUseCase = ActivateLicenseUseCase(repo)
        val statusUseCase = GetLicenseStatusUseCase(repo)

        activateUseCase(
            licenseKey = "CLEAR-TEST-0001",
            deviceId = "dev-01",
            deviceName = null,
            appVersion = "1.0.0",
            osVersion = null,
        )
        repo.clearLocalLicense()

        val license = statusUseCase()
        assertNull(license, "Should return null after license is cleared")
        assertTrue(repo.clearLocalLicenseCalled)
    }

    // ─── SendHeartbeatUseCase ─────────────────────────────────────────────────

    @Test
    fun `sendHeartbeat_succeeds_and_refreshes_license`() = runTest {
        val repo = FakeLicenseRepository()
        val activateUseCase = ActivateLicenseUseCase(repo)
        val heartbeatUseCase = SendHeartbeatUseCase(repo)

        activateUseCase(
            licenseKey = "HEARTBEAT-KEY-0001",
            deviceId = "dev-01",
            deviceName = "Terminal 1",
            appVersion = "1.0.0",
            osVersion = null,
        )

        val result = heartbeatUseCase(
            licenseKey = "HEARTBEAT-KEY-0001",
            deviceId = "dev-01",
            appVersion = "1.0.0",
            dbSizeBytes = 1_048_576L,
            syncQueueDepth = 3,
            lastErrorCount = 0,
            uptimeHours = 8.5,
        )

        assertTrue(result.isSuccess, "Heartbeat should succeed")
        val refreshed = result.getOrNull()
        assertNotNull(refreshed)
        assertNotNull(refreshed.lastHeartbeatAt, "lastHeartbeatAt should be updated after heartbeat")
    }

    @Test
    fun `sendHeartbeat_delegates_all_params_to_repository`() = runTest {
        val repo = FakeLicenseRepository()
        val useCase = SendHeartbeatUseCase(repo)

        useCase(
            licenseKey = "HB-PARAMS-KEY-0001",
            deviceId = "dev-param-01",
            appVersion = "1.0.0",
            dbSizeBytes = 2_097_152L,
            syncQueueDepth = 7,
            lastErrorCount = 2,
            uptimeHours = 24.0,
        )

        assertTrue(repo.sendHeartbeatCalled)
        assertEquals("HB-PARAMS-KEY-0001", repo.lastHeartbeatKey)
    }

    @Test
    fun `sendHeartbeat_returns_failure_when_device_revoked`() = runTest {
        val repo = FakeLicenseRepository().apply { shouldFailHeartbeat = true }
        val useCase = SendHeartbeatUseCase(repo)

        val result = useCase(
            licenseKey = "REVOKED-KEY-0001",
            deviceId = "dev-revoked",
            appVersion = "1.0.0",
            dbSizeBytes = 0L,
            syncQueueDepth = 0,
            lastErrorCount = 0,
            uptimeHours = 0.0,
        )

        assertTrue(result.isFailure, "Heartbeat should propagate repository failure")
    }

    @Test
    fun `sendHeartbeat_works_without_prior_activation`() = runTest {
        // Edge case: heartbeat called before local license stored (e.g. after data clear)
        val repo = FakeLicenseRepository()
        val useCase = SendHeartbeatUseCase(repo)

        val result = useCase(
            licenseKey = "NO-LOCAL-KEY-0001",
            deviceId = "dev-fresh",
            appVersion = "1.0.0",
            dbSizeBytes = 0L,
            syncQueueDepth = 0,
            lastErrorCount = 0,
            uptimeHours = 0.0,
        )

        // FakeLicenseRepository builds a license even with no storedLicense
        assertTrue(result.isSuccess, "Heartbeat should succeed even without prior local license")
    }

    @Test
    fun `sendHeartbeat_updates_stored_license_after_success`() = runTest {
        val repo = FakeLicenseRepository()
        val activateUseCase = ActivateLicenseUseCase(repo)
        val heartbeatUseCase = SendHeartbeatUseCase(repo)

        activateUseCase(
            licenseKey = "UPDATE-STORED-KEY",
            deviceId = "dev-01",
            deviceName = null,
            appVersion = "1.0.0",
            osVersion = null,
        )
        val beforeHeartbeat = repo.storedLicense?.lastHeartbeatAt

        heartbeatUseCase(
            licenseKey = "UPDATE-STORED-KEY",
            deviceId = "dev-01",
            appVersion = "1.0.0",
            dbSizeBytes = 0L,
            syncQueueDepth = 0,
            lastErrorCount = 0,
            uptimeHours = 1.0,
        )

        val afterHeartbeat = repo.storedLicense?.lastHeartbeatAt
        assertNotNull(afterHeartbeat, "lastHeartbeatAt should be set after heartbeat")
        // Before activation, lastHeartbeatAt was null
        assertNull(beforeHeartbeat, "lastHeartbeatAt should be null before first heartbeat")
        assertFalse(afterHeartbeat == beforeHeartbeat, "lastHeartbeatAt should be updated")
    }
}
