package com.zyntasolutions.zyntapos.feature.inventory.label

/** One-shot side effects emitted by [BarcodeLabelPrintViewModel]. */
sealed interface BarcodeLabelPrintEffect {
    /** Navigate back (no print action taken). */
    data object NavigateBack : BarcodeLabelPrintEffect

    /**
     * Deliver rendered PDF bytes to the platform layer.
     *
     * - Desktop: open OS print dialog or save-file dialog.
     * - Android: share via FileProvider + Intent.ACTION_SEND.
     *
     * @param pdfBytes Complete PDF binary.
     * @param fileName Suggested filename (without path), e.g. "labels_2026-02-27.pdf".
     */
    data class OpenPrintDialog(
        val pdfBytes: ByteArray,
        val fileName: String,
    ) : BarcodeLabelPrintEffect

    data class ShowError(val msg: String) : BarcodeLabelPrintEffect
    data class ShowSuccess(val msg: String) : BarcodeLabelPrintEffect
}
