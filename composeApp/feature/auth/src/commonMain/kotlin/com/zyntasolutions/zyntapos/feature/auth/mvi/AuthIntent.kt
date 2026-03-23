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

    /** The user tapped "Forgot Password?" link. */
    data object ForgotPasswordClicked : AuthIntent()

    /** Dismisses the current top-level error banner. */
    data object DismissError : AuthIntent()

    /** Multi-store login: user selected a store from the dropdown (G4). */
    data class StoreSelected(val storeId: String?) : AuthIntent()
}
