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
 * 3. Classifies the barcode by prefix and dispatches the appropriate intent:
 *    - `RCP-` → [PosIntent.ScanReceiptBarcode] (receipt lookup → refund flow)
 *    - `LC-`  → [PosIntent.ScanLoyaltyCard]   (customer auto-attach)
 *    - `CPN-` → [PosIntent.ScanCoupon]         (coupon auto-apply)
 *    - `GC-`  → [PosIntent.ScanGiftCard]        (gift card balance lookup)
 *    - else   → [PosIntent.ScanBarcode]          (product lookup → add to cart)
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
 *     barcodeScanner = barcodeScanner,
 *     scannerActive  = state.scannerActive,
 *     onIntent       = { intent -> viewModel.dispatch(intent) },
 * )
 * ```
 *
 * @param barcodeScanner  HAL interface providing [BarcodeScanner.scanEvents].
 * @param scannerActive   Whether scanner mode is currently enabled ([PosState.scannerActive]).
 * @param onIntent        Dispatches the resolved [PosIntent] to [PosViewModel].
 */
@Composable
fun BarcodeInputHandler(
    barcodeScanner: BarcodeScanner,
    scannerActive: Boolean,
    onIntent: (PosIntent) -> Unit,
) {
    LaunchedEffect(scannerActive) {
        if (scannerActive) {
            try {
                barcodeScanner.startListening()
                barcodeScanner.scanEvents.collect { result ->
                    // Only forward successful barcode decodes; errors are transient and
                    // do not terminate the flow (per BarcodeScanner HAL contract).
                    if (result is ScanResult.Barcode) {
                        onIntent(classifyBarcode(result.value))
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

/**
 * Classifies a raw barcode string by its prefix and returns the appropriate [PosIntent].
 *
 * | Prefix | Intent |
 * |--------|--------|
 * | `RCP-` | [PosIntent.ScanReceiptBarcode] |
 * | `LC-`  | [PosIntent.ScanLoyaltyCard] |
 * | `CPN-` | [PosIntent.ScanCoupon] |
 * | `GC-`  | [PosIntent.ScanGiftCard] |
 * | other  | [PosIntent.ScanBarcode] (product lookup) |
 */
fun classifyBarcode(barcode: String): PosIntent = when {
    barcode.startsWith("RCP-") -> PosIntent.ScanReceiptBarcode(barcode)
    barcode.startsWith("LC-")  -> PosIntent.ScanLoyaltyCard(barcode)
    barcode.startsWith("CPN-") -> PosIntent.ScanCoupon(barcode.removePrefix("CPN-"))
    barcode.startsWith("GC-")  -> PosIntent.ScanGiftCard(barcode)
    else                       -> PosIntent.ScanBarcode(barcode)
}
