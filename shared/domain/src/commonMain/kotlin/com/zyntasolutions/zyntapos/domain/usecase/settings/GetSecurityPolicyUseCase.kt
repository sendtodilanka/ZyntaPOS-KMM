package com.zyntasolutions.zyntapos.domain.usecase.settings

import com.zyntasolutions.zyntapos.domain.model.SecurityPolicy
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository

/**
 * Loads the current [SecurityPolicy] from the generic settings store.
 *
 * Missing or invalid values fall back to the defaults defined on
 * [SecurityPolicy] — meaning a freshly-installed app shows the canonical
 * "15 min session, 6-digit PIN, 5-attempt lockout, 15-minute cooldown,
 * biometric on" baseline before the admin touches anything.
 *
 * Stored values that don't match the allow-list are also coerced to the
 * default — defensive against forward-compat drift if the spec ever
 * changes the allowed sets.
 *
 * @param settingsRepository Generic key-value persistence backing.
 */
class GetSecurityPolicyUseCase(private val settingsRepository: SettingsRepository) {

    suspend operator fun invoke(): SecurityPolicy {
        val default = SecurityPolicy()

        val sessionTimeout = settingsRepository.get(SecurityPolicyKeys.SESSION_TIMEOUT_MINUTES)
            ?.toIntOrNull()
            ?.takeIf { it in SecurityPolicy.ALLOWED_SESSION_TIMEOUTS }
            ?: default.sessionTimeoutMinutes

        val pinComplexity = settingsRepository.get(SecurityPolicyKeys.PIN_COMPLEXITY)
            ?.let { stored -> SecurityPolicy.PinComplexity.entries.firstOrNull { it.name == stored } }
            ?: default.pinComplexity

        val lockoutAttempts = settingsRepository.get(SecurityPolicyKeys.FAILED_LOGIN_LOCKOUT_ATTEMPTS)
            ?.toIntOrNull()
            ?.takeIf { it in SecurityPolicy.ALLOWED_LOCKOUT_ATTEMPTS }
            ?: default.failedLoginLockoutAttempts

        val lockoutDuration = settingsRepository.get(SecurityPolicyKeys.LOCKOUT_DURATION_MINUTES)
            ?.toIntOrNull()
            ?.takeIf { it in SecurityPolicy.ALLOWED_LOCKOUT_DURATIONS }
            ?: default.lockoutDurationMinutes

        val biometric = settingsRepository.get(SecurityPolicyKeys.BIOMETRIC_ENABLED)
            ?.let { !it.equals("false", ignoreCase = true) }
            ?: default.biometricEnabled

        return SecurityPolicy(
            sessionTimeoutMinutes = sessionTimeout,
            pinComplexity = pinComplexity,
            failedLoginLockoutAttempts = lockoutAttempts,
            lockoutDurationMinutes = lockoutDuration,
            biometricEnabled = biometric,
        )
    }
}
