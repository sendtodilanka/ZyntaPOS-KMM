package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.printer.ReceiptPrinterPort

/**
 * Orchestrates thermal receipt printing for a completed [Order].
 *
 * This use case belongs in `:shared:domain` because it models a core POS business
 * operation ("print a receipt after checkout"). All infrastructure concerns — config
 * resolution, ESC/POS byte generation, transport connection, and audit logging — are
 * delegated to [ReceiptPrinterPort], keeping this class free of HAL and security
 * module dependencies (both of which are downstream layers that depend on `:shared:domain`).
 *
 * ### Layering rationale
 * ```
 * :composeApp:feature:pos  ← calls invoke()
 *        ↓
 * :shared:domain           ← PrintReceiptUseCase + ReceiptPrinterPort (this file)
 *        ↑
 * :shared:hal              ← PrinterManagerReceiptAdapter (implements ReceiptPrinterPort)
 * :shared:security         ← SecurityAuditLogger (used inside the adapter)
 * ```
 *
 * @param printerPort Infrastructure adapter responsible for the complete print
 *   pipeline (config → bytes → connect → deliver → audit).
 *
 * @see ReceiptPrinterPort
 * @see com.zyntasolutions.zyntapos.feature.pos.printer.PrinterManagerReceiptAdapter
 */
class PrintReceiptUseCase(
    private val printerPort: ReceiptPrinterPort,
) {

    /**
     * Prints the receipt for the given [order].
     *
     * @param order     The fully completed [Order] (all fields populated, status COMPLETED).
     * @param cashierId The authenticated cashier's user ID; forwarded to the audit trail.
     * @return [Result.Success] on successful delivery; [Result.Error] wrapping a
     *   [com.zyntasolutions.zyntapos.core.result.HalException] on printer failure.
     */
    suspend operator fun invoke(order: Order, cashierId: String): Result<Unit> =
        printerPort.print(order, cashierId)
}
