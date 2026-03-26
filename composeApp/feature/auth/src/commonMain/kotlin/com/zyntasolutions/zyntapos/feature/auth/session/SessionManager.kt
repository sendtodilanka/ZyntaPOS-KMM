package com.zyntasolutions.zyntapos.feature.auth.session

import com.zyntasolutions.zyntapos.feature.auth.mvi.AuthEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Tracks operator idle time and triggers PIN-lock after a configurable timeout.
 *
 * ### Design
 * - A [CoroutineScope]-based countdown timer is (re)started every time
 *   [onUserInteraction] is called (tap, key press, etc.).
 * - When [sessionTimeoutMs] elapses without any interaction the manager emits
 *   [AuthEffect.ShowPinLock] into [effects] exactly once.
 * - The timer is paused when [pause] is called (e.g., while the PIN screen is
 *   already showing) and resumed via [resume].
 * - [reset] cancels any running timer without emitting — use this on explicit logout.
 *
 * ### Integration
 * Wire [onUserInteraction] to every `Modifier.pointerInput` and `onKeyEvent` in
 * the authenticated scaffold. Observe [effects] in the root composable to show
 * [PinLockScreen] when [AuthEffect.ShowPinLock] is emitted.
 *
 * ### Timeout defaults (configurable via SettingsRepository, Phase 2)
 * | Role           | Default timeout  |
 * |----------------|-----------------|
 * | CASHIER        | 10 minutes       |
 * | STORE_MANAGER  | 20 minutes       |
 * | ADMIN          | 30 minutes       |
 *
 * @param scope            [CoroutineScope] that outlives the authenticated area
 *                         (e.g., tied to the root NavHost composable lifecycle or
 *                         an Application-scoped coroutine scope). Do NOT use
 *                         `viewModelScope` — sessions survive ViewModel recreation.
 * @param sessionTimeoutMs Duration of inactivity in milliseconds before PIN lock.
 *                         Defaults to 15 minutes (900_000 ms).
 */
class SessionManager(
    private val scope: CoroutineScope,
    private val sessionTimeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {

    // ── Effects ───────────────────────────────────────────────────────────────

    private val _effects = MutableSharedFlow<AuthEffect>(extraBufferCapacity = 1)

    /**
     * UI layer collects this to receive [AuthEffect.ShowPinLock] when idle timeout fires.
     */
    val effects: SharedFlow<AuthEffect> = _effects.asSharedFlow()

    // ── Timer state ───────────────────────────────────────────────────────────

    private var timerJob: Job? = null

    /** `true` while the timer is deliberately paused (e.g., PIN screen already visible). */
    private var isPaused: Boolean = false

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Notify the session manager that the user performed an interaction.
     *
     * Call this from every pointer-input and key-event handler in the authenticated
     * scaffold. Each call resets the idle countdown to [sessionTimeoutMs].
     *
     * No-op if the manager is currently [isPaused].
     */
    fun onUserInteraction() {
        if (isPaused) return
        restartTimer()
    }

    /**
     * Start the idle timer from scratch.
     *
     * Call this immediately after a successful login or PIN unlock to begin tracking.
     */
    fun start() {
        isPaused = false
        restartTimer()
    }

    /**
     * Pause the idle timer without emitting any effect.
     *
     * Use when the PIN lock screen is already visible — we do not want to re-emit
     * [AuthEffect.ShowPinLock] while the user is already on that screen.
     */
    fun pause() {
        isPaused = true
        timerJob?.cancel()
        timerJob = null
    }

    /**
     * Resume the idle timer after a [pause].
     *
     * Restarts the full [sessionTimeoutMs] countdown from the current moment.
     */
    fun resume() {
        isPaused = false
        restartTimer()
    }

    /**
     * Cancel any running timer without emitting [AuthEffect.ShowPinLock].
     *
     * Call this on explicit logout to prevent stale PIN-lock emissions.
     */
    fun reset() {
        timerJob?.cancel()
        timerJob = null
        isPaused = false
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun restartTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            // Emit warning 60s before timeout (only if timeout > warning period)
            val warningMs = WARNING_BEFORE_TIMEOUT_MS
            if (sessionTimeoutMs > warningMs) {
                delay(sessionTimeoutMs - warningMs)
                _effects.emit(AuthEffect.SessionTimeoutWarning(secondsRemaining = (warningMs / 1000).toInt()))
                delay(warningMs)
            } else {
                delay(sessionTimeoutMs)
            }
            _effects.emit(AuthEffect.ShowPinLock)
        }
    }

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        /** 15 minutes in milliseconds. */
        const val DEFAULT_TIMEOUT_MS: Long = 15L * 60L * 1_000L

        /** 10 minutes — recommended for Cashier role. */
        const val CASHIER_TIMEOUT_MS: Long = 10L * 60L * 1_000L

        /** 60 seconds warning before timeout. */
        const val WARNING_BEFORE_TIMEOUT_MS: Long = 60L * 1_000L

        /** 20 minutes — recommended for Store Manager role. */
        const val MANAGER_TIMEOUT_MS: Long = 20L * 60L * 1_000L

        /** 30 minutes — recommended for Admin role. */
        const val ADMIN_TIMEOUT_MS: Long = 30L * 60L * 1_000L
    }
}
