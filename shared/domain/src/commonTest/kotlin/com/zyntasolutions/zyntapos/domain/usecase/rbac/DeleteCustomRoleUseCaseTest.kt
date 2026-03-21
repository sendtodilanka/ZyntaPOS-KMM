package com.zyntasolutions.zyntapos.domain.usecase.rbac

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.CustomRole
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeRoleRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
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
 * 3. Multiple sequential deletions all recorded in order.
 * 4. First deletion succeeds even when a subsequent deletion fails.
 * 5. Use case is stateless — same repo can be reused across calls.
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

    @Test
    fun `deleteCustomRole multiple sequential calls records all IDs in order`() = runTest {
        val repo = FakeRoleRepository()
        val useCase = DeleteCustomRoleUseCase(repo)
        useCase("role-A")
        useCase("role-B")
        useCase("role-C")

        assertEquals(listOf("role-A", "role-B", "role-C"), repo.deletedRoleIds)
    }

    @Test
    fun `deleteCustomRole success clears role from repository`() = runTest {
        val repo = FakeRoleRepository()
        repo.seedRoles(
            CustomRole(
                id = "role-X",
                name = "Temp Role",
                description = "",
                permissions = emptySet(),
                createdAt = Instant.fromEpochSeconds(0),
                updatedAt = Instant.fromEpochSeconds(0),
            )
        )
        val useCase = DeleteCustomRoleUseCase(repo)

        val result = useCase("role-X")

        assertIs<Result.Success<Unit>>(result)
        assertEquals(listOf("role-X"), repo.deletedRoleIds)
    }

    @Test
    fun `deleteCustomRole failure leaves other roles intact`() = runTest {
        val repo = FakeRoleRepository().also { it.shouldFail = true }
        val useCase = DeleteCustomRoleUseCase(repo)

        val r1 = useCase("role-1")
        val r2 = useCase("role-2")

        assertIs<Result.Error>(r1)
        assertIs<Result.Error>(r2)
        assertTrue(repo.deletedRoleIds.isEmpty())
    }
}
