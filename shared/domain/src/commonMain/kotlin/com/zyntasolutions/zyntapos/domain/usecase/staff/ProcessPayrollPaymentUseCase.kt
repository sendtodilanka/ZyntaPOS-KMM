package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.repository.PayrollRepository

/**
 * Marks a payroll record as PAID after payment is confirmed.
 *
 * @param id Payroll record ID.
 * @param paidAt Epoch millis of the payment.
 * @param paymentRef External payment reference (bank transfer, cheque number, etc.).
 * @param updatedAt Epoch millis for the record update timestamp.
 */
class ProcessPayrollPaymentUseCase(
    private val payrollRepository: PayrollRepository,
) {
    suspend operator fun invoke(
        id: String,
        paidAt: Long,
        paymentRef: String? = null,
        updatedAt: Long,
    ): Result<Unit> =
        payrollRepository.markPaid(id, paidAt, paymentRef, updatedAt)
}
