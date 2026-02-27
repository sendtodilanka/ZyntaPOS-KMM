package com.zyntasolutions.zyntapos.hal.printer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * ZyntaPOS — HAL | Desktop (JVM) Label Printer Implementation
 *
 * [DesktopTcpLabelPrinterPort] connects to a network-attached label printer
 * (Zebra ZPL or TSC TSPL) over a raw TCP socket — the standard transport for
 * Ethernet/Wi-Fi label printers.
 *
 * ### Default port
 * Port **9100** is the raw-print listener used by Zebra, TSC, Argox, and SATO
 * label printers when configured in "TCP/IP Raw" mode.
 *
 * ### ZPL vs TSPL
 * This port is command-language agnostic — it transmits raw bytes directly.
 * The caller selects ZPL or TSPL via the corresponding builder:
 * - [ZplLabelBuilder.buildLabel] → [printZpl]
 * - [TsplLabelBuilder.buildLabel] → [printTspl]
 *
 * @param host             Printer IP address or hostname (e.g. `"192.168.1.201"`).
 * @param port             TCP port — default **9100**.
 * @param connectTimeoutMs Socket connect timeout in milliseconds; default 5 000.
 * @param writeTimeoutMs   SO_TIMEOUT applied after connect; default 3 000.
 */
class DesktopTcpLabelPrinterPort(
    private val host: String,
    private val port: Int = DEFAULT_PORT,
    private val connectTimeoutMs: Int = 5_000,
    private val writeTimeoutMs: Int = 3_000,
) : LabelPrinterPort {

    private var socket: Socket? = null

    // ── LabelPrinterPort implementation ──────────────────────────────────────

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

    override suspend fun printZpl(commands: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireConnected().getOutputStream().run {
                write(commands)
                flush()
            }
        }
    }

    override suspend fun printTspl(commands: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireConnected().getOutputStream().run {
                write(commands)
                flush()
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun requireConnected(): Socket =
        checkNotNull(socket?.takeIf { it.isConnected && !it.isClosed }) {
            "DesktopTcpLabelPrinterPort: not connected to $host:$port"
        }

    companion object {
        /** Standard raw-print TCP port for label printers. */
        const val DEFAULT_PORT = 9100
    }
}
