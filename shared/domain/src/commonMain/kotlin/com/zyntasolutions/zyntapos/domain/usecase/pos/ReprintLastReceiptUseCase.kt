package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.printer.ReceiptPrinterPort
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository

/**
 * Reprints the receipt for a previously completed order.
 *
 * Fetches the order from [OrderRepository] and delegates printing to
 * [ReceiptPrinterPort]. The caller must ensure the session user has a valid
 * cashier ID before invoking.
 *
 * @param orderRepository Provides order lookup by ID.
 * @param printerPort     Infrastructure adapter for receipt printing.
 */
class ReprintLastReceiptUseCase(
    private val orderRepository: OrderRepository,
    private val printerPort: ReceiptPrinterPort,
) {

    /**
     * Reprints the receipt for [orderId].
     *
     * @param orderId   UUID of the completed order to reprint.
     * @param cashierId Active cashier's user ID (used for audit trail).
     * @return [Result.Success] on successful delivery; [Result.Error] on failure.
     */
    suspend fun execute(orderId: String, cashierId: String): Result<Unit> {
        return when (val orderResult = orderRepository.getById(orderId)) {
            is Result.Success -> printerPort.print(orderResult.data, cashierId)
            is Result.Error   -> orderResult
            else              -> Result.Loading
        }
    }
}
