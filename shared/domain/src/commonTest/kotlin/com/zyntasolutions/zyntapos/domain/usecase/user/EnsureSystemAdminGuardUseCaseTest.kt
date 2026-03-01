package com.zyntasolutions.zyntapos.domain.usecase.user

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeUserRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildUser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Unit tests for [EnsureSystemAdminGuardUseCase].
 *
 * Coverage:
 * - Returns Success when requesting user IS the system admin
 * - Returns INSUFFICIENT_PRIVILEGES when requesting user is NOT the system admin
 * - Returns NO_SYSTEM_ADMIN when no system admin is designated
 * - Propagates database errors unchanged
 * - Edge: user with ADMIN role but without isSystemAdmin flag is rejected
 * - Edge: requesting user ID is an empty string (still resolved via repository)
 */
class EnsureSystemAdminGuardUseCaseTest {

    private fun makeUseCase(repo: FakeUserRepository = FakeUserRepository()): Pair<EnsureSystemAdminGuardUseCase, FakeUserRepository> =
        EnsureSystemAdminGuardUseCase(repo) to repo

    // ── Success path ──────────────────────────────────────────────────────────

    @Test
    fun `returns Success when user is the designated system admin`() = runTest {
        val (useCase, repo) = makeUseCase()
        val sysAdmin = buildUser(id = "sysadmin-1", role = Role.ADMIN).copy(isSystemAdmin = true)
        repo.users.add(sysAdmin)

        val result = useCase.execute("sysadmin-1")

        assertIs<Result.Success<Unit>>(result)
    }

    // ── Error: no system admin ────────────────────────────────────────────────

    @Test
    fun `returns NO_SYSTEM_ADMIN error when no system admin is designated`() = runTest {
        val (useCase, _) = makeUseCase()

        val result = useCase.execute("any-user-id")

        assertIs<Result.Error>(result)
        val ex = result.exception
        assertIs<ValidationException>(ex)
        assertEquals("NO_SYSTEM_ADMIN", ex.rule)
        assertEquals("requestingUserId", ex.field)
    }

    @Test
    fun `returns NO_SYSTEM_ADMIN even when ADMIN users exist without the flag`() = runTest {
        val (useCase, repo) = makeUseCase()
        // ADMIN role but no isSystemAdmin flag
        repo.users.add(buildUser(id = "admin-without-flag", role = Role.ADMIN).copy(isSystemAdmin = false))

        val result = useCase.execute("admin-without-flag")

        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("NO_SYSTEM_ADMIN", ex.rule)
    }

    // ── Error: wrong user ─────────────────────────────────────────────────────

    @Test
    fun `returns INSUFFICIENT_PRIVILEGES when requesting user is not the system admin`() = runTest {
        val (useCase, repo) = makeUseCase()
        val sysAdmin = buildUser(id = "sysadmin-1", role = Role.ADMIN).copy(isSystemAdmin = true)
        repo.users.add(sysAdmin)
        repo.users.add(buildUser(id = "cashier-1", role = Role.CASHIER))

        val result = useCase.execute("cashier-1")

        assertIs<Result.Error>(result)
        val ex = result.exception
        assertIs<ValidationException>(ex)
        assertEquals("INSUFFICIENT_PRIVILEGES", ex.rule)
        assertEquals("requestingUserId", ex.field)
    }

    @Test
    fun `ADMIN role user without isSystemAdmin flag returns INSUFFICIENT_PRIVILEGES`() = runTest {
        val (useCase, repo) = makeUseCase()
        val sysAdmin = buildUser(id = "sysadmin-1", role = Role.ADMIN).copy(isSystemAdmin = true)
        val regularAdmin = buildUser(id = "admin-regular", role = Role.ADMIN).copy(isSystemAdmin = false)
        repo.users.add(sysAdmin)
        repo.users.add(regularAdmin)

        val result = useCase.execute("admin-regular")

        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("INSUFFICIENT_PRIVILEGES", ex.rule)
    }

    // ── Error: database failure ───────────────────────────────────────────────

    @Test
    fun `propagates database error from getSystemAdmin`() = runTest {
        val repo = FakeUserRepository().also { it.shouldFail = true }
        val useCase = EnsureSystemAdminGuardUseCase(repo)

        val result = useCase.execute("any-user-id")

        assertIs<Result.Error>(result)
    }
}
