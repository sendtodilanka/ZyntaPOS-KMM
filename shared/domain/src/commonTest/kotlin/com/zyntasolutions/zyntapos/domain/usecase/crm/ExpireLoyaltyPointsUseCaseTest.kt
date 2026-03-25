package com.zyntasolutions.zyntapos.domain.usecase.crm

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.RewardPoints
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeLoyaltyRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Unit tests for [ExpireLoyaltyPointsUseCase].
 *
 * Covers:
 * - Happy path: expired entries reduce balance and insert EXPIRED ledger entries
 * - Already-expired entries are not double-expired
 * - Customer with no expirable points returns 0
 * - DB failure propagated correctly
 */
class ExpireLoyaltyPointsUseCaseTest {

    private val pastTime = 1_000_000L
    private val futureTime = 9_999_999_999L
    private val now = 5_000_000L

    private fun makeRepo() = FakeLoyaltyRepository()
    private fun makeUseCase(repo: FakeLoyaltyRepository) = ExpireLoyaltyPointsUseCase(repo)

    private fun FakeLoyaltyRepository.addEarnedEntry(
        id: String,
        customerId: String,
        points: Int,
        expiresAt: Long?,
    ) {
        val balance = (pointsStore.getOrDefault(customerId, 0)) + points
        ledger.add(
            RewardPoints(
                id = id,
                customerId = customerId,
                points = points,
                balanceAfter = balance,
                type = RewardPoints.PointsType.EARNED,
                referenceType = "ORDER",
                referenceId = "order-$id",
                expiresAt = expiresAt,
                createdAt = pastTime - 100,
            )
        )
        pointsStore[customerId] = balance
    }

    @Test
    fun `expire single expirable entry - reduces balance and inserts EXPIRED ledger entry`() = runTest {
        val repo = makeRepo()
        repo.addEarnedEntry("entry-01", "cust-01", points = 100, expiresAt = pastTime)
        val useCase = makeUseCase(repo)

        val result = useCase("cust-01", nowEpochMillis = now)

        assertIs<Result.Success<Int>>(result)
        assertEquals(100, result.data, "Should return total expired points")
        assertEquals(0, repo.pointsStore["cust-01"], "Balance should be zero after expiry")
        val expiredEntry = repo.ledger.find { it.type == RewardPoints.PointsType.EXPIRED }
        assertIs<RewardPoints>(expiredEntry)
        assertEquals(-100, expiredEntry.points)
        assertEquals("entry-01", expiredEntry.referenceId)
    }

    @Test
    fun `expire multiple expirable entries - sums correctly`() = runTest {
        val repo = makeRepo()
        repo.addEarnedEntry("entry-01", "cust-01", points = 50, expiresAt = pastTime)
        repo.addEarnedEntry("entry-02", "cust-01", points = 75, expiresAt = pastTime)
        val useCase = makeUseCase(repo)

        val result = useCase("cust-01", nowEpochMillis = now)

        assertIs<Result.Success<Int>>(result)
        assertEquals(125, result.data)
        assertEquals(0, repo.pointsStore["cust-01"])
        assertEquals(4, repo.ledger.size, "2 EARNED + 2 EXPIRED entries expected")
    }

    @Test
    fun `future expiry entries are not expired`() = runTest {
        val repo = makeRepo()
        repo.addEarnedEntry("entry-01", "cust-01", points = 100, expiresAt = futureTime)
        val useCase = makeUseCase(repo)

        val result = useCase("cust-01", nowEpochMillis = now)

        assertIs<Result.Success<Int>>(result)
        assertEquals(0, result.data, "Future entries must not be expired")
        assertEquals(100, repo.pointsStore["cust-01"], "Balance unchanged")
        assertEquals(1, repo.ledger.size, "No new ledger entries should be inserted")
    }

    @Test
    fun `entry with null expiresAt is not expired`() = runTest {
        val repo = makeRepo()
        repo.addEarnedEntry("entry-01", "cust-01", points = 200, expiresAt = null)
        val useCase = makeUseCase(repo)

        val result = useCase("cust-01", nowEpochMillis = now)

        assertIs<Result.Success<Int>>(result)
        assertEquals(0, result.data)
        assertEquals(200, repo.pointsStore["cust-01"])
    }

    @Test
    fun `already expired entry is not double-expired`() = runTest {
        val repo = makeRepo()
        repo.addEarnedEntry("entry-01", "cust-01", points = 100, expiresAt = pastTime)
        // Simulate that an EXPIRED entry already exists for entry-01
        repo.ledger.add(
            RewardPoints(
                id = "expired-entry-01",
                customerId = "cust-01",
                points = -100,
                balanceAfter = 0,
                type = RewardPoints.PointsType.EXPIRED,
                referenceId = "entry-01",
                createdAt = pastTime + 1,
            )
        )
        repo.pointsStore["cust-01"] = 0
        val useCase = makeUseCase(repo)

        val result = useCase("cust-01", nowEpochMillis = now)

        assertIs<Result.Success<Int>>(result)
        assertEquals(0, result.data, "Should not double-expire")
        assertEquals(2, repo.ledger.size, "No new entries should be added")
    }

    @Test
    fun `customer with no points returns 0`() = runTest {
        val repo = makeRepo()
        val useCase = makeUseCase(repo)

        val result = useCase("cust-99", nowEpochMillis = now)

        assertIs<Result.Success<Int>>(result)
        assertEquals(0, result.data)
    }

    @Test
    fun `DB failure propagated as Result Error`() = runTest {
        val repo = makeRepo().also { it.shouldFail = true }
        val useCase = makeUseCase(repo)

        val result = useCase("cust-01", nowEpochMillis = now)

        assertIs<Result.Error>(result)
    }
}
