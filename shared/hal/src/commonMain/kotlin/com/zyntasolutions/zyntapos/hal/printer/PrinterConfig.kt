package com.zyntasolutions.zyntapos.hal.printer

/**
 * ZentaPOS — Hardware Abstraction Layer
 *
 * Thermal printer paper roll widths supported by ZentaPOS.
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
 * @property paperWidth      Physical roll width; governs characters-per-line calculation.
 * @property printDensity    ESC/POS print density in the range **0–8** (0 = lightest,
 *                           8 = darkest). Sent via the `ESC ( E` command where supported.
 * @property characterSet    Code page used for non-ASCII characters (ESC t command).
 * @property headerLines     Up to 5 centred lines printed at the top of every receipt
 *                           (e.g., store name, address, phone number).
 * @property footerLines     Lines printed below the totals section
 *                           (e.g., "Thank you!", "No refunds after 7 days").
 * @property showLogo        When `true` implementations that support NV logo storage
 *                           will emit the `FS p` logo print command.
 * @property showQrCode      When `true` a QR code containing the order reference is
 *                           appended to every receipt via the `GS ( k` command.
 */
data class PrinterConfig(
    val paperWidth: PaperWidth = PaperWidth.MM_80,
    val printDensity: Int = 4,
    val characterSet: CharacterSet = CharacterSet.PC437,
    val headerLines: List<String> = emptyList(),
    val footerLines: List<String> = emptyList(),
    val showLogo: Boolean = false,
    val showQrCode: Boolean = true,
) {
    init {
        require(printDensity in 0..8) {
            "printDensity must be in range 0–8, got $printDensity"
        }
    }

    companion object {
        /** Sensible out-of-box defaults for an 80 mm printer with no header/footer. */
        val DEFAULT = PrinterConfig()
    }
}
