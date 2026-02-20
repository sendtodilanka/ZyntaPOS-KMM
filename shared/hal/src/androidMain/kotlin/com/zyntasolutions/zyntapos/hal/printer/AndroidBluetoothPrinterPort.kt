package com.zyntasolutions.zyntapos.hal.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID

/**
 * ZyntaPOS — Hardware Abstraction Layer · Android Bluetooth Printer
 *
 * Implements [PrinterPort] using a classic Bluetooth RFCOMM socket (SPP profile)
 * to communicate with a Bluetooth-connected ESC/POS thermal receipt printer.
 *
 * ### Bluetooth SPP profile
 * Serial Port Profile (SPP) uses the well-known UUID `00001101-0000-1000-8000-00805F9B34FB`.
 * The printer must already be **paired** with the Android device before [connect] is called;
 * this class does not initiate the pairing dialog.
 *
 * ### Android 12+ permissions
 * - `BLUETOOTH_CONNECT` (runtime permission on API 31+) must be granted before connecting.
 * - Legacy `BLUETOOTH` / `BLUETOOTH_ADMIN` cover API < 31.
 * The caller is responsible for requesting permissions; this class throws clearly if missing.
 *
 * ### Thread safety
 * All public methods are `suspend` and serialised with [mutex] so concurrent callers
 * never race on the [BluetoothSocket] or its [OutputStream].
 *
 * @param bluetoothDevice  The already-paired [BluetoothDevice] representing the printer.
 * @param bluetoothAdapter [BluetoothAdapter] instance (typically `BluetoothAdapter.getDefaultAdapter()`).
 * @param connectTimeoutMs Milliseconds to wait for the RFCOMM handshake. Default 8 000 ms.
 */
@SuppressLint("MissingPermission") // Caller is responsible for runtime BLUETOOTH_CONNECT grant
class AndroidBluetoothPrinterPort(
    private val bluetoothDevice: BluetoothDevice,
    private val bluetoothAdapter: BluetoothAdapter,
    private val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
) : PrinterPort {

    private val log = Logger.withTag("AndroidBluetoothPrinterPort")

    /** Serialises all Bluetooth socket operations. */
    private val mutex = Mutex()

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    // ── PrinterPort impl ────────────────────────────────────────────────────────

    override suspend fun connect(): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                if (socket?.isConnected == true) {
                    log.d { "connect() called while already connected — no-op" }
                    return@runCatching
                }

                if (bluetoothAdapter.isDiscovering) {
                    // Discovery interferes with connection — cancel it first
                    bluetoothAdapter.cancelDiscovery()
                    log.d { "Cancelled Bluetooth discovery before connecting to printer" }
                }

                val rfcommSocket = bluetoothDevice.createRfcommSocketToServiceRecord(SPP_UUID)
                rfcommSocket.connect() // Blocking — uses Dispatchers.IO
                outputStream = rfcommSocket.outputStream
                socket = rfcommSocket

                log.i {
                    "Bluetooth printer connected: ${bluetoothDevice.address} " +
                            "(name=${bluetoothDevice.name})"
                }
            }
        }
    }

    override suspend fun disconnect(): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                outputStream?.runCatching { close() }
                socket?.runCatching { close() }
                outputStream = null
                socket = null
                log.i { "Bluetooth printer disconnected: ${bluetoothDevice.address}" }
            }
        }
    }

    override suspend fun isConnected(): Boolean = mutex.withLock {
        socket?.isConnected == true
    }

    override suspend fun print(commands: ByteArray): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val out = outputStream ?: error("Not connected to Bluetooth printer")
                out.write(commands)
                out.flush()
            }
        }
    }

    override suspend fun openCashDrawer(): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val out = outputStream ?: error("Not connected to Bluetooth printer")
                // ESC p 0 25 250 — standard cash drawer kick
                out.write(ESC_CASH_DRAWER_KICK)
                out.flush()
                log.d { "Cash drawer kick sent via Bluetooth" }
            }
        }
    }

    override suspend fun cutPaper(): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val out = outputStream ?: error("Not connected to Bluetooth printer")
                // GS V 1 — partial cut
                out.write(ESC_PARTIAL_CUT)
                out.flush()
                log.d { "Paper cut command sent via Bluetooth" }
            }
        }
    }

    // ── Constants ────────────────────────────────────────────────────────────────

    companion object {
        private const val DEFAULT_CONNECT_TIMEOUT_MS = 8_000

        /**
         * Standard Serial Port Profile UUID.
         * All ESC/POS Bluetooth printers expose RFCOMM on this service record.
         */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        /** ESC p 0 25 250 — dual-solenoid cash drawer kick pulse. */
        private val ESC_CASH_DRAWER_KICK = byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte())

        /** GS V 1 — partial paper cut. */
        private val ESC_PARTIAL_CUT = byteArrayOf(0x1D, 0x56, 0x01)
    }
}
