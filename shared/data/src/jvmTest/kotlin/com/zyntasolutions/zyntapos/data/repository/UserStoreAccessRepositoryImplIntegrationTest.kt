package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.UserStoreAccess
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — UserStoreAccessRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [UserStoreAccessRepositoryImpl] against a real in-memory SQLite database.
 * No FK constraints on user_store_access — no pre-seeding required.
 *
 * Coverage:
 *  A. grantAccess → getById round-trip preserves all fields
 *  B. getAccessibleStores emits stores for a user via Turbine
 *  C. getUsersForStore emits users for a store via Turbine
 *  D. getByUserAndStore returns access for valid combination
 *  E. getByUserAndStore returns null for unknown combination
 *  F. revokeAccess sets is_active=false (excluded from getAccessibleStores)
 *  G. hasAccess returns true for active grant, false after revoke
 *  H. upsertFromSync inserts domain object
 */
class UserStoreAccessRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: UserStoreAccessRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = UserStoreAccessRepositoryImpl(db, SyncEnqueuer(db))
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    private fun makeAccess(
        id: String = "access-01",
        userId: String = "user-01",
        storeId: String = "store-01",
        roleAtStore: Role? = Role.CASHIER,
        isActive: Boolean = true,
        grantedBy: String? = "admin-01",
    ) = UserStoreAccess(
        id = id,
        userId = userId,
        storeId = storeId,
        roleAtStore = roleAtStore,
        isActive = isActive,
        grantedBy = grantedBy,
        createdAt = Instant.fromEpochMilliseconds(1_000_000L),
        updatedAt = Instant.fromEpochMilliseconds(1_000_000L),
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - grantAccess then getById returns full access record`() = runTest {
        val access = makeAccess(
            id = "access-01",
            userId = "user-01",
            storeId = "store-01",
            roleAtStore = Role.MANAGER,
            grantedBy = "admin-01",
        )
        val grantResult = repo.grantAccess(access)
        assertIs<Result.Success<Unit>>(grantResult)

        val fetchResult = repo.getById("access-01")
        assertIs<Result.Success<UserStoreAccess>>(fetchResult)
        val fetched = fetchResult.data
        assertEquals("access-01", fetched.id)
        assertEquals("user-01", fetched.userId)
        assertEquals("store-01", fetched.storeId)
        assertEquals(Role.MANAGER, fetched.roleAtStore)
        assertTrue(fetched.isActive)
        assertEquals("admin-01", fetched.grantedBy)
    }

    @Test
    fun `B - getAccessibleStores emits stores for a user via Turbine`() = runTest {
        repo.grantAccess(makeAccess(id = "access-01", userId = "user-01", storeId = "store-01"))
        repo.grantAccess(makeAccess(id = "access-02", userId = "user-01", storeId = "store-02"))
        repo.grantAccess(makeAccess(id = "access-03", userId = "user-02", storeId = "store-01"))

        repo.getAccessibleStores("user-01").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.any { it.storeId == "store-01" })
            assertTrue(list.any { it.storeId == "store-02" })
            assertTrue(list.none { it.userId == "user-02" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - getUsersForStore emits users for a store via Turbine`() = runTest {
        repo.grantAccess(makeAccess(id = "access-01", userId = "user-01", storeId = "store-01"))
        repo.grantAccess(makeAccess(id = "access-02", userId = "user-02", storeId = "store-01"))
        repo.grantAccess(makeAccess(id = "access-03", userId = "user-03", storeId = "store-02"))

        repo.getUsersForStore("store-01").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.any { it.userId == "user-01" })
            assertTrue(list.any { it.userId == "user-02" })
            assertTrue(list.none { it.storeId == "store-02" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `D - getByUserAndStore returns access for valid combination`() = runTest {
        repo.grantAccess(makeAccess(id = "access-01", userId = "user-01", storeId = "store-01"))

        val result = repo.getByUserAndStore("user-01", "store-01")
        assertIs<Result.Success<UserStoreAccess?>>(result)
        assertNotNull(result.data)
        assertEquals("access-01", result.data!!.id)
    }

    @Test
    fun `E - getByUserAndStore returns null for unknown combination`() = runTest {
        val result = repo.getByUserAndStore("non-existent-user", "non-existent-store")
        assertIs<Result.Success<UserStoreAccess?>>(result)
        assertNull(result.data)
    }

    @Test
    fun `F - revokeAccess deactivates grant and excludes from getAccessibleStores`() = runTest {
        repo.grantAccess(makeAccess(id = "access-01", userId = "user-01", storeId = "store-01"))
        repo.grantAccess(makeAccess(id = "access-02", userId = "user-01", storeId = "store-02"))

        val revokeResult = repo.revokeAccess("user-01", "store-01")
        assertIs<Result.Success<Unit>>(revokeResult)

        repo.getAccessibleStores("user-01").test {
            val list = awaitItem()
            // Only store-02 should remain (store-01 revoked)
            assertEquals(1, list.size)
            assertEquals("store-02", list.first().storeId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `G - hasAccess returns true for active grant and false after revoke`() = runTest {
        repo.grantAccess(makeAccess(id = "access-01", userId = "user-01", storeId = "store-01"))

        assertTrue(repo.hasAccess("user-01", "store-01"))
        assertFalse(repo.hasAccess("user-01", "store-02"))

        repo.revokeAccess("user-01", "store-01")
        assertFalse(repo.hasAccess("user-01", "store-01"))
    }

    @Test
    fun `H - upsertFromSync inserts access record without sync enqueue`() = runTest {
        val access = makeAccess(
            id = "access-sync-01",
            userId = "user-sync",
            storeId = "store-sync",
            roleAtStore = Role.ADMIN,
            grantedBy = null,
        )
        val upsertResult = repo.upsertFromSync(access)
        assertIs<Result.Success<Unit>>(upsertResult)

        val fetchResult = repo.getById("access-sync-01")
        assertIs<Result.Success<UserStoreAccess>>(fetchResult)
        assertEquals("user-sync", fetchResult.data.userId)
        assertEquals("store-sync", fetchResult.data.storeId)
        assertEquals(Role.ADMIN, fetchResult.data.roleAtStore)
        assertNull(fetchResult.data.grantedBy)
    }
}
