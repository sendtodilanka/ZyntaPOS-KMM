package com.zyntasolutions.zyntapos.hal.printer

import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.RegisterSession

/**
 * ZyntaPOS — Hardware Abstraction Layer
 *
 * [ReceiptBuilder] translates high-level POS domain objects into raw ESC/POS byte
 * arrays ready to be forwarded to [PrinterPort.print].
 *
 * Implementations handle paper-width formatting, character encoding, and ESC/POS
 * command assembly. The canonical implementation is
 * `com.zyntasolutions.zyntapos.hal.escpos.EscPosReceiptBuilder` (Step 4.2.10),
 * which supports 58 mm (32 chars/line) and 80 mm (48 chars/line) paper widths.
 *
 * All methods are **pure** — no I/O, no side effects. The returned [ByteArray] is
 * handed to [PrinterManager] which performs the actual transmission.
 */
interface ReceiptBuilder {

    /**
     * Renders a customer receipt for a completed [order].
     *
     * The output includes:
     * - Store header (centered, bold) from [config].headerLines
     * - Item rows: name / quantity / line-price columns
     * - Subtotal, tax breakdown, discount, grand total
     * - Payment method and change due
     * - Footer lines from [config].footerLines
     * - QR code ESC/POS command block if [PrinterConfig.showQrCode] is `true`
     * - Paper cut command (GS V)
     *
     * @param order  Fully-resolved [Order] with items, totals, and payment details.
     * @param config Printer layout configuration (paper width, character set, header/footer).
     * @return Raw ESC/POS byte array ready to be passed to [PrinterPort.print].
     */
    fun buildReceipt(order: Order, config: PrinterConfig): ByteArray

    /**
     * Renders a Z-report (end-of-shift summary) for the given [session].
     *
     * The report typically includes: opening float, total sales by category,
     * payment method breakdown, tax collected, refunds/voids, and closing totals.
     *
     * @param session The [RegisterSession] to summarise.
     * @return Raw ESC/POS byte array for the Z-report printout.
     */
    fun buildZReport(session: RegisterSession): ByteArray

    /**
     * Renders a self-test / alignment page to verify printer connectivity and
     * paper width calibration.
     *
     * The test page includes the store name, a ruler showing column positions,
     * each supported character set row, and a paper cut.
     *
     * @return Raw ESC/POS byte array for the test page.
     */
    fun buildTestPage(): ByteArray
}
