package com.zyntasolutions.zyntapos.hal.scanner

import kotlinx.coroutines.flow.Flow

/**
 * ZentaPOS — Hardware Abstraction Layer
 *
 * [BarcodeScanner] is the platform-agnostic contract for any barcode/QR input
 * source — USB HID wedge, Bluetooth SPP scanner, camera-based ML Kit scanner,
 * or serial port reader.
 *
 * Implementations live in androidMain (camera + USB) and jvmMain (HID wedge +
 * serial). Shared business logic binds only to this interface.
 *
 * ### Lifecycle contract
 * 1. Call [startListening] to open the hardware channel and begin emitting events.
 * 2. Collect [scanEvents] — the cold [Flow] becomes active only after [startListening].
 * 3. Call [stopListening] when the composable / ViewModel is torn down to release
 *    the camera, USB handle, or serial port.
 *
 * [scanEvents] must **never** throw; hardware errors are wrapped in [ScanResult.Error].
 */
interface BarcodeScanner {

    /**
     * Hot stream of scan events emitted whenever the underlying hardware reads a
     * barcode or encounters an error.
     *
     * The flow replays **nothing** — subscribers only receive events emitted after
     * they start collecting. Backpressure is handled by [kotlinx.coroutines.flow.SharedFlow]
     * with a small replay buffer in concrete implementations to avoid missed scans
     * during rapid successive reads.
     */
    val scanEvents: Flow<ScanResult>

    /**
     * Opens the hardware scanning channel (camera preview, USB endpoint, serial port)
     * and begins forwarding events into [scanEvents].
     *
     * Must be called before collecting [scanEvents]. Calling [startListening] while
     * already listening must be idempotent.
     *
     * @return [Result.success] when the channel is open and ready,
     *         [Result.failure] if permissions are denied or hardware is unavailable.
     */
    suspend fun startListening(): Result<Unit>

    /**
     * Closes the hardware channel and stops emitting into [scanEvents].
     *
     * Safe to call even if [startListening] was never called or has already been stopped.
     * Implementations must release all resources (camera session, file descriptors, etc.)
     * in this call to prevent leaks.
     */
    suspend fun stopListening()
}
