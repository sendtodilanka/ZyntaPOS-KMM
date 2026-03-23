package com.zyntasolutions.zyntapos.domain.usecase.auth

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.model.UserStoreAccess
import com.zyntasolutions.zyntapos.domain.repository.UserRepository
import com.zyntasolutions.zyntapos.domain.repository.UserStoreAccessRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoreAccessUseCasesTest {

    // ── Fakes ──────────────────────────────────────────────────────────

    private class FakeUserRepository : UserRepository {
        val users = mutableMapOf<String, User>()

        override fun getAll(storeId: String?): Flow<List<User>> = flowOf(users.values.toList())
        override suspend fun getById(id: String): Result<User> {
            val user = users[id] ?: return Result.Error(ValidationException("User not found"))
            return Result.Success(user)
        }
        override suspend fun create(user: User, plainPassword: String): Result<Unit> = Result.Success(Unit)
        override suspend fun update(user: User): Result<Unit> = Result.Success(Unit)
        override suspend fun updatePassword(userId: String, newPlainPassword: String): Result<Unit> = Result.Success(Unit)
        override suspend fun deactivate(userId: String): Result<Unit> = Result.Success(Unit)
        override suspend fun getSystemAdmin(): Result<User?> = Result.Success(null)
        override suspend fun adminExists(): Result<Boolean> = Result.Success(false)
        override suspend fun transferSystemAdmin(fromUserId: String, toUserId: String): Result<Unit> = Result.Success(Unit)
    }

    private class FakeUserStoreAccessRepository : UserStoreAccessRepository {
        val grants = mutableListOf<UserStoreAccess>()

        override fun getAccessibleStores(userId: String): Flow<List<UserStoreAccess>> =
            flowOf(grants.filter { it.userId == userId && it.isActive })

        override fun getUsersForStore(storeId: String): Flow<List<UserStoreAccess>> =
            flowOf(grants.filter { it.storeId == storeId && it.isActive })

        override suspend fun getById(id: String): Result<UserStoreAccess> {
            val grant = grants.find { it.id == id }
                ?: return Result.Error(ValidationException("Not found"))
            return Result.Success(grant)
        }

        override suspend fun getByUserAndStore(userId: String, storeId: String): Result<UserStoreAccess?> =
            Result.Success(grants.find { it.userId == userId && it.storeId == storeId })

        override suspend fun grantAccess(access: UserStoreAccess): Result<Unit> {
            grants.removeAll { it.userId == access.userId && it.storeId == access.storeId }
            grants.add(access)
            return Result.Success(Unit)
        }

        override suspend fun revokeAccess(userId: String, storeId: String): Result<Unit> {
            val idx = grants.indexOfFirst { it.userId == userId && it.storeId == storeId }
            if (idx >= 0) {
                grants[idx] = grants[idx].copy(isActive = false)
            }
            return Result.Success(Unit)
        }

        override suspend fun hasAccess(userId: String, storeId: String): Boolean =
            grants.any { it.userId == userId && it.storeId == storeId && it.isActive }

        override suspend fun upsertFromSync(access: UserStoreAccess): Result<Unit> {
            grants.removeAll { it.id == access.id }
            grants.add(access)
            return Result.Success(Unit)
        }
    }

    private fun createUser(
        id: String = "user-1",
        storeId: String = "store-A",
        role: Role = Role.CASHIER,
        isActive: Boolean = true,
    ) = User(
        id = id,
        name = "Test User",
        email = "test@example.com",
        role = role,
        storeId = storeId,
        isActive = isActive,
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now(),
    )

    // ── GrantStoreAccessUseCase tests ──────────────────────────────────

    @Test
    fun grantAccess_success() = runTest {
        val userRepo = FakeUserRepository()
        val accessRepo = FakeUserStoreAccessRepository()
        val user = createUser()
        userRepo.users[user.id] = user

        val useCase = GrantStoreAccessUseCase(userRepo, accessRepo)
        val result = useCase(GrantStoreAccessUseCase.Params(
            userId = user.id,
            storeId = "store-B",
            roleAtStore = Role.STORE_MANAGER,
            grantedBy = "admin-1",
        ))

        assertIs<Result.Success<UserStoreAccess>>(result)
        assertEquals("store-B", result.data.storeId)
        assertEquals(Role.STORE_MANAGER, result.data.roleAtStore)
        assertEquals(1, accessRepo.grants.size)
    }

    @Test
    fun grantAccess_rejectsPrimaryStore() = runTest {
        val userRepo = FakeUserRepository()
        val accessRepo = FakeUserStoreAccessRepository()
        val user = createUser(storeId = "store-A")
        userRepo.users[user.id] = user

        val useCase = GrantStoreAccessUseCase(userRepo, accessRepo)
        val result = useCase(GrantStoreAccessUseCase.Params(
            userId = user.id,
            storeId = "store-A", // same as primary store
        ))

        assertIs<Result.Error>(result)
        assertTrue(result.exception.message!!.contains("primary access"))
    }

    @Test
    fun grantAccess_rejectsDuplicate() = runTest {
        val userRepo = FakeUserRepository()
        val accessRepo = FakeUserStoreAccessRepository()
        val user = createUser()
        userRepo.users[user.id] = user

        // Pre-existing grant
        accessRepo.grants.add(UserStoreAccess(
            id = "grant-1",
            userId = user.id,
            storeId = "store-B",
            isActive = true,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
        ))

        val useCase = GrantStoreAccessUseCase(userRepo, accessRepo)
        val result = useCase(GrantStoreAccessUseCase.Params(
            userId = user.id,
            storeId = "store-B",
        ))

        assertIs<Result.Error>(result)
        assertTrue(result.exception.message!!.contains("already has active access"))
    }

    @Test
    fun grantAccess_rejectsInactiveUser() = runTest {
        val userRepo = FakeUserRepository()
        val accessRepo = FakeUserStoreAccessRepository()
        val user = createUser(isActive = false)
        userRepo.users[user.id] = user

        val useCase = GrantStoreAccessUseCase(userRepo, accessRepo)
        val result = useCase(GrantStoreAccessUseCase.Params(
            userId = user.id,
            storeId = "store-B",
        ))

        assertIs<Result.Error>(result)
        assertTrue(result.exception.message!!.contains("inactive"))
    }

    @Test
    fun grantAccess_rejectsNonExistentUser() = runTest {
        val userRepo = FakeUserRepository()
        val accessRepo = FakeUserStoreAccessRepository()

        val useCase = GrantStoreAccessUseCase(userRepo, accessRepo)
        val result = useCase(GrantStoreAccessUseCase.Params(
            userId = "nonexistent",
            storeId = "store-B",
        ))

        assertIs<Result.Error>(result)
    }

    // ── RevokeStoreAccessUseCase tests ─────────────────────────────────

    @Test
    fun revokeAccess_success() = runTest {
        val accessRepo = FakeUserStoreAccessRepository()
        accessRepo.grants.add(UserStoreAccess(
            id = "grant-1",
            userId = "user-1",
            storeId = "store-B",
            isActive = true,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
        ))

        val useCase = RevokeStoreAccessUseCase(accessRepo)
        val result = useCase("user-1", "store-B")

        assertIs<Result.Success<Unit>>(result)
        assertFalse(accessRepo.grants.first().isActive)
    }

    @Test
    fun revokeAccess_failsIfNoGrant() = runTest {
        val accessRepo = FakeUserStoreAccessRepository()

        val useCase = RevokeStoreAccessUseCase(accessRepo)
        val result = useCase("user-1", "store-B")

        assertIs<Result.Error>(result)
    }

    // ── CheckStoreAccessUseCase tests ──────────────────────────────────

    @Test
    fun checkAccess_primaryStore() = runTest {
        val userRepo = FakeUserRepository()
        val accessRepo = FakeUserStoreAccessRepository()
        val user = createUser(storeId = "store-A", role = Role.CASHIER)
        userRepo.users[user.id] = user

        val useCase = CheckStoreAccessUseCase(userRepo, accessRepo)
        val result = useCase(user.id, "store-A")

        assertTrue(result.hasAccess)
        assertEquals(Role.CASHIER, result.effectiveRole)
    }

    @Test
    fun checkAccess_grantedStore_withRoleOverride() = runTest {
        val userRepo = FakeUserRepository()
        val accessRepo = FakeUserStoreAccessRepository()
        val user = createUser(storeId = "store-A", role = Role.CASHIER)
        userRepo.users[user.id] = user

        accessRepo.grants.add(UserStoreAccess(
            id = "grant-1",
            userId = user.id,
            storeId = "store-B",
            roleAtStore = Role.STORE_MANAGER,
            isActive = true,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
        ))

        val useCase = CheckStoreAccessUseCase(userRepo, accessRepo)
        val result = useCase(user.id, "store-B")

        assertTrue(result.hasAccess)
        assertEquals(Role.STORE_MANAGER, result.effectiveRole)
    }

    @Test
    fun checkAccess_grantedStore_noRoleOverride_usesDefault() = runTest {
        val userRepo = FakeUserRepository()
        val accessRepo = FakeUserStoreAccessRepository()
        val user = createUser(storeId = "store-A", role = Role.CASHIER)
        userRepo.users[user.id] = user

        accessRepo.grants.add(UserStoreAccess(
            id = "grant-1",
            userId = user.id,
            storeId = "store-B",
            roleAtStore = null,
            isActive = true,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
        ))

        val useCase = CheckStoreAccessUseCase(userRepo, accessRepo)
        val result = useCase(user.id, "store-B")

        assertTrue(result.hasAccess)
        assertEquals(Role.CASHIER, result.effectiveRole) // Falls back to user's default
    }

    @Test
    fun checkAccess_noAccess() = runTest {
        val userRepo = FakeUserRepository()
        val accessRepo = FakeUserStoreAccessRepository()
        val user = createUser(storeId = "store-A")
        userRepo.users[user.id] = user

        val useCase = CheckStoreAccessUseCase(userRepo, accessRepo)
        val result = useCase(user.id, "store-C")

        assertFalse(result.hasAccess)
        assertNull(result.effectiveRole)
    }

    @Test
    fun checkAccess_revokedGrant() = runTest {
        val userRepo = FakeUserRepository()
        val accessRepo = FakeUserStoreAccessRepository()
        val user = createUser(storeId = "store-A")
        userRepo.users[user.id] = user

        accessRepo.grants.add(UserStoreAccess(
            id = "grant-1",
            userId = user.id,
            storeId = "store-B",
            isActive = false, // revoked
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
        ))

        val useCase = CheckStoreAccessUseCase(userRepo, accessRepo)
        val result = useCase(user.id, "store-B")

        assertFalse(result.hasAccess)
    }
}
