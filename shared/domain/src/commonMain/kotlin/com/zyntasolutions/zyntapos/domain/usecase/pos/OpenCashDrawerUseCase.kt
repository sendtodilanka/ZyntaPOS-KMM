package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.printer.ReceiptPrinterPort

/**
 * Opens the connected cash drawer via the printer port.
 *
 * Most ESC/POS printers have a cash drawer kick port. This use case
 * delegates to [ReceiptPrinterPort.openCashDrawer] which sends the
 * ESC p command via the HAL printer pipeline.
 *
 * @param printerPort Infrastructure adapter for printer hardware.
 */
class OpenCashDrawerUseCase(
    private val printerPort: ReceiptPrinterPort,
) {
    suspend operator fun invoke(): Result<Unit> =
        printerPort.openCashDrawer()
}
