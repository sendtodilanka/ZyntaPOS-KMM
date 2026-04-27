package com.zyntasolutions.zyntapos.domain.usecase.settings

/**
 * Key namespace for `SecurityPolicy` entries in the generic `settings` table.
 *
 * Matches the storage shape used by the audit policy slice — one row per
 * field rather than a JSON blob — so future per-field reactive observers
 * can come for free.
 */
internal object SecurityPolicyKeys {
    const val SESSION_TIMEOUT_MINUTES = "security.session_timeout_minutes"
    const val PIN_COMPLEXITY = "security.pin_complexity"
    const val FAILED_LOGIN_LOCKOUT_ATTEMPTS = "security.failed_login_lockout_attempts"
    const val LOCKOUT_DURATION_MINUTES = "security.lockout_duration_minutes"
    const val BIOMETRIC_ENABLED = "security.biometric_enabled"
}
