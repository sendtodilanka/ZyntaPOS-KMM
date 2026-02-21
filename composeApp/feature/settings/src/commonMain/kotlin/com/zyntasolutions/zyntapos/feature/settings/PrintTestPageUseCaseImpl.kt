package com.zyntasolutions.zyntapos.feature.settings

import com.zyntasolutions.zyntapos.domain.model.PrinterPaperWidth
import com.zyntasolutions.zyntapos.domain.usecase.settings.PrintTestPageUseCase
import com.zyntasolutions.zyntapos.hal.printer.EscPosReceiptBuilder
import com.zyntasolutions.zyntapos.hal.printer.PaperWidth
import com.zyntasolutions.zyntapos.hal.printer.PrinterConfig
import com.zyntasolutions.zyntapos.hal.printer.PrinterManager

/**
 * HAL-aware implementation of [PrintTestPageUseCase].
 *
 * ### Why this lives in `:composeApp:feature:settings` (NOT `:shared:domain`)
 * This class performs pure HAL orchestration: accept a [PrinterPaperWidth] domain
 * value, resolve the HAL [PaperWidth], construct a [PrinterConfig], generate
 * ESC/POS bytes, and forward them to the printer. Placing it in `:shared:domain`
 * would create an illegal dependency on `:shared:hal` (which depends on
 * `:shared:domain`), forming a cycle. The settings feature layer is the correct
 * home as it can legitimately import both `:shared:domain` and `:shared:hal`.
 *
 * **Layer contract:** callers above this class (ViewModels, other use cases) work
 * through the [PrintTestPageUseCase] interface and supply [PrinterPaperWidth].
 * The HAL types [PaperWidth], [PrinterConfig], [EscPosReceiptBuilder], and
 * [PrinterManager] are implementation details and must not leak past this class.
 *
 * The test page verifies:
 * - Paper width alignment (ruler line)
 * - Character encoding
 * - Print density
 *
 * @param printerManager HAL gateway for thermal printer I/O.
 */
class PrintTestPageUseCaseImpl(
    private val printerManager: PrinterManager,
) : PrintTestPageUseCase {

    /**
     * Builds and transmits a test page.
     *
     * @param paperWidth Active paper width expressed as a domain [PrinterPaperWidth].
     *   Resolved to the HAL [PaperWidth] internally.
     * @return [Result.success] when the job is queued or delivered;
     *         [Result.failure] with the underlying [Throwable] on transport error.
     */
    override suspend operator fun invoke(
        paperWidth: PrinterPaperWidth,
    ): Result<Unit> {
        val halWidth = when (paperWidth) {
            PrinterPaperWidth.MM_58 -> PaperWidth.MM_58
            PrinterPaperWidth.MM_80 -> PaperWidth.MM_80
        }
        val config = PrinterConfig(paperWidth = halWidth)
        val bytes = EscPosReceiptBuilder(config).buildTestPage()
        return printerManager.print(bytes)
    }
}
