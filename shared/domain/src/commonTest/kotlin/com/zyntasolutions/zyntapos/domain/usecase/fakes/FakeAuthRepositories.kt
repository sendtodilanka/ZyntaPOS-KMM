package com.zyntasolutions.zyntapos.domain.usecase.fakes

import com.zyntasolutions.zyntapos.core.result.AuthException
import com.zyntasolutions.zyntapos.core.result.AuthFailureReason
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.SyncStatus
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Clock

// ─────────────────────────────────────────────────────────────────────────────
// Fixtures
// ─────────────────────────────────────────────────────────────────────────────

/** Builds a test [User] with sensible defaults. */
fun buildUser(
    id: String = "user-01",
    name: String = "Test User",
    email: String = "test@zyntapos.com",
    role: Role = Role.CASHIER,
    isActive: Boolean = true,
) = User(id = id, name = name, email = email, role = role, storeId = "store-01",
    isActive = isActive, pinHash = "1234", createdAt = Clock.System.now(),
    updatedAt = Clock.System.now())

// ─────────────────────────────────────────────────────────────────────────────
// FakeAuthRepository
// ─────────────────────────────────────────────────────────────────────────────

class FakeAuthRepository : AuthRepository {
    var userToReturn: User? = buildUser()
    var shouldFailLogin: Boolean = false
    var loginFailureReason: AuthFailureReason = AuthFailureReason.INVALID_CREDENTIALS
    var logoutCalled: Boolean = false
    var pinToAccept: String = "1234"
    var shouldFailUpdatePin: Boolean = false
    var updatePinCalledWith: Pair<String, String>? = null

    private val _session = MutableStateFlow<User?>(null)

    override suspend fun login(email: String, password: String): Result<User> {
        if (shouldFailLogin) {
            return Result.Error(AuthException("Login failed", reason = loginFailureReason))
        }
        val user = userToReturn ?: return Result.Error(AuthException("No user"))
        if (!user.isActive) {
            return Result.Error(AuthException("Account disabled", reason = AuthFailureReason.ACCOUNT_DISABLED))
        }
        _session.value = user
        return Result.Success(user)
    }

    override suspend fun logout() {
        logoutCalled = true
        _session.value = null
    }

    override fun getSession(): Flow<User?> = _session

    override suspend fun refreshToken(): Result<Unit> = Result.Success(Unit)

    override suspend fun updatePin(userId: String, pin: String): Result<Unit> {
        updatePinCalledWith = Pair(userId, pin)
        if (shouldFailUpdatePin) {
            return Result.Error(AuthException("Invalid PIN", reason = AuthFailureReason.INVALID_CREDENTIALS))
        }
        pinToAccept = pin
        return Result.Success(Unit)
    }

    override suspend fun validatePin(userId: String, pin: String): Result<Boolean> =
        Result.Success(pin == pinToAccept)

    override suspend fun quickSwitch(userId: String, pin: String): Result<User> =
        Result.Error(AuthException("Not implemented in fake", reason = AuthFailureReason.INVALID_CREDENTIALS))

    override suspend fun validateManagerPin(pin: String): Result<Boolean> =
        Result.Success(false)

    fun setActiveUser(user: User?) { _session.value = user }
}
