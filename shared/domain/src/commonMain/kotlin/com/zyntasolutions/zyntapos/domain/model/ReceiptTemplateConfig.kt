package com.zyntasolutions.zyntapos.domain.model

/**
 * Configurable receipt template with section visibility toggles,
 * custom header/footer lines, and layout options.
 *
 * Used by the Receipt Template Visual Editor to let store owners
 * customize their printed receipt layout.
 */
data class ReceiptTemplateConfig(
    val showStoreName: Boolean = true,
    val showStoreAddress: Boolean = true,
    val showStorePhone: Boolean = true,
    val showOrderNumber: Boolean = true,
    val showDateTime: Boolean = true,
    val showCashierName: Boolean = true,
    val showItemizedList: Boolean = true,
    val showItemSku: Boolean = false,
    val showSubtotal: Boolean = true,
    val showTaxBreakdown: Boolean = true,
    val showDiscounts: Boolean = true,
    val showPaymentMethod: Boolean = true,
    val showChangeGiven: Boolean = true,
    val showQrCode: Boolean = false,
    val showBarcode: Boolean = false,
    val showLoyaltyPoints: Boolean = false,
    val showThankYouMessage: Boolean = true,
    val headerLines: List<String> = emptyList(),
    val footerLines: List<String> = listOf("Thank you for your purchase!"),
    val paperWidth: PaperWidth = PaperWidth.MM_80,
    val fontSize: ReceiptFontSize = ReceiptFontSize.NORMAL,
) {
    /** Ordered list of visible sections for preview rendering. */
    val visibleSections: List<ReceiptSection>
        get() = buildList {
            if (showStoreName || showStoreAddress || showStorePhone) add(ReceiptSection.STORE_INFO)
            if (headerLines.isNotEmpty()) add(ReceiptSection.CUSTOM_HEADER)
            if (showOrderNumber || showDateTime || showCashierName) add(ReceiptSection.ORDER_META)
            if (showItemizedList) add(ReceiptSection.LINE_ITEMS)
            if (showSubtotal || showTaxBreakdown || showDiscounts) add(ReceiptSection.TOTALS)
            if (showPaymentMethod || showChangeGiven) add(ReceiptSection.PAYMENT)
            if (showLoyaltyPoints) add(ReceiptSection.LOYALTY)
            if (showQrCode || showBarcode) add(ReceiptSection.CODES)
            if (showThankYouMessage || footerLines.isNotEmpty()) add(ReceiptSection.FOOTER)
        }
}

/** Named sections of a receipt for display and ordering. */
enum class ReceiptSection(val displayName: String) {
    STORE_INFO("Store Information"),
    CUSTOM_HEADER("Custom Header"),
    ORDER_META("Order Details"),
    LINE_ITEMS("Item List"),
    TOTALS("Totals & Tax"),
    PAYMENT("Payment Info"),
    LOYALTY("Loyalty Points"),
    CODES("QR/Barcode"),
    FOOTER("Footer"),
}

/** Paper width for receipt printers. */
enum class PaperWidth(val mm: Int, val charsPerLine: Int) {
    MM_58(58, 32),
    MM_80(80, 48),
}

/** Font size options. */
enum class ReceiptFontSize {
    SMALL,
    NORMAL,
    LARGE,
}
