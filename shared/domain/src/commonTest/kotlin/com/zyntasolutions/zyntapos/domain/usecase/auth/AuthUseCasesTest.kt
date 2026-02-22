package com.zyntasolutions.zyntapos.domain.usecase.auth

import com.zyntasolutions.zyntapos.core.result.AuthFailureReason
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.AuthException
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeAuthRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildUser
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for Auth use cases:
 * [LoginUseCase], [LogoutUseCase], [ValidatePinUseCase], [CheckPermissionUseCase].
 */
class AuthUseCasesTest {

    // ─── LoginUseCase ─────────────────────────────────────────────────────────

    @Test
    fun `login with valid credentials - returns active user`() = runTest {
        val repo = FakeAuthRepository()
        val result = LoginUseCase(repo)("admin@zentapos.com", "password123")
        assertIs<Result.Success<*>>(result)
        val user = (result as Result.Success).data
        assertEquals("user-01", user.id)
        assertTrue(user.isActive)
    }

    @Test
    fun `login with invalid credentials - returns AuthException`() = runTest {
        val repo = FakeAuthRepository().also {
            it.shouldFailLogin = true
            it.loginFailureReason = AuthFailureReason.INVALID_CREDENTIALS
        }
        val result = LoginUseCase(repo)("bad@email.com", "wrongpass")
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as AuthException
        assertEquals(AuthFailureReason.INVALID_CREDENTIALS, ex.reason)
    }

    @Test
    fun `login with disabled account - returns ACCOUNT_DISABLED`() = runTest {
        val repo = FakeAuthRepository().also {
            it.userToReturn = buildUser(isActive = false)
        }
        val result = LoginUseCase(repo)("inactive@zentapos.com", "pass")
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as AuthException
        assertEquals(AuthFailureReason.ACCOUNT_DISABLED, ex.reason)
    }

    @Test
    fun `login offline with no cache - returns OFFLINE_NO_CACHE`() = runTest {
        val repo = FakeAuthRepository().also {
            it.shouldFailLogin = true
            it.loginFailureReason = AuthFailureReason.OFFLINE_NO_CACHE
        }
        val result = LoginUseCase(repo)("user@zentapos.com", "pass")
        assertIs<Result.Error>(result)
        assertEquals(AuthFailureReason.OFFLINE_NO_CACHE, ((result as Result.Error).exception as AuthException).reason)
    }

    @Test
    fun `login too many attempts - returns TOO_MANY_ATTEMPTS`() = runTest {
        val repo = FakeAuthRepository().also {
            it.shouldFailLogin = true
            it.loginFailureReason = AuthFailureReason.TOO_MANY_ATTEMPTS
        }
        val result = LoginUseCase(repo)("user@zentapos.com", "pass")
        assertIs<Result.Error>(result)
        assertEquals(AuthFailureReason.TOO_MANY_ATTEMPTS, ((result as Result.Error).exception as AuthException).reason)
    }

    // ─── LogoutUseCase ────────────────────────────────────────────────────────

    @Test
    fun `logout - clears session and returns success`() = runTest {
        val repo = FakeAuthRepository()
        repo.setActiveUser(buildUser())
        LogoutUseCase(repo)()
        assertTrue(repo.logoutCalled)
    }

    @Test
    fun `logout - can be called multiple times without error`() = runTest {
        val repo = FakeAuthRepository()
        LogoutUseCase(repo)()
        LogoutUseCase(repo)()
        assertTrue(repo.logoutCalled)
    }

    // ─── ValidatePinUseCase ───────────────────────────────────────────────────

    @Test
    fun `validate pin with correct PIN - returns success`() = runTest {
        val repo = FakeAuthRepository().also { it.pinToAccept = "1234" }
        val result = ValidatePinUseCase(repo)(userId = "user-01", pin = "1234")
        assertIs<Result.Success<*>>(result)
        assertTrue((result as Result.Success).data)
    }

    @Test
    fun `validate pin with incorrect PIN - returns success false (repo returns error)`() = runTest {
        val repo = FakeAuthRepository().also { it.pinToAccept = "1234" }
        val result = ValidatePinUseCase(repo)(userId = "user-01", pin = "9999")
        assertIs<Result.Success<*>>(result)
        assertFalse((result as Result.Success).data)
    }

