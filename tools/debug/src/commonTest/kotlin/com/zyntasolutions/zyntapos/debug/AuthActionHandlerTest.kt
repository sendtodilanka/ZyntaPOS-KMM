package com.zyntasolutions.zyntapos.debug

import com.zyntasolutions.zyntapos.core.result.AuthException
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.debug.actions.AuthActionHandlerImpl
import com.zyntasolutions.zyntapos.debug.model.UserSummary
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [AuthActionHandlerImpl].
 *
 * Verifies that:
 * - [AuthActionHandlerImpl.getAllUsers] maps [User] domain objects to [UserSummary] DTOs.
 * - [AuthActionHandlerImpl.createAdminUser] constructs a [User] with [Role.ADMIN]
 *   and delegates to [UserRepository.create] — the plain password is forwarded
 *   and never stored.
 * - [AuthActionHandlerImpl.deactivateUser] delegates to [UserRepository.deactivate].
 * - [AuthActionHandlerImpl.clearSession] delegates to [AuthRepository.logout].
 * - [AuthActionHandlerImpl.getCurrentUser] reflects the current session from [AuthRepository].
 */
class AuthActionHandlerTest {

    private val now = Clock.System.now()

    // ── Test data ─────────────────────────────────────────────────────────────

    private val adminUser = User(
        id        = "u-admin",
        name      = "Super Admin",
        email     = "admin@store.com",
        role      = Role.ADMIN,
        storeId   = "store-1",
        isActive  = true,
        pinHash   = "salt:hash",
        createdAt = now,
        updatedAt = now,
    )

    private val cashierUser = User(
        id        = "u-cashier",
        name      = "Alice",
        email     = "alice@store.com",
        role      = Role.CASHIER,
        storeId   = "store-1",
        isActive  = true,
        pinHash   = null,
        createdAt = now,
        updatedAt = now,
    )

    private val inactiveUser = User(
        id        = "u-inactive",
        name      = "Bob",
        email     = "bob@store.com",
        role      = Role.STORE_MANAGER,
        storeId   = "store-1",
        isActive  = false,
        pinHash   = null,
        createdAt = now,
        updatedAt = now,
    )

    // ── Stub repositories ──────────────────────────────────────────────────────

    private inner class StubUserRepository(
        private val users: List<User> = emptyList(),
        private val getAllThrows: Boolean = false,
        var createResult: Result<Unit> = Result.Success(Unit),
        var deactivateResult: Result<Unit> = Result.Success(Unit),
    ) : UserRepository {
        val createdUsers = mutableListOf<Pair<User, String>>()  // user, plainPassword
        val deactivatedIds = mutableListOf<String>()

        private val usersFlow = MutableStateFlow(users)

        override fun getAll(storeId: String?): Flow<List<User>> {
            if (getAllThrows) throw RuntimeException("DB connection lost")
            return usersFlow
        }

        override suspend fun getById(id: String): Result<User> =
            users.find { it.id == id }
                ?.let { Result.Success(it) }
                ?: Result.Error(DatabaseException("User not found"))

        override suspend fun create(user: User, plainPassword: String): Result<Unit> {
            createdUsers += user to plainPassword
            return createResult
        }

        override suspend fun update(user: User): Result<Unit> = Result.Success(Unit)
        override suspend fun updatePassword(userId: String, newPlainPassword: String): Result<Unit> =
            Result.Success(Unit)
        override suspend fun deactivate(userId: String): Result<Unit> {
            deactivatedIds += userId
            return deactivateResult
        }

        override suspend fun getSystemAdmin(): Result<User?> = Result.Success(null)
        override suspend fun adminExists(): Result<Boolean> = Result.Success(false)
        override suspend fun transferSystemAdmin(fromUserId: String, toUserId: String): Result<Unit> =
            Result.Success(Unit)
    }

