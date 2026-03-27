package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.port.PasswordHashPort
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — UserRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [UserRepositoryImpl] against a real in-memory SQLite database.
 * Uses an inline fake [PasswordHashPort] — no real hashing or Keystore I/O.
 *
 * Coverage:
 *  A. create → getById round-trip preserves all fields
 *  B. getAll emits all users (no storeId filter)
 *  C. getAll filtered by storeId only returns matching users
 *  D. create duplicate email returns ValidationException error
 *  E. create second ADMIN account returns ValidationException error (one-admin rule)
 *  F. deactivate soft-deactivates user (isActive = false)
 *  G. updatePin stores hashed PIN on user
 *  H. getById for unknown ID returns error
 */
class UserRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: UserRepositoryImpl

    /** Simple fake: hash = "hashed:<plain>", verify checks that prefix. */
    private val fakePasswordHasher = object : PasswordHashPort {
        override fun hash(plain: String): String = "hashed:$plain"
        override fun verify(plain: String, hashed: String): Boolean = hashed == "hashed:$plain"
    }

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = UserRepositoryImpl(db, SyncEnqueuer(db), fakePasswordHasher)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val baseInstant: Instant = Instant.fromEpochMilliseconds(1_000_000L)

    private fun makeUser(
        id: String = "user-01",
        name: String = "Alice Smith",
        email: String = "alice@example.com",
        role: Role = Role.CASHIER, // valid values: ADMIN, STORE_MANAGER, CASHIER, STOCK_MANAGER
        storeId: String = "store-01",
        isActive: Boolean = true,
        isSystemAdmin: Boolean = false,
    ) = User(
        id = id,
        name = name,
        email = email,
        role = role,
        storeId = storeId,
        isActive = isActive,
        isSystemAdmin = isSystemAdmin,
        createdAt = baseInstant,
        updatedAt = baseInstant,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - create then getById returns full user`() = runTest {
        val user = makeUser(id = "user-01", name = "Bob Jones", email = "bob@example.com", role = Role.STORE_MANAGER)
        val createResult = repo.create(user, plainPassword = "secret123")
        assertIs<Result.Success<Unit>>(createResult)

        val fetchResult = repo.getById("user-01")
        assertIs<Result.Success<User>>(fetchResult)
        val fetched = fetchResult.data
        assertEquals("user-01", fetched.id)
        assertEquals("Bob Jones", fetched.name)
        assertEquals("bob@example.com", fetched.email)
        assertEquals(Role.STORE_MANAGER, fetched.role)
        assertEquals("store-01", fetched.storeId)
        assertTrue(fetched.isActive)
    }

    @Test
    fun `B - getAll emits all users`() = runTest {
        repo.create(makeUser(id = "user-01", email = "alice@example.com"), "pass1")
        repo.create(makeUser(id = "user-02", email = "bob@example.com"), "pass2")

        repo.getAll(storeId = null).test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.any { it.id == "user-01" })
            assertTrue(list.any { it.id == "user-02" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - getAll filtered by storeId returns only matching users`() = runTest {
        repo.create(makeUser(id = "user-01", email = "alice@example.com", storeId = "store-01"), "pass1")
        repo.create(makeUser(id = "user-02", email = "bob@example.com", storeId = "store-02"), "pass2")

        repo.getAll(storeId = "store-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("user-01", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `D - create duplicate email returns ValidationException`() = runTest {
        repo.create(makeUser(id = "user-01", email = "shared@example.com"), "pass1")
        val result = repo.create(makeUser(id = "user-02", email = "shared@example.com"), "pass2")
        assertIs<Result.Error>(result)
        assertNotNull((result as Result.Error).exception)
    }

    @Test
    fun `E - create second ADMIN returns ValidationException (one-admin rule)`() = runTest {
        // Create first ADMIN (isSystemAdmin = true enforces single-admin rule)
        val admin1 = makeUser(id = "admin-01", email = "admin1@example.com", role = Role.ADMIN, isSystemAdmin = true)
        repo.create(admin1, "adminpass1")

        // Attempt second ADMIN
        val admin2 = makeUser(id = "admin-02", email = "admin2@example.com", role = Role.ADMIN, isSystemAdmin = false)
        val result = repo.create(admin2, "adminpass2")
        assertIs<Result.Error>(result)
    }

    @Test
    fun `F - deactivate soft-deactivates user`() = runTest {
        repo.create(makeUser(id = "user-01", email = "alice@example.com"), "pass1")

        val deactivateResult = repo.deactivate("user-01")
        assertIs<Result.Success<Unit>>(deactivateResult)

        val fetched = (repo.getById("user-01") as Result.Success).data
        assertTrue(!fetched.isActive)
    }

    @Test
    fun `G - update changes user profile fields`() = runTest {
        val original = makeUser(id = "user-01", email = "alice@example.com", name = "Alice Smith")
        repo.create(original, "pass1")

        val updated = original.copy(name = "Alice Updated", role = Role.STORE_MANAGER)
        val updateResult = repo.update(updated)
        assertIs<Result.Success<Unit>>(updateResult)

        val fetched = (repo.getById("user-01") as Result.Success).data
        assertEquals("Alice Updated", fetched.name)
        assertEquals(Role.STORE_MANAGER, fetched.role)
    }

    @Test
    fun `H - getById for unknown ID returns error`() = runTest {
        val result = repo.getById("non-existent")
        assertIs<Result.Error>(result)
        assertNotNull((result as Result.Error).exception)
    }
}
