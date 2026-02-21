package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.Supplier
import com.zyntasolutions.zyntapos.domain.repository.SupplierRepository

/**
 * Validates and persists a [Supplier] (insert or update).
 *
 * **Validation rules:**
 * - [Supplier.name] must not be blank.
 * - [Supplier.email], if provided, must contain exactly one `@` character
 *   (basic RFC-5322 surface check; deep validation deferred to UI layer).
 * - [Supplier.phone], if provided, must contain only digits, spaces,
 *   `+`, `(`, `)`, and `-`.
 *
 * @param supplierRepository Persistence layer for supplier data.
 */
class SaveSupplierUseCase(
    private val supplierRepository: SupplierRepository,
) {
    private val phoneRegex = Regex("""^[\d\s+\-()+]+$""")
    private val emailRegex = Regex("""^[^@]+@[^@]+$""")

    /**
     * Saves [supplier] after validating all business rules.
     *
     * @param isUpdate When `true`, [SupplierRepository.update] is called;
     *                 otherwise [SupplierRepository.insert] is used.
     * @return [Result.Success] on success.
     * @return [Result.Error] wrapping a [ValidationException] for rule violations.
     */
    suspend operator fun invoke(
        supplier: Supplier,
        isUpdate: Boolean,
    ): Result<Unit> {
        // ── Name ─────────────────────────────────────────────────────────────
        if (supplier.name.isBlank()) {
            return Result.Error(
                ValidationException(
                    message = "Supplier name is required.",
                    field = "name",
                    rule = "REQUIRED",
                )
            )
        }

        // ── Email format ──────────────────────────────────────────────────────
        if (!supplier.email.isNullOrBlank() && !emailRegex.matches(supplier.email)) {
            return Result.Error(
                ValidationException(
                    message = "Supplier email format is invalid.",
                    field = "email",
                    rule = "INVALID_FORMAT",
                )
            )
        }

        // ── Phone format ──────────────────────────────────────────────────────
        if (!supplier.phone.isNullOrBlank() && !phoneRegex.matches(supplier.phone)) {
            return Result.Error(
                ValidationException(
                    message = "Supplier phone contains invalid characters.",
                    field = "phone",
                    rule = "INVALID_FORMAT",
                )
            )
        }

        return if (isUpdate) {
            supplierRepository.update(supplier)
        } else {
            supplierRepository.insert(supplier)
        }
    }
}
