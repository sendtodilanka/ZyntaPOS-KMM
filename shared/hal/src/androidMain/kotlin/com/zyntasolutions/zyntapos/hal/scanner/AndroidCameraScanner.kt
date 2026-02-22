package com.zyntasolutions.zyntapos.hal.scanner

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * ZyntaPOS — Hardware Abstraction Layer · Android Camera Barcode Scanner
 *
 * Implements [BarcodeScanner] using the **CameraX ImageAnalysis** pipeline backed
 * by **ML Kit Barcode Scanning** for real-time barcode / QR code detection.
 *
 * ### Architecture
 * ```
 * CameraX (ProcessCameraProvider)
 *   └─ ImageAnalysis (STRATEGY_KEEP_ONLY_LATEST, single analysis executor)
 *        └─ MlKitBarcodeAnalyzer
 *             └─ InputImage → ML Kit BarcodeScanner → MutableSharedFlow<ScanResult>
 * ```
 *
 * ### Lifecycle contract
 * The [lifecycleOwner] passed to [startListening] must outlive the scanning session.
 * Typically this is a Fragment or Activity. When the lifecycle owner is destroyed,
 * CameraX automatically releases the camera session; [stopListening] releases the
 * analysis executor and resets internal state.
 *
 * ### Deduplication
 * The last decoded barcode value is cached; consecutive identical scans within
 * [DEDUP_WINDOW_MS] are suppressed to avoid flooding the cart with duplicates from
 * a partially-triggered camera trigger.
 *
 * ### Permissions
 * `CAMERA` permission must be granted before [startListening] is called. The caller
 * (ViewModel / Composable) is responsible for the runtime permission request.
 *
 * @param context        Application context used to obtain [ProcessCameraProvider].
 * @param lifecycleOwner Lifecycle that controls the CameraX use-case binding.
 * @param cameraSelector Which camera to use — defaults to [CameraSelector.DEFAULT_BACK_CAMERA].
 */
class AndroidCameraScanner(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
) : BarcodeScanner {

    private val log = ZyntaLogger.forModule("AndroidCameraScanner")

    private val _scanEvents = MutableSharedFlow<ScanResult>(
        replay = 0,
        extraBufferCapacity = SCAN_BUFFER_CAPACITY,
    )
    override val scanEvents: Flow<ScanResult> = _scanEvents.asSharedFlow()

    /** Single-thread executor dedicated to ML Kit image analysis off the main thread. */
    private var analysisExecutor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null

    /** For deduplication: last successfully decoded barcode value + timestamp. */
    private var lastBarcode: String? = null
    private var lastBarcodeTime: Long = 0L

    // ── BarcodeScanner impl ─────────────────────────────────────────────────────

    override suspend fun startListening(): Result<Unit> = runCatching {
        if (analysisExecutor != null) {
            log.d("startListening() called while already listening — no-op")
            return@runCatching
        }

        val executor = Executors.newSingleThreadExecutor()
        analysisExecutor = executor

        val provider = getCameraProvider()
        cameraProvider = provider

        // Build ML Kit scanner — accept all major retail symbologies
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_PDF417,
                Barcode.FORMAT_DATA_MATRIX,
            )
            .build()

        val mlKitScanner = BarcodeScanning.getClient(options)

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply {
                setAnalyzer(executor, MlKitBarcodeAnalyzer(mlKitScanner))
            }

        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis)

        log.i("Camera scanner started — ML Kit ImageAnalysis bound to lifecycle")
    }

    override suspend fun stopListening() {
        cameraProvider?.unbindAll()
        analysisExecutor?.shutdown()
        analysisExecutor = null
        cameraProvider = null
        lastBarcode = null
        lastBarcodeTime = 0L
        log.i("Camera scanner stopped")
    }

    // ── Private — CameraX provider ──────────────────────────────────────────────

    private suspend fun getCameraProvider(): ProcessCameraProvider =
        suspendCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    runCatching { cont.resume(future.get()) }
                        .onFailure { cont.resumeWithException(it) }
                },
                ContextCompat.getMainExecutor(context),
            )
        }

    // ── Inner analyser ──────────────────────────────────────────────────────────

    /**
     * CameraX [ImageAnalysis.Analyzer] that routes each camera frame through ML Kit
     * and emits [ScanResult] events into [_scanEvents].
     *
     * The `@ExperimentalGetImage` annotation is required by CameraX 1.3+ for
     * [ImageProxy.image] access; this is stable API in practice.
     */
    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private inner class MlKitBarcodeAnalyzer(
        private val scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    ) : ImageAnalysis.Analyzer {

        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                return
            }

            val inputImage = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees,
            )

            scanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    barcodes.firstOrNull()?.let { barcode ->
                        val raw = barcode.rawValue ?: return@let
                        val now = System.currentTimeMillis()

                        // Deduplication: suppress identical consecutive scans
                        if (raw == lastBarcode && (now - lastBarcodeTime) < DEDUP_WINDOW_MS) {
                            return@let
                        }
                        lastBarcode = raw
                        lastBarcodeTime = now

                        val format = barcode.format.toZyntaFormat()
                        _scanEvents.tryEmit(ScanResult.Barcode(value = raw, format = format))
                        log.d("Barcode scanned: $raw (format=$format)")
                    }
                }
                .addOnFailureListener { ex ->
                    _scanEvents.tryEmit(ScanResult.Error(ex.message ?: "ML Kit scan error"))
                    log.e("ML Kit barcode scanning error", throwable = ex)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }

    // ── Constants ────────────────────────────────────────────────────────────────

    companion object {
        private const val SCAN_BUFFER_CAPACITY = 8
        private const val DEDUP_WINDOW_MS = 1_500L
    }
}

// ── Extension: ML Kit → ZyntaPOS BarcodeFormat ─────────────────────────────────

/**
 * Maps an ML Kit [Barcode.FORMAT_*] integer constant to a [BarcodeFormat] enum value
 * understood by ZyntaPOS shared business logic.
 */
private fun Int.toZyntaFormat(): BarcodeFormat = when (this) {
    Barcode.FORMAT_EAN_13      -> BarcodeFormat.EAN_13
    Barcode.FORMAT_EAN_8       -> BarcodeFormat.EAN_8
    Barcode.FORMAT_UPC_A       -> BarcodeFormat.UPC_A
    Barcode.FORMAT_UPC_E       -> BarcodeFormat.UPC_E
    Barcode.FORMAT_CODE_128    -> BarcodeFormat.CODE_128
    Barcode.FORMAT_CODE_39     -> BarcodeFormat.CODE_39
    Barcode.FORMAT_QR_CODE     -> BarcodeFormat.QR_CODE
    Barcode.FORMAT_PDF417      -> BarcodeFormat.PDF_417
    Barcode.FORMAT_DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
    else                       -> BarcodeFormat.UNKNOWN
}
