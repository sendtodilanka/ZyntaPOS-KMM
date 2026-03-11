package com.zyntasolutions.zyntapos.domain.usecase.license

import com.zyntasolutions.zyntapos.domain.model.License
import com.zyntasolutions.zyntapos.domain.repository.LicenseRepository

/**
 * Activates this device against the license service.
 *
 * Called once after the user enters their license key.
 * On success the license is persisted locally and the app proceeds to the main screen.
 */
class ActivateLicenseUseCase(
    private val licenseRepository: LicenseRepository,
) {
    suspend operator fun invoke(
        licenseKey: String,
        deviceId: String,
        deviceName: String?,
        appVersion: String,
        osVersion: String?,
    ): Result<License> = licenseRepository.activate(
        licenseKey = licenseKey,
        deviceId = deviceId,
        deviceName = deviceName,
        appVersion = appVersion,
        osVersion = osVersion,
    )
}
