package com.zyntasolutions.zyntapos.hal.printer

import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [DesktopUsbPrinterPort] provides USB direct-print capability for desktop
 * thermal receipt printers (e.g. Epson TM-T88, Star TSP100 in native USB mode).
 *
 * USB thermal printers typically appear as virtual serial ports on the OS.
 * This implementation uses jSerialComm to enumerate serial ports and match
 * by USB descriptor patterns.
 *
 * At startup [PrinterManager] calls [detectAndConnect] to find a matching printer.
 * If found, the port is promoted to active; otherwise the system falls back to
 * [DesktopTcpPrinterPort] or [DesktopSerialPrinterPort].
 *
 * @param vendorId   USB Vendor ID of the target printer (e.g. `0x04B8` for Epson).
 * @param productId  USB Product ID of the target printer.
 */
class DesktopUsbPrinterPort(
    private val _vendorId: Int = EPSON_VENDOR_ID,
    private val _productId: Int = 0x0000,
) : PrinterPort {

    @Volatile private var connected: Boolean = false
    private var port: SerialPort? = null

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (port?.isOpen == true) return@runCatching

            val matchedPort = findUsbPrinterPort()
                ?: throw IllegalStateException(
                    "DesktopUsbPrinterPort: no USB printer found matching vendorId=0x${_vendorId.toString(16)}. " +
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
                "jSerialComm: could not open USB port ${matchedPort.systemPortName}"
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

    override suspend fun print(commands: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val sp = requireConnected()
            sp.outputStream.use {
                it.write(commands)
                it.flush()
            }
        }
    }

    override suspend fun openCashDrawer(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireConnected().outputStream.use {
                it.write(ESC_P_DRAWER_KICK)
                it.flush()
            }
        }
    }

    override suspend fun cutPaper(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireConnected().outputStream.use {
                it.write(GS_V_PARTIAL_CUT)
                it.flush()
            }
        }
    }

    /**
     * Attempts to detect a connected USB printer matching [_vendorId]/[_productId] and
     * connect to it. Returns `true` if a device was found and claimed, `false` otherwise.
     *
     * Called once at startup by [PrinterManager].
     */
    suspend fun detectAndConnect(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val matchedPort = findUsbPrinterPort() ?: return@runCatching false
            matchedPort.baudRate = 115_200
            matchedPort.numDataBits = 8
            matchedPort.numStopBits = SerialPort.ONE_STOP_BIT
            matchedPort.parity = SerialPort.NO_PARITY
            matchedPort.setComPortTimeouts(
                SerialPort.TIMEOUT_WRITE_BLOCKING, 0, WRITE_TIMEOUT_MS,
            )
            if (matchedPort.openPort()) {
                port = matchedPort
                connected = true
                true
            } else {
                false
            }
        }
    }

    private fun findUsbPrinterPort(): SerialPort? {
        val ports = SerialPort.getCommPorts()
        val vid = _vendorId.toString(16).padStart(4, '0')
        // Match by USB vendor ID in the port description
        return ports.firstOrNull { sp ->
            sp.portDescription.lowercase().contains(vid)
        } ?: ports.firstOrNull { sp ->
            // Fallback: match common USB-to-serial adapters used with receipt printers
            sp.portDescription.lowercase().let {
                it.contains("usb") && (it.contains("printer") || it.contains("receipt"))
            }
        }
    }

    private fun requireConnected(): SerialPort =
        checkNotNull(port?.takeIf { it.isOpen }) {
            "DesktopUsbPrinterPort: not connected"
        }

    companion object {
        /** Epson USB Vendor ID — covers TM-T20, TM-T88, TM-m30 families. */
        const val EPSON_VENDOR_ID = 0x04B8
        /** Star Micronics USB Vendor ID — covers TSP100, TSP650 families. */
        const val STAR_VENDOR_ID = 0x0519
        /** BIXOLON USB Vendor ID — covers SRP-350, SRP-500 families. */
        const val BIXOLON_VENDOR_ID = 0x1504

        private const val WRITE_TIMEOUT_MS = 2_000
        /** ESC p 0 50 250 — drawer kick pin 0, on-time 100 ms, off-time 500 ms. */
        private val ESC_P_DRAWER_KICK = byteArrayOf(0x1B, 0x70, 0x00, 50, -6)
        /** GS V 66 0 — partial paper cut. */
        private val GS_V_PARTIAL_CUT = byteArrayOf(0x1D, 0x56, 0x42, 0x00)
    }
}
