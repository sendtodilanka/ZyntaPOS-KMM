package com.zyntasolutions.zyntapos.core.mvi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── MVI Marker Interfaces ──────────────────────────────────────────────────────

/**
 * Marker interface for all MVI UI state objects.
 *
 * Implementations are data classes (or data objects for states with no fields).
 * Must be immutable — every state change produces a **new** instance via `copy()`.
 */
interface UiState

/**
 * Marker interface for all MVI UI intents (user actions / events).
 *
 * Implementations are sealed classes with one sub-class per user action.
 * Named in verb form: `LoadProducts`, `SearchQueryChanged(query)`, etc.
 */
interface UiIntent

/**
 * Marker interface for MVI side-effects (one-shot events consumed by the UI exactly once).
 *
 * Used for navigation triggers, Snackbar messages, and dialog shows that must not
 * replay on recomposition.
 */
interface UiEffect

// ── Base ViewModel ─────────────────────────────────────────────────────────────

/**
 * Platform-agnostic MVI ViewModel base class for ZentaPOS.
 *
 * This class does **not** extend the Jetpack/AndroidX `ViewModel` — it is a pure
 * Kotlin coroutine-based state container. Feature modules wrap it in the
 * platform lifecycle via Koin `viewModelOf {}` which calls [close] on scope end.
 *
 * ### Architecture
 * ```
 *  ┌─────────┐  Intent  ┌──────────────┐  State  ┌────────────┐
 *  │   UI    │ ───────▶ │ BaseViewModel│ ───────▶ │ Composable │
 *  │         │          │  (MVI logic) │          │  (render)  │
 *  └─────────┘          └──────┬───────┘          └────────────┘
 *                              │ Effect (one-shot)
 *                              ▼
 *                        LaunchedEffect collector
 * ```
 *
 * @param S State type — must implement [UiState].
 * @param I Intent type — must implement [UiIntent].
 * @param E Effect type — must implement [UiEffect].
 * @param initialState The starting state emitted immediately to new collectors.
 */
abstract class BaseViewModel<S : UiState, I : UiIntent, E : UiEffect>(
    initialState: S,
) : AutoCloseable {

    /** Coroutine scope tied to the ViewModel lifecycle. Cancelled on [close]. */
    protected val viewModelScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ── State ─────────────────────────────────────────────────────────────────

    private val _state = MutableStateFlow(initialState)

    /**
     * The current UI state as a [StateFlow].
     * Collect in Composables via `collectAsStateWithLifecycle()` or `collectAsState()`.
     */
    val state: StateFlow<S> = _state.asStateFlow()

    /** Snapshot of the current state value. Use inside [onIntent] for business logic. */
    protected val currentState: S get() = _state.value

    // ── Effects ───────────────────────────────────────────────────────────────

    private val _effect = MutableSharedFlow<E>(
        replay = 0,
        extraBufferCapacity = 16,
    )

    /**
     * One-shot side-effects as a [SharedFlow].
     * Collect in a `LaunchedEffect(Unit)` block — effects are not replayed on
     * recomposition.
     */
    val effect: SharedFlow<E> = _effect.asSharedFlow()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Processes an incoming [UiIntent].
     *
     * Override in subclasses to implement feature-specific intent handling.
     * Call [setState] to produce new states and [sendEffect] for one-shot events.
     *
     * ```kotlin
     * override fun onIntent(intent: PosIntent) = when (intent) {
     *     is PosIntent.AddToCart -> handleAddToCart(intent.product)
     *     is PosIntent.ClearCart -> setState { copy(cartItems = emptyList()) }
     * }
     * ```
     */
    abstract fun onIntent(intent: I)

    // ── Protected helpers ──────────────────────────────────────────────────────

    /**
     * Updates the state atomically using the [reducer] function.
     *
     * ```kotlin
     * setState { copy(isLoading = true) }
     * ```
     */
    protected fun setState(reducer: S.() -> S) {
        _state.value = _state.value.reducer()
    }

    /**
     * Emits a one-shot [UiEffect] to the UI.
     *
     * Launches in [viewModelScope] so callers are never blocked.
     */
    protected fun sendEffect(effect: E) {
        viewModelScope.launch {
            _effect.emit(effect)
        }
    }

    /**
     * Launches a coroutine in [viewModelScope] with the provided [block].
     * Use this for all async ViewModel work to ensure proper lifecycle cancellation.
     */
    protected fun launch(block: suspend CoroutineScope.() -> Unit) =
        viewModelScope.launch(block = block)

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Cancels [viewModelScope] and cleans up resources.
     * Called automatically by Koin `viewModelOf {}` when the owning screen leaves composition.
     */
    override fun close() {
        viewModelScope.cancel()
        onCleared()
    }

    /**
     * Optional override for subclasses that need to perform cleanup on destruction
     * (e.g., closing HAL connections, stopping timers).
     */
    protected open fun onCleared() = Unit
}
