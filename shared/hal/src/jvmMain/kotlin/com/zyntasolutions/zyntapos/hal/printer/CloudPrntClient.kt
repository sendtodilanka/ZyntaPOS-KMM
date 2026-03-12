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
import java.net.URI

/**
 * [CloudPrntClient] implements the **Star Micronics CloudPRNT** protocol —
 * an HTTP pull-printing mechanism where the printer polls a server for pending
 * print jobs.
 *
 * ### CloudPRNT Protocol
 * 1. Client polls `GET {serverUrl}/printer/{deviceId}` on [pollIntervalMs] cadence.
 * 2. If the response contains `jobReady: true` the client extracts the job URL
 *    from the response JSON.
 * 3. The raw job bytes are downloaded and forwarded to [PrinterManager.print].
 * 4. The client posts a status update back to the server.
 *
 * @param serverUrl      Base URL of the CloudPRNT server.
 * @param deviceId       Unique device identifier registered with the CloudPRNT server.
 * @param printerManager [PrinterManager] used to forward downloaded job bytes.
 * @param pollIntervalMs Polling cadence in milliseconds; default 10 000.
 * @param scope          Optional coroutine scope; defaults to IO.
 */
class CloudPrntClient(
    private val serverUrl: String,
    private val deviceId: String,
    private val _printerManager: PrinterManager,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    @Volatile private var running: Boolean = false

    /** Starts the CloudPRNT polling loop. No-op if already running. */
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

    private suspend fun poll() {
        runCatching {
            val pollUrl = "$serverUrl/printer/$deviceId"
            val connection = (URI.create(pollUrl).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val body = BufferedReader(InputStreamReader(connection.inputStream))
                    .use { it.readText() }

                if (body.contains("\"jobReady\"") && body.contains("true")) {
                    downloadAndPrint(body)
                }
            }
            connection.disconnect()
        }
    }

    /**
     * Parses the CloudPRNT poll response to extract the job URL, downloads the
     * print job bytes, and forwards them to the printer manager.
     */
    private suspend fun downloadAndPrint(pollResponse: String) {
        runCatching {
            // Extract job URL from JSON response (simple regex parse — no JSON lib dependency)
            val jobUrl = extractJsonString(pollResponse, "jobUrl")
                ?: extractJsonString(pollResponse, "url")
                ?: return

            // Download the print job bytes
            val jobConnection = (URI.create(jobUrl).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
            }

            if (jobConnection.responseCode == HttpURLConnection.HTTP_OK) {
                val jobBytes = jobConnection.inputStream.use { it.readBytes() }
                _printerManager.print(jobBytes)

                // Post completion status back to the server
                postJobStatus(jobUrl, "completed")
            }
            jobConnection.disconnect()
        }
    }

    /** Posts job completion status back to the CloudPRNT server. */
    private fun postJobStatus(jobUrl: String, status: String) {
        runCatching {
            val statusUrl = jobUrl.substringBeforeLast('/') + "/status"
            val connection = (URI.create(statusUrl).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            connection.outputStream.use {
                it.write("""{"deviceId":"$deviceId","status":"$status"}""".toByteArray())
            }
            connection.responseCode // trigger the request
            connection.disconnect()
        }
    }

    /** Extracts a string value from a JSON object by key (simple regex — no library). */
    private fun extractJsonString(json: String, key: String): String? {
        val regex = """"$key"\s*:\s*"([^"]+)"""".toRegex()
        return regex.find(json)?.groupValues?.getOrNull(1)
    }

    companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 10_000L
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 10_000
    }
}
