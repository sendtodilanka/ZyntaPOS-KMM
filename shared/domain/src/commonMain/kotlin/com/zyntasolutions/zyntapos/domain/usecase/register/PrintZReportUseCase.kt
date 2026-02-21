package com.zyntasolutions.zyntapos.domain.usecase.register

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.RegisterSession
import com.zyntasolutions.zyntapos.domain.printer.ZReportPrinterPort

/**
 * Prints the Z-report (end-of-shift summary) to the connected thermal printer.
 *
 * ### Layering rationale
 * This use case belongs in `:shared:domain` because printing a Z-report is a core
 * register-management business operation. All ESC/POS byte generation, transport
 * connection, and retry logic are delegated to [ZReportPrinterPort], keeping this
 * class free of HAL and infrastructure dependencies.
 *
 * ```
 * :composeApp:feature:register   ← calls invoke()
 *        ↓
 * :shared:domain                 ← PrintZReportUseCase + ZReportPrinterPort (this file)
 *        ↑
 * :shared:hal                    ← ZReportPrinterAdapter (implements ZReportPrinterPort)
 * ```
 *
 * @param printerPort Adapter responsible for the complete Z-report print pipeline.
 *
 * @see ZReportPrinterPort
 * @see com.zyntasolutions.zyntapos.feature.register.printer.ZReportPrinterAdapter
 */
class PrintZReportUseCase(
    private val printerPort: ZReportPrinterPort,
) {
    /**
     * Generates and prints the Z-report for the given [session].
     *
     * @param session The **closed** [RegisterSession] to print.
     * @return [Result.Success] when the print job is accepted or queued.
     *         [Result.Error] wrapping a [com.zyntasolutions.zyntapos.core.result.HalException]
     *         if all retries are exhausted.
     */
    suspend operator fun invoke(session: RegisterSession): Result<Unit> =
        printerPort.printZReport(session)
}
