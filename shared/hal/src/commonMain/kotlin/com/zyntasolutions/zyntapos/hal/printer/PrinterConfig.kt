package com.zyntasolutions.zyntapos.hal.printer

/**
 * ZyntaPOS — Hardware Abstraction Layer
 *
 * Thermal printer paper roll widths supported by ZyntaPOS.
 *
 * | Value      | Characters / line | Typical use           |
 * |------------|-------------------|-----------------------|
 * | [MM_58]    | 32                | Compact POS terminals |
 * | [MM_80]    | 48                | Standard retail POS   |
 */
enum class PaperWidth(
    /** Usable character columns at standard font size. */
    val charsPerLine: Int,
) {
    /** 58 mm paper — 32 characters per line at standard font. */
    MM_58(charsPerLine = 32),

    /** 80 mm paper — 48 characters per line at standard font. */
    MM_80(charsPerLine = 48),
}

/**
 * ESC/POS character-set codes sent via the `ESC t` command.
 *
 * Add additional code pages as required by deployment regions.
 */
enum class CharacterSet(
    /** Byte value transmitted in the `ESC t n` command. */
    val code: Int,
) {
    /** PC437 — USA / Standard Latin (ESC/POS page 0). */
    PC437(code = 0),

    /** PC850 — Multilingual / Western European (ESC/POS page 2). */
    PC850(code = 2),

    /** PC860 — Portuguese (ESC/POS page 3). */
    PC860(code = 3),

    /** PC863 — Canadian-French (ESC/POS page 4). */
    PC863(code = 4),

    /** PC865 — Nordic (ESC/POS page 5). */
    PC865(code = 5),

    /** UTF-8 — where supported by firmware (non-standard, model-dependent). */
    UTF8(code = 255),
}

/**
 * Immutable configuration snapshot describing how the thermal printer should
 * render receipts, Z-reports, and test pages.
 *
 * A [PrinterConfig] instance is typically loaded from the settings data-source
 * and injected into [ReceiptBuilder] calls by [PrinterManager].
 *
 * @property paperWidth            Physical roll width; governs characters-per-line calculation.
 * @property printDensity          ESC/POS print density in the range **0–8** (0 = lightest,
 *                                 8 = darkest). Sent via the `ESC ( E` command where supported.
 * @property characterSet          Code page used for non-ASCII characters (ESC t command).
 * @property headerLines           Up to 5 centred lines printed at the top of every receipt
 *                                 (e.g., store name, address, phone number).
 * @property footerLines           Lines printed below the totals section
 *                                 (e.g., "Thank you!", "No refunds after 7 days").
 * @property showLogo              When `true` implementations that support NV logo storage
 *                                 will emit the `FS p` logo print command.
 * @property showQrCode            When `true` a QR code containing the order reference is
 *                                 appended to every receipt via the `GS ( k` command.
 * @property cashDrawerTrigger     Controls when the cash drawer kick pulse is emitted.
 *                                 Default: [CashDrawerTrigger.ALL_PAYMENTS].
 * @property showCashierName       When `true` the cashier's display name is printed on
 *                                 the receipt below the order timestamp.
 * @property showTaxDetail         When `true` a per-tax-group breakdown is printed in the
 *                                 totals block (e.g., "VAT 8%  : 120.00").
 * @property showReceiptBarcode    When `true` a Code 128 barcode of the order number is
 *                                 printed at the bottom of the receipt for scan-to-return.
 * @property rotatingFooterTexts   List of up to 5 promotional footer messages that cycle
 *                                 across receipts. When empty, [footerLines] is used.
 * @property footerRotationInterval How many receipts before advancing to the next rotating
 *                                 footer text (default 1 = change every receipt).
 * @property logoNvSlot            NV RAM slot index for the uploaded logo (null = not set).
 *                                 When non-null and [showLogo] is `true`, `FS p` is emitted.
 */
data class PrinterConfig(
    val paperWidth: PaperWidth = PaperWidth.MM_80,
    val printDensity: Int = 4,
    val characterSet: CharacterSet = CharacterSet.PC437,
    val headerLines: List<String> = emptyList(),
    val footerLines: List<String> = emptyList(),
    val showLogo: Boolean = false,
    val showQrCode: Boolean = true,
    val cashDrawerTrigger: CashDrawerTrigger = CashDrawerTrigger.ALL_PAYMENTS,
    val showCashierName: Boolean = false,
    val showTaxDetail: Boolean = false,
    val showReceiptBarcode: Boolean = false,
    val rotatingFooterTexts: List<String> = emptyList(),
    val footerRotationInterval: Int = 1,
    val logoNvSlot: Int? = null,
) {
    init {
        require(printDensity in 0..8) {
            "printDensity must be in range 0–8, got $printDensity"
        }
        require(footerRotationInterval >= 1) {
            "footerRotationInterval must be >= 1, got $footerRotationInterval"
        }
    }

    companion object {
        /** Sensible out-of-box defaults for an 80 mm printer with no header/footer. */
        val DEFAULT = PrinterConfig()
    }
}