    private inner class StubAuthRepository(
        private var sessionUser: User? = null,
        private val logoutThrows: Boolean = false,
    ) : AuthRepository {
        var logoutCallCount = 0
        private val sessionFlow = MutableStateFlow(sessionUser)

        override suspend fun login(email: String, password: String): Result<User> =
            Result.Error(AuthException("not used in this test"))

        override suspend fun logout() {
            logoutCallCount++
            if (logoutThrows) throw RuntimeException("Keystore unavailable")
            sessionFlow.value = null
        }

        override fun getSession(): Flow<User?> = sessionFlow

        override suspend fun refreshToken(): Result<Unit> = Result.Success(Unit)

        override suspend fun updatePin(userId: String, pin: String): Result<Unit> =
            Result.Success(Unit)

        override suspend fun validatePin(userId: String, pin: String): Result<Boolean> =
            Result.Success(true)
    }

    // ── SUT builder ───────────────────────────────────────────────────────────

    private fun buildHandler(
        userRepo: StubUserRepository = StubUserRepository(),
        authRepo: StubAuthRepository = StubAuthRepository(),
    ) = AuthActionHandlerImpl(authRepo, userRepo)

    // ── getAllUsers ────────────────────────────────────────────────────────────

    @Test
    fun `getAllUsers returns Success with empty list when no users exist`() = runTest {
        val handler = buildHandler(userRepo = StubUserRepository(users = emptyList()))

        val result = handler.getAllUsers()

        assertIs<Result.Success<List<UserSummary>>>(result)
        assertTrue(result.data.isEmpty())
    }

    @Test
    fun `getAllUsers maps User to UserSummary preserving id`() = runTest {
        val handler = buildHandler(userRepo = StubUserRepository(users = listOf(adminUser)))

        val result = handler.getAllUsers() as Result.Success
        val summary = result.data.first()

        assertEquals("u-admin", summary.id)
    }

    @Test
    fun `getAllUsers maps User to UserSummary preserving name`() = runTest {
        val handler = buildHandler(userRepo = StubUserRepository(users = listOf(adminUser)))

        val result = handler.getAllUsers() as Result.Success
        assertEquals("Super Admin", result.data.first().name)
    }

    @Test
    fun `getAllUsers maps User to UserSummary preserving email`() = runTest {
        val handler = buildHandler(userRepo = StubUserRepository(users = listOf(adminUser)))

        val result = handler.getAllUsers() as Result.Success
        assertEquals("admin@store.com", result.data.first().email)
    }

    @Test
    fun `getAllUsers maps role to role name string`() = runTest {
        val handler = buildHandler(userRepo = StubUserRepository(users = listOf(adminUser)))

        val result = handler.getAllUsers() as Result.Success
        assertEquals("ADMIN", result.data.first().role)
    }

    @Test
    fun `getAllUsers maps isActive correctly for active user`() = runTest {
        val handler = buildHandler(userRepo = StubUserRepository(users = listOf(cashierUser)))

        val result = handler.getAllUsers() as Result.Success
        assertTrue(result.data.first().isActive)
    }

    @Test
    fun `getAllUsers maps isActive correctly for inactive user`() = runTest {
        val handler = buildHandler(userRepo = StubUserRepository(users = listOf(inactiveUser)))

        val result = handler.getAllUsers() as Result.Success
        assertFalse(result.data.first().isActive)
    }

    @Test
    fun `getAllUsers returns all users including inactive`() = runTest {
        val allUsers = listOf(adminUser, cashierUser, inactiveUser)
        val handler = buildHandler(userRepo = StubUserRepository(users = allUsers))

        val result = handler.getAllUsers() as Result.Success
        assertEquals(3, result.data.size)
    }

    @Test
    fun `getAllUsers returns Result Error when UserRepository getAll throws`() = runTest {
        val handler = buildHandler(userRepo = StubUserRepository(getAllThrows = true))

        val result = handler.getAllUsers()

        assertIs<Result.Error>(result)
        assertIs<DatabaseException>(result.exception)
    }

