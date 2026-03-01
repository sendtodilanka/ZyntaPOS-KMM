package com.zyntasolutions.zyntapos.domain.usecase.user

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeUserRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildUser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [GetSystemAdminUseCase].
 *
 * Coverage:
 * - Returns the system admin when one exists
 * - Returns null when no system admin is designated
 * - Propagates database errors unchanged
 * - Correctly identifies the system admin among multiple users
 * - Admin with isSystemAdmin=false is not returned as system admin
 */
class GetSystemAdminUseCaseTest {

    private fun makeUseCase(): Pair<GetSystemAdminUseCase, FakeUserRepository> {
        val repo = FakeUserRepository()
        return GetSystemAdminUseCase(repo) to repo
    }

    // ── Happy paths ───────────────────────────────────────────────────────────

    @Test
    fun `returns null when no users exist`() = runTest {
        val (useCase, _) = makeUseCase()

        val result = useCase.execute()

        assertIs<Result.Success<*>>(result)
        assertNull((result as Result.Success).data)
    }

    @Test
    fun `returns null when users exist but none is system admin`() = runTest {
        val (useCase, repo) = makeUseCase()
        repo.users.add(buildUser(id = "user-1", role = Role.ADMIN, isActive = true))
        repo.users.add(buildUser(id = "user-2", role = Role.CASHIER, isActive = true))

        val result = useCase.execute()

        assertIs<Result.Success<*>>(result)
        assertNull((result as Result.Success).data)
    }

    @Test
    fun `returns system admin user when one is designated`() = runTest {
        val (useCase, repo) = makeUseCase()
        val admin = buildUser(id = "admin-1", role = Role.ADMIN, isActive = true).copy(isSystemAdmin = true)
        repo.users.add(admin)

        val result = useCase.execute()

        assertIs<Result.Success<*>>(result)
        val user = (result as Result.Success).data
        assertEquals("admin-1", user?.id)
        assertTrue(user?.isSystemAdmin == true)
    }

    @Test
    fun `returns correct admin when multiple users exist and only one is system admin`() = runTest {
        val (useCase, repo) = makeUseCase()
        repo.users.add(buildUser(id = "cashier-1", role = Role.CASHIER))
        val sysAdmin = buildUser(id = "sysadmin-1", role = Role.ADMIN).copy(isSystemAdmin = true)
        repo.users.add(sysAdmin)
        repo.users.add(buildUser(id = "manager-1", role = Role.STORE_MANAGER))

        val result = useCase.execute()

        assertIs<Result.Success<*>>(result)
        assertEquals("sysadmin-1", (result as Result.Success).data?.id)
    }

    @Test
    fun `admin user with isSystemAdmin false is not returned`() = runTest {
        val (useCase, repo) = makeUseCase()
        // ADMIN role but NOT system admin flag
        repo.users.add(buildUser(id = "admin-regular", role = Role.ADMIN).copy(isSystemAdmin = false))

        val result = useCase.execute()

        assertIs<Result.Success<*>>(result)
        assertNull((result as Result.Success).data, "Regular ADMIN without flag must not be returned")
    }

    @Test
    fun `inactive user who is system admin is still returned`() = runTest {
        val (useCase, repo) = makeUseCase()
        // Edge case: system admin marked inactive (e.g., revoked temporarily)
        val inactiveAdmin = buildUser(id = "inactive-sysadmin", role = Role.ADMIN, isActive = false)
            .copy(isSystemAdmin = true)
        repo.users.add(inactiveAdmin)

        val result = useCase.execute()

        assertIs<Result.Success<*>>(result)
        // getSystemAdmin returns whoever has the flag, isActive gating is handled at login/use-case level
        assertEquals("inactive-sysadmin", (result as Result.Success).data?.id)
    }

    // ── Error path ────────────────────────────────────────────────────────────

    @Test
    fun `propagates database error unchanged`() = runTest {
        val repo = FakeUserRepository().also { it.shouldFail = true }
        val useCase = GetSystemAdminUseCase(repo)

        val result = useCase.execute()

        assertIs<Result.Error>(result)
    }
}
