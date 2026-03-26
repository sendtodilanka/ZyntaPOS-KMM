package com.zyntasolutions.zyntapos.feature.auth.mvi

/**
 * All user-driven intentions that can mutate [AuthState] or trigger [AuthEffect].
 *
 * Processed exclusively by [com.zyntasolutions.zyntapos.feature.auth.AuthViewModel].
 * The UI layer converts raw events (button clicks, text changes) into these sealed
 * subtypes before dispatching them — keeping UI composables free of business logic.
 */
sealed class AuthIntent {

    /** Fired on every keystroke in the email input field. */
    data class EmailChanged(val email: String) : AuthIntent()

    /** Fired on every keystroke in the password input field. */
    data class PasswordChanged(val password: String) : AuthIntent()

    /** Toggles the visibility of the password field between masked and plaintext. */
    data object TogglePasswordVisibility : AuthIntent()

    /** The user tapped the primary Login / "Login to Dashboard" button. */
    data object LoginClicked : AuthIntent()

    /** The "Remember Me" checkbox was toggled. */
    data class RememberMeToggled(val checked: Boolean) : AuthIntent()

    /** The user tapped "Forgot Password?" link — opens the dialog. */
    data object ForgotPasswordClicked : AuthIntent()

    /** Opens the forgot-password dialog explicitly. */
    data object ShowForgotPasswordDialog : AuthIntent()

    /** Dismisses the forgot-password dialog and resets its fields. */
    data object DismissForgotPasswordDialog : AuthIntent()

    /** Fired on every keystroke in the forgot-password email field. */
    data class ForgotPasswordEmailChanged(val email: String) : AuthIntent()

    /** Submits the forgot-password request with the current email. */
    data object SubmitForgotPassword : AuthIntent()

    /** Dismisses the current top-level error banner. */
    data object DismissError : AuthIntent()

    /** Multi-store login: user selected a store from the dropdown (G4). */
    data class StoreSelected(val storeId: String?) : AuthIntent()

    // ── PIN Lock / Quick-Switch (G4) ───────────────────────────────────────

    /** The user entered their PIN on the lock screen to re-authenticate. */
    data class PinEntered(val pin: String) : AuthIntent()

    /** The user tapped "Different user?" — open quick-switch picker. */
    data object OpenQuickSwitch : AuthIntent()

    /** The user selected a different employee from the quick-switch list. */
    data class QuickSwitchSelected(val userId: String) : AuthIntent()

    /** The user entered a PIN for the quick-switch target. */
    data class QuickSwitchPinEntered(val pin: String) : AuthIntent()

    /** Cancel quick-switch and return to the normal PIN lock. */
    data object CancelQuickSwitch : AuthIntent()

    /** Dismiss the PIN error message. */
    data object DismissPinError : AuthIntent()

    // ── Biometric Fallback (G4) ──────────────────────────────────────────

    /** Check if biometric hardware is available and whether the user has enabled biometric. */
    data object CheckBiometricAvailability : AuthIntent()

    /** Toggle biometric fallback enabled/disabled preference. */
    data class SetBiometricEnabled(val enabled: Boolean) : AuthIntent()

    /** Request biometric authentication on the PIN lock screen. */
    data object RequestBiometricAuth : AuthIntent()

    /** Biometric authentication succeeded — unlock the screen. */
    data object BiometricAuthSuccess : AuthIntent()

    /** Biometric authentication failed (wrong finger, etc.). */
    data class BiometricAuthFailed(val error: String) : AuthIntent()
}
