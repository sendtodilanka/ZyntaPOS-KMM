package com.zyntasolutions.zyntapos.hal.printer

import com.zyntasolutions.zyntapos.core.result.HalException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Contract tests for [NullPrinterPort] (TEST-01).
 *
 * Verifies that the null-object implementation satisfies the [PrinterPort] contract:
 * - All print/connect operations fail with a descriptive [HalException]
 * - [disconnect] is a safe no-op (returns success)
 * - [isConnected] always returns false
 * - [statusEvents] emits nothing (empty flow)
 */
class NullPrinterPortTest {

    private val port = NullPrinterPort()

    // ── connect ───────────────────────────────────────────────────────────────

    @Test
    fun `connect returns failure with HalException`() = runTest {
        val result = port.connect()

        assertFalse(result.isSuccess, "NullPrinterPort.connect() must fail")
        assertIs<HalException>(result.exceptionOrNull())
    }

    @Test
    fun `connect failure message mentions settings`() = runTest {
        val result = port.connect()
        val message = result.exceptionOrNull()?.message.orEmpty()

        assertTrue(
            message.contains("not configured", ignoreCase = true),
            "Error message should mention 'not configured', got: $message",
        )
    }

    // ── disconnect ────────────────────────────────────────────────────────────

    @Test
    fun `disconnect returns success (safe no-op)`() = runTest {
        val result = port.disconnect()

        assertTrue(result.isSuccess, "NullPrinterPort.disconnect() must succeed (no-op)")
    }

    @Test
    fun `disconnect is idempotent`() = runTest {
        // Calling disconnect twice must never throw or return failure
        assertTrue(port.disconnect().isSuccess)
        assertTrue(port.disconnect().isSuccess)
    }

    // ── isConnected ───────────────────────────────────────────────────────────

    @Test
    fun `isConnected returns false`() = runTest {
        assertFalse(port.isConnected(), "NullPrinterPort is never connected")
    }

    // ── print ─────────────────────────────────────────────────────────────────

    @Test
    fun `print returns failure with HalException`() = runTest {
        val result = port.print(byteArrayOf(0x1B, 0x40))

        assertFalse(result.isSuccess)
        assertIs<HalException>(result.exceptionOrNull())
    }

    @Test
    fun `print with empty commands returns failure`() = runTest {
        val result = port.print(byteArrayOf())

        assertFalse(result.isSuccess)
        assertIs<HalException>(result.exceptionOrNull())
    }

    // ── openCashDrawer ────────────────────────────────────────────────────────

    @Test
    fun `openCashDrawer returns failure with HalException`() = runTest {
        val result = port.openCashDrawer()

        assertFalse(result.isSuccess)
        assertIs<HalException>(result.exceptionOrNull())
    }

    // ── cutPaper ──────────────────────────────────────────────────────────────

    @Test
    fun `cutPaper returns failure with HalException`() = runTest {
        val result = port.cutPaper()

        assertFalse(result.isSuccess)
        assertIs<HalException>(result.exceptionOrNull())
    }

    // ── statusEvents ──────────────────────────────────────────────────────────

    @Test
    fun `statusEvents emits nothing (empty flow)`() = runTest {
        val events = mutableListOf<PrinterStatusEvent>()
        val job = launch { port.statusEvents.toList(events) }
        job.cancel()

        assertEquals(0, events.size, "NullPrinterPort.statusEvents must be empty")
    }

    // ── Error message uniqueness (each op names itself) ───────────────────────

    @Test
    fun `each operation names itself in the error message`() = runTest {
        val connectMsg  = port.connect().exceptionOrNull()?.message.orEmpty()
        val printMsg    = port.print(byteArrayOf(0x00)).exceptionOrNull()?.message.orEmpty()
        val drawerMsg   = port.openCashDrawer().exceptionOrNull()?.message.orEmpty()
        val cutMsg      = port.cutPaper().exceptionOrNull()?.message.orEmpty()

        assertTrue(connectMsg.contains("connect"),      "connect error should mention 'connect'")
        assertTrue(printMsg.contains("print"),          "print error should mention 'print'")
        assertTrue(drawerMsg.contains("openCashDrawer"),"drawer error should mention 'openCashDrawer'")
        assertTrue(cutMsg.contains("cutPaper"),         "cutPaper error should mention 'cutPaper'")
    }
}
