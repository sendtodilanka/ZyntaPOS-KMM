package com.zyntasolutions.zyntapos.hal.printer

import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [DesktopUsbLabelPrinterPort] provides USB direct-print capability for desktop
 * label printers (Zebra ZD series, TSC TTP series in native USB mode).
 *
 * USB label printers typically appear as virtual serial ports. This implementation
 * uses jSerialComm to enumerate and match by USB descriptor patterns, then sends
 * raw ZPL/TSPL command bytes over the serial channel.
 *
 * @param vendorId   USB Vendor ID of the target label printer.
 * @param productId  USB Product ID of the target label printer.
 */
class DesktopUsbLabelPrinterPort(
    private val _vendorId: Int = ZEBRA_VENDOR_ID,
    private val _productId: Int = 0x0000,
) : LabelPrinterPort {

    @Volatile private var connected: Boolean = false
    private var port: SerialPort? = null

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (port?.isOpen == true) return@runCatching

            val matchedPort = findUsbLabelPrinterPort()
                ?: throw IllegalStateException(
                    "DesktopUsbLabelPrinterPort: no USB label printer found matching vendorId=0x${_vendorId.toString(16)}. " +
                        "Available ports: ${SerialPort.getCommPorts().map { it.systemPortName }.joinToString()}"
                )

            matchedPort.baudRate = 115_200
            matchedPort.numDataBits = 8
            matchedPort.numStopBits = SerialPort.ONE_STOP_BIT
            matchedPort.parity = SerialPort.NO_PARITY
            matchedPort.setComPortTimeouts(
                SerialPort.TIMEOUT_WRITE_BLOCKING, 0, WRITE_TIMEOUT_MS,
            )

            check(matchedPort.openPort()) {
                "jSerialComm: could not open USB label printer port ${matchedPort.systemPortName}"
            }
            port = matchedPort
            connected = true
        }
    }

    override suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            port?.closePort()
            port = null
            connected = false
        }
    }

    override suspend fun isConnected(): Boolean = connected && port?.isOpen == true

    override suspend fun printZpl(commands: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val sp = requireConnected()
            sp.outputStream.use {
                it.write(commands)
                it.flush()
            }
        }
    }

    override suspend fun printTspl(commands: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val sp = requireConnected()
            sp.outputStream.use {
                it.write(commands)
                it.flush()
            }
        }
    }

    private fun findUsbLabelPrinterPort(): SerialPort? {
        val ports = SerialPort.getCommPorts()
        val vid = _vendorId.toString(16).padStart(4, '0')
        return ports.firstOrNull { sp ->
            sp.portDescription.lowercase().contains(vid)
        } ?: ports.firstOrNull { sp ->
            sp.portDescription.lowercase().let {
                it.contains("usb") && (it.contains("label") || it.contains("zebra") || it.contains("tsc"))
            }
        }
    }

    private fun requireConnected(): SerialPort =
        checkNotNull(port?.takeIf { it.isOpen }) {
            "DesktopUsbLabelPrinterPort: not connected"
        }

    companion object {
        /** Zebra Technologies USB Vendor ID — covers ZD410, ZD420, ZD620, ZT series. */
        const val ZEBRA_VENDOR_ID = 0x0A5F
        /** TSC Auto ID Technology USB Vendor ID. */
        const val TSC_VENDOR_ID = 0x1203

        private const val WRITE_TIMEOUT_MS = 2_000
    }
}
