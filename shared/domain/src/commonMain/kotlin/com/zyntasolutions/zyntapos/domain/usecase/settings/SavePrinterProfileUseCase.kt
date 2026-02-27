package com.zyntasolutions.zyntapos.domain.usecase.settings

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.PrinterProfile
import com.zyntasolutions.zyntapos.domain.repository.PrinterProfileRepository

/**
 * Validates and persists a [PrinterProfile] (create or update).
 *
 * **Validation rules:**
 * - [PrinterProfile.name] must not be blank.
 * - [PrinterProfile.printerType] must not be blank.
 * - [PrinterProfile.paperWidthMm] must be > 0.
 *
 * @param repository Persistence contract for printer profiles.
 */
class SavePrinterProfileUseCase(
    private val repository: PrinterProfileRepository,
) {

    /**
     * Saves [profile] after validation.
     *
     * @return [Result.Success] on success; [Result.Error] with [ValidationException]
     *         if any rule is violated.
     */
    suspend operator fun invoke(profile: PrinterProfile): Result<Unit> {
        if (profile.name.isBlank()) {
            return Result.Error(
                ValidationException("Profile name must not be blank.", field = "name", rule = "NAME_BLANK")
            )
        }
        if (profile.printerType.isBlank()) {
            return Result.Error(
                ValidationException(
                    "Printer type must not be blank.",
                    field = "printerType",
                    rule = "PRINTER_TYPE_BLANK",
                )
            )
        }
        if (profile.paperWidthMm <= 0) {
            return Result.Error(
                ValidationException(
                    "Paper width must be greater than 0.",
                    field = "paperWidthMm",
                    rule = "PAPER_WIDTH_INVALID",
                )
            )
        }
        return repository.save(profile)
    }
}
