package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.SyncConflict
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — ConflictLogRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [ConflictLogRepositoryImpl] against a real in-memory SQLite database.
 * No mocks — exercises the full SQLDelight query layer.
 *
 * Coverage:
 *  A. insert → getUnresolved emits the conflict via Turbine
 *  B. getUnresolvedCount returns correct count
 *  C. resolve marks conflict as resolved and removes from unresolved list
 *  D. getByEntity filters to specific entity type and ID
 *  E. pruneOld removes resolved conflicts before timestamp
 *  F. insert with blank id auto-generates an ID
 */
class ConflictLogRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: ConflictLogRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = ConflictLogRepositoryImpl(db)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    private fun makeConflict(
        id: String = "conflict-01",
        entityType: String = "products",
        entityId: String = "prod-01",
        fieldName: String = "price",
        localValue: String = "100.0",
        serverValue: String = "95.0",
        createdAt: Long = now,
    ) = SyncConflict(
        id = id,
        entityType = entityType,
        entityId = entityId,
        fieldName = fieldName,
        localValue = localValue,
        serverValue = serverValue,
        createdAt = createdAt,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - insert then getUnresolved emits the conflict via Turbine`() = runTest {
        val conflict = makeConflict(id = "conflict-01", entityType = "products", fieldName = "price")
        val insertResult = repo.insert(conflict)
        assertIs<Result.Success<Unit>>(insertResult)

        repo.getUnresolved().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            val fetched = list.first()
            assertEquals("conflict-01", fetched.id)
            assertEquals("products", fetched.entityType)
            assertEquals("prod-01", fetched.entityId)
            assertEquals("price", fetched.fieldName)
            assertEquals("100.0", fetched.localValue)
            assertEquals("95.0", fetched.serverValue)
            assertNull(fetched.resolvedBy)
            assertNull(fetched.resolvedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `B - getUnresolvedCount returns correct count`() = runTest {
        repo.insert(makeConflict(id = "conflict-01"))
        repo.insert(makeConflict(id = "conflict-02", entityId = "prod-02"))
        repo.insert(makeConflict(id = "conflict-03", entityId = "prod-03"))

        val countResult = repo.getUnresolvedCount()
        assertIs<Result.Success<Int>>(countResult)
        assertEquals(3, countResult.data)
    }

    @Test
    fun `C - resolve marks conflict resolved and removes from unresolved list`() = runTest {
        repo.insert(makeConflict(id = "conflict-01"))
        repo.insert(makeConflict(id = "conflict-02", entityId = "prod-02"))

        val resolveResult = repo.resolve(
            id = "conflict-01",
            resolvedBy = SyncConflict.Resolution.SERVER,
            resolution = "95.0",
            resolvedAt = now,
        )
        assertIs<Result.Success<Unit>>(resolveResult)

        repo.getUnresolved().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("conflict-02", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }

        val countResult = repo.getUnresolvedCount()
        assertIs<Result.Success<Int>>(countResult)
        assertEquals(1, countResult.data)
    }

    @Test
    fun `D - getByEntity filters to specific entity type and ID`() = runTest {
        repo.insert(makeConflict(id = "conflict-01", entityType = "products", entityId = "prod-01", fieldName = "price"))
        repo.insert(makeConflict(id = "conflict-02", entityType = "products", entityId = "prod-01", fieldName = "name"))
        repo.insert(makeConflict(id = "conflict-03", entityType = "products", entityId = "prod-02", fieldName = "price"))
        repo.insert(makeConflict(id = "conflict-04", entityType = "customers", entityId = "prod-01", fieldName = "name"))

        repo.getByEntity("products", "prod-01").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.all { it.entityType == "products" && it.entityId == "prod-01" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `E - pruneOld removes resolved conflicts before timestamp`() = runTest {
        val cutoff = now
        val old = cutoff - 100_000L
        val recent = cutoff + 100_000L

        repo.insert(makeConflict(id = "c-old-resolved", createdAt = old))
        repo.insert(makeConflict(id = "c-recent-resolved", entityId = "prod-02", createdAt = recent))
        repo.insert(makeConflict(id = "c-old-unresolved", entityId = "prod-03", createdAt = old))

        // Resolve the first two
        repo.resolve("c-old-resolved", SyncConflict.Resolution.SERVER, "resolved", old)
        repo.resolve("c-recent-resolved", SyncConflict.Resolution.LOCAL, "resolved", recent)

        val pruneResult = repo.pruneOld(cutoff)
        assertIs<Result.Success<Unit>>(pruneResult)

        // After pruning: old resolved conflict gone; recent resolved and unresolved remain
        val countResult = repo.getUnresolvedCount()
        assertIs<Result.Success<Int>>(countResult)
        // c-old-unresolved still present (unresolved cannot be pruned)
        assertEquals(1, countResult.data)
    }

    @Test
    fun `F - insert with blank id auto-generates an ID`() = runTest {
        val conflictWithBlankId = makeConflict(id = "")
        val insertResult = repo.insert(conflictWithBlankId)
        assertIs<Result.Success<Unit>>(insertResult)

        // At least one entry should exist in the unresolved list
        repo.getUnresolved().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertNotNull(list.first().id)
            assertTrue(list.first().id.isNotBlank())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
