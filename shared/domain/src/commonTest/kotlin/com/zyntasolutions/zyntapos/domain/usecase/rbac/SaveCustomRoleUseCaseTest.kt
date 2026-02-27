package com.zyntasolutions.zyntapos.domain.usecase.rbac

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.CustomRole
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeRoleRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [SaveCustomRoleUseCase].
 *
 * Coverage:
 * 1. Blank role name → [ValidationException] with field="name" and rule="REQUIRED"
 * 2. Valid role + isUpdate=false → delegates to [RoleRepository.createCustomRole]
 * 3. Valid role + isUpdate=true → delegates to [RoleRepository.updateCustomRole]
 */
class SaveCustomRoleUseCaseTest {

    private fun makeRole(name: String = "Kitchen Staff") = CustomRole(
        id          = "cr-01",
        name        = name,
        description = "Test role",
        permissions = setOf(Permission.MANAGE_PRODUCTS),
        createdAt   = Instant.fromEpochSeconds(0),
        updatedAt   = Instant.fromEpochSeconds(0),
    )

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    fun `blank role name returns ValidationException with REQUIRED rule`() = runTest {
        val repo   = FakeRoleRepository()
        val result = SaveCustomRoleUseCase(repo)(makeRole(name = "  "), isUpdate = false)

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("name", ex.field)
        assertEquals("REQUIRED", ex.rule)
        assertTrue(repo.createdRoles.isEmpty(), "createCustomRole must NOT be called for blank name")
    }

    @Test
    fun `empty role name returns ValidationException with REQUIRED rule`() = runTest {
        val repo   = FakeRoleRepository()
        val result = SaveCustomRoleUseCase(repo)(makeRole(name = ""), isUpdate = false)

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("REQUIRED", ex.rule)
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Test
    fun `valid role with isUpdate=false delegates to createCustomRole`() = runTest {
        val repo   = FakeRoleRepository()
        val role   = makeRole()
        val result = SaveCustomRoleUseCase(repo)(role, isUpdate = false)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(listOf(role), repo.createdRoles)
        assertTrue(repo.updatedRoles.isEmpty())
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Test
    fun `valid role with isUpdate=true delegates to updateCustomRole`() = runTest {
        val repo = FakeRoleRepository()
        repo.seedRoles(makeRole())
        val updated = makeRole().copy(name = "Renamed Staff")
        val result  = SaveCustomRoleUseCase(repo)(updated, isUpdate = true)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(listOf(updated), repo.updatedRoles)
        assertTrue(repo.createdRoles.isEmpty())
    }
}
