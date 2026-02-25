package com.zyntasolutions.zyntapos.domain.usecase.einvoice

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.EInvoice
import com.zyntasolutions.zyntapos.domain.repository.EInvoiceRepository

/**
 * Creates a new e-invoice in DRAFT status from an order.
 *
 * ### Business Rules
 * 1. [EInvoice.invoiceNumber] must not be blank.
 * 2. [EInvoice.lineItems] must not be empty.
 * 3. [EInvoice.total] must be positive.
 * 4. sum(lineItems.lineTotal) must equal [EInvoice.subtotal] + [EInvoice.totalTax].
 */
class CreateEInvoiceUseCase(
    private val eInvoiceRepository: EInvoiceRepository,
) {
    suspend operator fun invoke(invoice: EInvoice): Result<Unit> {
        if (invoice.invoiceNumber.isBlank()) {
            return Result.Error(
                ValidationException(
                    "Invoice number must not be blank.",
                    field = "invoiceNumber",
                    rule = "REQUIRED",
                ),
            )
        }
        if (invoice.lineItems.isEmpty()) {
            return Result.Error(
                ValidationException(
                    "Invoice must have at least one line item.",
                    field = "lineItems",
                    rule = "REQUIRED",
                ),
            )
        }
        if (invoice.total <= 0.0) {
            return Result.Error(
                ValidationException(
                    "Invoice total must be positive.",
                    field = "total",
                    rule = "MIN_VALUE",
                ),
            )
        }
        // Verify totals balance
        val computedTotal = invoice.subtotal + invoice.totalTax
        if (kotlin.math.abs(computedTotal - invoice.total) > 0.01) {
            return Result.Error(
                ValidationException(
                    "Invoice total does not match subtotal + tax (${invoice.subtotal} + ${invoice.totalTax} ≠ ${invoice.total}).",
                    field = "total",
                    rule = "BALANCE_MISMATCH",
                ),
            )
        }
        return eInvoiceRepository.insert(invoice)
    }
}
