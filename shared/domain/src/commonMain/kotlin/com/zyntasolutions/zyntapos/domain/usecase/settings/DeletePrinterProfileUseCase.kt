package com.zyntasolutions.zyntapos.domain.usecase.settings

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.repository.PrinterProfileRepository

/**
 * Deletes a [com.zyntasolutions.zyntapos.domain.model.PrinterProfile] by its UUID.
 *
 * @param repository Persistence contract for printer profiles.
 */
class DeletePrinterProfileUseCase(
    private val repository: PrinterProfileRepository,
) {

    /**
     * Deletes the profile identified by [profileId].
     *
     * @param profileId UUID of the profile to remove.
     * @return [Result.Success] when deleted; [Result.Error] if not found.
     */
    suspend operator fun invoke(profileId: String): Result<Unit> = repository.delete(profileId)
}
