package com.zyntasolutions.zyntapos.hal.printer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * ZyntaPOS — HAL | Desktop (JVM) CloudPRNT Client
 *
 * [CloudPrntClient] implements the **Star Micronics CloudPRNT** protocol —
 * an HTTP pull-printing mechanism where the printer polls a server for pending
 * print jobs.
 *
 * ### CloudPRNT Protocol (simplified)
 * 1. Client polls `GET {serverUrl}/printer/{deviceId}` on [pollIntervalMs] cadence.
 * 2. If the response contains `jobReady: true` the client downloads the job from
 *    the URL in the `mediaTypes` array that the printer supports.
 * 3. The raw job bytes are forwarded to [PrinterManager.print].
 * 4. The client then posts a job status update to the server.
 *
 * ### MVP status — Functional stub
 * The current implementation performs the poll loop and logs responses, but does not
 * yet parse the full CloudPRNT JSON schema or download real job data. Full parsing
 * will be implemented in Phase 2 when a Star Micronics mC-Print3 test unit is available.
 *
 * @param serverUrl     Base URL of the CloudPRNT server (e.g. `"https://cloudprnt.example.com"`).
 * @param deviceId      Unique device identifier registered with the CloudPRNT server.
 * @param printerManager [PrinterManager] used to forward downloaded job bytes.
 * @param pollIntervalMs Polling cadence in milliseconds; default 10 000.
 * @param scope          Optional coroutine scope; defaults to IO.
 */
class CloudPrntClient(
    private val serverUrl: String,
    private val deviceId: String,
    private val printerManager: PrinterManager,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    @Volatile private var running: Boolean = false

    /**
     * Starts the CloudPRNT polling loop.
     * No-op if already running.
     */
    fun start() {
        if (running) return
        running = true
        scope.launch {
            while (isActive && running) {
                poll()
                delay(pollIntervalMs)
            }
        }
    }

    /** Stops the polling loop. */
    fun stop() {
        running = false
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private suspend fun poll() {
        runCatching {
            val pollUrl = "$serverUrl/printer/$deviceId"
            val connection = (URL(pollUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val body = BufferedReader(InputStreamReader(connection.inputStream))
                    .use { it.readText() }

                // TODO Phase 2: parse CloudPRNT JSON, download job, send to printerManager
                // For now log the response for debugging
                if (body.contains("jobReady") && body.contains("true")) {
                    downloadAndPrint(connection, body)
                }
            }
            connection.disconnect()
        }
    }

    private suspend fun downloadAndPrint(
        @Suppress("UNUSED_PARAMETER") pollConnection: HttpURLConnection,
        @Suppress("UNUSED_PARAMETER") pollResponse: String,
    ) {
        // TODO Phase 2: parse mediaTypes from JSON, download job bytes via GET,
        //  then call printerManager.print(jobBytes), then POST status back.
        //  Requires a JSON parser dependency or manual parsing.
    }

    companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 10_000L
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 10_000
    }
}
