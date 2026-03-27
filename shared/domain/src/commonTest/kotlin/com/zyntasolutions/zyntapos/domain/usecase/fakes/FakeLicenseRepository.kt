package com.zyntasolutions.zyntapos.domain.usecase.fakes

import com.zyntasolutions.zyntapos.domain.model.Edition
import com.zyntasolutions.zyntapos.domain.model.License
import com.zyntasolutions.zyntapos.domain.model.LicenseStatus
import com.zyntasolutions.zyntapos.domain.repository.LicenseRepository
import kotlin.time.Clock

/**
 * In-memory fake for [LicenseRepository].
 *
 * Controls [shouldFailActivation] and [shouldFailHeartbeat] to simulate network failures.
 * Stores the last-activated license in [storedLicense].
 */
class FakeLicenseRepository : LicenseRepository {

    var shouldFailActivation: Boolean = false
    var shouldFailHeartbeat: Boolean = false
    var storedLicense: License? = null

    var activateCalled: Boolean = false
    var sendHeartbeatCalled: Boolean = false
    var clearLocalLicenseCalled: Boolean = false

    var lastActivatedKey: String? = null
    var lastActivatedDeviceId: String? = null
    var lastHeartbeatKey: String? = null

    override suspend fun activate(
        licenseKey: String,
        deviceId: String,
        deviceName: String?,
        appVersion: String,
        osVersion: String?,
    ): Result<License> {
        activateCalled = true
        lastActivatedKey = licenseKey
        lastActivatedDeviceId = deviceId
        if (shouldFailActivation) {
            return Result.failure(RuntimeException("Activation failed: invalid license key"))
        }
        val license = buildLicense(
            key = licenseKey,
            deviceId = deviceId,
            status = LicenseStatus.ACTIVE,
        )
        storedLicense = license
        return Result.success(license)
    }

    override suspend fun sendHeartbeat(
        licenseKey: String,
        deviceId: String,
        appVersion: String,
        dbSizeBytes: Long,
        syncQueueDepth: Int,
        lastErrorCount: Int,
        uptimeHours: Double,
    ): Result<License> {
        sendHeartbeatCalled = true
        lastHeartbeatKey = licenseKey
        if (shouldFailHeartbeat) {
            return Result.failure(RuntimeException("Heartbeat failed: device revoked"))
        }
        val refreshed = storedLicense?.copy(
            lastHeartbeatAt = Clock.System.now(),
        ) ?: buildLicense(key = licenseKey, deviceId = deviceId, status = LicenseStatus.ACTIVE)
        storedLicense = refreshed
        return Result.success(refreshed)
    }

    override suspend fun getLocalLicense(): License? = storedLicense

    override suspend fun clearLocalLicense() {
        clearLocalLicenseCalled = true
        storedLicense = null
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildLicense(
        key: String,
        deviceId: String,
        status: LicenseStatus,
    ): License = License(
        key = key,
        deviceId = deviceId,
        customerId = "customer-test-001",
        edition = Edition.PROFESSIONAL,
        status = status,
        issuedAt = Clock.System.now(),
        expiresAt = null,
        gracePeriodEndsAt = null,
        lastHeartbeatAt = null,
        maxDevices = 5,
    )
}