    @Test
    fun `validate pin with blank PIN - returns INVALID_PIN_FORMAT error`() = runTest {
        val result = ValidatePinUseCase(FakeAuthRepository())(userId = "user-01", pin = "")
        assertIs<Result.Error>(result)
        assertEquals("INVALID_PIN_FORMAT", ((result as Result.Error).exception as com.zyntasolutions.zyntapos.core.result.ValidationException).rule)
    }

    @Test
    fun `validate pin with 3-digit PIN - returns INVALID_PIN_FORMAT error`() = runTest {
        val result = ValidatePinUseCase(FakeAuthRepository())(userId = "user-01", pin = "123")
        assertIs<Result.Error>(result)
        assertEquals("INVALID_PIN_FORMAT", ((result as Result.Error).exception as com.zyntasolutions.zyntapos.core.result.ValidationException).rule)
    }

    @Test
    fun `validate pin with non-numeric PIN - returns INVALID_PIN_FORMAT error`() = runTest {
        val result = ValidatePinUseCase(FakeAuthRepository())(userId = "user-01", pin = "ab12")
        assertIs<Result.Error>(result)
        assertEquals("INVALID_PIN_FORMAT", ((result as Result.Error).exception as com.zyntasolutions.zyntapos.core.result.ValidationException).rule)
    }

    // ─── CheckPermissionUseCase ───────────────────────────────────────────────

    @Test
    fun `admin role has all permissions`() {
        val uc = CheckPermissionUseCase(flowOf(null))
        Permission.entries.forEach { permission ->
            assertTrue(
                uc(Role.ADMIN, permission),
                "ADMIN should have $permission"
            )
        }
    }

    @Test
    fun `cashier can process sale, void order, apply discount`() {
        val uc = CheckPermissionUseCase(flowOf(null))
        assertTrue(uc(Role.CASHIER, Permission.PROCESS_SALE))
        assertTrue(uc(Role.CASHIER, Permission.VOID_ORDER))
        assertTrue(uc(Role.CASHIER, Permission.APPLY_DISCOUNT))
    }

    @Test
    fun `cashier cannot manage users or settings`() {
        val uc = CheckPermissionUseCase(flowOf(null))
        assertFalse(uc(Role.CASHIER, Permission.MANAGE_USERS))
        assertFalse(uc(Role.CASHIER, Permission.MANAGE_SETTINGS))
    }

    @Test
    fun `accountant can view reports but cannot process sales`() {
        val uc = CheckPermissionUseCase(flowOf(null))
        assertTrue(uc(Role.ACCOUNTANT, Permission.VIEW_REPORTS))
        assertFalse(uc(Role.ACCOUNTANT, Permission.PROCESS_SALE))
    }

    @Test
    fun `stock manager can adjust stock but cannot void orders`() {
        val uc = CheckPermissionUseCase(flowOf(null))
        assertTrue(uc(Role.STOCK_MANAGER, Permission.ADJUST_STOCK))
        assertFalse(uc(Role.STOCK_MANAGER, Permission.VOID_ORDER))
    }

    @Test
    fun `user-based check - matching userId and cashier role - permission granted`() {
        val user = buildUser(id = "cashier-01", role = Role.CASHIER)
        val uc = CheckPermissionUseCase(flowOf(user))
        uc.updateSession(user)
        assertTrue(uc("cashier-01", Permission.PROCESS_SALE))
    }

    @Test
    fun `user-based check - mismatched userId - permission denied`() {
        val user = buildUser(id = "cashier-01", role = Role.ADMIN)
        val uc = CheckPermissionUseCase(flowOf(user))
        uc.updateSession(user)
        // Checking with a different userId than the active session user
        assertFalse(uc("other-user", Permission.PROCESS_SALE))
    }

    @Test
    fun `no active session - all permissions denied`() {
        val uc = CheckPermissionUseCase(flowOf(null))
        // No updateSession called → snapshot is null
        assertFalse(uc("any-user", Permission.PROCESS_SALE))
    }

    @Test
    fun `store manager has all cashier permissions plus management`() {
        val uc = CheckPermissionUseCase(flowOf(null))
        assertTrue(uc(Role.STORE_MANAGER, Permission.PROCESS_SALE))
        assertTrue(uc(Role.STORE_MANAGER, Permission.MANAGE_PRODUCTS))
        assertTrue(uc(Role.STORE_MANAGER, Permission.MANAGE_USERS))
        assertTrue(uc(Role.STORE_MANAGER, Permission.VIEW_REPORTS))
    }
}
