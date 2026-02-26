package com.zyntasolutions.zyntapos.domain.validation

/**
 * Domain-layer validator for user account fields.
 *
 * Validates raw string values supplied from registration and profile forms.
 * All methods are pure functions (no I/O, no side effects) and return a
 * nullable error message (`null` = valid, non-null = error string to show
 * in the UI).
 *
 * ### Rules enforced
 * - **Name:** Required (must not be blank).
 * - **Email:** Required; must contain `@`, a `.`, and be ≥ 5 characters.
 * - **Password:** Required; must be ≥ 6 characters.
 * - **Phone:** If provided, must be non-blank (format checked server-side).
 *
 * Server-side uniqueness checks (duplicate email) are handled by use cases
 * via [com.zyntasolutions.zyntapos.domain.repository.UserRepository.getByEmail].
 *
 * ### Usage
 * ```kotlin
 * val emailError = UserValidator.validateEmail(email)
 * if (emailError != null) showError(emailError)
 * ```
 */
object UserValidator {

    /**
     * Validates a display name.
     *
     * @return `null` if valid; an error message string otherwise.
     */
    fun validateName(name: String): String? =
        if (name.isBlank()) "Name is required" else null

    /**
     * Validates an email address.
     *
     * Phase 1 enforces a structural sanity check (contains `@` and `.`,
     * total length ≥ 5). Full RFC-5322 validation is delegated to the server.
     *
     * @return `null` if valid; an error message string otherwise.
     */
    fun validateEmail(email: String): String? = when {
        email.isBlank()                                       -> "Email is required"
        !email.contains("@") || !email.contains(".") ||
            email.length < 5                                  -> "Enter a valid email address"
        else                                                  -> null
    }

    /**
     * Validates a new password on registration or password-change flows.
     *
     * ### Rules
     * - Must not be blank.
     * - Must be at least 6 characters (Phase 1 minimum; bump to 8+ in Phase 2).
     *
     * @return `null` if valid; an error message string otherwise.
     */
    fun validatePassword(password: String): String? = when {
        password.isBlank()    -> "Password is required"
        password.length < 6   -> "Password must be at least 6 characters"
        else                  -> null
    }

    /**
     * Validates a password-confirmation field against the original password.
     *
     * @param confirmPassword The value entered in the "Confirm password" field.
     * @param password        The value of the "Password" field.
     * @return `null` if valid; an error message string otherwise.
     */
    fun validateConfirmPassword(confirmPassword: String, password: String): String? = when {
        confirmPassword.isBlank()      -> "Please confirm your password"
        confirmPassword != password    -> "Passwords do not match"
        else                           -> null
    }

    /**
     * Validates a phone number.
     *
     * Phase 1 only checks that a value was entered; format validation
     * (country code, digit count) is handled server-side.
     *
     * @return `null` if valid; an error message string otherwise.
     */
    fun validatePhone(phone: String): String? =
        if (phone.isBlank()) "Phone is required" else null
}
