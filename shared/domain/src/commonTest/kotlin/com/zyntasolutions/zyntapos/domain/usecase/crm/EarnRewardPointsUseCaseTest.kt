package com.zyntasolutions.zyntapos.domain.usecase.crm

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeLoyaltyRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildLoyaltyTier
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [EarnRewardPointsUseCase].
 *
 * Covers:
 * - Happy path: points earned with no tier (1x multiplier)
 * - Tier multiplier applied correctly
 * - Zero base points returns 0 immediately (no DB write)
 * - Negative base points rejected with [ValidationException]
 * - DB error propagated correctly
 */
class EarnRewardPointsUseCaseTest {

    private fun makeUseCase(repo: FakeLoyaltyRepository = FakeLoyaltyRepository()) =
        EarnRewardPointsUseCase(repo) to repo

    @Test
    fun `earn points with no tier - credits 1x base points`() = runTest {
        val (useCase, repo) = makeUseCase()
        repo.pointsStore["cust-01"] = 0

        val result = useCase("cust-01", basePoints = 100, orderId = "order-01")

        assertIs<Result.Success<Int>>(result)
        assertEquals(100, result.data)
        assertEquals(100, repo.pointsStore["cust-01"])
        assertEquals(1, repo.ledger.size)
        assertEquals(100, repo.ledger.first().points)
    }

    @Test
    fun `earn points with 2x tier multiplier - credits double base points`() = runTest {
        val (useCase, repo) = makeUseCase()
        repo.pointsStore["cust-01"] = 50
        val goldTier = buildLoyaltyTier(multiplier = 2.0)

        val result = useCase("cust-01", basePoints = 100, orderId = "order-01", tier = goldTier)

        assertIs<Result.Success<Int>>(result)
        assertEquals(250, result.data) // 50 existing + 200 earned
        assertEquals(250, repo.pointsStore["cust-01"])
    }

    @Test
    fun `earn zero base points - returns 0 without DB write`() = runTest {
        val (useCase, repo) = makeUseCase()
        repo.pointsStore["cust-01"] = 100

        val result = useCase("cust-01", basePoints = 0, orderId = "order-01")

        assertIs<Result.Success<Int>>(result)
        assertEquals(0, result.data)
        assertTrue(repo.ledger.isEmpty(), "No ledger entry should be written for 0 points")
    }

    @Test
    fun `negative base points - returns ValidationException`() = runTest {
        val (useCase, _) = makeUseCase()

        val result = useCase("cust-01", basePoints = -10, orderId = "order-01")

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
    }

    @Test
    fun `fractional multiplier rounds down correctly`() = runTest {
        val (useCase, repo) = makeUseCase()
        repo.pointsStore["cust-01"] = 0
        val tier = buildLoyaltyTier(multiplier = 1.5)

        val result = useCase("cust-01", basePoints = 7, orderId = "order-01", tier = tier)

        // 7 * 1.5 = 10.5 → floor to 10
        assertIs<Result.Success<Int>>(result)
        assertEquals(10, result.data)
    }

    @Test
    fun `DB error from getBalance - propagated as Result Error`() = runTest {
        val repo = FakeLoyaltyRepository().also { it.shouldFail = true }
        val useCase = EarnRewardPointsUseCase(repo)

        val result = useCase("cust-01", basePoints = 50, orderId = "order-01")

        assertIs<Result.Error>(result)
    }
}
