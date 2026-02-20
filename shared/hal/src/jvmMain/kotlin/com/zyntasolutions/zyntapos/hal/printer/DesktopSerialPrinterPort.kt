package com.zyntasolutions.zyntapos.hal.printer

import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ZentaPOS — HAL | Desktop (JVM) Implementation
 *
 * [DesktopSerialPrinterPort] drives a thermal receipt printer connected via an
 * RS-232 serial (COM) port using the jSerialComm library.
 *
 * ### Supported baud rates
 * `9600`, `19200`, `38400`, `57600`, `115200` — pass the desired rate through
 * [PrinterConfig.serialBaudRate] (or use the secondary constructor's default).
 *
 * ### ESC/POS transport
 * Raw ESC/POS byte arrays produced by [EscPosReceiptBuilder] are written directly
 * to the serial port output stream.  No framing is added — ESC/POS is self-delimiting.
 *
 * ### Thread safety
 * All blocking I/O is dispatched to [Dispatchers.IO].  The single [SerialPort]
 * handle is accessed only from that dispatcher, so callers may safely call from
 * any coroutine context.
 *
 * @param portDescriptor Platform port name (e.g. `"COM3"` on Windows, `"/dev/ttyUSB0"` on Linux).
 * @param baudRate       Serial baud rate; must match printer DIP-switch setting. Default 115200.
 * @param dataBits       Data bits per frame (usually 8).
 * @param stopBits       Stop bits — [SerialPort.ONE_STOP_BIT], [SerialPort.TWO_STOP_BITS].
 * @param parity         Parity — [SerialPort.NO_PARITY] etc.
 */
class DesktopSerialPrinterPort(
    private val portDescriptor: String,
    private val baudRate: Int = 115_200,
    private val dataBits: Int = 8,
    private val stopBits: Int = SerialPort.ONE_STOP_BIT,
    private val parity: Int = SerialPort.NO_PARITY,
) : PrinterPort {

    private var port: SerialPort? = null

    // ──────────────────────────────────────────────────────────────────────────
    // PrinterPort implementation
    // ──────────────────────────────────────────────────────────────────────────

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (port?.isOpen == true) return@runCatching          // idempotent

            val sp = SerialPort.getCommPort(portDescriptor)
            sp.baudRate     = baudRate
            sp.numDataBits  = dataBits
            sp.numStopBits  = stopBits
            sp.parity       = parity
            sp.setComPortTimeouts(
                SerialPort.TIMEOUT_WRITE_BLOCKING,
                /* readTimeout = */ 0,
                /* writeTimeout = */ WRITE_TIMEOUT_MS,
            )

            check(sp.openPort()) { "jSerialComm: could not open port $portDescriptor" }
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

    override suspend fun print(commands: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val sp = requireConnected()
            val written = sp.outputStream.use { it.write(commands); it.flush(); commands.size }
            check(written == commands.size) {
                "Serial write incomplete: expected ${commands.size} bytes, wrote $written"
            }
        }
    }

    override suspend fun openCashDrawer(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // ESC p <pin> <t1> <t2>  — drawer kick on pin 0, pulse 250 ms
            requireConnected().outputStream.use {
                it.write(ESC_P_DRAWER_KICK)
                it.flush()
            }
        }
    }

    override suspend fun cutPaper(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // GS V 66 <n>  — partial cut (leave 1-point feed)
            requireConnected().outputStream.use {
                it.write(GS_V_PARTIAL_CUT)
                it.flush()
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun requireConnected(): SerialPort =
        checkNotNull(port?.takeIf { it.isOpen }) {
            "DesktopSerialPrinterPort: not connected to $portDescriptor"
        }

    // ──────────────────────────────────────────────────────────────────────────
    // Constants
    // ──────────────────────────────────────────────────────────────────────────

    companion object {
        /** Write timeout in milliseconds before the port gives up. */
        private const val WRITE_TIMEOUT_MS = 2_000

        /** ESC p 0 50 250 — drawer kick pin 0, on-time 100 ms, off-time 500 ms (×2 ms). */
        private val ESC_P_DRAWER_KICK = byteArrayOf(0x1B, 0x70, 0x00, 50, -6) // -6 = 250.toByte()

        /** GS V 66 0 — partial paper cut. */
        private val GS_V_PARTIAL_CUT = byteArrayOf(0x1D, 0x56, 0x42, 0x00)
    }
}
