package com.zyntasolutions.zyntapos.feature.register.printer

import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import com.zyntasolutions.zyntapos.core.result.HalException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.RegisterSession
import com.zyntasolutions.zyntapos.domain.printer.ZReportPrinterPort
import com.zyntasolutions.zyntapos.hal.printer.EscPosReceiptBuilder
import com.zyntasolutions.zyntapos.hal.printer.PrinterConfig
import com.zyntasolutions.zyntapos.hal.printer.PrinterManager

/**
 * Infrastructure adapter that implements [ZReportPrinterPort] using the HAL printer pipeline.
 *
 * This class is the **sole owner** of all Z-report printing infrastructure concerns:
 * 1. Builds a default [PrinterConfig] (or resolves from settings if extended).
 * 2. Generates ESC/POS byte commands via [EscPosReceiptBuilder.buildZReport].
 * 3. Opens the printer transport via [PrinterManager.connect].
 * 4. Delivers the byte buffer via [PrinterManager.print].
 *
 * Lives in `:composeApp:feature:register` — the only module in the register feature
 * that can safely import from both `:shared:domain` and `:shared:hal` without creating
 * circular dependencies.
 *
 * ### Dependency note
 * `:shared:domain` defines [ZReportPrinterPort]; `:shared:hal` depends on `:shared:domain`.
 * Placing this adapter in `:composeApp:feature:register` (which depends on all three) resolves
 * the layering constraint.
 *
 * @param printerManager      HAL gateway for ESC/POS command delivery.
 * @param receiptBuilder      ESC/POS Z-report byte generator.
 */
class ZReportPrinterAdapter(
    private val printerManager: PrinterManager,
    private val receiptBuilder: EscPosReceiptBuilder,
) : ZReportPrinterPort {

    override suspend fun printZReport(session: RegisterSession): Result<Unit> {
        return try {
            // 1. Build ESC/POS byte commands (pure — no I/O)
            val bytes = receiptBuilder.buildZReport(session)

            // 2. Ensure printer transport is open
            val connectResult = printerManager.connect()
            if (connectResult.isFailure) {
                val msg = connectResult.exceptionOrNull()?.message
                ZyntaLogger.e(TAG, "Printer connect failed: $msg")
                return Result.Error(
                    HalException(
                        message = "Printer connection failed: $msg",
                        device = "thermal_printer",
                        cause = connectResult.exceptionOrNull(),
                    ),
                )
            }

            // 3. Deliver to printer (PrinterManager retries internally)
            printerManager.print(bytes).fold(
                onSuccess = { _ -> Result.Success(Unit) },
                onFailure = { error: Throwable ->
                    ZyntaLogger.e(TAG, "Z-report print failed: ${error.message}")
                    Result.Error(
                        HalException(
                            message = "Failed to print Z-report: ${error.message}",
                            device = "thermal_printer",
                            cause = error,
                        ),
                    )
                },
            )
        } catch (e: Exception) {
            ZyntaLogger.e(TAG, "Z-report generation threw: ${e.message}")
            Result.Error(
                HalException(
                    message = "Z-report generation failed: ${e.message}",
                    device = "thermal_printer",
                    cause = e,
                ),
            )
        }
    }

    companion object {
        private const val TAG = "ZReportPrinterAdapter"
    }
}
