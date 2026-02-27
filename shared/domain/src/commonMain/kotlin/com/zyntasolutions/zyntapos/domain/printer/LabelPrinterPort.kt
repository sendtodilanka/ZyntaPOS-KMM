package com.zyntasolutions.zyntapos.domain.printer

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.LabelPrintItem
import com.zyntasolutions.zyntapos.domain.model.LabelTemplate

/**
 * Domain output port for direct label printer communication (ZPL / TSPL).
 *
 * Defined in `:shared:domain` to keep label printing use cases independent of
 * the HAL layer. Implementations live in the infrastructure layer:
 * - `:composeApp:feature:inventory` — `LabelPrinterAdapter`
 *   (routes to `LabelPrinterManager` or PDF fallback based on config)
 *
 * ### Command language
 * The caller selects the appropriate byte sequence:
 * - ZPL → [printZpl] (use [com.zyntasolutions.zyntapos.hal.printer.ZplLabelBuilder])
 * - TSPL → [printTspl] (use [com.zyntasolutions.zyntapos.hal.printer.TsplLabelBuilder])
 */
interface LabelPrinterPort {

    /**
     * Transmits a ZPL command byte array to the label printer.
     *
     * @param commands Raw ZPL bytes produced by `ZplLabelBuilder.buildLabel()`.
     * @return [Result.Success] on delivery; [Result.Error] on transport failure.
     */
    suspend fun printZpl(commands: ByteArray): Result<Unit>

    /**
     * Transmits a TSPL command byte array to the label printer.
     *
     * @param commands Raw TSPL bytes produced by `TsplLabelBuilder.buildLabel()`.
     * @return [Result.Success] on delivery; [Result.Error] on transport failure.
     */
    suspend fun printTspl(commands: ByteArray): Result<Unit>

    /**
     * High-level label-print entry point for use by domain use cases.
     *
     * The implementation ([com.zyntasolutions.zyntapos.feature.inventory.label.LabelPrinterAdapter])
     * selects the appropriate output channel based on the active
     * [com.zyntasolutions.zyntapos.domain.model.LabelPrinterConfig]:
     * - `ZPL_*`     → builds ZPL via `ZplLabelBuilder` and calls [printZpl]
     * - `TSPL_*`    → builds TSPL via `TsplLabelBuilder` and calls [printTspl]
     * - `PDF_SYSTEM` / `NONE` → triggers the OS PDF/print dialog fallback
     *
     * @param items    Items to print; each item carries its own copy count.
     * @param template Label layout template (dimensions, fields to show, etc.).
     * @return [Result.Success] when all labels have been delivered;
     *         [Result.Error] on configuration, build, or transport failure.
     */
    suspend fun printLabels(items: List<LabelPrintItem>, template: LabelTemplate): Result<Unit>
}
