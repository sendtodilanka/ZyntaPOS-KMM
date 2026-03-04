package com.zyntasolutions.zyntapos.hal.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID

/**
 * ZyntaPOS — HAL | Android Bluetooth Label Printer
 *
 * Implements [LabelPrinterPort] using a classic Bluetooth RFCOMM (SPP) socket to
 * communicate with a Bluetooth-connected label printer (e.g. Zebra ZD411-BT,
 * BIXOLON XD5-40B, TSC TC200 BT).
 *
 * ### Requirements
 * - The printer must be **paired** with the Android device before [connect] is called.
 * - `BLUETOOTH_CONNECT` permission must be granted on API 31+.
 *
 * @param bluetoothDevice  The already-paired [BluetoothDevice].
 * @param bluetoothAdapter [BluetoothAdapter] instance.
 * @param connectTimeoutMs Milliseconds to wait for RFCOMM handshake. Default 8 000.
 */
@SuppressLint("MissingPermission") // Caller is responsible for BLUETOOTH_CONNECT grant
class AndroidBluetoothLabelPrinterPort(
    private val bluetoothDevice: BluetoothDevice,
    private val bluetoothAdapter: BluetoothAdapter,
    private val _connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
) : LabelPrinterPort {

    private val log = ZyntaLogger.forModule("AndroidBluetoothLabelPrinterPort")
    private val mutex = Mutex()

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    // ── LabelPrinterPort implementation ──────────────────────────────────────

    override suspend fun connect(): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                if (socket?.isConnected == true) return@runCatching

                if (bluetoothAdapter.isDiscovering) {
                    bluetoothAdapter.cancelDiscovery()
                }

                val rfcomm = bluetoothDevice.createRfcommSocketToServiceRecord(SPP_UUID)
                rfcomm.connect()
                outputStream = rfcomm.outputStream
                socket = rfcomm
                log.i("BT label printer connected: ${bluetoothDevice.address}")
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
                log.i("BT label printer disconnected: ${bluetoothDevice.address}")
            }
        }
    }

    override suspend fun isConnected(): Boolean = mutex.withLock {
        socket?.isConnected == true
    }

    override suspend fun printZpl(commands: ByteArray): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val out = outputStream ?: error("Not connected to BT label printer")
                out.write(commands)
                out.flush()
                log.d("ZPL sent via BT: ${commands.size} bytes")
            }
        }
    }

    override suspend fun printTspl(commands: ByteArray): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val out = outputStream ?: error("Not connected to BT label printer")
                out.write(commands)
                out.flush()
                log.d("TSPL sent via BT: ${commands.size} bytes")
            }
        }
    }

    companion object {
        private const val DEFAULT_CONNECT_TIMEOUT_MS = 8_000

        /** Standard Bluetooth Serial Port Profile UUID — used by all SPP printers. */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
