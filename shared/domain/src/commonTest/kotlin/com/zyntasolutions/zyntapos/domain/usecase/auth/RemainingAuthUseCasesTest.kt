package com.zyntasolutions.zyntapos.domain.usecase.auth

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.AuthException
import com.zyntasolutions.zyntapos.core.result.AuthFailureReason
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.UserStoreAccess
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.UserStoreAccessRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// ─────────────────────────────────────────────────────────────────────────────
// Fixtures
// ─────────────────────────────────────────────────────────────────────────────

private fun buildAccess(
    userId: String = "user-01",
    storeId: String = "store-01",
    isActive: Boolean = true,
) = UserStoreAccess(
    id = "$userId-$storeId",
    userId = userId,
    storeId = storeId,
    roleAtStore = Role.CASHIER,
    isActive = isActive,
    createdAt = Instant.fromEpochSeconds(0),
    updatedAt = Instant.fromEpochSeconds(0),
)

// ─────────────────────────────────────────────────────────────────────────────
// Inline Fakes
// ─────────────────────────────────────────────────────────────────────────────

private class AuthUserStoreAccessRepo(
    private val grants: List<UserStoreAccess> = emptyList(),
) : UserStoreAccessRepository {
    override fun getAccessibleStores(userId: String): Flow<List<UserStoreAccess>> =
        flowOf(grants.filter { it.userId == userId && it.isActive })

    override fun getUsersForStore(storeId: String): Flow<List<UserStoreAccess>> =
        flowOf(grants.filter { it.storeId == storeId && it.isActive })

    override suspend fun getById(id: String): Result<UserStoreAccess> =
        grants.firstOrNull { it.id == id }?.let { Result.Success(it) }
            ?: Result.Error(com.zyntasolutions.zyntapos.core.result.DatabaseException("Not found"))

    override suspend fun getByUserAndStore(userId: String, storeId: String): Result<UserStoreAccess?> =
        Result.Success(grants.firstOrNull { it.userId == userId && it.storeId == storeId })

    override suspend fun grantAccess(access: UserStoreAccess): Result<Unit> = Result.Success(Unit)
    override suspend fun revokeAccess(userId: String, storeId: String): Result<Unit> = Result.Success(Unit)
    override suspend fun hasAccess(userId: String, storeId: String): Boolean =
        grants.any { it.userId == userId && it.storeId == storeId && it.isActive }
    override suspend fun upsertFromSync(access: UserStoreAccess): Result<Unit> = Result.Success(Unit)
}

private class QuickSwitchFakeAuthRepo(
    private val userToReturn: com.zyntasolutions.zyntapos.domain.model.User? = null,
) : AuthRepository {
    override suspend fun login(email: String, password: String): Result<com.zyntasolutions.zyntapos.domain.model.User> =
        Result.Error(AuthException("n/a"))
    override suspend fun logout() {}
    override fun getSession(): Flow<com.zyntasolutions.zyntapos.domain.model.User?> = MutableStateFlow(null)
    override suspend fun refreshToken(): Result<Unit> = Result.Success(Unit)
    override suspend fun updatePin(userId: String, pin: String): Result<Unit> = Result.Success(Unit)
    override suspend fun validatePin(userId: String, pin: String): Result<Boolean> = Result.Success(true)
    override suspend fun validateManagerPin(pin: String): Result<Boolean> = Result.Success(false)
    override suspend fun quickSwitch(userId: String, pin: String): Result<com.zyntasolutions.zyntapos.domain.model.User> {
        val user = userToReturn
            ?: return Result.Error(AuthException("User not found", reason = AuthFailureReason.INVALID_CREDENTIALS))
        return Result.Success(user)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GetUserAccessibleStoresUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class GetUserAccessibleStoresUseCaseTest {

    @Test
    fun `invoke_returnsActiveGrantsForUser`() = runTest {
        val grants = listOf(
            buildAccess(userId = "user-01", storeId = "store-01", isActive = true),
            buildAccess(userId = "user-01", storeId = "store-02", isActive = true),
            buildAccess(userId = "user-01", storeId = "store-03", isActive = false),
            buildAccess(userId = "user-02", storeId = "store-01", isActive = true),
        )
        GetUserAccessibleStoresUseCase(AuthUserStoreAccessRepo(grants)).invoke("user-01").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assert(list.all { it.userId == "user-01" && it.isActive })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `invoke_returnsEmptyFlow_whenNoGrants`() = runTest {
        GetUserAccessibleStoresUseCase(AuthUserStoreAccessRepo()).invoke("user-99").test {
            assertEquals(0, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// QuickSwitchUserUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class QuickSwitchUserUseCaseTest {

    @Test
    fun `validPin_returnsSuccessWithUser`() = runTest {
        val user = buildUser(id = "user-01", role = Role.CASHIER)
        val result = QuickSwitchUserUseCase(QuickSwitchFakeAuthRepo(user)).invoke("user-01", "1234")
        assertIs<Result.Success<*>>(result)
        assertEquals("user-01", (result as Result.Success).data.id)
    }

    @Test
    fun `shortPin_returnsInvalidPinFormatError`() = runTest {
        val result = QuickSwitchUserUseCase(QuickSwitchFakeAuthRepo()).invoke("user-01", "123")
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("pin", ex.field)
        assertEquals("INVALID_PIN_FORMAT", ex.rule)
    }

    @Test
    fun `longPin_returnsInvalidPinFormatError`() = runTest {
        val result = QuickSwitchUserUseCase(QuickSwitchFakeAuthRepo()).invoke("user-01", "1234567")
        assertIs<Result.Error>(result)
        assertEquals("INVALID_PIN_FORMAT", ((result as Result.Error).exception as ValidationException).rule)
    }

    @Test
    fun `nonNumericPin_returnsInvalidPinFormatError`() = runTest {
        val result = QuickSwitchUserUseCase(QuickSwitchFakeAuthRepo()).invoke("user-01", "12a4")
        assertIs<Result.Error>(result)
        assertEquals("INVALID_PIN_FORMAT", ((result as Result.Error).exception as ValidationException).rule)
    }

    @Test
    fun `sixDigitPin_isAccepted`() = runTest {
        val user = buildUser(id = "user-01", role = Role.CASHIER)
        val result = QuickSwitchUserUseCase(QuickSwitchFakeAuthRepo(user)).invoke("user-01", "123456")
        assertIs<Result.Success<*>>(result)
    }
}
