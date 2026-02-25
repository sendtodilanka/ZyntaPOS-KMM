package com.zyntasolutions.zyntapos.ui.core.mvi

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// BaseViewModelTest
// Verifies that BaseViewModel<S,I,E> correctly manages state, effects, and
// intent dispatch using a minimal concrete test double.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class BaseViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // ── Minimal test double ───────────────────────────────────────────────────

    /** Immutable state for the test ViewModel. */
    private data class TestState(
        val counter: Int = 0,
        val label: String = "",
        val isLoading: Boolean = false,
        val error: String? = null,
    )

    /** Sealed intents for the test ViewModel. */
    private sealed interface TestIntent {
        data object Increment : TestIntent
        data object Decrement : TestIntent
        data class SetLabel(val text: String) : TestIntent
        data object StartLoading : TestIntent
        data object FinishLoading : TestIntent
        data class TriggerError(val message: String) : TestIntent
        data class EmitEffect(val payload: String) : TestIntent
        data class EmitTwoEffects(val first: String, val second: String) : TestIntent
        data object ConcurrentIncrements : TestIntent
    }

    /** Sealed effects for the test ViewModel. */
    private sealed interface TestEffect {
        data class Notify(val payload: String) : TestEffect
        data object NavigateBack : TestEffect
    }

    /**
     * Concrete ViewModel that exercises all BaseViewModel capabilities.
     */
    private inner class TestViewModel : BaseViewModel<TestState, TestIntent, TestEffect>(TestState()) {
        override suspend fun handleIntent(intent: TestIntent) {
            when (intent) {
                is TestIntent.Increment   -> updateState { copy(counter = counter + 1) }
                is TestIntent.Decrement   -> updateState { copy(counter = counter - 1) }
                is TestIntent.SetLabel    -> updateState { copy(label = intent.text) }
                is TestIntent.StartLoading -> updateState { copy(isLoading = true) }
                is TestIntent.FinishLoading -> updateState { copy(isLoading = false) }
                is TestIntent.TriggerError -> updateState { copy(error = intent.message) }
                is TestIntent.EmitEffect -> sendEffect(TestEffect.Notify(intent.payload))
                is TestIntent.EmitTwoEffects -> {
                    sendEffect(TestEffect.Notify(intent.first))
                    sendEffect(TestEffect.Notify(intent.second))
                }
                is TestIntent.ConcurrentIncrements -> {
                    // Simulate concurrent state updates inside a single intent handler
                    repeat(5) { updateState { copy(counter = counter + 1) } }
                }
            }
        }
    }

    private lateinit var viewModel: TestViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = TestViewModel()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state is emitted correctly`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.state.value
        assertEquals(0, state.counter)
        assertEquals("", state.label)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    // ── updateState ───────────────────────────────────────────────────────────

    @Test
    fun `Increment intent increments counter`() = runTest {
        viewModel.dispatch(TestIntent.Increment)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.counter)
    }

    @Test
    fun `Decrement intent decrements counter`() = runTest {
        viewModel.dispatch(TestIntent.Increment)
        viewModel.dispatch(TestIntent.Increment)
        viewModel.dispatch(TestIntent.Decrement)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.counter)
    }

    @Test
    fun `SetLabel updates label field`() = runTest {
        viewModel.dispatch(TestIntent.SetLabel("Hello ZyntaPOS"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Hello ZyntaPOS", viewModel.state.value.label)
    }

    @Test
    fun `multiple updateState calls in one intent are applied in order`() = runTest {
        viewModel.dispatch(TestIntent.ConcurrentIncrements)
        testDispatcher.scheduler.advanceUntilIdle()

        // 5 increments from initial 0 → 5
        assertEquals(5, viewModel.state.value.counter)
    }

    @Test
    fun `sequential intents produce cumulative state`() = runTest {
        repeat(3) { viewModel.dispatch(TestIntent.Increment) }
        viewModel.dispatch(TestIntent.SetLabel("batch"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, viewModel.state.value.counter)
        assertEquals("batch", viewModel.state.value.label)
    }

    @Test
    fun `isLoading transitions through StartLoading and FinishLoading`() = runTest {
        viewModel.dispatch(TestIntent.StartLoading)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.isLoading)

        viewModel.dispatch(TestIntent.FinishLoading)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `TriggerError stores error message in state`() = runTest {
        viewModel.dispatch(TestIntent.TriggerError("Something went wrong"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Something went wrong", viewModel.state.value.error)
    }

    // ── state StateFlow is a hot flow ─────────────────────────────────────────

    @Test
    fun `state StateFlow always holds the latest value`() = runTest {
        viewModel.dispatch(TestIntent.SetLabel("first"))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.dispatch(TestIntent.SetLabel("second"))
        testDispatcher.scheduler.advanceUntilIdle()

        // New collector immediately gets the current value
        assertEquals("second", viewModel.state.value.label)
    }

    // ── sendEffect / effects channel ──────────────────────────────────────────

    @Test
    fun `EmitEffect sends effect to collector`() = runTest {
        viewModel.effects.test {
            viewModel.dispatch(TestIntent.EmitEffect("ping"))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is TestEffect.Notify)
            assertEquals("ping", (effect as TestEffect.Notify).payload)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `EmitTwoEffects delivers both effects in order`() = runTest {
        viewModel.effects.test {
            viewModel.dispatch(TestIntent.EmitTwoEffects("alpha", "beta"))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect1 = awaitItem()
            assertTrue(effect1 is TestEffect.Notify)
            assertEquals("alpha", (effect1 as TestEffect.Notify).payload)

            val effect2 = awaitItem()
            assertTrue(effect2 is TestEffect.Notify)
            assertEquals("beta", (effect2 as TestEffect.Notify).payload)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `effects channel buffers effects when collector is slow`() = runTest {
        // Dispatch two effects before starting the Turbine collector
        viewModel.dispatch(TestIntent.EmitEffect("buffered-1"))
        viewModel.dispatch(TestIntent.EmitEffect("buffered-2"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            val e1 = awaitItem()
            val e2 = awaitItem()
            assertTrue(e1 is TestEffect.Notify)
            assertTrue(e2 is TestEffect.Notify)
            assertEquals("buffered-1", (e1 as TestEffect.Notify).payload)
            assertEquals("buffered-2", (e2 as TestEffect.Notify).payload)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── dispatch delegates to handleIntent ────────────────────────────────────

    @Test
    fun `dispatch launches handleIntent inside viewModelScope`() = runTest {
        // Verify dispatch is non-blocking and eventually updates state
        viewModel.dispatch(TestIntent.Increment)
        // State is not yet updated (coroutine hasn't run)
        testDispatcher.scheduler.advanceUntilIdle()
        // Now state should be updated
        assertEquals(1, viewModel.state.value.counter)
    }

    @Test
    fun `dispatch handles multiple intents sequentially`() = runTest {
        viewModel.dispatch(TestIntent.Increment)
        viewModel.dispatch(TestIntent.Increment)
        viewModel.dispatch(TestIntent.Increment)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, viewModel.state.value.counter)
    }
}
