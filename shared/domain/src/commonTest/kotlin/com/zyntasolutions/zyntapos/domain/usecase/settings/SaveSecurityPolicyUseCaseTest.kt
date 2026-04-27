package com.zyntasolutions.zyntapos.domain.usecase.settings

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.SecurityPolicy
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SaveSecurityPolicyUseCaseTest {

    @Test
    fun `valid policy writes all five keys`() = runTest {
        val repo = FakeSettingsRepository()
        val outcome = SaveSecurityPolicyUseCase(repo).invoke(
            SecurityPolicy(
                sessionTimeoutMinutes = 30,
                pinComplexity = SecurityPolicy.PinComplexity.FOUR_DIGIT,
                failedLoginLockoutAttempts = 3,
                lockoutDurationMinutes = 30,
                biometricEnabled = false,
            ),
        )
        assertIs<Result.Success<Unit>>(outcome)
        assertEquals("30", repo.get("security.session_timeout_minutes"))
        assertEquals("FOUR_DIGIT", repo.get("security.pin_complexity"))
        assertEquals("3", repo.get("security.failed_login_lockout_attempts"))
        assertEquals("30", repo.get("security.lockout_duration_minutes"))
        assertEquals("false", repo.get("security.biometric_enabled"))
    }

    @Test
    fun `out-of-spec sessionTimeout is rejected with ValidationException and no writes`() = runTest {
        val repo = FakeSettingsRepository()
        val outcome = SaveSecurityPolicyUseCase(repo).invoke(SecurityPolicy(sessionTimeoutMinutes = 999))
        val ex = assertIs<ValidationException>(assertIs<Result.Error>(outcome).exception)
        assertEquals("sessionTimeoutMinutes", ex.field)
        assertEquals("ENUM", ex.rule)
        assertNull(repo.get("security.session_timeout_minutes"))
    }

    @Test
    fun `out-of-spec lockout attempts is rejected`() = runTest {
        val repo = FakeSettingsRepository()
        val outcome = SaveSecurityPolicyUseCase(repo).invoke(
            SecurityPolicy(failedLoginLockoutAttempts = 7),
        )
        val ex = assertIs<ValidationException>(assertIs<Result.Error>(outcome).exception)
        assertEquals("failedLoginLockoutAttempts", ex.field)
    }

    @Test
    fun `out-of-spec lockout duration is rejected`() = runTest {
        val repo = FakeSettingsRepository()
        val outcome = SaveSecurityPolicyUseCase(repo).invoke(
            SecurityPolicy(lockoutDurationMinutes = 60),
        )
        val ex = assertIs<ValidationException>(assertIs<Result.Error>(outcome).exception)
        assertEquals("lockoutDurationMinutes", ex.field)
    }

    @Test
    fun `default policy is valid`() = runTest {
        val repo = FakeSettingsRepository()
        val outcome = SaveSecurityPolicyUseCase(repo).invoke(SecurityPolicy())
        assertIs<Result.Success<Unit>>(outcome)
    }
}
