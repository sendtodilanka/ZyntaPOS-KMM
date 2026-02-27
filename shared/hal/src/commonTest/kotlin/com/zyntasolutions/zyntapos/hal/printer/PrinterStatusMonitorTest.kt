package com.zyntasolutions.zyntapos.hal.printer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [PrinterStatusMonitor].
 *
 * Uses a fake [PrinterPort] that exposes a [MutableSharedFlow] to simulate
 * real-time printer status events.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrinterStatusMonitorTest {

    // ── Fake port ─────────────────────────────────────────────────────────────

    private class FakePrinterPort : PrinterPort {
        val eventBus = MutableSharedFlow<PrinterStatusEvent>(extraBufferCapacity = 64)

        override val statusEvents: Flow<PrinterStatusEvent> = eventBus

        override suspend fun connect() = Result.success(Unit)
        override suspend fun disconnect() = Result.success(Unit)
        override suspend fun isConnected() = true
        override suspend fun print(commands: ByteArray) = Result.success(Unit)
        override suspend fun openCashDrawer() = Result.success(Unit)
        override suspend fun cutPaper() = Result.success(Unit)
    }

    // ── 1. Initial state ──────────────────────────────────────────────────────

    @Test
    fun `initial status is all-false`() = runTest(UnconfinedTestDispatcher()) {
        val port = FakePrinterPort()
        val monitor = PrinterStatusMonitor(port, this)

        val status = monitor.statusState.value
        assertFalse(status.isOnline)
        assertFalse(status.isPaperOut)
        assertFalse(status.isPaperLow)
        assertFalse(status.isCoverOpen)
        assertFalse(status.isDrawerOpen)
    }

    // ── 2. Online / Offline ───────────────────────────────────────────────────

    @Test
    fun `Online event sets isOnline to true`() = runTest(UnconfinedTestDispatcher()) {
        val port = FakePrinterPort()
        val monitor = PrinterStatusMonitor(port, this)

        port.eventBus.emit(PrinterStatusEvent.Online)

        assertTrue(monitor.statusState.value.isOnline)
    }

    @Test
    fun `Offline event sets isOnline to false`() = runTest(UnconfinedTestDispatcher()) {
        val port = FakePrinterPort()
        val monitor = PrinterStatusMonitor(port, this)

        port.eventBus.emit(PrinterStatusEvent.Online)
        port.eventBus.emit(PrinterStatusEvent.Offline)

        assertFalse(monitor.statusState.value.isOnline)
    }

    // ── 3. Paper ──────────────────────────────────────────────────────────────

    @Test
    fun `PaperOut event sets isPaperOut to true`() = runTest(UnconfinedTestDispatcher()) {
        val port = FakePrinterPort()
        val monitor = PrinterStatusMonitor(port, this)

        port.eventBus.emit(PrinterStatusEvent.PaperOut)

        assertTrue(monitor.statusState.value.isPaperOut)
    }

    @Test
    fun `PaperLow event sets isPaperLow to true`() = runTest(UnconfinedTestDispatcher()) {
        val port = FakePrinterPort()
        val monitor = PrinterStatusMonitor(port, this)

        port.eventBus.emit(PrinterStatusEvent.PaperLow)

        assertTrue(monitor.statusState.value.isPaperLow)
    }

    @Test
    fun `Online event clears paper-out state`() = runTest(UnconfinedTestDispatcher()) {
        val port = FakePrinterPort()
        val monitor = PrinterStatusMonitor(port, this)

        port.eventBus.emit(PrinterStatusEvent.PaperOut)
        port.eventBus.emit(PrinterStatusEvent.Online)

        // Online does not clear PaperOut — paper must physically be refilled
        // The UI should show both isOnline=true and isPaperOut=true simultaneously.
        val status = monitor.statusState.value
        assertTrue(status.isOnline)
        assertTrue(status.isPaperOut)
    }

    // ── 4. Cover ──────────────────────────────────────────────────────────────

    @Test
    fun `CoverOpen event sets isCoverOpen to true`() = runTest(UnconfinedTestDispatcher()) {
        val port = FakePrinterPort()
        val monitor = PrinterStatusMonitor(port, this)

        port.eventBus.emit(PrinterStatusEvent.CoverOpen)

        assertTrue(monitor.statusState.value.isCoverOpen)
    }

    @Test
    fun `CoverClosed event clears isCoverOpen`() = runTest(UnconfinedTestDispatcher()) {
        val port = FakePrinterPort()
        val monitor = PrinterStatusMonitor(port, this)

        port.eventBus.emit(PrinterStatusEvent.CoverOpen)
        port.eventBus.emit(PrinterStatusEvent.CoverClosed)

        assertFalse(monitor.statusState.value.isCoverOpen)
    }

    // ── 5. Cash drawer ────────────────────────────────────────────────────────

    @Test
    fun `DrawerOpen event sets isDrawerOpen to true`() = runTest(UnconfinedTestDispatcher()) {
        val port = FakePrinterPort()
        val monitor = PrinterStatusMonitor(port, this)

        port.eventBus.emit(PrinterStatusEvent.DrawerOpen)

        assertTrue(monitor.statusState.value.isDrawerOpen)
    }

    @Test
    fun `DrawerClosed event clears isDrawerOpen`() = runTest(UnconfinedTestDispatcher()) {
        val port = FakePrinterPort()
        val monitor = PrinterStatusMonitor(port, this)

        port.eventBus.emit(PrinterStatusEvent.DrawerOpen)
        port.eventBus.emit(PrinterStatusEvent.DrawerClosed)

        assertFalse(monitor.statusState.value.isDrawerOpen)
    }

    // ── 6. Multiple events ────────────────────────────────────────────────────

    @Test
    fun `multiple events accumulate correctly`() = runTest(UnconfinedTestDispatcher()) {
        val port = FakePrinterPort()
        val monitor = PrinterStatusMonitor(port, this)

        port.eventBus.emit(PrinterStatusEvent.Online)
        port.eventBus.emit(PrinterStatusEvent.PaperLow)
        port.eventBus.emit(PrinterStatusEvent.CoverOpen)

        val status = monitor.statusState.value
        assertTrue(status.isOnline)
        assertTrue(status.isPaperLow)
        assertTrue(status.isCoverOpen)
        assertFalse(status.isPaperOut)
        assertFalse(status.isDrawerOpen)
    }

    // ── 7. StateFlow emissions ────────────────────────────────────────────────

    @Test
    fun `statusState emits updated value after event`() = runTest(UnconfinedTestDispatcher()) {
        val port = FakePrinterPort()
        val monitor = PrinterStatusMonitor(port, this)

        port.eventBus.emit(PrinterStatusEvent.PaperOut)

        val emitted = monitor.statusState.first { it.isPaperOut }
        assertTrue(emitted.isPaperOut)
    }
}
