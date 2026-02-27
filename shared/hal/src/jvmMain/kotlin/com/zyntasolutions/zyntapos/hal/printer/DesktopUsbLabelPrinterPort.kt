package com.zyntasolutions.zyntapos.hal.printer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ZyntaPOS — HAL | Desktop (JVM) Label Printer Implementation
 *
 * [DesktopUsbLabelPrinterPort] provides USB direct-print capability for desktop
 * label printers (Zebra ZD series, TSC TTP series in native USB mode).
 *
 * ### MVP status — Stub implementation
 * For the MVP release the USB path is implemented as a functional stub that:
 * - Reports [isConnected] = `false` until a real USB handle is acquired.
 * - Returns [Result.failure] with a descriptive [UnsupportedOperationException] for
 *   all print operations.
 *
 * ### Full implementation path (Phase 2)
 * Zebra and TSC label printers expose a **Vendor-class USB** interface with a
 * bulk-OUT endpoint accepting raw ZPL/TSPL bytes. The full implementation will:
 * 1. Enumerate USB devices via libusb4j (`LibUsb.getDeviceList()`).
 * 2. Match the printer by Vendor ID + Product ID.
 * 3. Claim the bulk-OUT interface.
 * 4. Write ZPL/TSPL byte arrays to the endpoint.
 * 5. Release interface and close context on [disconnect].
 *
 * @param vendorId   USB Vendor ID of the target label printer.
 * @param productId  USB Product ID of the target label printer.
 */
class DesktopUsbLabelPrinterPort(
    private val vendorId: Int = ZEBRA_VENDOR_ID,
    private val productId: Int = 0x0000,
) : LabelPrinterPort {

    @Volatile private var connected: Boolean = false

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // TODO Phase 2: enumerate USB bus, match vendorId/productId, claim interface
            connected = false
            throw UnsupportedOperationException(
                "DesktopUsbLabelPrinterPort: USB direct-print not yet implemented (MVP stub). " +
                    "Configure a TCP or Serial label printer port instead."
            )
        }
    }

    override suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { connected = false }
    }

    override suspend fun isConnected(): Boolean = connected

    override suspend fun printZpl(commands: ByteArray): Result<Unit> =
        Result.failure(notImplemented("printZpl"))

    override suspend fun printTspl(commands: ByteArray): Result<Unit> =
        Result.failure(notImplemented("printTspl"))

    private fun notImplemented(op: String): UnsupportedOperationException =
        UnsupportedOperationException(
            "DesktopUsbLabelPrinterPort.$op: USB label printing is an MVP stub. " +
                "Use DesktopTcpLabelPrinterPort or DesktopSerialLabelPrinterPort."
        )

    companion object {
        /** Zebra Technologies USB Vendor ID — covers ZD410, ZD420, ZD620, ZT series. */
        const val ZEBRA_VENDOR_ID = 0x0A5F

        /** TSC Auto ID Technology USB Vendor ID. */
        const val TSC_VENDOR_ID = 0x1203
    }
}
