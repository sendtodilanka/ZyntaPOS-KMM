package com.zyntasolutions.zyntapos.hal.printer

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * ZyntaPOS — Hardware Abstraction Layer · Android USB Printer
 *
 * Implements [PrinterPort] using the Android USB Host API to communicate with a
 * USB-connected ESC/POS thermal receipt printer.
 *
 * ### USB topology assumed
 * - Printer exposes a **USB Printer Class** interface (class 0x07) or a generic
 *   Bulk-OUT endpoint on a vendor-specific interface.
 * - Bulk-OUT endpoint (direction = [UsbConstants.USB_DIR_OUT]) is used for all writes.
 * - Cash-drawer kick and paper-cut are ESC/POS byte sequences sent over the same endpoint.
 *
 * ### Permissions
 * USB permission must be granted by the caller **before** [connect] is invoked.
 * Use `UsbManager.requestPermission()` with a `PendingIntent`; this class does not
 * manage the runtime permission dialog to keep concerns separated.
 *
 * ### Thread safety
 * All public methods are `suspend` and protected by [mutex] so concurrent callers
 * never race on the [UsbDeviceConnection].
 *
 * @param context   Application or Activity context used to obtain [UsbManager].
 * @param device    The [UsbDevice] representing the printer to communicate with.
 * @param timeoutMs Write-transfer timeout passed to [UsbDeviceConnection.bulkTransfer].
 *                  Default 2 000 ms covers large receipt buffers on slow printers.
 */
class AndroidUsbPrinterPort(
    private val context: Context,
    private val device: UsbDevice,
    private val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
) : PrinterPort {

    private val log = Logger.withTag("AndroidUsbPrinterPort")

    /** Serialises all USB operations; prevents concurrent [bulkTransfer] calls. */
    private val mutex = Mutex()

    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var bulkOut: UsbEndpoint? = null

    // ── PrinterPort impl ────────────────────────────────────────────────────────

    override suspend fun connect(): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                if (connection != null) {
                    log.d { "connect() called while already connected — no-op" }
                    return@runCatching
                }

                val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

                if (!usbManager.hasPermission(device)) {
                    error("USB permission not granted for device ${device.deviceName}")
                }

                // Locate the first interface that has a Bulk-OUT endpoint.
                val (iface, endpoint) = findBulkOutEndpoint(device)
                    ?: error(
                        "No Bulk-OUT endpoint found on device ${device.deviceName} " +
                                "(vendorId=0x${device.vendorId.toString(16)}, " +
                                "productId=0x${device.productId.toString(16)})"
                    )

                val conn = usbManager.openDevice(device)
                    ?: error("Failed to open USB device ${device.deviceName}")

                if (!conn.claimInterface(iface, /*force=*/true)) {
                    conn.close()
                    error("Failed to claim USB interface ${iface.id} on ${device.deviceName}")
                }

                connection = conn
                usbInterface = iface
                bulkOut = endpoint

                log.i {
                    "USB printer connected: ${device.deviceName} " +
                            "interface=${iface.id} endpoint=0x${endpoint.address.toString(16)}"
                }
            }
        }
    }

    override suspend fun disconnect(): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = connection ?: return@runCatching
                usbInterface?.let { conn.releaseInterface(it) }
                conn.close()
                connection = null
                usbInterface = null
                bulkOut = null
                log.i { "USB printer disconnected: ${device.deviceName}" }
            }
        }
    }

    override suspend fun isConnected(): Boolean = mutex.withLock {
        connection != null && bulkOut != null
    }

    override suspend fun print(commands: ByteArray): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = connection ?: error("Not connected to USB printer")
                val endpoint = bulkOut ?: error("Bulk-OUT endpoint not available")
                bulkWrite(conn, endpoint, commands)
            }
        }
    }

    override suspend fun openCashDrawer(): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = connection ?: error("Not connected to USB printer")
                val endpoint = bulkOut ?: error("Bulk-OUT endpoint not available")
                // ESC p 0 25 250 — standard cash drawer kick pulse
                bulkWrite(conn, endpoint, ESC_CASH_DRAWER_KICK)
                log.d { "Cash drawer kick sent via USB" }
            }
        }
    }

    override suspend fun cutPaper(): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = connection ?: error("Not connected to USB printer")
                val endpoint = bulkOut ?: error("Bulk-OUT endpoint not available")
                // GS V 1 — partial cut
                bulkWrite(conn, endpoint, ESC_PARTIAL_CUT)
                log.d { "Paper cut command sent via USB" }
            }
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    /**
     * Iterates over all interfaces and their endpoints to locate the first
     * USB Bulk-OUT endpoint suitable for ESC/POS data transfer.
     *
     * Printer-class devices (class 0x07) are preferred; vendor-specific interfaces
     * with a Bulk-OUT endpoint are accepted as a fallback.
     *
     * @return A pair of [UsbInterface] and [UsbEndpoint], or `null` if none found.
     */
    private fun findBulkOutEndpoint(dev: UsbDevice): Pair<UsbInterface, UsbEndpoint>? {
        for (i in 0 until dev.interfaceCount) {
            val iface = dev.getInterface(i)
            for (j in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(j)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                    ep.direction == UsbConstants.USB_DIR_OUT
                ) {
                    return iface to ep
                }
            }
        }
        return null
    }

    /**
     * Sends [data] over [endpoint] using [connection], splitting into
     * [MAX_CHUNK_BYTES]-byte chunks to respect USB transfer limits.
     *
     * @throws IllegalStateException on a transfer failure (negative return value).
     */
    private fun bulkWrite(
        connection: UsbDeviceConnection,
        endpoint: UsbEndpoint,
        data: ByteArray,
    ) {
        var offset = 0
        while (offset < data.size) {
            val chunkSize = minOf(MAX_CHUNK_BYTES, data.size - offset)
            val result = connection.bulkTransfer(endpoint, data, offset, chunkSize, timeoutMs)
            if (result < 0) {
                error(
                    "USB bulkTransfer failed at offset=$offset " +
                            "(result=$result, device=${device.deviceName})"
                )
            }
            offset += result
        }
    }

    // ── Constants ────────────────────────────────────────────────────────────────

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 2_000
        private const val MAX_CHUNK_BYTES = 16_384 // 16 KB per USB transfer

        /** ESC p 0 25 250 — dual-solenoid cash drawer kick (48V and 24V compatible). */
        private val ESC_CASH_DRAWER_KICK = byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte())

        /** GS V 1 — partial paper cut. */
        private val ESC_PARTIAL_CUT = byteArrayOf(0x1D, 0x56, 0x01)
    }
}