    @Test
    fun `getAllUsers Error message contains Failed to load users`() = runTest {
        val handler = buildHandler(userRepo = StubUserRepository(getAllThrows = true))

        val result = handler.getAllUsers()

        assertIs<Result.Error>(result)
        assertTrue(result.exception.message.contains("Failed to load users"))
    }

    // ── createAdminUser ────────────────────────────────────────────────────────

    @Test
    fun `createAdminUser calls UserRepository create`() = runTest {
        val userRepo = StubUserRepository()
        val handler = buildHandler(userRepo = userRepo)

        handler.createAdminUser("new@store.com", "Admin User", "Secure123!")

        assertEquals(1, userRepo.createdUsers.size)
    }

    @Test
    fun `createAdminUser creates user with ADMIN role`() = runTest {
        val userRepo = StubUserRepository()
        val handler = buildHandler(userRepo = userRepo)

        handler.createAdminUser("admin@store.com", "Admin", "pass")

        val (createdUser, _) = userRepo.createdUsers.first()
        assertEquals(Role.ADMIN, createdUser.role)
    }

    @Test
    fun `createAdminUser passes email to created User`() = runTest {
        val userRepo = StubUserRepository()
        val handler = buildHandler(userRepo = userRepo)

        handler.createAdminUser("boss@store.com", "Boss", "pass")

        val (createdUser, _) = userRepo.createdUsers.first()
        assertEquals("boss@store.com", createdUser.email)
    }

    @Test
    fun `createAdminUser passes name to created User`() = runTest {
        val userRepo = StubUserRepository()
        val handler = buildHandler(userRepo = userRepo)

        handler.createAdminUser("boss@store.com", "The Boss", "pass")

        val (createdUser, _) = userRepo.createdUsers.first()
        assertEquals("The Boss", createdUser.name)
    }

    @Test
    fun `createAdminUser passes plainPassword to UserRepository create`() = runTest {
        val userRepo = StubUserRepository()
        val handler = buildHandler(userRepo = userRepo)

        handler.createAdminUser("admin@store.com", "Admin", "MySecretPass!")

        val (_, password) = userRepo.createdUsers.first()
        assertEquals("MySecretPass!", password)
    }

