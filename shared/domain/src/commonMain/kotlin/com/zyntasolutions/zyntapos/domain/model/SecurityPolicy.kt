package com.zyntasolutions.zyntapos.domain.model

/**
 * Platform-wide security policy (Sprint 23 task 23.9).
 *
 * Persisted as five string-encoded keys in the generic `settings` table —
 * see `domain.usecase.settings.SecurityPolicyKeys`. Defaults below match the
 * Sprint 23 spec.
 *
 * @property sessionTimeoutMinutes        Auto-logout window (must be one of
 *                                        [ALLOWED_SESSION_TIMEOUTS]).
 * @property pinComplexity                PIN format requirement.
 * @property failedLoginLockoutAttempts   How many consecutive bad PINs trigger
 *                                        a lockout (must be one of
 *                                        [ALLOWED_LOCKOUT_ATTEMPTS]).
 * @property lockoutDurationMinutes       How long the lockout lasts (must be
 *                                        one of [ALLOWED_LOCKOUT_DURATIONS]).
 * @property biometricEnabled             Whether biometric unlock is offered
 *                                        on supported devices.
 */
data class SecurityPolicy(
    val sessionTimeoutMinutes: Int = 15,
    val pinComplexity: PinComplexity = PinComplexity.SIX_DIGIT,
    val failedLoginLockoutAttempts: Int = 5,
    val lockoutDurationMinutes: Int = 15,
    val biometricEnabled: Boolean = true,
) {
    enum class PinComplexity { FOUR_DIGIT, SIX_DIGIT, ALPHANUMERIC }

    companion object {
        /** Allowed values for [sessionTimeoutMinutes]. */
        val ALLOWED_SESSION_TIMEOUTS: List<Int> = listOf(5, 15, 30, 60)

        /** Allowed values for [failedLoginLockoutAttempts]. */
        val ALLOWED_LOCKOUT_ATTEMPTS: List<Int> = listOf(3, 5, 10)

        /** Allowed values for [lockoutDurationMinutes]. */
        val ALLOWED_LOCKOUT_DURATIONS: List<Int> = listOf(5, 15, 30)
    }
}
