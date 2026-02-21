package com.zyntasolutions.zyntapos.hal.scanner

import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ZyntaPOS — HAL | Desktop (JVM) Implementation
 *
 * [DesktopSerialScanner] reads barcode data from a scanner connected via a
 * serial (COM/RS-232) port using the **jSerialComm** library.
 *
 * ### Protocol
 * Most serial barcode scanners transmit decoded barcode data as an ASCII string
 * terminated by `CR` (`0x0D`), `LF` (`0x0A`), or `CRLF` (`0x0D 0x0A`).
 * [DesktopSerialScanner] accumulates bytes into a line buffer and emits a
 * [ScanResult.Barcode] whenever a line terminator is detected.
 *
 * ### Concurrency model
 * A dedicated coroutine runs a blocking read loop on [Dispatchers.IO].
 * Results are published to a [MutableSharedFlow] with a replay of 0 and
 * `extraBufferCapacity = 8` so that brief back-pressure is absorbed without
 * dropping scan events.  Collectors receive events via [scanEvents].
 *
 * ### Lifecycle
 * Call [startListening] to open the port and begin the read loop.
 * Call [stopListening] to cancel the loop and close the port.
 * Both operations are safe to call multiple times.
 *
 * @param portDescriptor Platform port name (`"COM4"` on Windows, `"/dev/ttyUSB1"` on Linux/macOS).
 * @param baudRate       Scanner baud rate — must match scanner configuration (default 9600).
 * @param dataBits       Data bits per frame (default 8).
 * @param stopBits       Stop bits — [SerialPort.ONE_STOP_BIT] etc. (default 1).
 * @param parity         Parity — [SerialPort.NO_PARITY] etc. (default none).
 * @param minBarcodeLen  Minimum line length to emit as a barcode event (default 4).
 */
class DesktopSerialScanner(
    private val portDescriptor: String,
    private val baudRate: Int = 9_600,
    private val dataBits: Int = 8,
    private val stopBits: Int = SerialPort.ONE_STOP_BIT,
    private val parity: Int = SerialPort.NO_PARITY,
    private val minBarcodeLen: Int = 4,
) : BarcodeScanner {

    // ──────────────────────────────────────────────────────────────────────────
    // Flow infrastructure
    // ──────────────────────────────────────────────────────────────────────────

    private val _scanEvents = MutableSharedFlow<ScanResult>(
        replay             = 0,
        extraBufferCapacity = 8,
    )

    override val scanEvents: Flow<ScanResult> = _scanEvents.asSharedFlow()

    // ──────────────────────────────────────────────────────────────────────────
    // State
    // ──────────────────────────────────────────────────────────────────────────

    private var port: SerialPort? = null
    private var readScope: CoroutineScope? = null
    private var readJob: Job? = null

    // ──────────────────────────────────────────────────────────────────────────
    // BarcodeScanner implementation
    // ──────────────────────────────────────────────────────────────────────────

    override suspend fun startListening(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (readJob?.isActive == true) return@runCatching   // idempotent

            val sp = SerialPort.getCommPort(portDescriptor).also { sp ->
                sp.baudRate    = baudRate
                sp.numDataBits = dataBits
                sp.numStopBits = stopBits
                sp.parity      = parity
                // Non-blocking read with 100 ms inter-byte timeout
                sp.setComPortTimeouts(
                    SerialPort.TIMEOUT_READ_SEMI_BLOCKING,
                    /* readTimeout = */ 100,
                    /* writeTimeout = */ 0,
                )
                check(sp.openPort()) { "jSerialComm: could not open scanner port $portDescriptor" }
            }
            port = sp

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            readScope = scope

            readJob = scope.launch {
                val lineBuffer = StringBuilder()
                val byteBuffer = ByteArray(READ_CHUNK_SIZE)

                while (isActive) {
                    val n = sp.inputStream.read(byteBuffer)
                    if (n <= 0) continue

                    for (i in 0 until n) {
                        val ch = byteBuffer[i].toInt().and(0xFF).toChar()
                        when (ch) {
                            '\r', '\n' -> {
                                val line = lineBuffer.toString().trim()
                                lineBuffer.clear()

                                if (line.length >= minBarcodeLen) {
                                    _scanEvents.emit(
                                        ScanResult.Barcode(
                                            value  = line,
                                            format = inferFormat(line),
                                        )
                                    )
                                }
                            }
                            else -> lineBuffer.append(ch)
                        }
                    }
                }
            }
        }
    }

    override suspend fun stopListening() {
        readJob?.cancel()
        readJob = null
        readScope?.cancel()
        readScope = null
        withContext(Dispatchers.IO) {
            port?.closePort()
            port = null
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Format inference
    // ──────────────────────────────────────────────────────────────────────────

    private fun inferFormat(value: String): BarcodeFormat {
        if (value.all { it.isDigit() }) {
            return when (value.length) {
                13   -> BarcodeFormat.EAN_13
                12   -> BarcodeFormat.UPC_A
                8    -> BarcodeFormat.EAN_8
                else -> BarcodeFormat.UNKNOWN
            }
        }
        return BarcodeFormat.CODE_128
    }

    companion object {
        private const val READ_CHUNK_SIZE = 256
    }
}
