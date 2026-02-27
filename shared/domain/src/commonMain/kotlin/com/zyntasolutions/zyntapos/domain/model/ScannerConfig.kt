package com.zyntasolutions.zyntapos.domain.model

/**
 * Configuration for the barcode scanner at a given location/terminal.
 *
 * Controls input filtering, sound feedback, and enable/disable state.
 * Stored per-terminal in [SettingsRepository] and surfaced in Settings → Scanner.
 *
 * @property minBarcodeLength     Barcodes shorter than this value are ignored.
 *                                Default 3 prevents accidental single-key scans.
 * @property prefixToStrip        Prefix string that the scanner hardware prepends;
 *                                automatically stripped from raw scan input.
 * @property suffixToStrip        Suffix string that the scanner hardware appends
 *                                (e.g. carriage return); stripped before processing.
 * @property soundFeedbackEnabled Whether the application plays an audio beep on
 *                                successful scan. Default `true`.
 * @property isEnabledAtLocation  Whether scanner input is processed at this terminal.
 *                                Can be toggled to disable scanning temporarily.
 */
data class ScannerConfig(
    val minBarcodeLength: Int = 3,
    val prefixToStrip: String = "",
    val suffixToStrip: String = "",
    val soundFeedbackEnabled: Boolean = true,
    val isEnabledAtLocation: Boolean = true,
) {
    companion object {
        val DEFAULT = ScannerConfig()
    }
}
