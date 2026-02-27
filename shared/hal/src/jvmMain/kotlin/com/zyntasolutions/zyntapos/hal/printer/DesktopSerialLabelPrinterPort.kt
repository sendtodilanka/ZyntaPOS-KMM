package com.zyntasolutions.zyntapos.hal.printer

import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ZyntaPOS — HAL | Desktop (JVM) Label Printer Implementation
 *
 * [DesktopSerialLabelPrinterPort] drives a serial-connected label printer
 * (Zebra ZPL or TSC TSPL) using the jSerialComm library.
 *
 * Common use cases: legacy Zebra LP 2844 (COM port), TSC TTP-245 (RS-232).
 *
 * @param portDescriptor  Platform port name (e.g. `"COM4"` on Windows, `"/dev/ttyUSB0"` on Linux).
 * @param baudRate        Serial baud rate; must match printer configuration. Default 9600.
 * @param dataBits        Data bits per frame. Default 8.
 * @param stopBits        Stop bits — [SerialPort.ONE_STOP_BIT] etc.
 * @param parity          Parity — [SerialPort.NO_PARITY] etc.
 */
class DesktopSerialLabelPrinterPort(
    private val portDescriptor: String,
    private val baudRate: Int = 9_600,
    private val dataBits: Int = 8,
    private val stopBits: Int = SerialPort.ONE_STOP_BIT,
    private val parity: Int = SerialPort.NO_PARITY,
) : LabelPrinterPort {

    private var port: SerialPort? = null

    // ── LabelPrinterPort implementation ──────────────────────────────────────

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (port?.isOpen == true) return@runCatching

            val sp = SerialPort.getCommPort(portDescriptor)
            sp.baudRate    = baudRate
            sp.numDataBits = dataBits
            sp.numStopBits = stopBits
            sp.parity      = parity
            sp.setComPortTimeouts(
                SerialPort.TIMEOUT_WRITE_BLOCKING,
                /* readTimeout  = */ 0,
                /* writeTimeout = */ WRITE_TIMEOUT_MS,
            )

            check(sp.openPort()) { "jSerialComm: could not open label printer port $portDescriptor" }
            port = sp
        }
    }

    override suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            port?.closePort()
            port = null
        }
    }

    override suspend fun isConnected(): Boolean = port?.isOpen == true

    override suspend fun printZpl(commands: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireConnected().outputStream.use {
                it.write(commands)
                it.flush()
            }
        }
    }

    override suspend fun printTspl(commands: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireConnected().outputStream.use {
                it.write(commands)
                it.flush()
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun requireConnected(): SerialPort =
        checkNotNull(port?.takeIf { it.isOpen }) {
            "DesktopSerialLabelPrinterPort: not connected to $portDescriptor"
        }

    companion object {
        private const val WRITE_TIMEOUT_MS = 2_000
    }
}
