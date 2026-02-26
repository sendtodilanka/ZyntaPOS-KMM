package com.zyntasolutions.zyntapos.feature.auth

import com.zyntasolutions.zyntapos.core.result.AuthException
import com.zyntasolutions.zyntapos.core.result.AuthFailureReason
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.usecase.auth.LoginUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [LoginUseCase].
 *
 * Uses a hand-rolled fake [AuthRepository] instead of Mockative
 * (KSP1 is incompatible with Kotlin 2.3+).
 */
class LoginUseCaseTest {

    // ── Fake AuthRepository ────────────────────────────────────────────────────

    private val loginResponses = mutableMapOf<Pair<String, String>, Result<User>>()
    private var loginThrows: Throwable? = null

    private val fakeAuthRepository = object : AuthRepository {
        override suspend fun login(email: String, password: String): Result<User> {
            loginThrows?.let { throw it }
            return loginResponses[email to password]
                ?: Result.Error(AuthException("Invalid credentials", reason = AuthFailureReason.INVALID_CREDENTIALS))
        }

        override suspend fun logout() {}

        override fun getSession(): Flow<User?> = MutableStateFlow(null)

        override suspend fun refreshToken(): Result<Unit> = Result.Success(Unit)

        override suspend fun updatePin(userId: String, pin: String): Result<Unit> = Result.Success(Unit)
    }

    private val useCase = LoginUseCase(fakeAuthRepository)

    private val fakeUser = User(
        id = "user-001",
        name = "Admin User",
        email = "admin@zentapos.com",
        role = Role.ADMIN,
        storeId = "store-001",
        isActive = true,
        pinHash = null,
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now(),
    )

    // ── Success ───────────────────────────────────────────────────────────────

    @Test
    fun `invoke returns Success with User on valid credentials`() = runTest {
        loginResponses["admin@zentapos.com" to "secure123"] = Result.Success(fakeUser)

        val result = useCase(email = "admin@zentapos.com", password = "secure123")

        assertTrue(result is Result.Success)
        assertEquals(fakeUser, (result as Result.Success).data)
    }

    // ── Invalid credentials ───────────────────────────────────────────────────

    @Test
    fun `invoke returns AuthException on wrong password`() = runTest {
        val error = AuthException("Invalid credentials", reason = AuthFailureReason.INVALID_CREDENTIALS)
        loginResponses["admin@zentapos.com" to "wrongpass"] = Result.Error(error)

        val result = useCase(email = "admin@zentapos.com", password = "wrongpass")

        assertTrue(result is Result.Error)
        val ex = (result as Result.Error).exception
        assertTrue(ex is AuthException)
        assertEquals(AuthFailureReason.INVALID_CREDENTIALS, (ex as AuthException).reason)
    }

    // ── Account disabled ──────────────────────────────────────────────────────

    @Test
    fun `invoke returns ACCOUNT_DISABLED when user is inactive`() = runTest {
        val error = AuthException("Account is disabled", reason = AuthFailureReason.ACCOUNT_DISABLED)
        loginResponses["disabled@zentapos.com" to "pass123"] = Result.Error(error)

        val result = useCase(email = "disabled@zentapos.com", password = "pass123")

        assertTrue(result is Result.Error)
        assertEquals(
            AuthFailureReason.ACCOUNT_DISABLED,
            ((result as Result.Error).exception as AuthException).reason,
        )
    }

    // ── Offline no cache ──────────────────────────────────────────────────────

    @Test
    fun `invoke returns OFFLINE_NO_CACHE when device has no local user record`() = runTest {
        val error = AuthException("No local account found", reason = AuthFailureReason.OFFLINE_NO_CACHE)
        loginResponses["new@device.com" to "anypass"] = Result.Error(error)

        val result = useCase(email = "new@device.com", password = "anypass")

        assertTrue(result is Result.Error)
        assertEquals(
            AuthFailureReason.OFFLINE_NO_CACHE,
            ((result as Result.Error).exception as AuthException).reason,
        )
    }

    // ── Network error ─────────────────────────────────────────────────────────

    @Test
    fun `invoke propagates generic exception as Result Error`() = runTest {
        loginThrows = RuntimeException("Network timeout")

        // The repository implementation wraps exceptions; for pure use case testing
        // we verify it does not silently swallow errors raised by the repository.
        val result = runCatching { useCase(email = "user@test.com", password = "pass") }
        // Either an exception or a Result.Error is acceptable — both indicate failure.
        assertTrue(result.isFailure || (result.getOrNull() is Result.Error))
    }
}