    @Test
    fun `createAdminUser returns Result Success when UserRepository create succeeds`() = runTest {
        val userRepo = StubUserRepository(createResult = Result.Success(Unit))
        val handler = buildHandler(userRepo = userRepo)

        val result = handler.createAdminUser("a@b.com", "Admin", "pass")

        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `createAdminUser returns Result Error when UserRepository create fails`() = runTest {
        val userRepo = StubUserRepository(
            createResult = Result.Error(DatabaseException("Duplicate email"))
        )
        val handler = buildHandler(userRepo = userRepo)

        val result = handler.createAdminUser("dup@store.com", "Admin", "pass")

        assertIs<Result.Error>(result)
    }

    @Test
    fun `createAdminUser creates User with isActive true`() = runTest {
        val userRepo = StubUserRepository()
        val handler = buildHandler(userRepo = userRepo)

        handler.createAdminUser("active@store.com", "Active", "pass")

        val (createdUser, _) = userRepo.createdUsers.first()
        assertTrue(createdUser.isActive)
    }

    @Test
    fun `createAdminUser creates User with null pinHash`() = runTest {
        val userRepo = StubUserRepository()
        val handler = buildHandler(userRepo = userRepo)

        handler.createAdminUser("npin@store.com", "NoPIN", "pass")

        val (createdUser, _) = userRepo.createdUsers.first()
        assertNull(createdUser.pinHash)
    }

    @Test
    fun `createAdminUser creates User with non-empty generated id`() = runTest {
        val userRepo = StubUserRepository()
        val handler = buildHandler(userRepo = userRepo)

        handler.createAdminUser("id@store.com", "Admin", "pass")

        val (createdUser, _) = userRepo.createdUsers.first()
        assertTrue(createdUser.id.isNotEmpty())
    }

    // ── deactivateUser ─────────────────────────────────────────────────────────

    @Test
    fun `deactivateUser calls UserRepository deactivate with correct id`() = runTest {
        val userRepo = StubUserRepository()
        val handler = buildHandler(userRepo = userRepo)

        handler.deactivateUser("u-cashier")

        assertEquals(listOf("u-cashier"), userRepo.deactivatedIds)
    }

    @Test
    fun `deactivateUser returns Result Success when deactivate succeeds`() = runTest {
        val userRepo = StubUserRepository(deactivateResult = Result.Success(Unit))
        val handler = buildHandler(userRepo = userRepo)

        val result = handler.deactivateUser("u-target")

        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `deactivateUser returns Result Error when deactivate fails`() = runTest {
        val userRepo = StubUserRepository(
            deactivateResult = Result.Error(DatabaseException("User not found"))
        )
        val handler = buildHandler(userRepo = userRepo)

        val result = handler.deactivateUser("u-missing")

        assertIs<Result.Error>(result)
    }

    @Test
    fun `deactivateUser with multiple calls tracks each id`() = runTest {
        val userRepo = StubUserRepository()
        val handler = buildHandler(userRepo = userRepo)

        handler.deactivateUser("u-1")
        handler.deactivateUser("u-2")

        assertEquals(listOf("u-1", "u-2"), userRepo.deactivatedIds)
    }

    // ── clearSession ───────────────────────────────────────────────────────────

    @Test
    fun `clearSession calls AuthRepository logout`() = runTest {
        val authRepo = StubAuthRepository()
        val handler = buildHandler(authRepo = authRepo)

        handler.clearSession()

        assertEquals(1, authRepo.logoutCallCount)
    }

    @Test
    fun `clearSession returns Result Success when logout succeeds`() = runTest {
        val handler = buildHandler(authRepo = StubAuthRepository())

        val result = handler.clearSession()

        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `clearSession returns Result Error when logout throws`() = runTest {
        val handler = buildHandler(authRepo = StubAuthRepository(logoutThrows = true))

        val result = handler.clearSession()

        assertIs<Result.Error>(result)
        assertIs<AuthException>(result.exception)
    }

    @Test
    fun `clearSession Error message contains Logout failed`() = runTest {
        val handler = buildHandler(authRepo = StubAuthRepository(logoutThrows = true))

        val result = handler.clearSession()

        assertIs<Result.Error>(result)
        assertTrue(result.exception.message.contains("Logout failed"))
    }

    // ── getCurrentUser ─────────────────────────────────────────────────────────

    @Test
    fun `getCurrentUser returns null when no session exists`() = runTest {
        val handler = buildHandler(authRepo = StubAuthRepository(sessionUser = null))

        val user = handler.getCurrentUser()

        assertNull(user)
    }

    @Test
    fun `getCurrentUser returns User when session is active`() = runTest {
        val handler = buildHandler(authRepo = StubAuthRepository(sessionUser = adminUser))

        val user = handler.getCurrentUser()

        assertNotNull(user)
        assertEquals("u-admin", user.id)
    }

    @Test
    fun `getCurrentUser returns User with correct email`() = runTest {
        val handler = buildHandler(authRepo = StubAuthRepository(sessionUser = cashierUser))

        val user = handler.getCurrentUser()

        assertNotNull(user)
        assertEquals("alice@store.com", user.email)
    }

    @Test
    fun `getCurrentUser reflects role of the session user`() = runTest {
        val handler = buildHandler(authRepo = StubAuthRepository(sessionUser = cashierUser))

        val user = handler.getCurrentUser()

        assertNotNull(user)
        assertEquals(Role.CASHIER, user.role)
    }
}
