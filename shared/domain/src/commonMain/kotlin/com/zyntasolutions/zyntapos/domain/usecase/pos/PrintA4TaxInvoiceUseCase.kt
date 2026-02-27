package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.printer.A4InvoicePrinterPort
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository
import com.zyntasolutions.zyntapos.domain.usecase.auth.CheckPermissionUseCase

/**
 * Generates and delivers an A4 tax invoice PDF for a completed order.
 *
 * RBAC gated: the active user must hold [Permission.PRINT_INVOICE].
 *
 * @param orderRepository  Provides order lookup by ID.
 * @param printerPort      Infrastructure adapter for A4 PDF printing.
 * @param checkPermission  RBAC check use case.
 */
class PrintA4TaxInvoiceUseCase(
    private val orderRepository: OrderRepository,
    private val printerPort: A4InvoicePrinterPort,
    private val checkPermission: CheckPermissionUseCase,
) {

    /**
     * Prints the A4 invoice for [orderId].
     *
     * @param orderId  UUID of the completed order.
     * @param userId   Active user requesting the print; must have [Permission.PRINT_INVOICE].
     * @return [Result.Success] on delivery; [Result.Error] on permission, lookup, or print failure.
     */
    suspend fun execute(orderId: String, userId: String): Result<Unit> {
        if (!checkPermission(userId, Permission.PRINT_INVOICE)) {
            return Result.Error(
                com.zyntasolutions.zyntapos.core.result.AuthException(
                    "User $userId does not have PRINT_INVOICE permission",
                )
            )
        }

        return when (val orderResult = orderRepository.getById(orderId)) {
            is Result.Success -> printerPort.printA4Invoice(orderResult.data)
            is Result.Error   -> orderResult
            else              -> Result.Loading
        }
    }
}
