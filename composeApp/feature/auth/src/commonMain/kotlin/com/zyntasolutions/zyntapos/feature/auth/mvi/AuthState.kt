package com.zyntasolutions.zyntapos.feature.auth.mvi

import androidx.compose.runtime.Immutable
import com.zyntasolutions.zyntapos.domain.model.QuickSwitchCandidate
import com.zyntasolutions.zyntapos.domain.model.Store

/**
 * Represents the complete UI state for the authentication flow.
 *
 * Consumed by [com.zyntasolutions.zyntapos.feature.auth.AuthViewModel] and
 * rendered by [com.zyntasolutions.zyntapos.feature.auth.screen.LoginScreen].
 *
 * All fields have safe defaults so an initial emission renders a blank form.
 *
 * @property isLoading    `true` while a login network/local call is in progress.
 * @property email        Current value of the email input field.
 * @property password     Current value of the password input field.
 * @property emailError   Inline validation error for the email field, or `null` if valid.
 * @property passwordError Inline validation error for the password field, or `null` if valid.
 * @property isPasswordVisible `true` when the password field shows plaintext.
 * @property rememberMe   Whether the "Remember Me" checkbox is checked.
 * @property error        Top-level error message (login failure, network error), or `null`.
 */
@Immutable
data class AuthState(
    val isLoading: Boolean = false,
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val isPasswordVisible: Boolean = false,
    val rememberMe: Boolean = false,
    val error: String? = null,
    /** Available stores for multi-store login selector (G4). Empty = single-store mode. */
    val availableStores: List<Store> = emptyList(),
    /** Selected store ID for multi-store login. Null = use user's default store. */
    val selectedStoreId: String? = null,
    /**
     * Epoch-millisecond timestamp when the brute-force lockout expires (G4).
     * Non-null while the user is locked out after too many failed attempts.
     * The UI renders a live countdown from this value.
     */
    val lockedOutUntilMs: Long? = null,
    // ── PIN Lock / Quick-Switch ────────────────────────────────────────────
    /** True while validating a PIN on the lock screen. */
    val isPinValidating: Boolean = false,
    /** Error message from a failed PIN attempt. */
    val pinError: String? = null,
    /** True when the quick-switch user picker is showing. */
    val isQuickSwitchMode: Boolean = false,
    /** Available users for quick-switch at the current store. */
    val quickSwitchCandidates: List<QuickSwitchCandidate> = emptyList(),
    /** The user selected from the quick-switch picker (PIN entry for this user). */
    val quickSwitchTargetId: String? = null,
    // ── Forgot Password Dialog ───────────────────────────────────────────
    /** True when the forgot-password dialog is visible. */
    val showForgotPasswordDialog: Boolean = false,
    /** Current value of the email field inside the forgot-password dialog. */
    val forgotPasswordEmail: String = "",
    /** True after the reset request was accepted (shows confirmation message). */
    val forgotPasswordSent: Boolean = false,
    /** Inline validation error inside the forgot-password dialog, or null. */
    val forgotPasswordError: String? = null,
    // ── Biometric Fallback (G4) ──────────────────────────────────────────
    /** True when biometric authentication (fingerprint/Face ID) is available on this device. */
    val isBiometricAvailable: Boolean = false,
    /** True when the user has opted to use biometric as a PIN lock fallback. */
    val isBiometricEnabled: Boolean = false,
    /** True while waiting for biometric authentication result. */
    val isBiometricAuthenticating: Boolean = false,
)
