package com.zyntasolutions.zyntapos.hal.printer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * ZentaPOS — HAL | Desktop (JVM) Implementation
 *
 * [DesktopTcpPrinterPort] communicates with a network-connected thermal receipt
 * printer over a raw TCP socket — the de-facto transport standard for Ethernet /
 * Wi-Fi ESC/POS printers (e.g. Epson TM series, BIXOLON, Star TSP700).
 *
 * ### Default port
 * Port **9100** is used by virtually every network-capable ESC/POS printer as its
 * raw-print listener.  Supply a different port only if the printer has been
 * reconfigured.
 *
 * ### Connection lifecycle
 * The socket is opened on [connect] and closed on [disconnect].  [isConnected]
 * reflects `Socket.isConnected && !Socket.isClosed` without additional I/O.
 * Callers should reconnect after any [print] failure — the socket is invalidated.
 *
 * ### Concurrency
 * All blocking socket operations run on [Dispatchers.IO].  The socket handle is
 * not synchronized — callers must serialize [print] calls (e.g. via [PrinterManager]).
 *
 * @param host    Printer's IP address or hostname (e.g. `"192.168.1.200"`).
 * @param port    TCP port — default **9100** (raw ESC/POS).
 * @param connectTimeoutMs  Socket connect timeout in milliseconds; default 5 000.
 * @param writeTimeoutMs    `SO_TIMEOUT` applied after connect; default 3 000.
 */
class DesktopTcpPrinterPort(
    private val host: String,
    private val port: Int = DEFAULT_PORT,
    private val connectTimeoutMs: Int = 5_000,
    private val writeTimeoutMs: Int = 3_000,
) : PrinterPort {

    private var socket: Socket? = null

    // ──────────────────────────────────────────────────────────────────────────
    // PrinterPort implementation
    // ──────────────────────────────────────────────────────────────────────────

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (socket?.isConnected == true && socket?.isClosed == false) return@runCatching

            val s = Socket()
            s.soTimeout = writeTimeoutMs
            s.connect(InetSocketAddress(host, port), connectTimeoutMs)
            socket = s
        }
    }

    override suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            socket?.close()
            socket = null
        }
    }

    override suspend fun isConnected(): Boolean =
        socket?.let { it.isConnected && !it.isClosed } == true

    override suspend fun print(commands: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val s = requireConnected()
            s.getOutputStream().run {
                write(commands)
                flush()
            }
        }
    }

    override suspend fun openCashDrawer(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireConnected().getOutputStream().run {
                write(ESC_P_DRAWER_KICK)
                flush()
            }
        }
    }

    override suspend fun cutPaper(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireConnected().getOutputStream().run {
                write(GS_V_PARTIAL_CUT)
                flush()
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun requireConnected(): Socket =
        checkNotNull(socket?.takeIf { it.isConnected && !it.isClosed }) {
            "DesktopTcpPrinterPort: not connected to $host:$port"
        }

    // ──────────────────────────────────────────────────────────────────────────
    // Constants
    // ──────────────────────────────────────────────────────────────────────────

    companion object {
        /** Standard ESC/POS raw-print TCP port used by all major printer brands. */
        const val DEFAULT_PORT = 9100

        /** ESC p 0 50 250 — cash drawer kick, pin 0. */
        private val ESC_P_DRAWER_KICK = byteArrayOf(0x1B, 0x70, 0x00, 50, -6)

        /** GS V 66 0 — partial paper cut. */
        private val GS_V_PARTIAL_CUT = byteArrayOf(0x1D, 0x56, 0x42, 0x00)
    }
}
