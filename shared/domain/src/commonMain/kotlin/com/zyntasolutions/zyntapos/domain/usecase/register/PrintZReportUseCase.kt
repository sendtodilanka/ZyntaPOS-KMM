package com.zyntasolutions.zyntapos.domain.usecase.register

import com.zyntasolutions.zyntapos.core.result.HalException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.RegisterSession
import com.zyntasolutions.zyntapos.hal.printer.EscPosReceiptBuilder
import com.zyntasolutions.zyntapos.hal.printer.PrinterManager

/**
 * Prints the Z-report (end-of-shift summary) to the connected thermal printer.
 *
 * ### Workflow
 * 1. Receives a fully-closed [RegisterSession] containing opening balance,
 *    expected balance, actual balance, and timestamps.
 * 2. Delegates to [EscPosReceiptBuilder.buildZReport] to assemble ESC/POS bytes.
 * 3. Forwards the byte payload to [PrinterManager.print] which handles
 *    connection, retry, and queue management.
 *
 * ### Error Handling
 * - If the printer is disconnected, [PrinterManager] queues the job and
 *   returns [Result.Success] (job will drain on reconnect).
 * - If all retries are exhausted, [Result.Error] is returned with a
 *   [HalException] wrapping the underlying transport error.
 * - The caller (ViewModel) translates failures into user-visible snackbar errors.
 *
 * @param receiptBuilder ESC/POS Z-report formatter.
 * @param printerManager Printer connection and delivery gateway.
 */
class PrintZReportUseCase(
    private val receiptBuilder: EscPosReceiptBuilder,
    private val printerManager: PrinterManager,
) {
    /**
     * Generates and prints the Z-report for the given [session].
     *
     * @param session The CLOSED [RegisterSession] to print.
     * @return [Result.Success] when the print job is accepted (or queued),
     *         [Result.Error] if all print retries are exhausted.
     */
    suspend operator fun invoke(session: RegisterSession): Result<Unit> {
        return try {
            val bytes = receiptBuilder.buildZReport(session)
            val printResult = printerManager.print(bytes)
            printResult.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { error ->
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
            Result.Error(
                HalException(
                    message = "Z-report generation failed: ${e.message}",
                    device = "thermal_printer",
                    cause = e,
                ),
            )
        }
    }
}
