package com.zyntasolutions.zyntapos.hal.printer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ZyntaPOS — HAL | Desktop (JVM) Implementation
 *
 * [DesktopUsbPrinterPort] provides USB direct-print capability for desktop
 * thermal receipt printers (e.g. Epson TM-T88, Star TSP100 in native USB mode).
 *
 * ### MVP status — Stub implementation
 * For the MVP release the USB path is implemented as a functional stub that:
 * - Reports [isConnected] = `false` until a real USB handle is acquired.
 * - Returns [Result.failure] with a descriptive [UnsupportedOperationException] for
 *   all print operations.
 * - Logs a clear "USB printer not yet initialised" message so QA can differentiate
 *   a stub no-op from an actual transport error.
 *
 * ### Full implementation path (Phase 2)
 * USB thermal printers expose a **HID / Vendor-class** interface with a bulk-OUT
 * endpoint that accepts raw ESC/POS byte arrays.  The full implementation will:
 * 1. Enumerate USB devices via `javax.usb` (`UsbHostManager.getUsbServices()`) or
 *    **libusb4j** (`LibUsbContext.init()` + `LibUsb.getDeviceList()`).
 * 2. Match the printer by Vendor ID + Product ID (configured in [PrinterConfig]).
 * 3. Claim the bulk-OUT interface and open a pipe.
 * 4. Write ESC/POS byte arrays to the pipe in chunks ≤ `wMaxPacketSize` (typically 64 B).
 * 5. Release the interface and close context on [disconnect].
 *
 * ### Activation trigger
 * At startup [PrinterManager] calls `DesktopUsbPrinterPort.detectAndConnect()`.
 * If a matching USB device is found the port is promoted to active; otherwise the
 * system falls back to [DesktopTcpPrinterPort] or [DesktopSerialPrinterPort].
 *
 * @param vendorId   USB Vendor ID of the target printer (e.g. `0x04B8` for Epson).
 * @param productId  USB Product ID of the target printer.
 */
class DesktopUsbPrinterPort(
    private val _vendorId: Int = EPSON_VENDOR_ID,
    private val _productId: Int = 0x0000,
) : PrinterPort {

    // ──────────────────────────────────────────────────────────────────────────
    // State
    // ──────────────────────────────────────────────────────────────────────────

    /** Whether a real USB handle has been acquired. Always false in MVP stub. */
    @Volatile private var connected: Boolean = false

    // ──────────────────────────────────────────────────────────────────────────
    // PrinterPort implementation
    // ──────────────────────────────────────────────────────────────────────────

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // TODO Phase 2: enumerate USB bus, match vendorId/productId, claim interface
            connected = false
            throw UnsupportedOperationException(
                "DesktopUsbPrinterPort: USB direct-print not yet implemented (MVP stub). " +
                    "Configure a TCP or Serial printer port instead."
            )
        }
    }

    override suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // TODO Phase 2: release USB interface + close libusb context
            connected = false
        }
    }

    override suspend fun isConnected(): Boolean = connected

    override suspend fun print(commands: ByteArray): Result<Unit> =
        Result.failure(notImplemented("print"))

    override suspend fun openCashDrawer(): Result<Unit> =
        Result.failure(notImplemented("openCashDrawer"))

    override suspend fun cutPaper(): Result<Unit> =
        Result.failure(notImplemented("cutPaper"))

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun notImplemented(op: String): UnsupportedOperationException =
        UnsupportedOperationException(
            "DesktopUsbPrinterPort.$op: USB direct-print is an MVP stub. " +
                "Use DesktopTcpPrinterPort or DesktopSerialPrinterPort."
        )

    /**
     * Attempts to detect a connected USB printer matching [vendorId]/[productId] and
     * connect to it.  Returns [Result.success] with `true` if a device was found and
     * claimed, `false` if the bus was enumerated but no matching device was present.
     *
     * Called once at startup by [PrinterManager].
     *
     * @return `Result.success(true)` — device found and connected.
     *         `Result.success(false)` — bus enumerated, no matching device.
     *         `Result.failure` — USB subsystem unavailable.
     */
    suspend fun detectAndConnect(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            // TODO Phase 2: real USB enumeration
            false
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Constants
    // ──────────────────────────────────────────────────────────────────────────

    companion object {
        /** Epson USB Vendor ID — covers TM-T20, TM-T88, TM-m30 families. */
        const val EPSON_VENDOR_ID = 0x04B8

        /** Star Micronics USB Vendor ID — covers TSP100, TSP650 families. */
        const val STAR_VENDOR_ID = 0x0519

        /** BIXOLON USB Vendor ID — covers SRP-350, SRP-500 families. */
        const val BIXOLON_VENDOR_ID = 0x1504
    }
}
