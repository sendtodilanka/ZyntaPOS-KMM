package com.zyntasolutions.zyntapos.feature.auth

import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.usecase.auth.LoginUseCase
import com.zyntasolutions.zyntapos.domain.usecase.auth.LogoutUseCase
import com.zyntasolutions.zyntapos.feature.auth.mvi.AuthEffect
import com.zyntasolutions.zyntapos.feature.auth.mvi.AuthIntent
import com.zyntasolutions.zyntapos.feature.auth.mvi.AuthState
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ViewModel for the authentication flow.
 *
 * Handles all [AuthIntent] dispatched from [screen.LoginScreen] and [screen.PinLockScreen], * producing [AuthState] updates and one-shot [AuthEffect] emissions for navigation.
 *
 * ### Responsibilities
 * - Validates email/password fields inline (before calling the use case).
 * - Delegates credential verification to [LoginUseCase].
 * - Observes [AuthRepository.getSession] to detect session changes (e.g., token expiry).
 * - Emits [AuthEffect.NavigateToDashboard] or [AuthEffect.NavigateToRegisterGuard] on success.
 * - Never exposes raw exceptions to the UI — all errors are mapped to [AuthState.error].
 *
 * @param loginUseCase    Authenticates email + password credentials.
 * @param logoutUseCase   Destroys the current session (used for logout from other screens).
 * @param authRepository  Provides live [AuthRepository.getSession] flow.
 */
class AuthViewModel(
    private val loginUseCase: LoginUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val authRepository: AuthRepository,
) : BaseViewModel<AuthState, AuthIntent, AuthEffect>(AuthState()) {

    init {
        // Observe session changes driven by external events (token expiry, forced logout).
        // Handled separately — the UI navigation is driven by effects, not state.
        observeSession()
    }

    override suspend fun handleIntent(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.EmailChanged        -> onEmailChanged(intent.email)
            is AuthIntent.PasswordChanged     -> onPasswordChanged(intent.password)
            is AuthIntent.TogglePasswordVisibility -> togglePasswordVisibility()
            is AuthIntent.LoginClicked        -> onLoginClicked()
            is AuthIntent.RememberMeToggled   -> updateState { copy(rememberMe = intent.checked) }
            is AuthIntent.ForgotPasswordClicked -> handleForgotPassword()
            is AuthIntent.DismissError        -> updateState { copy(error = null) }
        }
    }

    // ── Intent handlers ───────────────────────────────────────────────────────

    private fun onEmailChanged(email: String) {
        updateState {
            copy(
                email = email,
                emailError = if (email.isNotBlank() && !email.isValidEmail()) "Enter a valid email" else null,
                error = null,
            )
        }
    }

    private fun onPasswordChanged(password: String) {
        updateState {
            copy(
                password = password,
                passwordError = if (password.isNotBlank() && password.length < 6) "Password too short" else null,
                error = null,
            )
        }
    }

    private fun togglePasswordVisibility() {
        updateState { copy(isPasswordVisible = !isPasswordVisible) }
    }

    private suspend fun onLoginClicked() {
        val s = currentState

        // ── Client-side validation ────────────────────────────────────────────
        val emailError = when {
            s.email.isBlank()       -> "Email is required"
            !s.email.isValidEmail() -> "Enter a valid email address"
            else                    -> null
        }
        val passwordError = when {
            s.password.isBlank()      -> "Password is required"
            s.password.length < 6     -> "Password must be at least 6 characters"
            else                      -> null
        }

        if (emailError != null || passwordError != null) {
            updateState { copy(emailError = emailError, passwordError = passwordError) }
            return
        }

        // ── Network / local call ──────────────────────────────────────────────
        updateState { copy(isLoading = true, error = null) }

        when (val result = loginUseCase(email = s.email, password = s.password)) {
            is Result.Success -> {
                updateState { copy(isLoading = false) }
                // TODO (Sprint 20): check open register session; emit NavigateToRegisterGuard if none
                sendEffect(AuthEffect.NavigateToDashboard)
            }
            is Result.Error -> {
                updateState {
                    copy(
                        isLoading = false,
                        error = result.exception.message ?: "Login failed. Please try again.",
                    )
                }
            }
            is Result.Loading -> Unit // transitional — no UI action needed
        }
    }

    private fun handleForgotPassword() {
        // Phase 1: show informational error. Password reset UI is Phase 2.
        sendEffect(AuthEffect.ShowError("Password reset is not available offline. Contact your administrator."))
    }

    // ── Session observer ──────────────────────────────────────────────────────

    private fun observeSession() {
        viewModelScope.launch {
            authRepository.getSession().collectLatest { user ->
                if (user == null) {
                    // Session was cleared externally (e.g., token expired).
                    // LoginScreen will be shown via SessionGuard.
                    updateState { AuthState() } // reset form
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun String.isValidEmail(): Boolean {
        return contains("@") && contains(".") && length >= 5
    }
}
