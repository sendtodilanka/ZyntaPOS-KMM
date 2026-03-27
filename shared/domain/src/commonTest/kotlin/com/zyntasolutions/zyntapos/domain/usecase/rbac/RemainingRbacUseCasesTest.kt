package com.zyntasolutions.zyntapos.domain.usecase.rbac

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.CustomRole
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeRoleRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// Fixtures
// ─────────────────────────────────────────────────────────────────────────────

private fun buildCustomRole(
    id: String = "role-01",
    name: String = "Custom Role",
) = CustomRole(
    id = id,
    name = name,
    description = "A test custom role",
    permissions = setOf(Permission.MANAGE_PRODUCTS),
    createdAt = Instant.fromEpochSeconds(0),
    updatedAt = Instant.fromEpochSeconds(0),
)

// ─────────────────────────────────────────────────────────────────────────────
// GetCustomRolesUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class GetCustomRolesUseCaseTest {

    @Test
    fun `invoke_returnsEmptyFlow_whenNoCustomRoles`() = runTest {
        GetCustomRolesUseCase(FakeRoleRepository()).invoke().test {
            assertEquals(0, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `invoke_returnsAllCustomRoles`() = runTest {
        val repo = FakeRoleRepository()
        repo.createCustomRole(buildCustomRole(id = "role-01", name = "Cashier Plus"))
        repo.createCustomRole(buildCustomRole(id = "role-02", name = "Inventory Manager"))

        GetCustomRolesUseCase(repo).invoke().test {
            val list = awaitItem()
            assertEquals(2, list.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `invoke_reflectsNewRolesAddedAfterSubscription`() = runTest {
        val repo = FakeRoleRepository()

        GetCustomRolesUseCase(repo).invoke().test {
            assertEquals(0, awaitItem().size)

            repo.createCustomRole(buildCustomRole(id = "role-01"))

            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
