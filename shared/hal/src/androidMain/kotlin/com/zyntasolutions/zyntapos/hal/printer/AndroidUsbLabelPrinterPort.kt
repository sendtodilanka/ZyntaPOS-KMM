package com.zyntasolutions.zyntapos.hal.printer

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * ZyntaPOS — HAL | Android USB Label Printer
 *
 * Implements [LabelPrinterPort] using the Android USB Host API to communicate
 * with a USB-connected label printer (Zebra ZD series, TSC TTP series, etc.).
 *
 * ### Transport
 * ZPL and TSPL command bytes are sent over the printer's Bulk-OUT USB endpoint.
 * No protocol wrapping is needed — both Zebra (ZPL) and TSC (TSPL) accept raw
 * command bytes on the bulk endpoint.
 *
 * ### Permissions
 * USB permission must be granted by the caller **before** [connect] is invoked.
 * Use `UsbManager.requestPermission()` with a `PendingIntent`.
 *
 * @param context   Application context for [UsbManager] access.
 * @param device    The [UsbDevice] representing the label printer.
 * @param timeoutMs Bulk transfer timeout in milliseconds. Default 3 000.
 */
class AndroidUsbLabelPrinterPort(
    private val context: Context,
    private val device: UsbDevice,
    private val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
) : LabelPrinterPort {

    private val log = ZyntaLogger.forModule("AndroidUsbLabelPrinterPort")
    private val mutex = Mutex()

    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var bulkOut: UsbEndpoint? = null

    // ── LabelPrinterPort implementation ──────────────────────────────────────

    override suspend fun connect(): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                if (connection != null) return@runCatching

                val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                if (!usbManager.hasPermission(device)) {
                    error("USB permission not granted for label printer ${device.deviceName}")
                }

                val (iface, endpoint) = findBulkOutEndpoint(device)
                    ?: error(
                        "No Bulk-OUT endpoint on label printer ${device.deviceName} " +
                            "(vendorId=0x${device.vendorId.toString(16)}, " +
                            "productId=0x${device.productId.toString(16)})"
                    )

                val conn = usbManager.openDevice(device)
                    ?: error("Failed to open USB label printer ${device.deviceName}")

                if (!conn.claimInterface(iface, /*force=*/true)) {
                    conn.close()
                    error("Failed to claim USB interface ${iface.id} on ${device.deviceName}")
                }

                connection = conn
                usbInterface = iface
                bulkOut = endpoint
                log.i("USB label printer connected: ${device.deviceName}")
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
                log.i("USB label printer disconnected: ${device.deviceName}")
            }
        }
    }

    override suspend fun isConnected(): Boolean = mutex.withLock {
        connection != null && bulkOut != null
    }

    override suspend fun printZpl(commands: ByteArray): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                bulkWrite(
                    connection ?: error("Not connected to USB label printer"),
                    bulkOut ?: error("Bulk-OUT endpoint not available"),
                    commands,
                )
                log.d("ZPL commands sent: ${commands.size} bytes")
            }
        }
    }

    override suspend fun printTspl(commands: ByteArray): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                bulkWrite(
                    connection ?: error("Not connected to USB label printer"),
                    bulkOut ?: error("Bulk-OUT endpoint not available"),
                    commands,
                )
                log.d("TSPL commands sent: ${commands.size} bytes")
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    private fun bulkWrite(
        conn: UsbDeviceConnection,
        endpoint: UsbEndpoint,
        data: ByteArray,
    ) {
        var offset = 0
        while (offset < data.size) {
            val chunkSize = minOf(MAX_CHUNK_BYTES, data.size - offset)
            val result = conn.bulkTransfer(endpoint, data, offset, chunkSize, timeoutMs)
            if (result < 0) {
                error(
                    "USB bulkTransfer failed at offset=$offset " +
                        "(result=$result, device=${device.deviceName})"
                )
            }
            offset += result
        }
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 3_000
        private const val MAX_CHUNK_BYTES = 16_384
    }
}
