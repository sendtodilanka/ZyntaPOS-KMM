package com.zyntasolutions.zyntapos.hal.printer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/**
 * ZyntaPOS — HAL | Desktop (JVM) Printer Discovery
 *
 * [PrinterDiscoveryService] scans the local LAN subnet for devices listening on
 * TCP port 9100 (the ESC/POS raw-print standard) and returns discovered printers
 * as a cold [Flow].
 *
 * ### Discovery algorithm
 * 1. Enumerates non-loopback IPv4 network interfaces to derive the local subnet.
 * 2. Iterates all 254 host addresses in the /24 subnet.
 * 3. Attempts a TCP connect on port 9100 with [probeTimeoutMs] timeout.
 * 4. Emits [DiscoveredPrinter] for each host that responds within the timeout.
 *
 * ### Usage
 * ```kotlin
 * discoveryService.scan().collect { printer ->
 *     println("Found: ${printer.ip}:${printer.port} (${printer.hostname})")
 * }
 * ```
 *
 * @param rawPrintPort   Port to probe; default 9100.
 * @param probeTimeoutMs Per-host connection timeout in milliseconds; default 300.
 */
class PrinterDiscoveryService(
    private val rawPrintPort: Int = DEFAULT_RAW_PORT,
    private val probeTimeoutMs: Int = DEFAULT_PROBE_TIMEOUT_MS,
) {

    /**
     * Scans the local /24 subnet and emits each responding [DiscoveredPrinter].
     *
     * The flow is cold and runs entirely on [Dispatchers.IO]. Collect it from a
     * coroutine scope and cancel to abort the scan mid-way.
     */
    fun scan(): Flow<DiscoveredPrinter> = flow {
        val localAddress = detectLocalIpAddress() ?: return@flow
        val parts = localAddress.split(".")
        if (parts.size != 4) return@flow

        val subnet = "${parts[0]}.${parts[1]}.${parts[2]}"
        for (hostOctet in 1..254) {
            val candidateIp = "$subnet.$hostOctet"
            if (candidateIp == localAddress) continue

            val discovered = probe(candidateIp, rawPrintPort, probeTimeoutMs)
            if (discovered != null) emit(discovered)
        }
    }.flowOn(Dispatchers.IO)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun probe(ip: String, port: Int, timeoutMs: Int): DiscoveredPrinter? =
        withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), timeoutMs)
                    val hostname = runCatching {
                        InetAddress.getByName(ip).canonicalHostName.takeIf { it != ip }
                    }.getOrNull()
                    DiscoveredPrinter(ip = ip, port = port, hostname = hostname)
                }
            }.getOrNull()
        }

    private fun detectLocalIpAddress(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces()
                ?.asSequence()
                ?.filter { !it.isLoopback && it.isUp }
                ?.flatMap { it.inetAddresses.asSequence() }
                ?.firstOrNull { addr ->
                    !addr.isLoopbackAddress &&
                        addr.hostAddress.contains(".") && // IPv4
                        !addr.hostAddress.startsWith("169.254") // exclude link-local
                }
                ?.hostAddress
        }.getOrNull()
    }

    companion object {
        const val DEFAULT_RAW_PORT = 9100
        const val DEFAULT_PROBE_TIMEOUT_MS = 300
    }
}

/**
 * A thermal or label printer discovered on the local network.
 *
 * @property ip       IPv4 address of the printer.
 * @property port     TCP port that responded (typically 9100).
 * @property hostname Reverse-DNS hostname if resolvable; `null` otherwise.
 */
data class DiscoveredPrinter(
    val ip: String,
    val port: Int,
    val hostname: String?,
)
