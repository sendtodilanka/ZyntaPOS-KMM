package com.zyntasolutions.zyntapos.feature.auth

import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsEvents
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsParams
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.RegisterRepository
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository
import com.zyntasolutions.zyntapos.domain.usecase.auth.LoginUseCase
import com.zyntasolutions.zyntapos.domain.usecase.auth.LogoutUseCase
import com.zyntasolutions.zyntapos.feature.auth.mvi.AuthEffect
import com.zyntasolutions.zyntapos.feature.auth.mvi.AuthIntent
import com.zyntasolutions.zyntapos.feature.auth.mvi.AuthState
import com.zyntasolutions.zyntapos.security.audit.SecurityAuditLogger
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
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
 * @param loginUseCase       Authenticates email + password credentials.
 * @param logoutUseCase      Destroys the current session (used for logout from other screens).
 * @param authRepository     Provides live [AuthRepository.getSession] flow.
 * @param registerRepository Provides [RegisterRepository.getActive] to check for an open
 *                           cash register session after login (Sprint 20 guard).
 *                           When null (e.g., legacy tests), the guard is skipped and
 *                           [AuthEffect.NavigateToDashboard] is emitted unconditionally.
 */
class AuthViewModel(
    private val loginUseCase: LoginUseCase,
    private val _logoutUseCase: LogoutUseCase,
    private val authRepository: AuthRepository,
    private val registerRepository: RegisterRepository? = null,
    private val settingsRepository: SettingsRepository,
    private val auditLogger: SecurityAuditLogger,
    private val analytics: AnalyticsTracker,
) : BaseViewModel<AuthState, AuthIntent, AuthEffect>(AuthState()) {

    init {
        analytics.logScreenView("Auth", "AuthViewModel")
        observeSession()
        loadRememberMe()
    }

    /** Loads persisted "remember me" preference and pre-fills the saved email. */
    private fun loadRememberMe() {
        viewModelScope.launch {
            val remembered = settingsRepository.get(KEY_REMEMBER_ME) == "true"
            val savedEmail = if (remembered) settingsRepository.get(KEY_SAVED_EMAIL).orEmpty() else ""
            updateState { copy(rememberMe = remembered, email = savedEmail) }
        }
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
                auditLogger.logLoginAttempt(true, s.email, "", null)
                analytics.logEvent(AnalyticsEvents.LOGIN, mapOf(AnalyticsParams.METHOD to "email"))
                analytics.setUserId(s.email)
                // Persist "Remember Me" preference
                settingsRepository.set(KEY_REMEMBER_ME, s.rememberMe.toString())
                if (s.rememberMe) {
                    settingsRepository.set(KEY_SAVED_EMAIL, s.email)
                } else {
                    settingsRepository.set(KEY_SAVED_EMAIL, "")
                }
                updateState { copy(isLoading = false) }
                // Sprint 20: Check whether a cash register session is currently open.
                // If no session is open, redirect to the RegisterGuard screen so the
                // cashier must open their till before accessing the POS.
                // registerRepository is nullable for backward compatibility with legacy tests.
                if (registerRepository != null) {
                    val activeSession = registerRepository.getActive().first()
                    if (activeSession == null) {
                        sendEffect(AuthEffect.NavigateToRegisterGuard)
                    } else {
                        sendEffect(AuthEffect.NavigateToDashboard)
                    }
                } else {
                    sendEffect(AuthEffect.NavigateToDashboard)
                }
            }
            is Result.Error -> {
                auditLogger.logLoginAttempt(false, s.email, "", null)
                val errorMessage = if (auditLogger.isLoginBruteForced(s.email)) {
                    "Too many failed attempts. Please try again in 5 minutes."
                } else {
                    result.exception.message ?: "Login failed. Please try again."
                }
                updateState { copy(isLoading = false, error = errorMessage) }
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

    companion object {
        const val KEY_REMEMBER_ME = "auth.remember_me"
        const val KEY_SAVED_EMAIL = "auth.saved_email"
    }
}
