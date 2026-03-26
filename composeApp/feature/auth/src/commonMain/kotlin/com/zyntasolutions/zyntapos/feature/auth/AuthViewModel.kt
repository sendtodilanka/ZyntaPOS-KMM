package com.zyntasolutions.zyntapos.feature.auth

import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsEvents
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsParams
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.RegisterRepository
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository
import com.zyntasolutions.zyntapos.domain.repository.StoreRepository
import com.zyntasolutions.zyntapos.domain.repository.UserRepository
import com.zyntasolutions.zyntapos.domain.usecase.auth.LoginUseCase
import com.zyntasolutions.zyntapos.domain.usecase.auth.LogoutUseCase
import com.zyntasolutions.zyntapos.domain.usecase.auth.QuickSwitchUserUseCase
import com.zyntasolutions.zyntapos.domain.usecase.auth.ValidatePinUseCase
import com.zyntasolutions.zyntapos.feature.auth.mvi.AuthEffect
import com.zyntasolutions.zyntapos.feature.auth.mvi.AuthIntent
import com.zyntasolutions.zyntapos.feature.auth.mvi.AuthState
import com.zyntasolutions.zyntapos.security.audit.SecurityAuditLogger
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock

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
    private val storeRepository: StoreRepository? = null,
    private val userRepository: UserRepository? = null,
    private val validatePinUseCase: ValidatePinUseCase? = null,
    private val quickSwitchUserUseCase: QuickSwitchUserUseCase? = null,
    private val auditLogger: SecurityAuditLogger,
    private val analytics: AnalyticsTracker,
) : BaseViewModel<AuthState, AuthIntent, AuthEffect>(AuthState()) {

    init {
        analytics.logScreenView("Auth", "AuthViewModel")
        observeSession()
        loadRememberMe()
        loadAvailableStores()
        loadPersistedStoreSelection()
    }

    /** Loads persisted "remember me" preference and pre-fills the saved email. */
    private fun loadRememberMe() {
        viewModelScope.launch {
            val remembered = settingsRepository.get(KEY_REMEMBER_ME) == "true"
            val savedEmail = if (remembered) settingsRepository.get(KEY_SAVED_EMAIL).orEmpty() else ""
            updateState { copy(rememberMe = remembered, email = savedEmail) }
        }
    }

    /** Restores the previously selected store ID so the selector pre-populates on next login. */
    private fun loadPersistedStoreSelection() {
        viewModelScope.launch {
            val storeId = settingsRepository.get(KEY_SELECTED_STORE_ID).orEmpty().ifBlank { null }
            if (storeId != null) updateState { copy(selectedStoreId = storeId) }
        }
    }

    /** Loads available stores for the multi-store login selector (G4). */
    private fun loadAvailableStores() {
        val repo = storeRepository ?: return
        viewModelScope.launch {
            repo.getAllStores().collectLatest { stores ->
                updateState { copy(availableStores = stores) }
            }
        }
    }

    override suspend fun handleIntent(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.EmailChanged        -> onEmailChanged(intent.email)
            is AuthIntent.PasswordChanged     -> onPasswordChanged(intent.password)
            is AuthIntent.TogglePasswordVisibility -> togglePasswordVisibility()
            is AuthIntent.LoginClicked        -> onLoginClicked()
            is AuthIntent.RememberMeToggled   -> updateState { copy(rememberMe = intent.checked) }
            is AuthIntent.ForgotPasswordClicked -> showForgotPasswordDialog()
            is AuthIntent.ShowForgotPasswordDialog -> showForgotPasswordDialog()
            is AuthIntent.DismissForgotPasswordDialog -> dismissForgotPasswordDialog()
            is AuthIntent.ForgotPasswordEmailChanged -> updateState { copy(forgotPasswordEmail = intent.email, forgotPasswordError = null) }
            is AuthIntent.SubmitForgotPassword -> submitForgotPassword()
            is AuthIntent.DismissError        -> updateState { copy(error = null, lockedOutUntilMs = null) }
            is AuthIntent.StoreSelected       -> updateState { copy(selectedStoreId = intent.storeId) }
            // PIN Lock / Quick-Switch
            is AuthIntent.PinEntered          -> onPinEntered(intent.pin)
            is AuthIntent.OpenQuickSwitch     -> onOpenQuickSwitch()
            is AuthIntent.QuickSwitchSelected -> onQuickSwitchSelected(intent.userId)
            is AuthIntent.QuickSwitchPinEntered -> onQuickSwitchPinEntered(intent.pin)
            is AuthIntent.CancelQuickSwitch   -> updateState { copy(isQuickSwitchMode = false, quickSwitchTargetId = null, pinError = null) }
            is AuthIntent.DismissPinError     -> updateState { copy(pinError = null) }
            // Biometric Fallback (G4)
            is AuthIntent.CheckBiometricAvailability -> checkBiometricAvailability()
            is AuthIntent.SetBiometricEnabled -> setBiometricEnabled(intent.enabled)
            is AuthIntent.RequestBiometricAuth -> updateState { copy(isBiometricAuthenticating = true) }
            is AuthIntent.BiometricAuthSuccess -> onBiometricAuthSuccess()
            is AuthIntent.BiometricAuthFailed -> updateState {
                copy(isBiometricAuthenticating = false, pinError = intent.error)
            }
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
                // Persist selected store for next login pre-population
                settingsRepository.set(KEY_SELECTED_STORE_ID, s.selectedStoreId.orEmpty())
                updateState { copy(isLoading = false, lockedOutUntilMs = null) }
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
                analytics.logEvent(AnalyticsEvents.LOGIN, mapOf(
                    AnalyticsParams.METHOD to "email",
                    "success" to "false",
                ))
                val isBruteForced = auditLogger.isLoginBruteForced(s.email)
                val errorMessage = if (isBruteForced) {
                    "Too many failed attempts. Account locked."
                } else {
                    result.exception.message ?: "Login failed. Please try again."
                }
                // G4: Set lockout expiry for countdown UI (5-min window matches SecurityAuditLogger default)
                val lockedUntilMs = if (isBruteForced) {
                    Clock.System.now().toEpochMilliseconds() + (5 * 60 * 1000L)
                } else null
                updateState { copy(isLoading = false, error = errorMessage, lockedOutUntilMs = lockedUntilMs) }
            }
            is Result.Loading -> Unit // transitional — no UI action needed
        }
    }

    private fun showForgotPasswordDialog() {
        updateState {
            copy(
                showForgotPasswordDialog = true,
                forgotPasswordEmail = email, // pre-fill from login form
                forgotPasswordSent = false,
                forgotPasswordError = null,
            )
        }
    }

    private fun dismissForgotPasswordDialog() {
        updateState {
            copy(
                showForgotPasswordDialog = false,
                forgotPasswordEmail = "",
                forgotPasswordSent = false,
                forgotPasswordError = null,
            )
        }
    }

    private fun submitForgotPassword() {
        val email = currentState.forgotPasswordEmail.trim()
        if (email.isBlank() || !email.isValidEmail()) {
            updateState { copy(forgotPasswordError = "Enter a valid email address") }
            return
        }
        // API call deferred to backend implementation — for now mark as sent.
        updateState { copy(forgotPasswordSent = true, forgotPasswordError = null) }
    }

    // ── PIN Lock / Quick-Switch handlers ──────────────────────────────────────

    /**
     * Validates the current user's PIN on the lock screen.
     * On success: emits [AuthEffect.PinUnlocked]. On failure: sets [AuthState.pinError].
     */
    private suspend fun onPinEntered(pin: String) {
        val session = authRepository.getSession().first() ?: return
        val useCase = validatePinUseCase ?: return
        updateState { copy(isPinValidating = true, pinError = null) }

        when (val result = useCase(session.id, pin)) {
            is Result.Success -> {
                if (result.data == true) {
                    updateState { copy(isPinValidating = false, pinError = null) }
                    sendEffect(AuthEffect.PinUnlocked)
                } else {
                    updateState { copy(isPinValidating = false, pinError = "Incorrect PIN") }
                }
            }
            is Result.Error -> {
                updateState { copy(isPinValidating = false, pinError = result.exception.message ?: "PIN validation failed") }
            }
            is Result.Loading -> Unit
        }
    }

    /**
     * Opens the quick-switch user picker. Loads active users with PINs at the current store.
     */
    private suspend fun onOpenQuickSwitch() {
        val session = authRepository.getSession().first() ?: run {
            sendEffect(AuthEffect.NavigateToLogin)
            return
        }
        val repo = userRepository ?: run {
            // Fallback: no UserRepository available — navigate to full login
            sendEffect(AuthEffect.NavigateToLogin)
            return
        }
        when (val result = repo.getQuickSwitchCandidates(session.storeId)) {
            is Result.Success -> {
                // Exclude the current user from the list
                val candidates = result.data.filter { it.id != session.id }
                if (candidates.isEmpty()) {
                    sendEffect(AuthEffect.NavigateToLogin)
                } else {
                    updateState { copy(isQuickSwitchMode = true, quickSwitchCandidates = candidates, quickSwitchTargetId = null, pinError = null) }
                }
            }
            is Result.Error -> sendEffect(AuthEffect.NavigateToLogin)
            is Result.Loading -> Unit
        }
    }

    /** User picked a specific employee from the quick-switch list. */
    private fun onQuickSwitchSelected(userId: String) {
        updateState { copy(quickSwitchTargetId = userId, pinError = null) }
    }

    /** Validates PIN for the selected quick-switch target and switches session. */
    private suspend fun onQuickSwitchPinEntered(pin: String) {
        val targetId = currentState.quickSwitchTargetId ?: return
        val useCase = quickSwitchUserUseCase ?: return
        updateState { copy(isPinValidating = true, pinError = null) }

        when (val result = useCase(targetId, pin)) {
            is Result.Success -> {
                analytics.logEvent(AnalyticsEvents.LOGIN, mapOf(AnalyticsParams.METHOD to "quick_switch"))
                analytics.setUserId(result.data.email)
                updateState { copy(isPinValidating = false, isQuickSwitchMode = false, quickSwitchTargetId = null, pinError = null) }
                sendEffect(AuthEffect.QuickSwitchCompleted(result.data.name))
            }
            is Result.Error -> {
                updateState { copy(isPinValidating = false, pinError = result.exception.message ?: "Quick switch failed") }
            }
            is Result.Loading -> Unit
        }
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

    // ── Biometric Fallback (G4) ─────────────────────────────────────────────

    /**
     * Checks if biometric hardware is available and loads the user's preference.
     * Called on PIN lock screen init. The actual biometric API call happens in the
     * platform layer (Android: BiometricPrompt, JVM: no-op) — the ViewModel only
     * manages state. The UI dispatches BiometricAuthSuccess/Failed after the prompt.
     */
    private suspend fun checkBiometricAvailability() {
        val enabled = settingsRepository.get("auth.biometric_enabled") == "true"
        // Platform availability is detected by the UI layer and passed via state.
        // Here we just load the persisted preference.
        updateState { copy(isBiometricEnabled = enabled) }
    }

    private suspend fun setBiometricEnabled(enabled: Boolean) {
        settingsRepository.set("auth.biometric_enabled", enabled.toString())
        updateState { copy(isBiometricEnabled = enabled) }
    }

    private suspend fun onBiometricAuthSuccess() {
        updateState { copy(isBiometricAuthenticating = false, pinError = null) }
        auditLogger.logLoginAttempt(true, "biometric", "", null)
        sendEffect(AuthEffect.PinUnlocked)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun String.isValidEmail(): Boolean {
        return contains("@") && contains(".") && length >= 5
    }

    companion object {
        const val KEY_REMEMBER_ME = "auth.remember_me"
        const val KEY_SAVED_EMAIL = "auth.saved_email"
        const val KEY_SELECTED_STORE_ID = "auth.selected_store_id"
    }
}
