package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.LabelPrintItem
import com.zyntasolutions.zyntapos.domain.model.LabelTemplate
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.printer.LabelPrinterPort

/**
 * Prints a single test label for a [Product] using the given [LabelTemplate].
 *
 * Useful for verifying label layout and printer connectivity before printing
 * a full batch. Always prints exactly 1 copy regardless of the batch quantity.
 *
 * The [LabelPrinterPort] implementation decides whether to route to a
 * ZPL / TSPL printer or fall back to the system PDF dialog.
 *
 * @param labelPrinterPort Domain output port for label printing.
 */
class PrintTestLabelUseCase(
    private val labelPrinterPort: LabelPrinterPort,
) {

    /**
     * Sends a single test label to the active label printer.
     *
     * @param product  Source product data for the label.
     * @param template Layout template to use.
     * @return [Result.Success] on delivery; [Result.Error] on validation or print failure.
     */
    suspend fun execute(product: Product, template: LabelTemplate): Result<Unit> {
        val barcode = product.barcode
        if (barcode.isNullOrBlank()) {
            return Result.Error(
                ValidationException(
                    "Product '${product.name}' has no barcode — cannot print label.",
                    field = "barcode",
                    rule = "BARCODE_BLANK",
                )
            )
        }

        val item = LabelPrintItem(
            productId = product.id,
            productName = product.name,
            barcode = barcode,
            price = product.price,
            copies = 1,
        )
        return labelPrinterPort.printLabels(listOf(item), template)
    }
}
