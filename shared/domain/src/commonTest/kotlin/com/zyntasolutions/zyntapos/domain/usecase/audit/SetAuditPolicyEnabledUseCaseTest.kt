package com.zyntasolutions.zyntapos.domain.usecase.audit

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.AuditPolicy
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SetAuditPolicyEnabledUseCaseTest {

    @Test
    fun `enabling a category writes 'true' to the matching settings key`() = runTest {
        val repo = FakeSettingsRepository()
        val outcome = SetAuditPolicyEnabledUseCase(repo).invoke(AuditPolicy.Category.LOGIN, true)
        assertIs<Result.Success<Unit>>(outcome)
        assertEquals("true", repo.get("audit.login.enabled"))
    }

    @Test
    fun `disabling a category writes 'false' to the matching settings key`() = runTest {
        val repo = FakeSettingsRepository()
        val outcome = SetAuditPolicyEnabledUseCase(repo).invoke(AuditPolicy.Category.PRODUCT, false)
        assertIs<Result.Success<Unit>>(outcome)
        assertEquals("false", repo.get("audit.product.enabled"))
    }

    @Test
    fun `disabling ROLE_CHANGES is rejected with ValidationException and no write`() = runTest {
        val repo = FakeSettingsRepository()
        val outcome = SetAuditPolicyEnabledUseCase(repo).invoke(
            AuditPolicy.Category.ROLE_CHANGES,
            enabled = false,
        )
        val error = assertIs<Result.Error>(outcome)
        val ex = assertIs<ValidationException>(error.exception)
        assertEquals("category", ex.field)
        assertEquals("ROLE_CHANGES_LOCKED", ex.rule)
        assertNull(repo.get("audit.role.enabled"), "No persistence should occur on rejection.")
    }

    @Test
    fun `enabling ROLE_CHANGES is allowed (no-op invariant)`() = runTest {
        val repo = FakeSettingsRepository()
        val outcome = SetAuditPolicyEnabledUseCase(repo).invoke(AuditPolicy.Category.ROLE_CHANGES, true)
        assertIs<Result.Success<Unit>>(outcome)
        assertEquals("true", repo.get("audit.role.enabled"))
    }
}
