package com.zyntasolutions.zyntapos.hal

import kotlinx.coroutines.flow.Flow

/**
 * Hardware Abstraction Layer contract for barcode scanner devices.
 *
 * Implementations are provided per-platform:
 * - **Android** (`androidMain`): USB/Bluetooth HID scanner via input events.
 * - **Desktop/JVM** (`jvmMain`): Serial port (RS-232/USB-CDC) or keyboard-wedge HID scanner.
 *
 * The interface is injected into [PosViewModel] via Koin. Platform actuals
 * are bound in [PosModule] using `expect/actual` or interface+factory pattern.
 *
 * ### Scan event contract
 * Each emission of [scanEvents] carries a raw barcode string (EAN-13, Code128,
 * QR, DataMatrix, etc.) decoded by the scanner firmware. The caller is responsible
 * for looking up the corresponding product via [ProductRepository.getByBarcode].
 */
interface BarcodeScanner {

    /**
     * A cold [Flow] of scanned barcode strings.
     *
     * - Each emission represents one completed scan event from the hardware scanner.
     * - The flow is inactive until [startScanning] is called.
     * - Collecting this flow after calling [stopScanning] yields no further emissions.
     *
     * Collect inside a `LaunchedEffect` tied to `scannerActive` state:
     * ```kotlin
     * LaunchedEffect(scannerActive) {
     *     if (scannerActive) {
     *         barcodeScanner.startScanning()
     *         barcodeScanner.scanEvents.collect { barcode ->
     *             viewModel.dispatch(PosIntent.ScanBarcode(barcode))
     *         }
     *     } else {
     *         barcodeScanner.stopScanning()
     *     }
     * }
     * ```
     */
    val scanEvents: Flow<String>

    /**
     * Activates the scanner hardware or begins listening for scanner input events.
     * Must be called before [scanEvents] emits. Safe to call multiple times.
     */
    suspend fun startScanning()

    /**
     * Deactivates the scanner hardware or stops listening for scanner input events.
     * After this call [scanEvents] will not emit until [startScanning] is called again.
     */
    suspend fun stopScanning()

    /**
     * Returns `true` if the underlying scanner hardware is connected and operational.
     * Used to show a warning banner in the POS UI when no scanner is detected.
     */
    suspend fun isConnected(): Boolean
}
