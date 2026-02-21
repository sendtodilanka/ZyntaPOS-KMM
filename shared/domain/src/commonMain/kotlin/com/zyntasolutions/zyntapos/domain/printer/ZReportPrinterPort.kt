package com.zyntasolutions.zyntapos.domain.printer

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.RegisterSession

/**
 * Output port that abstracts Z-report printing from the domain layer.
 *
 * Defined in `:shared:domain` so that [com.zyntasolutions.zyntapos.domain.usecase.register.PrintZReportUseCase]
 * remains free of HAL dependencies (`:shared:hal` depends *on* `:shared:domain`, so a direct
 * import in the reverse direction would create a circular dependency).
 *
 * ### Adapter contract
 * Implementations are responsible for:
 * 1. Resolving the active [com.zyntasolutions.zyntapos.hal.printer.PrinterConfig].
 * 2. Building ESC/POS byte commands via [com.zyntasolutions.zyntapos.hal.printer.EscPosReceiptBuilder.buildZReport].
 * 3. Connecting the printer transport via [com.zyntasolutions.zyntapos.hal.printer.PrinterManager.connect].
 * 4. Delivering the byte buffer via [com.zyntasolutions.zyntapos.hal.printer.PrinterManager.print].
 *
 * The canonical adapter is
 * [com.zyntasolutions.zyntapos.feature.register.printer.ZReportPrinterAdapter]
 * in `:composeApp:feature:register`.
 *
 * @see com.zyntasolutions.zyntapos.feature.register.printer.ZReportPrinterAdapter
 */
interface ZReportPrinterPort {

    /**
     * Generates ESC/POS bytes for [session] and delivers them to the thermal printer.
     *
     * @param session The **closed** [RegisterSession] whose Z-report totals will be printed.
     * @return [Result.Success] when the print job is accepted or queued.
     *         [Result.Error] wrapping a [com.zyntasolutions.zyntapos.core.result.HalException]
     *         when all delivery retries are exhausted.
     */
    suspend fun printZReport(session: RegisterSession): Result<Unit>
}
