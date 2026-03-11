package com.zyntasolutions.zyntapos.domain.usecase.license

import com.zyntasolutions.zyntapos.domain.model.License
import com.zyntasolutions.zyntapos.domain.repository.LicenseRepository

/**
 * Returns the locally-cached license without hitting the network.
 *
 * Used by the navigation layer to determine whether to show the
 * LicenseActivation screen or route directly to the main graph.
 */
class GetLicenseStatusUseCase(
    private val licenseRepository: LicenseRepository,
) {
    suspend operator fun invoke(): License? = licenseRepository.getLocalLicense()
}
