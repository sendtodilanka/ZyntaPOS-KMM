package com.zyntasolutions.zyntapos.ui.core.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Generic MVI ViewModel base shared by every `:composeApp:feature:*` module.
 *
 * Enforces a consistent **Model–View–Intent** contract across all ZentaPOS screens:
 * - [S] — immutable **State** data class with meaningful defaults.
 * - [I] — sealed **Intent** class representing every user action.
 * - [E] — sealed **Effect** class for one-shot side-effects (navigation, snackbars).
 *
 * ### State model
 * State is held in a [MutableStateFlow] and updated atomically via [updateState].
 * The UI collects [state] which always reflects the latest snapshot.
 *
 * ### Effect model
 * Effects are enqueued via a [Channel] (buffered). `Channel` semantics guarantee
 * that every effect is delivered **exactly once**, even if the UI collector is
 * momentarily suspended. Unlike `SharedFlow(replay=0)`, slow collectors cannot
 * miss emissions.
 *
 * ### Intent dispatch
 * All intents funnel through [dispatch], which launches a coroutine inside
 * [viewModelScope]. Concrete ViewModels implement [handleIntent] as a
 * `suspend` function, enabling direct `suspend` call sites without nesting.
 *
 * ### Usage
 * ```kotlin
 * class PosViewModel(
 *     private val addItemUseCase: AddItemToCartUseCase,
 * ) : BaseViewModel<PosState, PosIntent, PosEffect>(PosState()) {
 *
 *     override suspend fun handleIntent(intent: PosIntent) {
 *         when (intent) {
 *             is PosIntent.AddToCart -> onAddToCart(intent.product)
 *             …
 *         }
 *     }
 * }
 * ```
 *
 * ### Threading
 * [updateState] is safe to call from any dispatcher — [MutableStateFlow.update]
 * is lock-free and atomic. [sendEffect] dispatches within [viewModelScope].
 *
 * @param S  Immutable UI state. Must have meaningful field defaults so the
 *           **initial emission renders correctly before any intent is processed**.
 * @param I  Sealed class representing every user-driven intent for this screen.
 * @param E  Sealed class of one-shot effects: navigation, toasts, hardware triggers.
 * @param initialState  The state emitted before the first intent is processed.
 */
abstract class BaseViewModel<S, I, E>(initialState: S) : ViewModel() {

    // ── State ─────────────────────────────────────────────────────────────────

    private val _state = MutableStateFlow(initialState)

    /**
     * Primary state stream. The UI collects this via
     * `collectAsStateWithLifecycle()` (lifecycle-safe) or `collectAsState()`.
     */
    val state: StateFlow<S> = _state.asStateFlow()

    /**
     * Snapshot of the current state. Use this inside `handleIntent` for
     * synchronous reads without collecting the Flow.
     */
    protected val currentState: S get() = _state.value

    /**
     * Atomically transform and publish new state.
     *
     * The lambda receiver is the **current** state snapshot; return the
     * updated copy. [MutableStateFlow.update] guarantees atomic compare-and-set.
     *
     * ```kotlin
     * updateState { copy(isLoading = true, error = null) }
     * ```
     */
    protected fun updateState(transform: S.() -> S) = _state.update { it.transform() }

    // ── Effects ───────────────────────────────────────────────────────────────

    private val _effects = Channel<E>(Channel.BUFFERED)

    /**
     * One-shot event stream. Collect via `LaunchedEffect(Unit) { effects.collect(…) }`.
     *
     * Channel semantics ensure every effect is delivered exactly once,
     * regardless of collector speed.
     */
    val effects = _effects.receiveAsFlow()

    /**
     * Enqueue a one-shot effect for the UI.
     *
     * Safe to call from any coroutine context. The send is dispatched within
     * [viewModelScope] to respect ViewModel lifecycle boundaries.
     *
     * @param effect The effect to deliver to the UI layer.
     */
    protected fun sendEffect(effect: E) {
        viewModelScope.launch { _effects.send(effect) }
    }

    // ── Intent dispatch ───────────────────────────────────────────────────────

    /**
     * UI entry-point for all user interactions.
     *
     * Launches [handleIntent] inside [viewModelScope]. All intents are processed
     * sequentially within their coroutine — concurrent intent handling is safe
     * because state mutations go through atomic [updateState].
     *
     * @param intent The [I] subtype originating from a UI event.
     */
    fun dispatch(intent: I) {
        viewModelScope.launch { handleIntent(intent) }
    }

    /**
     * Override in each concrete ViewModel to map intents to state/effect changes.
     *
     * Runs inside a coroutine — all `suspend` calls, [updateState], and
     * [sendEffect] are safe here. Use a `when` exhaustive expression for
     * compile-time coverage of every [I] subtype.
     *
     * @param intent The intent to handle.
     */
    protected abstract suspend fun handleIntent(intent: I)
}
