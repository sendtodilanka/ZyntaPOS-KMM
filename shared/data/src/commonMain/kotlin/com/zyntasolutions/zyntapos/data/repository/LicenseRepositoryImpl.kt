package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.data.remote.api.ApiService
import com.zyntasolutions.zyntapos.data.remote.dto.LicenseActivateRequestDto
import com.zyntasolutions.zyntapos.data.remote.dto.LicenseHeartbeatRequestDto
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.Edition
import com.zyntasolutions.zyntapos.domain.model.License
import com.zyntasolutions.zyntapos.domain.model.LicenseStatus
import com.zyntasolutions.zyntapos.domain.repository.LicenseRepository
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlinx.datetime.Instant

/**
 * Concrete implementation of [LicenseRepository].
 *
 * Communicates with the license service via [ApiService.activateLicense] and
 * [ApiService.licenseHeartbeat], then persists the result in the local
 * [ZyntaDatabase] (`license_state` table) for offline access.
 */
class LicenseRepositoryImpl(
    private val db: ZyntaDatabase,
    private val apiService: ApiService,
) : LicenseRepository {

    override suspend fun activate(
        licenseKey: String,
        deviceId: String,
        deviceName: String?,
        appVersion: String,
        osVersion: String?,
    ): Result<License> = withContext(Dispatchers.IO) {
        runCatching {
            val response = apiService.activateLicense(
                LicenseActivateRequestDto(
                    licenseKey = licenseKey,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    appVersion = appVersion,
                    osVersion = osVersion,
                )
            )

            if (!response.isValid) {
                throw IllegalStateException(response.message ?: response.errorCode ?: "Activation failed")
            }

            val gracePeriodEndsAt = response.expiresAt?.let {
                Instant.fromEpochMilliseconds(it + response.gracePeriodDays.toLong() * 24 * 3600 * 1000)
            }

            val license = License(
                key = licenseKey,
                deviceId = deviceId,
                customerId = "",   // Not returned by activate; populated from JWT claims at app layer
                edition = Edition.valueOf(response.edition),
                status = LicenseStatus.ACTIVE,
                issuedAt = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
                expiresAt = response.expiresAt?.let { Instant.fromEpochMilliseconds(it) },
                gracePeriodEndsAt = gracePeriodEndsAt,
                lastHeartbeatAt = null,
                maxDevices = response.maxDevices,
            )

            persistLicense(license)
            license
        }
    }

    override suspend fun sendHeartbeat(
        licenseKey: String,
        deviceId: String,
        appVersion: String,
        dbSizeBytes: Long,
        syncQueueDepth: Int,
        lastErrorCount: Int,
        uptimeHours: Double,
    ): Result<License> = withContext(Dispatchers.IO) {
        runCatching {
            val nonce = Uuid.random().toString()
            val clientTimestamp = Clock.System.now().toEpochMilliseconds()
            val response = apiService.licenseHeartbeat(
                LicenseHeartbeatRequestDto(
                    licenseKey = licenseKey,
                    deviceId = deviceId,
                    appVersion = appVersion,
                    dbSizeBytes = dbSizeBytes,
                    syncQueueDepth = syncQueueDepth,
                    lastErrorCount = lastErrorCount,
                    uptimeHours = uptimeHours,
                    nonce = nonce,
                    clientTimestamp = clientTimestamp,
                )
            )

            val existing = db.licenseQueries.getLicense().executeAsOneOrNull()

            val status = runCatching { LicenseStatus.valueOf(response.status) }
                .getOrDefault(LicenseStatus.ACTIVE)

            val gracePeriodEndsAt = response.expiresAt?.let {
                Instant.fromEpochMilliseconds(it + 7L * 24 * 3600 * 1000)
            }

            val license = License(
                key = licenseKey,
                deviceId = deviceId,
                customerId = existing?.customer_id ?: "",
                edition = existing?.edition?.let { runCatching { Edition.valueOf(it) }.getOrDefault(Edition.STARTER) }
                    ?: Edition.STARTER,
                status = status,
                issuedAt = existing?.issued_at?.let { Instant.fromEpochMilliseconds(it) }
                    ?: Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
                expiresAt = response.expiresAt?.let { Instant.fromEpochMilliseconds(it) },
                gracePeriodEndsAt = gracePeriodEndsAt,
                lastHeartbeatAt = Instant.fromEpochMilliseconds(response.serverTimestamp),
                maxDevices = existing?.max_devices?.toInt() ?: 1,
            )

            persistLicense(license)
            license
        }
    }

    override suspend fun getLocalLicense(): License? = withContext(Dispatchers.IO) {
        db.licenseQueries.getLicense().executeAsOneOrNull()?.let { row ->
            License(
                key = row.key,
                deviceId = row.device_id,
                customerId = row.customer_id,
                edition = runCatching { Edition.valueOf(row.edition) }.getOrDefault(Edition.STARTER),
                status = runCatching { LicenseStatus.valueOf(row.status) }.getOrDefault(LicenseStatus.UNACTIVATED),
                issuedAt = Instant.fromEpochMilliseconds(row.issued_at),
                expiresAt = row.expires_at?.let { Instant.fromEpochMilliseconds(it) },
                gracePeriodEndsAt = row.grace_period_ends_at?.let { Instant.fromEpochMilliseconds(it) },
                lastHeartbeatAt = row.last_heartbeat_at?.let { Instant.fromEpochMilliseconds(it) },
                maxDevices = row.max_devices.toInt(),
            )
        }
    }

    override suspend fun clearLocalLicense() = withContext(Dispatchers.IO) {
        db.licenseQueries.deleteLicense()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun persistLicense(license: License) {
        db.licenseQueries.upsertLicense(
            key = license.key,
            device_id = license.deviceId,
            customer_id = license.customerId,
            edition = license.edition.name,
            status = license.status.name,
            max_devices = license.maxDevices.toLong(),
            issued_at = license.issuedAt.toEpochMilliseconds(),
            expires_at = license.expiresAt?.toEpochMilliseconds(),
            grace_period_ends_at = license.gracePeriodEndsAt?.toEpochMilliseconds(),
            last_heartbeat_at = license.lastHeartbeatAt?.toEpochMilliseconds(),
        )
    }
}
