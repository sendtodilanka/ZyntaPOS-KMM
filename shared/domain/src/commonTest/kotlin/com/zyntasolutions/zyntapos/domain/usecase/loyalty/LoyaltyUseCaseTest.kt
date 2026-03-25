package com.zyntasolutions.zyntapos.domain.usecase.loyalty

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.LoyaltyTier
import com.zyntasolutions.zyntapos.domain.model.RewardPoints
import com.zyntasolutions.zyntapos.domain.repository.LoyaltyRepository
import com.zyntasolutions.zyntapos.domain.usecase.crm.EarnRewardPointsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.crm.RedeemRewardPointsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for the loyalty earn/redeem use cases (C4.2).
 *
 * Validates:
 * - Points earning with tier multiplier
 * - Points redemption with balance validation
 * - Edge cases (zero points, insufficient balance, negative inputs)
 */
class LoyaltyUseCaseTest {

    private val silverTier = LoyaltyTier(
        id = "tier-silver",
        name = "Silver",
        minPoints = 100,
        discountPercent = 2.0,
        pointsMultiplier = 1.5,
    )

    private val goldTier = LoyaltyTier(
        id = "tier-gold",
        name = "Gold",
        minPoints = 500,
        discountPercent = 5.0,
        pointsMultiplier = 2.0,
    )

    private val tiers = listOf(silverTier, goldTier)

    private fun createRepo(balance: Int = 0): FakeLoyaltyRepository {
        return FakeLoyaltyRepository(balance, tiers)
    }

    // ── EarnRewardPointsUseCase ───────────────────────────────

    @Test
    fun `earn base points without tier`() = runTest {
        val repo = createRepo(balance = 0)
        val useCase = EarnRewardPointsUseCase(repo)

        val result = useCase("cust-001", basePoints = 50, orderId = "order-001")

        assertIs<Result.Success<Int>>(result)
        assertEquals(50, result.data) // balance after earning
        assertEquals(1, repo.recordedEntries.size)
        assertEquals(50, repo.recordedEntries[0].points)
    }

    @Test
    fun `earn points with silver tier multiplier`() = runTest {
        val repo = createRepo(balance = 200)
        val useCase = EarnRewardPointsUseCase(repo)

        val result = useCase("cust-001", basePoints = 10, orderId = "order-002", tier = silverTier)

        assertIs<Result.Success<Int>>(result)
        // 10 * 1.5 = 15 points earned, balance was 200 + 15 = 215
        assertEquals(215, result.data)
    }

    @Test
    fun `earn points with gold tier multiplier`() = runTest {
        val repo = createRepo(balance = 600)
        val useCase = EarnRewardPointsUseCase(repo)

        val result = useCase("cust-001", basePoints = 10, orderId = "order-003", tier = goldTier)

        assertIs<Result.Success<Int>>(result)
        // 10 * 2.0 = 20 points, balance 600 + 20 = 620
        assertEquals(620, result.data)
    }

    @Test
    fun `earn rejects negative base points`() = runTest {
        val repo = createRepo()
        val useCase = EarnRewardPointsUseCase(repo)

        val result = useCase("cust-001", basePoints = -5, orderId = "order-001")
        assertIs<Result.Error>(result)
    }

    @Test
    fun `earn zero points returns 0`() = runTest {
        val repo = createRepo()
        val useCase = EarnRewardPointsUseCase(repo)

        val result = useCase("cust-001", basePoints = 0, orderId = "order-001")
        assertIs<Result.Success<Int>>(result)
        assertEquals(0, result.data)
    }

    // ── RedeemRewardPointsUseCase ─────────────────────────────

    @Test
    fun `redeem points returns new balance`() = runTest {
        val repo = createRepo(balance = 500)
        val useCase = RedeemRewardPointsUseCase(repo)

        val result = useCase("cust-001", pointsToRedeem = 100, orderId = "order-001")

        assertIs<Result.Success<Int>>(result)
        assertEquals(400, result.data) // 500 - 100 = 400
        assertEquals(1, repo.recordedEntries.size)
        assertEquals(-100, repo.recordedEntries[0].points)
        assertEquals(RewardPoints.PointsType.REDEEMED, repo.recordedEntries[0].type)
    }

    @Test
    fun `redeem rejects insufficient balance`() = runTest {
        val repo = createRepo(balance = 50)
        val useCase = RedeemRewardPointsUseCase(repo)

        val result = useCase("cust-001", pointsToRedeem = 100, orderId = "order-001")
        assertIs<Result.Error>(result)
    }

    @Test
    fun `redeem rejects zero points`() = runTest {
        val repo = createRepo(balance = 100)
        val useCase = RedeemRewardPointsUseCase(repo)

        val result = useCase("cust-001", pointsToRedeem = 0, orderId = "order-001")
        assertIs<Result.Error>(result)
    }

    @Test
    fun `redeem exact balance succeeds`() = runTest {
        val repo = createRepo(balance = 100)
        val useCase = RedeemRewardPointsUseCase(repo)

        val result = useCase("cust-001", pointsToRedeem = 100, orderId = "order-001")
        assertIs<Result.Success<Int>>(result)
        assertEquals(0, result.data) // balance now 0
    }

    @Test
    fun `redeem records correct balance after`() = runTest {
        val repo = createRepo(balance = 500)
        val useCase = RedeemRewardPointsUseCase(repo)

        useCase("cust-001", pointsToRedeem = 200, orderId = "order-001")

        assertEquals(300, repo.recordedEntries[0].balanceAfter)
    }

    @Test
    fun `tier lookup returns correct tier for points`() = runTest {
        val repo = createRepo()

        // 0 points = no tier
        val noTier = repo.getTierForPoints(0)
        assertIs<Result.Success<LoyaltyTier?>>(noTier)
        assertEquals(null, noTier.data)

        // 150 points = Silver
        val silver = repo.getTierForPoints(150)
        assertIs<Result.Success<LoyaltyTier?>>(silver)
        assertEquals("Silver", silver.data?.name)

        // 600 points = Gold
        val gold = repo.getTierForPoints(600)
        assertIs<Result.Success<LoyaltyTier?>>(gold)
        assertEquals("Gold", gold.data?.name)
    }
}

private class FakeLoyaltyRepository(
    private var balance: Int = 0,
    private val tiers: List<LoyaltyTier> = emptyList(),
) : LoyaltyRepository {

    val recordedEntries = mutableListOf<RewardPoints>()

    override fun getPointsHistory(customerId: String): Flow<List<RewardPoints>> =
        flowOf(recordedEntries)

    override suspend fun getBalance(customerId: String): Result<Int> =
        Result.Success(balance)

    override suspend fun recordPoints(entry: RewardPoints): Result<Unit> {
        recordedEntries.add(entry)
        balance = entry.balanceAfter
        return Result.Success(Unit)
    }

    override fun getAllTiers(): Flow<List<LoyaltyTier>> = flowOf(tiers)

    override suspend fun getTierForPoints(points: Int): Result<LoyaltyTier?> {
        val tier = tiers.filter { it.minPoints <= points }
            .maxByOrNull { it.minPoints }
        return Result.Success(tier)
    }

    override suspend fun saveTier(tier: LoyaltyTier): Result<Unit> = Result.Success(Unit)
    override suspend fun deleteTier(id: String): Result<Unit> = Result.Success(Unit)
    override suspend fun expirePointsForCustomer(customerId: String, nowEpochMillis: Long): Result<Int> = Result.Success(0)
}
