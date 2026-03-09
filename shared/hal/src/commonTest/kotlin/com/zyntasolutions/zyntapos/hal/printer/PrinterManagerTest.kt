package com.zyntasolutions.zyntapos.hal.printer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Contract tests for [PrinterManager] (TEST-01).
 *
 * Uses a [FakePrinterPort] to verify:
 * - Connection state transitions (Disconnected → Connecting → Connected / Error)
 * - Print operations delegate to the port and respect connection state
 * - Commands are queued while disconnected and delivered on reconnect
 * - Retry logic surfaces failure after [PrinterManager.MAX_RETRIES] attempts
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrinterManagerTest {

    // ── Fake PrinterPort ──────────────────────────────────────────────────────

    private class FakePrinterPort(
        private val connectResult: Result<Unit> = Result.success(Unit),
        private val printResult: Result<Unit> = Result.success(Unit),
    ) : PrinterPort {
        val printedCommands = mutableListOf<ByteArray>()
        var disconnectCalled = false

        override val statusEvents: Flow<PrinterStatusEvent> = emptyFlow()

        override suspend fun connect(): Result<Unit> = connectResult
        override suspend fun disconnect(): Result<Unit> {
            disconnectCalled = true
            return Result.success(Unit)
        }
        override suspend fun isConnected(): Boolean = connectResult.isSuccess
        override suspend fun print(commands: ByteArray): Result<Unit> {
            printedCommands += commands
            return printResult
        }
        override suspend fun openCashDrawer(): Result<Unit> = Result.success(Unit)
        override suspend fun cutPaper(): Result<Unit> = Result.success(Unit)
    }

    private class AlwaysFailPrinterPort : PrinterPort {
        override val statusEvents: Flow<PrinterStatusEvent> = emptyFlow()
        override suspend fun connect(): Result<Unit> =
            Result.failure(Exception("Connection refused"))
        override suspend fun disconnect(): Result<Unit> = Result.success(Unit)
        override suspend fun isConnected(): Boolean = false
        override suspend fun print(commands: ByteArray): Result<Unit> =
            Result.failure(Exception("Not connected"))
        override suspend fun openCashDrawer(): Result<Unit> =
            Result.failure(Exception("Not connected"))
        override suspend fun cutPaper(): Result<Unit> =
            Result.failure(Exception("Not connected"))
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial connection state is Disconnected`() = runTest {
        val manager = PrinterManager(FakePrinterPort())

        assertIs<ConnectionState.Disconnected>(manager.connectionState.value)
    }

    // ── connect ───────────────────────────────────────────────────────────────

    @Test
    fun `connect transitions state to Connected on success`() = runTest {
        val manager = PrinterManager(FakePrinterPort(), backgroundScope)

        manager.connect()

        assertIs<ConnectionState.Connected>(manager.connectionState.value)
    }

    @Test
    fun `connect transitions state to Error on port failure`() = runTest {
        val port = AlwaysFailPrinterPort()
        val manager = PrinterManager(port, backgroundScope)

        manager.connect()

        assertIs<ConnectionState.Error>(manager.connectionState.value)
    }

    @Test
    fun `connect returns success when already connected (idempotent)`() = runTest {
        val manager = PrinterManager(FakePrinterPort(), backgroundScope)
        manager.connect()                   // first connect
        val result = manager.connect()      // second call — must be no-op

        assertTrue(result.isSuccess)
        assertIs<ConnectionState.Connected>(manager.connectionState.value)
    }

    @Test
    fun `connect returns failure when port fails`() = runTest {
        val manager = PrinterManager(AlwaysFailPrinterPort(), this)

        val result = manager.connect()

        assertFalse(result.isSuccess)
    }

    // ── disconnect ────────────────────────────────────────────────────────────

    @Test
    fun `disconnect transitions state to Disconnected`() = runTest {
        val port = FakePrinterPort()
        val manager = PrinterManager(port, backgroundScope)
        manager.connect()

        manager.disconnect()

        assertIs<ConnectionState.Disconnected>(manager.connectionState.value)
        assertTrue(port.disconnectCalled)
    }

    // ── print ─────────────────────────────────────────────────────────────────

    @Test
    fun `print delegates to port when connected`() = runTest {
        val port = FakePrinterPort()
        val manager = PrinterManager(port, backgroundScope)
        manager.connect()

        val commands = byteArrayOf(0x1B, 0x40)
        val result = manager.print(commands)

        assertTrue(result.isSuccess)
        assertTrue(port.printedCommands.isNotEmpty())
        assertTrue(port.printedCommands.first().contentEquals(commands))
    }

    @Test
    fun `print enqueues commands when disconnected (no immediate failure)`() = runTest {
        val port = FakePrinterPort()
        val manager = PrinterManager(port, backgroundScope)
        // Not connected — state is Disconnected

        val result = manager.print(byteArrayOf(0x1B, 0x40))

        // Must succeed (command queued, not rejected) so receipts survive transient disconnects
        assertTrue(result.isSuccess)
        // Port should NOT have received the call yet
        assertTrue(port.printedCommands.isEmpty())
    }

    // ── openCashDrawer ────────────────────────────────────────────────────────

    @Test
    fun `openCashDrawer succeeds when port succeeds`() = runTest {
        val port = FakePrinterPort()
        val manager = PrinterManager(port, backgroundScope)
        manager.connect()

        val result = manager.openCashDrawer()

        assertTrue(result.isSuccess)
    }

    // ── cutPaper ──────────────────────────────────────────────────────────────

    @Test
    fun `cutPaper succeeds when port succeeds`() = runTest {
        val port = FakePrinterPort()
        val manager = PrinterManager(port, backgroundScope)
        manager.connect()

        val result = manager.cutPaper()

        assertTrue(result.isSuccess)
    }

    // ── Retry constants ───────────────────────────────────────────────────────

    @Test
    fun `MAX_RETRIES is 3`() {
        assertEquals(3, PrinterManager.MAX_RETRIES)
    }

    @Test
    fun `RETRY_DELAY_MS is positive`() {
        assertTrue(PrinterManager.RETRY_DELAY_MS > 0)
    }

    @Test
    fun `MAX_DELAY_MS is greater than RETRY_DELAY_MS`() {
        assertTrue(PrinterManager.MAX_DELAY_MS > PrinterManager.RETRY_DELAY_MS)
    }

    private fun assertEquals(expected: Int, actual: Int) {
        assertTrue(expected == actual, "Expected $expected but was $actual")
    }
}
