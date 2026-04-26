package com.zyntasolutions.zyntapos.domain.usecase.audit

import com.zyntasolutions.zyntapos.domain.model.AuditPolicy
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetAuditPolicyUseCaseTest {

    @Test
    fun `defaults to all-enabled when settings store is empty`() = runTest {
        val policy = GetAuditPolicyUseCase(FakeSettingsRepository()).invoke()
        assertEquals(AuditPolicy(), policy)
        AuditPolicy.Category.entries.forEach { c ->
            assertTrue(policy.isEnabled(c), "Default for $c should be true.")
        }
    }

    @Test
    fun `reads stored false values for individual categories`() = runTest {
        val repo = FakeSettingsRepository().apply {
            put("audit.login.enabled", "false")
            put("audit.product.enabled", "true")
            put("audit.order.enabled", "FALSE")
            put("audit.payroll.enabled", "false")
        }

        val policy = GetAuditPolicyUseCase(repo).invoke()

        assertEquals(false, policy.login)
        assertEquals(true, policy.product)
        assertEquals(false, policy.order, "Match should be case-insensitive.")
        assertEquals(false, policy.payroll)
        // Untouched keys remain at their default (true).
        assertEquals(true, policy.customer)
        assertEquals(true, policy.settings)
        assertEquals(true, policy.backup)
    }

    @Test
    fun `roleChanges always reads as true regardless of stored value`() = runTest {
        val repo = FakeSettingsRepository().apply {
            put("audit.role.enabled", "false")
        }
        val policy = GetAuditPolicyUseCase(repo).invoke()
        assertTrue(
            policy.roleChanges,
            "ROLE_CHANGES is forensically locked-on; storage must not turn it off.",
        )
    }

    @Test
    fun `non-false values resolve to true (forward-compat with future encodings)`() = runTest {
        val repo = FakeSettingsRepository().apply {
            put("audit.product.enabled", "1")
            put("audit.order.enabled", "on")
            put("audit.customer.enabled", "yes")
        }
        val policy = GetAuditPolicyUseCase(repo).invoke()
        assertTrue(policy.product)
        assertTrue(policy.order)
        assertTrue(policy.customer)
    }
}
