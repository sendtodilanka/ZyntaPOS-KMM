package com.zyntasolutions.zyntapos.feature.auth

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.UserRepository
import com.zyntasolutions.zyntapos.domain.validation.UserValidator
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlinx.datetime.Clock

data class SignUpState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val isLoading: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val error: String? = null,
)

sealed class SignUpIntent {
    data class NameChanged(val name: String) : SignUpIntent()
    data class EmailChanged(val email: String) : SignUpIntent()
    data class PasswordChanged(val password: String) : SignUpIntent()
    data class ConfirmPasswordChanged(val confirmPassword: String) : SignUpIntent()
    data object TogglePasswordVisibility : SignUpIntent()
    data object SignUpClicked : SignUpIntent()
}

sealed class SignUpEffect {
    data object NavigateToLogin : SignUpEffect()
    data class ShowError(val message: String) : SignUpEffect()
}

class SignUpViewModel(
    private val userRepository: UserRepository,
) : BaseViewModel<SignUpState, SignUpIntent, SignUpEffect>(SignUpState()) {

    override suspend fun handleIntent(intent: SignUpIntent) {
        when (intent) {
            is SignUpIntent.NameChanged -> updateState {
                copy(name = intent.name, nameError = null, error = null)
            }
            is SignUpIntent.EmailChanged -> updateState {
                copy(
                    email = intent.email,
                    emailError = if (intent.email.isNotBlank()) UserValidator.validateEmail(intent.email) else null,
                    error = null,
                )
            }
            is SignUpIntent.PasswordChanged -> updateState {
                copy(
                    password = intent.password,
                    passwordError = if (intent.password.isNotBlank()) UserValidator.validatePassword(intent.password) else null,
                    error = null,
                )
            }
            is SignUpIntent.ConfirmPasswordChanged -> updateState {
                copy(
                    confirmPassword = intent.confirmPassword,
                    confirmPasswordError = if (intent.confirmPassword.isNotBlank()) UserValidator.validateConfirmPassword(intent.confirmPassword, password) else null,
                    error = null,
                )
            }
            is SignUpIntent.TogglePasswordVisibility -> updateState {
                copy(isPasswordVisible = !isPasswordVisible)
            }
            is SignUpIntent.SignUpClicked -> onSignUpClicked()
        }
    }

    private suspend fun onSignUpClicked() {
        val s = currentState

        val nameError            = UserValidator.validateName(s.name)
        val emailError           = UserValidator.validateEmail(s.email)
        val passwordError        = UserValidator.validatePassword(s.password)
        val confirmPasswordError = UserValidator.validateConfirmPassword(s.confirmPassword, s.password)

        if (nameError != null || emailError != null || passwordError != null || confirmPasswordError != null) {
            updateState {
                copy(
                    nameError = nameError,
                    emailError = emailError,
                    passwordError = passwordError,
                    confirmPasswordError = confirmPasswordError,
                )
            }
            return
        }

        updateState { copy(isLoading = true, error = null) }

        val now = Clock.System.now()
        val userId = generateUserId()
        val user = User(
            id = userId,
            name = s.name,
            email = s.email,
            role = Role.ADMIN,
            storeId = "default-store",
            isActive = true,
            createdAt = now,
            updatedAt = now,
        )

        when (val result = userRepository.create(user, s.password)) {
            is Result.Success -> {
                updateState { copy(isLoading = false) }
                sendEffect(SignUpEffect.NavigateToLogin)
            }
            is Result.Error -> {
                updateState {
                    copy(
                        isLoading = false,
                        error = result.exception.message ?: "Registration failed. Please try again.",
                    )
                }
            }
            is Result.Loading -> Unit
        }
    }

    private fun generateUserId(): String {
        // Simple UUID-like ID generation using random chars
        val chars = "0123456789abcdef"
        fun segment(len: Int) = (1..len).map { chars.random() }.joinToString("")
        return "${segment(8)}-${segment(4)}-4${segment(3)}-${segment(4)}-${segment(12)}"
    }
}
