package com.zyntasolutions.zyntapos.domain.usecase.settings

import com.zyntasolutions.zyntapos.domain.model.SecurityPolicy
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetSecurityPolicyUseCaseTest {

    @Test
    fun `defaults when settings store is empty`() = runTest {
        val policy = GetSecurityPolicyUseCase(FakeSettingsRepository()).invoke()
        assertEquals(SecurityPolicy(), policy)
    }

    @Test
    fun `reads valid stored values for every field`() = runTest {
        val repo = FakeSettingsRepository().apply {
            put("security.session_timeout_minutes", "30")
            put("security.pin_complexity", "ALPHANUMERIC")
            put("security.failed_login_lockout_attempts", "10")
            put("security.lockout_duration_minutes", "5")
            put("security.biometric_enabled", "false")
        }

        val policy = GetSecurityPolicyUseCase(repo).invoke()

        assertEquals(30, policy.sessionTimeoutMinutes)
        assertEquals(SecurityPolicy.PinComplexity.ALPHANUMERIC, policy.pinComplexity)
        assertEquals(10, policy.failedLoginLockoutAttempts)
        assertEquals(5, policy.lockoutDurationMinutes)
        assertEquals(false, policy.biometricEnabled)
    }

    @Test
    fun `out-of-spec stored values fall back to defaults`() = runTest {
        val repo = FakeSettingsRepository().apply {
            put("security.session_timeout_minutes", "999")  // not in {5, 15, 30, 60}
            put("security.pin_complexity", "MORSE_CODE")    // not an enum value
            put("security.failed_login_lockout_attempts", "7")
            put("security.lockout_duration_minutes", "999")
        }

        val policy = GetSecurityPolicyUseCase(repo).invoke()

        // Each invalid field collapses to the default (15 / SIX_DIGIT / 5 / 15)
        assertEquals(SecurityPolicy(), policy)
    }

    @Test
    fun `non-numeric stored values fall back to defaults`() = runTest {
        val repo = FakeSettingsRepository().apply {
            put("security.session_timeout_minutes", "fifteen")
            put("security.failed_login_lockout_attempts", "five")
            put("security.lockout_duration_minutes", "fifteen")
        }
        val policy = GetSecurityPolicyUseCase(repo).invoke()
        assertEquals(15, policy.sessionTimeoutMinutes)
        assertEquals(5, policy.failedLoginLockoutAttempts)
        assertEquals(15, policy.lockoutDurationMinutes)
    }

    @Test
    fun `biometric reads true for any non-false value`() = runTest {
        val repo = FakeSettingsRepository().apply {
            put("security.biometric_enabled", "yes")
        }
        val policy = GetSecurityPolicyUseCase(repo).invoke()
        assertEquals(true, policy.biometricEnabled)
    }
}
