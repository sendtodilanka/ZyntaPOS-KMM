package com.zyntasolutions.zyntapos.hal.scanner

/**
 * ZentaPOS — Hardware Abstraction Layer
 *
 * Symbologies (barcode formats) that ZentaPOS recognises from scanner hardware
 * or ML Kit.
 *
 * Implementations map platform-specific constants to these values so that
 * shared business logic (product lookup, coupon validation, etc.) can branch
 * on format without importing platform APIs.
 */
enum class BarcodeFormat {
    /** EAN-13 — standard retail product barcode (13 digits). */
    EAN_13,

    /** EAN-8 — short-form EAN for small packaging (8 digits). */
    EAN_8,

    /** UPC-A — North American product barcode (12 digits). */
    UPC_A,

    /** UPC-E — compressed UPC for small packaging (8 digits). */
    UPC_E,

    /** Code 128 — high-density alphanumeric barcode used on shipping labels & SKUs. */
    CODE_128,

    /** Code 39 — variable-length alphanumeric barcode. */
    CODE_39,

    /** QR Code — 2-D matrix code; used for order references, payment links, receipts. */
    QR_CODE,

    /** PDF-417 — 2-D stacked barcode on driving licences, boarding passes. */
    PDF_417,

    /** Data Matrix — compact 2-D code used in healthcare and electronics. */
    DATA_MATRIX,

    /** Any format not covered by the enumerated values above. */
    UNKNOWN,
}

/**
 * ZentaPOS — Hardware Abstraction Layer
 *
 * Discriminated union representing the outcome of a single scan attempt emitted
 * by [BarcodeScanner.scanEvents].
 *
 * Shared code pattern:
 * ```kotlin
 * scannerEvents.collect { result ->
 *     when (result) {
 *         is ScanResult.Barcode -> handleBarcode(result.value, result.format)
 *         is ScanResult.Error   -> showScanError(result.message)
 *     }
 * }
 * ```
 */
sealed class ScanResult {

    /**
     * A successful decode of a barcode symbol.
     *
     * @property value  The decoded string payload (e.g., `"5901234123457"` for EAN-13).
     * @property format The detected [BarcodeFormat] of the scanned symbol.
     */
    data class Barcode(
        val value: String,
        val format: BarcodeFormat,
    ) : ScanResult()

    /**
     * A scan attempt that failed due to a hardware or decoding error.
     *
     * This variant is emitted instead of throwing so that the hot [kotlinx.coroutines.flow.Flow]
     * in [BarcodeScanner.scanEvents] stays alive after transient errors (e.g., an
     * unrecognised symbology or a camera focus miss).
     *
     * @property message Human-readable description of the failure cause.
     */
    data class Error(
        val message: String,
    ) : ScanResult()
}
