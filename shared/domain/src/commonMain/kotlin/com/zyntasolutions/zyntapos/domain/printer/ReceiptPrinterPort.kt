package com.zyntasolutions.zyntapos.domain.printer

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Order

/**
 * Output port that abstracts the physical receipt printing pipeline.
 *
 * Defined in `:shared:domain` to keep [PrintReceiptUseCase] free of HAL
 * and security dependencies (both of which depend on `:shared:domain`,
 * making direct imports circular). Implementations live in the infrastructure
 * layer — currently [com.zyntasolutions.zyntapos.feature.pos.printer.PrinterManagerReceiptAdapter]
 * in `:composeApp:feature:pos`.
 *
 * ### Adapter contract
 * Implementations are responsible for:
 * 1. Resolving the active [com.zyntasolutions.zyntapos.hal.printer.PrinterConfig]
 *    from [com.zyntasolutions.zyntapos.domain.repository.SettingsRepository].
 * 2. Building ESC/POS byte commands via
 *    [com.zyntasolutions.zyntapos.hal.printer.EscPosReceiptBuilder].
 * 3. Establishing a printer transport connection via
 *    [com.zyntasolutions.zyntapos.hal.printer.PrinterManager].
 * 4. Delivering the byte buffer to the printer.
 * 5. Appending a security audit entry for the print event.
 *
 * @see com.zyntasolutions.zyntapos.feature.pos.printer.PrinterManagerReceiptAdapter
 */
interface ReceiptPrinterPort {

    /**
     * Prints a receipt for the given completed [order].
     *
     * @param order     The fully processed [Order] (all fields populated, status = COMPLETED).
     * @param cashierId The authenticated cashier's user ID used for the audit log.
     * @return [Result.Success] when the receipt has been delivered to the printer hardware.
     *         [Result.Error] wrapping a [com.zyntasolutions.zyntapos.core.result.HalException]
     *         on connection or print failure.
     */
    suspend fun print(order: Order, cashierId: String): Result<Unit>

    /**
     * Sends a cash drawer kick pulse to open the connected cash drawer.
     *
     * @return [Result.Success] when the pulse is sent,
     *         [Result.Error] if not connected or unsupported.
     */
    suspend fun openCashDrawer(): Result<Unit>
}
