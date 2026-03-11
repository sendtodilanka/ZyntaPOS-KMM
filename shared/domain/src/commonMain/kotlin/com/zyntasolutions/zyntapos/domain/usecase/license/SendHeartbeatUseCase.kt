package com.zyntasolutions.zyntapos.domain.usecase.license

import com.zyntasolutions.zyntapos.domain.model.License
import com.zyntasolutions.zyntapos.domain.repository.LicenseRepository

/**
 * Sends a periodic heartbeat to keep the device registration active.
 *
 * Called every 6 hours by the platform-specific scheduler
 * (WorkManager on Android, coroutine loop on Desktop).
 * On success the local license cache is updated with the server's current status.
 */
class SendHeartbeatUseCase(
    private val licenseRepository: LicenseRepository,
) {
    suspend operator fun invoke(
        licenseKey: String,
        deviceId: String,
        appVersion: String,
        dbSizeBytes: Long = 0L,
        syncQueueDepth: Int = 0,
        lastErrorCount: Int = 0,
        uptimeHours: Double = 0.0,
    ): Result<License> = licenseRepository.sendHeartbeat(
        licenseKey = licenseKey,
        deviceId = deviceId,
        appVersion = appVersion,
        dbSizeBytes = dbSizeBytes,
        syncQueueDepth = syncQueueDepth,
        lastErrorCount = lastErrorCount,
        uptimeHours = uptimeHours,
    )
}
