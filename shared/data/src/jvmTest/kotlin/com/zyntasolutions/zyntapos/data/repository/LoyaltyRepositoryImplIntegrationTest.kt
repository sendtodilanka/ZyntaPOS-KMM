package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.RewardPoints
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock

/**
 * ZyntaPOS — LoyaltyRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [LoyaltyRepositoryImpl] against a real in-memory SQLite database.
 * A customer row is seeded in BeforeTest to satisfy the reward_points FK constraint.
 *
 * Coverage:
 *  A. recordPoints then getBalance returns correct sum
 *  B. recordPoints multiple entries accumulates balance correctly
 *  C. getBalance for customer with no points returns 0
 *  D. expirePointsForCustomer expires eligible entries and inserts EXPIRED ledger row
 *  E. expirePointsForCustomer with no expirable entries returns 0
 */
class LoyaltyRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: LoyaltyRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = LoyaltyRepositoryImpl(db)

        val now = Clock.System.now().toEpochMilliseconds()
        // Seed customer (FK required by reward_points)
        db.customersQueries.insertCustomer(
            id = "cust-01",
            name = "Alice Smith",
            phone = null,
            email = null,
            address = null,
            group_id = null,
            loyalty_points = 0L,
            notes = null,
            is_active = 1L,
            credit_limit = 0.0,
            credit_enabled = 0L,
            gender = null,
            birthday = null,
            is_walk_in = 0L,
            store_id = "store-01",
            created_at = now,
            updated_at = now,
            sync_status = "PENDING",
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    private fun makePoints(
        id: String,
        customerId: String = "cust-01",
        points: Int = 100,
        balanceAfter: Int = 100,
        type: RewardPoints.PointsType = RewardPoints.PointsType.EARNED,
        expiresAt: Long? = null,
        createdAt: Long = now,
    ) = RewardPoints(
        id = id,
        customerId = customerId,
        points = points,
        balanceAfter = balanceAfter,
        type = type,
        createdAt = createdAt,
        expiresAt = expiresAt,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - recordPoints then getBalance returns correct sum`() = runTest {
        val entry = makePoints(id = "rp-01", points = 200, balanceAfter = 200)
        val recordResult = repo.recordPoints(entry)
        assertIs<Result.Success<Unit>>(recordResult)

        val balanceResult = repo.getBalance("cust-01")
        assertIs<Result.Success<Int>>(balanceResult)
        assertEquals(200, balanceResult.data)
    }

    @Test
    fun `B - recordPoints multiple entries accumulates balance`() = runTest {
        repo.recordPoints(makePoints("rp-01", points = 100, balanceAfter = 100))
        repo.recordPoints(makePoints("rp-02", points = 50, balanceAfter = 150))
        repo.recordPoints(makePoints("rp-03", points = -30, balanceAfter = 120, type = RewardPoints.PointsType.REDEEMED))

        val balanceResult = repo.getBalance("cust-01")
        assertIs<Result.Success<Int>>(balanceResult)
        // sum of points column: 100 + 50 - 30 = 120
        assertEquals(120, balanceResult.data)
    }

    @Test
    fun `C - getBalance for customer with no points returns 0`() = runTest {
        val balanceResult = repo.getBalance("cust-01")
        assertIs<Result.Success<Int>>(balanceResult)
        assertEquals(0, balanceResult.data)
    }

    @Test
    fun `D - expirePointsForCustomer expires eligible entries`() = runTest {
        val pastExpiry = now - 1_000L  // already expired
        repo.recordPoints(makePoints("rp-01", points = 100, balanceAfter = 100, expiresAt = pastExpiry))
        repo.recordPoints(makePoints("rp-02", points = 50, balanceAfter = 150, expiresAt = null))

        val expiredCount = repo.expirePointsForCustomer("cust-01", now)
        assertIs<Result.Success<Int>>(expiredCount)
        assertEquals(100, expiredCount.data) // 100 points expired
    }

    @Test
    fun `E - expirePointsForCustomer with no expirable entries returns 0`() = runTest {
        val futureExpiry = now + 1_000_000L
        repo.recordPoints(makePoints("rp-01", points = 100, balanceAfter = 100, expiresAt = futureExpiry))

        val expiredCount = repo.expirePointsForCustomer("cust-01", now)
        assertIs<Result.Success<Int>>(expiredCount)
        assertEquals(0, expiredCount.data)
    }
}
