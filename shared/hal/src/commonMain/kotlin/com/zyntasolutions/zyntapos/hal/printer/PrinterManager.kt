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
 * ZentaPOS — Hardware Abstraction Layer
 *
 * Connection states exposed by [PrinterManager.connectionState].
 */
sealed interface ConnectionState {

    /** No active connection; the manager is idle. */
    data object Disconnected : ConnectionState

    /** A connection attempt is in progress. */
    data object Connecting : ConnectionState

    /** The transport is open and the printer is reachable. */
    data object Connected : ConnectionState

    /**
     * An error occurred on the last connect / print attempt.
     *
     * @property cause The underlying exception for logging / display.
     */
    data class Error(val cause: Throwable) : ConnectionState
}

/**
 * ZentaPOS — Hardware Abstraction Layer
 *
 * [PrinterManager] is the single Koin-provided gateway through which all shared
 * business logic (use cases, ViewModels) interacts with the physical thermal
 * printer.
 *
 * ### Responsibilities
 * 1. **Connection lifecycle** — delegates to the injected [PrinterPort];
 *    exposes [connectionState] so UI can show status indicators.
 * 2. **Retry on failure** — if [PrinterPort.print] returns [Result.failure] the
 *    manager retries up to [MAX_RETRIES] times with an exponential back-off
 *    before marking the print job as failed.
 * 3. **Command queue** — while the printer is disconnected, incoming [ByteArray]
 *    commands are queued in an unbounded [Channel] (FIFO). On reconnect the
 *    queue is drained before new commands are accepted.
 *
 * ### Usage (shared ViewModel / UseCase)
 * ```kotlin
 * // Inject PrinterManager via Koin
 * val manager: PrinterManager by inject()
 *
 * // Observe connection state
 * manager.connectionState.collect { state -> updateUiIndicator(state) }
 *
 * // Print receipt (suspends until delivered or all retries exhausted)
 * val result = manager.print(receiptBytes)
 * result.onFailure { showPrinterError(it.message) }
 * ```
 *
 * @param port  The platform-specific [PrinterPort] implementation provided by
 *              the platform Koin module (androidMain / jvmMain).
 * @param scope An optional [CoroutineScope] for the queue drain coroutine;
 *              defaults to a [SupervisorJob] + [Dispatchers.IO] scope.
 */
class PrinterManager(
    private val port: PrinterPort,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    // ── Public state ─────────────────────────────────────────────────────────

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)

    /**
     * Hot [StateFlow] reflecting the current printer transport state.
     * Always safe to collect from the UI layer.
     */
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // ── Internal queue ────────────────────────────────────────────────────────

    /**
     * Unbounded FIFO channel that buffers [ByteArray] print jobs while the
     * printer is disconnected. The drain coroutine is started lazily on the
     * first [connect] call.
     */
    private val commandQueue: Channel<ByteArray> = Channel(capacity = Channel.UNLIMITED)

    // ── Connection management ─────────────────────────────────────────────────

    /**
     * Opens the printer transport.
     *
     * If already connected (state is [ConnectionState.Connected]), this is a
     * no-op that returns [Result.success] immediately. On success the internal
     * command-drain coroutine is (re)started.
     */
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

    /**
     * Closes the printer transport and transitions to [ConnectionState.Disconnected].
     */
    suspend fun disconnect(): Result<Unit> {
        val result = port.disconnect()
        _connectionState.value = ConnectionState.Disconnected
        return result
    }

    // ── Print operations ─────────────────────────────────────────────────────

    /**
     * Delivers a raw ESC/POS [commands] byte array to the printer.
     *
     * * If the printer is **connected**: transmits immediately with up to
     *   [MAX_RETRIES] retry attempts on transient failure.
     * * If the printer is **disconnected** or **connecting**: enqueues the job
     *   in [commandQueue] so it is drained automatically on reconnect.
     *
     * @param commands Raw ESC/POS byte array assembled by a [ReceiptBuilder].
     * @return [Result.success] when delivered; [Result.failure] if all retries fail.
     */
    suspend fun print(commands: ByteArray): Result<Unit> {
        return if (_connectionState.value is ConnectionState.Connected) {
            printWithRetry(commands)
        } else {
            commandQueue.send(commands)
            Result.success(Unit)
        }
    }

    /**
     * Sends a cash-drawer kick pulse, with retry logic identical to [print].
     */
    suspend fun openCashDrawer(): Result<Unit> =
        retryOnFailure { port.openCashDrawer() }

    /**
     * Issues a paper cut command, with retry logic identical to [print].
     */
    suspend fun cutPaper(): Result<Unit> =
        retryOnFailure { port.cutPaper() }

    // ── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Launches a coroutine that drains [commandQueue] whenever a [ByteArray] is
     * available. The coroutine runs for the lifetime of [scope].
     */
    private fun startQueueDrain() {
        scope.launch {
            for (commands in commandQueue) {
                printWithRetry(commands)
            }
        }
    }

    /**
     * Attempts to print [commands] up to [MAX_RETRIES] times. Each retry waits
     * [RETRY_DELAY_MS] * 2^attempt milliseconds (exponential back-off, capped at
     * [MAX_DELAY_MS]).
     */
    private suspend fun printWithRetry(commands: ByteArray): Result<Unit> {
        return retryOnFailure { port.print(commands) }
    }

    /**
     * Generic retry wrapper for any suspend [operation].
     *
     * Retries up to [MAX_RETRIES] times with exponential back-off. Returns
     * [Result.failure] with the last exception if all attempts are exhausted.
     */
    private suspend fun retryOnFailure(operation: suspend () -> Result<Unit>): Result<Unit> {
        var lastError: Throwable? = null
        repeat(MAX_RETRIES) { attempt ->
            val result = operation()
            if (result.isSuccess) return result
            lastError = result.exceptionOrNull()

            // Reconnect before next retry if we've lost the transport
            if (_connectionState.value !is ConnectionState.Connected) {
                connect()
            }

            val waitMs = minOf(RETRY_DELAY_MS * (1L shl attempt), MAX_DELAY_MS)
            delay(waitMs)
        }
        _connectionState.value = ConnectionState.Error(
            lastError ?: Exception("Print failed after $MAX_RETRIES retries"),
        )
        return Result.failure(lastError ?: Exception("Print failed after $MAX_RETRIES retries"))
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        /** Maximum number of delivery attempts before a print job is declared failed. */
        const val MAX_RETRIES = 3

        /** Base delay (ms) before the first retry; doubles on each subsequent attempt. */
        const val RETRY_DELAY_MS = 500L

        /** Upper bound on back-off delay to prevent excessive wait times. */
        const val MAX_DELAY_MS = 4_000L
    }
}
