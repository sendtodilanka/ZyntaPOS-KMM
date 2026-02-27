package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.LabelPrintItem
import com.zyntasolutions.zyntapos.domain.model.LabelTemplate
import com.zyntasolutions.zyntapos.domain.printer.LabelPrinterPort

/**
 * Sends a batch of barcode labels to the label printer.
 *
 * Routes to the [LabelPrinterPort] implementation which decides the output
 * channel: ZPL direct (Zebra), TSPL direct (TSC / Argox), or system PDF dialog
 * (fallback when no hardware printer is configured).
 *
 * **Validation rules:**
 * - [items] must not be empty.
 * - Each [LabelPrintItem.copies] must be ≥ 1 (enforced by the model constructor).
 *
 * @param labelPrinterPort Domain output port for label printing.
 */
class PrintLabelBatchUseCase(
    private val labelPrinterPort: LabelPrinterPort,
) {

    /**
     * Prints all [items] using the given [template].
     *
     * @param items    List of items to print. Each item specifies its own copy count.
     * @param template Label layout template.
     * @return [Result.Success] when all labels have been delivered;
     *         [Result.Error] with [ValidationException] if [items] is empty,
     *         or [com.zyntasolutions.zyntapos.core.result.HalException] on hardware failure.
     */
    suspend fun execute(items: List<LabelPrintItem>, template: LabelTemplate): Result<Unit> {
        if (items.isEmpty()) {
            return Result.Error(
                ValidationException("Label print batch must not be empty.", rule = "EMPTY_BATCH")
            )
        }
        return labelPrinterPort.printLabels(items, template)
    }
}
