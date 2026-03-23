package com.zyntasolutions.zyntapos.feature.auth.mvi

import androidx.compose.runtime.Immutable
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
)
