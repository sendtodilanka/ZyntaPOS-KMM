package com.zyntasolutions.zyntapos.domain.usecase.rbac

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeRoleRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [DeleteCustomRoleUseCase].
 *
 * Coverage:
 * 1. Returns [Result.Success] and delegates deletion to [RoleRepository.deleteCustomRole].
 * 2. Repository failure is propagated as [Result.Error].
 */
class DeleteCustomRoleUseCaseTest {

    @Test
    fun `deleteCustomRole delegates to repository and returns Success`() = runTest {
        val repo   = FakeRoleRepository()
        val result = DeleteCustomRoleUseCase(repo)("role-42")

        assertIs<Result.Success<Unit>>(result)
        assertEquals(listOf("role-42"), repo.deletedRoleIds)
    }

    @Test
    fun `deleteCustomRole propagates repository failure as Result Error`() = runTest {
        val repo = FakeRoleRepository().also { it.shouldFail = true }
        val result = DeleteCustomRoleUseCase(repo)("role-99")

        assertIs<Result.Error>(result)
        assertTrue(repo.deletedRoleIds.isEmpty())
    }
}
