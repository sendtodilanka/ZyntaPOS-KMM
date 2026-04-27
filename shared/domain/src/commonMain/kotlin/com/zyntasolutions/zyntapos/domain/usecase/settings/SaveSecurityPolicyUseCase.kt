package com.zyntasolutions.zyntapos.domain.usecase.settings

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.SecurityPolicy
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository

/**
 * Persists every field of a [SecurityPolicy] (Sprint 23 task 23.9).
 *
 * Writes are NOT atomic — [SettingsRepository.set] is per-key — but each
 * value is validated against its allow-list before any persistence occurs,
 * so the only failure modes are (a) caller passes an out-of-spec value
 * (rejected up-front with a [ValidationException], no writes happen) or
 * (b) the underlying repo errors mid-batch (the first error is propagated
 * and subsequent writes are skipped).
 *
 * @param settingsRepository Generic key-value persistence backing.
 */
class SaveSecurityPolicyUseCase(private val settingsRepository: SettingsRepository) {

    suspend operator fun invoke(policy: SecurityPolicy): Result<Unit> {
        validate(policy)?.let { return Result.Error(it) }

        val writes: List<Pair<String, String>> = listOf(
            SecurityPolicyKeys.SESSION_TIMEOUT_MINUTES to policy.sessionTimeoutMinutes.toString(),
            SecurityPolicyKeys.PIN_COMPLEXITY to policy.pinComplexity.name,
            SecurityPolicyKeys.FAILED_LOGIN_LOCKOUT_ATTEMPTS to policy.failedLoginLockoutAttempts.toString(),
            SecurityPolicyKeys.LOCKOUT_DURATION_MINUTES to policy.lockoutDurationMinutes.toString(),
            SecurityPolicyKeys.BIOMETRIC_ENABLED to policy.biometricEnabled.toString(),
        )
        for ((key, value) in writes) {
            when (val outcome = settingsRepository.set(key, value)) {
                is Result.Success -> { /* keep writing */ }
                is Result.Error -> return Result.Error(outcome.exception)
                Result.Loading -> { /* never returned by this repo */ }
            }
        }
        return Result.Success(Unit)
    }

    private fun validate(policy: SecurityPolicy): ValidationException? {
        if (policy.sessionTimeoutMinutes !in SecurityPolicy.ALLOWED_SESSION_TIMEOUTS) {
            return ValidationException(
                "Session timeout must be one of ${SecurityPolicy.ALLOWED_SESSION_TIMEOUTS}.",
                field = "sessionTimeoutMinutes",
                rule = "ENUM",
            )
        }
        if (policy.failedLoginLockoutAttempts !in SecurityPolicy.ALLOWED_LOCKOUT_ATTEMPTS) {
            return ValidationException(
                "Failed-login lockout threshold must be one of ${SecurityPolicy.ALLOWED_LOCKOUT_ATTEMPTS}.",
                field = "failedLoginLockoutAttempts",
                rule = "ENUM",
            )
        }
        if (policy.lockoutDurationMinutes !in SecurityPolicy.ALLOWED_LOCKOUT_DURATIONS) {
            return ValidationException(
                "Lockout duration must be one of ${SecurityPolicy.ALLOWED_LOCKOUT_DURATIONS}.",
                field = "lockoutDurationMinutes",
                rule = "ENUM",
            )
        }
        return null
    }
}
