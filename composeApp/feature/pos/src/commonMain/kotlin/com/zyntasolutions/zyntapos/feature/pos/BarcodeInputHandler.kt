package com.zyntasolutions.zyntapos.feature.pos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.zyntasolutions.zyntapos.hal.scanner.BarcodeScanner
import com.zyntasolutions.zyntapos.hal.scanner.ScanResult

/**
 * Side-effect composable that bridges the HAL [BarcodeScanner] to [PosViewModel] (Sprint 14, task 9.1.5).
 *
 * ### Responsibility
 * When [scannerActive] is `true`:
 * 1. Calls [BarcodeScanner.startScanning] on the HAL layer to activate the hardware/listener.
 * 2. Collects [BarcodeScanner.scanEvents] — each emission is a raw barcode string from the scanner.
 * 3. Dispatches [PosIntent.ScanBarcode] to the ViewModel, which resolves the product and either
 *    auto-adds it to the cart (unique match) or emits [PosEffect.BarcodeNotFound].
 *
 * When [scannerActive] is `false`:
 * 1. Calls [BarcodeScanner.stopScanning] to deactivate the hardware/listener.
 *
 * ### Lifecycle safety
 * - Uses `LaunchedEffect(scannerActive)` so the coroutine is restarted whenever scan mode is toggled,
 *   and cancelled when the composable leaves the composition (no memory leak).
 * - The scanner is always stopped in the `finally` block of the `LaunchedEffect` lambda.
 *
 * ### Usage
 * Place this composable at the root of `PosScreen`, alongside other side-effect composables:
 * ```kotlin
 * BarcodeInputHandler(
 *     barcodeScanner = barcodeScanner,        // injected via Koin
 *     scannerActive  = state.scannerActive,
 *     onBarcodeScan  = { barcode -> viewModel.dispatch(PosIntent.ScanBarcode(barcode)) },
 * )
 * ```
 *
 * @param barcodeScanner  HAL interface providing [BarcodeScanner.scanEvents].
 * @param scannerActive   Whether scanner mode is currently enabled ([PosState.scannerActive]).
 * @param onBarcodeScan   Called with each raw barcode string; dispatch to [PosViewModel] here.
 */
@Composable
fun BarcodeInputHandler(
    barcodeScanner: BarcodeScanner,
    scannerActive: Boolean,
    onBarcodeScan: (String) -> Unit,
) {
    LaunchedEffect(scannerActive) {
        if (scannerActive) {
            try {
                barcodeScanner.startListening()
                barcodeScanner.scanEvents.collect { result ->
                    // Only forward successful barcode decodes; errors are transient and
                    // do not terminate the flow (per BarcodeScanner HAL contract).
                    if (result is ScanResult.Barcode) {
                        onBarcodeScan(result.value)
                    }
                }
            } finally {
                // Ensure scanner is stopped if the composable leaves composition
                // or scannerActive is toggled to false (LaunchedEffect cancels the coroutine).
                barcodeScanner.stopListening()
            }
        } else {
            barcodeScanner.stopListening()
        }
    }
}
