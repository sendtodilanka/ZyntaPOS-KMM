package com.zyntasolutions.zyntapos.hal.printer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ZyntaPOS — Hardware Abstraction Layer
 *
 * [LabelPrinterManager] is the single Koin-provided gateway through which all shared
 * business logic interacts with the physical label printer (Zebra / TSC / SATO / DYMO).
 *
 * Mirrors the architecture of [PrinterManager] but handles ZPL/TSPL byte streams rather
 * than ESC/POS receipt data.
 *
 * ### Responsibilities
 * 1. **Connection lifecycle** — delegates to [LabelPrinterPort]; exposes [connectionState].
 * 2. **FIFO command queue** — buffers label jobs while disconnected; drains on reconnect.
 * 3. **Retry on failure** — up to [MAX_RETRIES] attempts with exponential back-off.
 *
 * @param port  Platform-specific [LabelPrinterPort] implementation.
 * @param scope Optional [CoroutineScope]; defaults to [SupervisorJob] + [Dispatchers.IO].
 */
class LabelPrinterManager(
    private val port: LabelPrinterPort,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    // ── Public state ──────────────────────────────────────────────────────────

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    /** Hot [StateFlow] reflecting the current label printer transport state. */
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // ── Internal queue ────────────────────────────────────────────────────────

    /** ZPL/TSPL job queue: Pair<ByteArray (commands), Boolean (isZpl)>. */
    private val commandQueue: Channel<LabelJob> = Channel(capacity = Channel.UNLIMITED)

    private data class LabelJob(val commands: ByteArray, val isZpl: Boolean)

    // ── Connection management ─────────────────────────────────────────────────

    /** Opens the label printer transport. No-op if already connected. */
    suspend fun connect(): Result<Unit> {
        if (_connectionState.value is ConnectionState.Connected) return Result.success(Unit)

        _connectionState.value = ConnectionState.Connecting
        return port.connect().fold(
            onSuccess = {
                _connectionState.value = ConnectionState.Connected
                startQueueDrain()
                Result.success(Unit)
            },
            onFailure = { err ->
                _connectionState.value = ConnectionState.Error(err)
                Result.failure(err)
            },
        )
    }

    /** Closes the label printer transport. */
    suspend fun disconnect(): Result<Unit> {
        val result = port.disconnect()
        _connectionState.value = ConnectionState.Disconnected
        return result
    }

    // ── Print operations ─────────────────────────────────────────────────────

    /**
     * Delivers a ZPL command byte array to the label printer.
     *
     * If connected, transmits immediately with retry. If disconnected, enqueues.
     */
    suspend fun printZpl(commands: ByteArray): Result<Unit> =
        if (_connectionState.value is ConnectionState.Connected) {
            printZplWithRetry(commands)
        } else {
            commandQueue.send(LabelJob(commands, isZpl = true))
            Result.success(Unit)
        }

    /**
     * Delivers a TSPL command byte array to the label printer.
     *
     * If connected, transmits immediately with retry. If disconnected, enqueues.
     */
    suspend fun printTspl(commands: ByteArray): Result<Unit> =
        if (_connectionState.value is ConnectionState.Connected) {
            printTsplWithRetry(commands)
        } else {
            commandQueue.send(LabelJob(commands, isZpl = false))
            Result.success(Unit)
        }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun startQueueDrain() {
        scope.launch {
            for (job in commandQueue) {
                if (job.isZpl) printZplWithRetry(job.commands)
                else printTsplWithRetry(job.commands)
            }
        }
    }

    private suspend fun printZplWithRetry(commands: ByteArray): Result<Unit> =
        retryOnFailure { port.printZpl(commands) }

    private suspend fun printTsplWithRetry(commands: ByteArray): Result<Unit> =
        retryOnFailure { port.printTspl(commands) }

    private suspend fun retryOnFailure(operation: suspend () -> Result<Unit>): Result<Unit> {
        var lastError: Throwable? = null
        repeat(MAX_RETRIES) { attempt ->
            val result = operation()
            if (result.isSuccess) return result
            lastError = result.exceptionOrNull()

            if (_connectionState.value !is ConnectionState.Connected) connect()

            val waitMs = minOf(RETRY_DELAY_MS * (1L shl attempt), MAX_DELAY_MS)
            delay(waitMs)
        }
        _connectionState.value = ConnectionState.Error(
            lastError ?: Exception("Label print failed after $MAX_RETRIES retries"),
        )
        return Result.failure(lastError ?: Exception("Label print failed after $MAX_RETRIES retries"))
    }

    companion object {
        const val MAX_RETRIES = 3
        const val RETRY_DELAY_MS = 500L
        const val MAX_DELAY_MS = 4_000L
    }
}
