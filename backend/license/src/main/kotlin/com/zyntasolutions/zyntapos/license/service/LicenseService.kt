package com.zyntasolutions.zyntapos.license.service

import com.zyntasolutions.zyntapos.license.models.ActivateRequest
import com.zyntasolutions.zyntapos.license.models.ActivateResponse
import com.zyntasolutions.zyntapos.license.models.HeartbeatRequest
import com.zyntasolutions.zyntapos.license.models.HeartbeatResponse
import com.zyntasolutions.zyntapos.license.models.LicenseStatusResponse
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

class LicenseService {
    private val logger = LoggerFactory.getLogger(LicenseService::class.java)

    /**
     * Activate a device under a license key.
     * Returns null if the license key doesn't exist.
     * Returns ActivateResponse with isValid=false if the license is suspended/expired/device limit reached.
     */
    suspend fun activate(request: ActivateRequest): ActivateResponse? {
        logger.info("Activate: key=${request.licenseKey.take(8)}… device=${request.deviceId}")
        // TODO: Query licenses table in PostgreSQL
        // 1. Check license exists → return null if not
        // 2. Check license status → return invalid if SUSPENDED/REVOKED
        // 3. Check device count <= maxDevices
        // 4. Upsert device_registrations row
        // 5. Return activation response
        return ActivateResponse(
            isValid = true,
            licenseKey = request.licenseKey,
            edition = "STARTER",
            maxDevices = 1,
            activeDevices = 1,
            expiresAt = null,
            message = "License activated (stub — DB not yet connected)"
        )
    }

    /**
     * Record a heartbeat from a POS terminal.
     * Returns null if the license key or device is not registered.
     */
    suspend fun heartbeat(request: HeartbeatRequest): HeartbeatResponse? {
        logger.info("Heartbeat: key=${request.licenseKey.take(8)}… device=${request.deviceId} queueDepth=${request.syncQueueDepth}")
        // TODO: Update device_registrations.last_seen_at and licenses.last_heartbeat_at
        val now = System.currentTimeMillis()
        return HeartbeatResponse(
            status = "ACTIVE",
            licenseKey = request.licenseKey,
            expiresAt = null,
            daysUntilExpiry = null,
            serverTimestamp = now
        )
    }

    /**
     * Get current license status. Returns null if key not found.
     */
    suspend fun getStatus(key: String): LicenseStatusResponse? {
        logger.info("Status: key=${key.take(8)}…")
        // TODO: Query licenses + device_registrations
        return null
    }
}
