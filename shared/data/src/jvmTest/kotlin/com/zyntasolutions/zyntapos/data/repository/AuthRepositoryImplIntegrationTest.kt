package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.sync.InMemorySecurePreferences
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.port.PasswordHashPort
import com.zyntasolutions.zyntapos.domain.port.SecureStoragePort
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — AuthRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [AuthRepositoryImpl.validatePin] using a real in-memory SQLite database
 * (via [createTestDatabase]) and lightweight test doubles for [SecureStoragePort]
 * ([InMemorySecurePreferences]) and [PasswordHashPort] ([fakePasswordHasher]).
 *
 * No real hashing, Keystore, or filesystem I/O is performed — every test is
 * fully isolated and completes in-process.
 *
 * Test cases:
 *  1. validatePin returns Result.Success(true) when PIN matches stored hash
 *  2. validatePin returns Result.Success(false) when PIN does not match
 *  3. validatePin returns Result.Success(false) when user has no PIN hash (null)
 *  4. validatePin returns Result.Success(false) when user does not exist
 */
class AuthRepositoryImplIntegrationTest {

    // ── Test doubles ─────────────────────────────────────────────────────────

    private val fakePasswordHasher = object : PasswordHashPort {
        override fun hash(plain: String): String = "hashed:$plain"
        override fun verify(plain: String, hashed: String): Boolean = hashed == "hashed:$plain"
    }

    // ── Subject under test ───────────────────────────────────────────────────

    private lateinit var db: ZyntaDatabase
    private lateinit var securePrefs: InMemorySecurePreferences
    private lateinit var repository: AuthRepositoryImpl

    private val now get() = Clock.System.now().toEpochMilliseconds()

    @BeforeTest
    fun setup() {
        db          = createTestDatabase()
        securePrefs = InMemorySecurePreferences()
        repository  = AuthRepositoryImpl(
            db             = db,
            securePrefs    = securePrefs,
            passwordHasher = fakePasswordHasher,
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Inserts a minimal user row into the `users` table.
     *
     * [pinHash] is nullable — pass `null` to simulate a user with no PIN set.
     */
    private fun insertUser(
        id           : String,
        email        : String,
        pinHash      : String?,
        passwordHash : String = fakePasswordHasher.hash("password"),
        role         : String = "CASHIER",
        storeId      : String = "store-1",
        isActive     : Long   = 1L,
    ) {
        db.usersQueries.insertUser(
            id             = id,
            name           = "Test User",
            email          = email,
            password_hash  = passwordHash,
            role           = role,
            pin_hash       = pinHash,
            store_id       = storeId,
            is_active      = isActive,
            created_at     = now,
            updated_at     = now,
            sync_status    = "PENDING",
            custom_role_id = null,
        )
    }

    // ── 1. Correct PIN returns Success(true) ──────────────────────────────────

    @Test
    fun validatePin_returns_true_when_PIN_matches_stored_hash() = runTest {
        val userId = "user-correct-pin"
        insertUser(
            id      = userId,
            email   = "cashier@example.com",
            pinHash = fakePasswordHasher.hash("1234"),
        )

        val result = repository.validatePin(userId, "1234")

        assertIs<Result.Success<Boolean>>(result)
        assertEquals(true, result.data)
    }

    // ── 2. Wrong PIN returns Success(false) ───────────────────────────────────

    @Test
    fun validatePin_returns_false_when_PIN_does_not_match() = runTest {
        val userId = "user-wrong-pin"
        insertUser(
            id      = userId,
            email   = "manager@example.com",
            pinHash = fakePasswordHasher.hash("1234"),
        )

        val result = repository.validatePin(userId, "9999")

        assertIs<Result.Success<Boolean>>(result)
        assertEquals(false, result.data)
    }

    // ── 3. User has no PIN set (null) → Success(false) ────────────────────────

    @Test
    fun validatePin_returns_false_when_user_has_no_PIN_hash() = runTest {
        val userId = "user-no-pin"
        insertUser(
            id      = userId,
            email   = "nopin@example.com",
            pinHash = null,
        )

        val result = repository.validatePin(userId, "1234")

        assertIs<Result.Success<Boolean>>(result)
        assertEquals(false, result.data)
    }

    // ── 4. User does not exist → Success(false) ───────────────────────────────

    @Test
    fun validatePin_returns_false_when_user_does_not_exist() = runTest {
        val result = repository.validatePin("nonexistent-id", "1234")

        assertIs<Result.Success<Boolean>>(result)
        assertEquals(false, result.data)
    }
}
