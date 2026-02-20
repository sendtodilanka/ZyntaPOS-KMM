package com.zyntasolutions.zyntapos.feature.auth

import com.zyntasolutions.zyntapos.core.result.AuthException
import com.zyntasolutions.zyntapos.core.result.AuthFailureReason
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.usecase.auth.LoginUseCase
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.everySuspend
import io.mockative.mock
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [LoginUseCase].
 *
 * Validates the use case's delegation to [AuthRepository.login] across all
 * scenarios: successful authentication, invalid credentials, offline no-cache,
 * and network errors.
 */
class LoginUseCaseTest {

    @Mock
    private val authRepository = mock(classOf<AuthRepository>())

    private val useCase = LoginUseCase(authRepository)

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
    fun `invoke returns Success with User on valid credentials`() = kotlinx.coroutines.test.runTest {
        everySuspend { authRepository.login(email = "admin@zentapos.com", password = "secure123") }
            .returns(Result.Success(fakeUser))

        val result = useCase(email = "admin@zentapos.com", password = "secure123")

        assertTrue(result is Result.Success)
        assertEquals(fakeUser, (result as Result.Success).data)
    }

    // ── Invalid credentials ───────────────────────────────────────────────────

    @Test
    fun `invoke returns AuthException on wrong password`() = kotlinx.coroutines.test.runTest {
        val error = AuthException("Invalid credentials", reason = AuthFailureReason.INVALID_CREDENTIALS)
        everySuspend { authRepository.login(email = "admin@zentapos.com", password = "wrongpass") }
            .returns(Result.Error(error))

        val result = useCase(email = "admin@zentapos.com", password = "wrongpass")

        assertTrue(result is Result.Error)
        val ex = (result as Result.Error).exception
        assertTrue(ex is AuthException)
        assertEquals(AuthFailureReason.INVALID_CREDENTIALS, (ex as AuthException).reason)
    }

    // ── Account disabled ──────────────────────────────────────────────────────

    @Test
    fun `invoke returns ACCOUNT_DISABLED when user is inactive`() = kotlinx.coroutines.test.runTest {
        val error = AuthException("Account is disabled", reason = AuthFailureReason.ACCOUNT_DISABLED)
        everySuspend { authRepository.login(email = "disabled@zentapos.com", password = "pass123") }
            .returns(Result.Error(error))

        val result = useCase(email = "disabled@zentapos.com", password = "pass123")

        assertTrue(result is Result.Error)
        assertEquals(
            AuthFailureReason.ACCOUNT_DISABLED,
            ((result as Result.Error).exception as AuthException).reason,
        )
    }

    // ── Offline no cache ──────────────────────────────────────────────────────

    @Test
    fun `invoke returns OFFLINE_NO_CACHE when device has no local user record`() = kotlinx.coroutines.test.runTest {
        val error = AuthException("No local account found", reason = AuthFailureReason.OFFLINE_NO_CACHE)
        everySuspend { authRepository.login(email = "new@device.com", password = "anypass") }
            .returns(Result.Error(error))

        val result = useCase(email = "new@device.com", password = "anypass")

        assertTrue(result is Result.Error)
        assertEquals(
            AuthFailureReason.OFFLINE_NO_CACHE,
            ((result as Result.Error).exception as AuthException).reason,
        )
    }

    // ── Network error ─────────────────────────────────────────────────────────

    @Test
    fun `invoke propagates generic exception as Result Error`() = kotlinx.coroutines.test.runTest {
        val networkError = RuntimeException("Network timeout")
        everySuspend { authRepository.login(email = "user@test.com", password = "pass") }
            .throws(networkError)

        // The repository implementation wraps exceptions; for pure use case testing
        // we verify it does not silently swallow errors raised by the repository.
        val result = runCatching { useCase(email = "user@test.com", password = "pass") }
        // Either an exception or a Result.Error is acceptable — both indicate failure.
        assertTrue(result.isFailure || (result.getOrNull() is Result.Error))
    }
}
