package com.zyntasolutions.zyntapos.domain.usecase.settings

import com.zyntasolutions.zyntapos.domain.model.PrinterPaperWidth

/**
 * Sends a built-in ESC/POS self-test page to the currently configured printer.
 *
 * Defined as a `fun interface` so that callers (ViewModels, tests) depend on an
 * abstraction rather than a concrete HAL-aware implementation.  The concrete
 * implementation ([com.zyntasolutions.zyntapos.feature.settings.PrintTestPageUseCaseImpl])
 * lives in `:composeApp:feature:settings`, which is the only module permitted to
 * import both `:shared:domain` and `:shared:hal` together.
 *
 * **Layer contract:** the interface accepts the domain type [PrinterPaperWidth].
 * Any HAL-to-domain mapping is the implementation's responsibility and must never
 * be performed by ViewModels or other domain-layer callers.
 */
fun interface PrintTestPageUseCase {
    /**
     * Builds and transmits a self-test page to the connected printer.
     *
     * @param paperWidth Active paper width expressed as a domain [PrinterPaperWidth].
     * @return [Result.success] when the job is queued or delivered;
     *         [Result.failure] wrapping the underlying [Throwable] on transport error.
     */
    suspend operator fun invoke(
        paperWidth: PrinterPaperWidth = PrinterPaperWidth.MM_80,
    ): Result<Unit>
}
