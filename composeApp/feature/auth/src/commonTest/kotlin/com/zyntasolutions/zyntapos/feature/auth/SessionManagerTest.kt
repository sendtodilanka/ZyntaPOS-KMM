package com.zyntasolutions.zyntapos.feature.auth

import com.zyntasolutions.zyntapos.feature.auth.mvi.AuthEffect
import com.zyntasolutions.zyntapos.feature.auth.session.SessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import app.cash.turbine.test

/**
 * Unit tests for [SessionManager].
 *
 * Verifies idle timer behaviour using [TestScope] + [advanceTimeBy] so tests
 * run in virtual time without any real wall-clock delay.
 *
 * All tests use a reduced [SHORT_TIMEOUT_MS] (200 ms virtual) to keep them fast.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {

    companion object {
        /** Short virtual timeout used in tests (200 ms). */
        const val SHORT_TIMEOUT_MS = 200L
    }

    // ── Timeout fires ─────────────────────────────────────────────────────────

    @Test
    fun `ShowPinLock emitted after sessionTimeoutMs of inactivity`() = runTest {
        val testScope = TestScope(StandardTestDispatcher())
        val manager = SessionManager(scope = testScope, sessionTimeoutMs = SHORT_TIMEOUT_MS)

        manager.effects.test {
            manager.start()
            testScope.advanceTimeBy(SHORT_TIMEOUT_MS + 1)
            assertEquals(AuthEffect.ShowPinLock, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── No emission before timeout ────────────────────────────────────────────

    @Test
    fun `No effect emitted before timeout elapses`() = runTest {
        val testScope = TestScope(StandardTestDispatcher())
        val manager = SessionManager(scope = testScope, sessionTimeoutMs = SHORT_TIMEOUT_MS)

        manager.effects.test {
            manager.start()
            testScope.advanceTimeBy(SHORT_TIMEOUT_MS - 10) // just before timeout
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Interaction resets timer ──────────────────────────────────────────────

    @Test
    fun `onUserInteraction resets timeout so no lock fires at original deadline`() = runTest {
        val testScope = TestScope(StandardTestDispatcher())
        val manager = SessionManager(scope = testScope, sessionTimeoutMs = SHORT_TIMEOUT_MS)

        manager.effects.test {
            manager.start()
            testScope.advanceTimeBy(SHORT_TIMEOUT_MS - 50) // 50 ms before deadline
            manager.onUserInteraction()                     // reset timer
            testScope.advanceTimeBy(SHORT_TIMEOUT_MS - 10) // original deadline passed, new not yet
            expectNoEvents()                                // no lock — timer was reset
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ShowPinLock fires after full timeout following interaction reset`() = runTest {
        val testScope = TestScope(StandardTestDispatcher())
        val manager = SessionManager(scope = testScope, sessionTimeoutMs = SHORT_TIMEOUT_MS)

        manager.effects.test {
            manager.start()
            testScope.advanceTimeBy(SHORT_TIMEOUT_MS - 50)
            manager.onUserInteraction() // reset to full timeout
            testScope.advanceTimeBy(SHORT_TIMEOUT_MS + 1)  // advance past new deadline
            assertEquals(AuthEffect.ShowPinLock, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Pause / Resume ────────────────────────────────────────────────────────

    @Test
    fun `onUserInteraction is no-op while paused`() = runTest {
        val testScope = TestScope(StandardTestDispatcher())
        val manager = SessionManager(scope = testScope, sessionTimeoutMs = SHORT_TIMEOUT_MS)

        manager.effects.test {
            manager.start()
            manager.pause()
            testScope.advanceTimeBy(SHORT_TIMEOUT_MS + 100) // timer was cancelled by pause
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Resume restarts timer and fires ShowPinLock after full timeout`() = runTest {
        val testScope = TestScope(StandardTestDispatcher())
        val manager = SessionManager(scope = testScope, sessionTimeoutMs = SHORT_TIMEOUT_MS)

        manager.effects.test {
            manager.start()
            manager.pause()
            testScope.advanceTimeBy(SHORT_TIMEOUT_MS + 10) // no lock during pause
            manager.resume()
            testScope.advanceTimeBy(SHORT_TIMEOUT_MS + 1) // now fires
            assertEquals(AuthEffect.ShowPinLock, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    @Test
    fun `reset cancels timer so no effect is emitted even after timeout elapses`() = runTest {
        val testScope = TestScope(StandardTestDispatcher())
        val manager = SessionManager(scope = testScope, sessionTimeoutMs = SHORT_TIMEOUT_MS)

        manager.effects.test {
            manager.start()
            testScope.advanceTimeBy(SHORT_TIMEOUT_MS - 20)
            manager.reset()
            testScope.advanceTimeBy(SHORT_TIMEOUT_MS + 100) // past original deadline
            expectNoEvents()                                  // timer was cancelled by reset
            cancelAndIgnoreRemainingEvents()
        }
    }
}
