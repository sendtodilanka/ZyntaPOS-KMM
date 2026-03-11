package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.domain.model.License

/**
 * Repository contract for license activation and heartbeat operations.
 *
 * All methods communicate with the license service at license.zyntapos.com.
 * Results are cached locally so the app can display the last-known status
 * even while offline.
 */
interface LicenseRepository {

    /**
     * Activates the device against the license service.
     *
     * @param licenseKey The license key entered by the user (format XXXX-XXXX-XXXX-XXXX).
     * @param deviceId   Unique device identifier (Android ID or Desktop UUID).
     * @param deviceName Human-readable device name for the admin panel.
     * @param appVersion Current app version string.
     * @param osVersion  OS version string (optional).
     * @return [Result] wrapping the [License] on success or an exception on failure.
     */
    suspend fun activate(
        licenseKey: String,
        deviceId: String,
        deviceName: String?,
        appVersion: String,
        osVersion: String?,
    ): Result<License>

    /**
     * Sends a heartbeat to the license service to confirm the device is still active.
     *
     * @param licenseKey       The currently active license key.
     * @param deviceId         This device's unique identifier.
     * @param appVersion       Current app version.
     * @param dbSizeBytes      Current database file size in bytes.
     * @param syncQueueDepth   Number of pending sync operations.
     * @param lastErrorCount   Count of errors since last heartbeat.
     * @param uptimeHours      Hours since app was last launched.
     * @return [Result] wrapping the refreshed [License] on success.
     */
    suspend fun sendHeartbeat(
        licenseKey: String,
        deviceId: String,
        appVersion: String,
        dbSizeBytes: Long,
        syncQueueDepth: Int,
        lastErrorCount: Int,
        uptimeHours: Double,
    ): Result<License>

    /** Returns the locally-cached license, or null if the device has never been activated. */
    suspend fun getLocalLicense(): License?

    /** Clears locally-cached license data (called on factory reset / license revocation). */
    suspend fun clearLocalLicense()
}
