package com.zyntasolutions.zyntapos.domain.usecase.crm

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeLoyaltyRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [RedeemRewardPointsUseCase].
 *
 * Covers:
 * - Happy path: points deducted from balance
 * - Insufficient points: [ValidationException] with descriptive message
 * - Zero or negative points: [ValidationException] before any DB access
 * - Exact balance redemption: allowed (balance goes to 0)
 * - DB error from getBalance: propagated as [Result.Error]
 */
class RedeemRewardPointsUseCaseTest {

    private fun makeUseCase(repo: FakeLoyaltyRepository = FakeLoyaltyRepository()) =
        RedeemRewardPointsUseCase(repo) to repo

    @Test
    fun `redeem points with sufficient balance - returns new balance`() = runTest {
        val (useCase, repo) = makeUseCase()
        repo.pointsStore["cust-01"] = 500

        val result = useCase("cust-01", pointsToRedeem = 200, orderId = "order-01")

        assertIs<Result.Success<Int>>(result)
        assertEquals(300, result.data)
        assertEquals(300, repo.pointsStore["cust-01"])
        assertEquals(1, repo.ledger.size)
        assertEquals(-200, repo.ledger.first().points)
    }

    @Test
    fun `redeem exactly the full balance - balance goes to zero`() = runTest {
        val (useCase, repo) = makeUseCase()
        repo.pointsStore["cust-01"] = 100

        val result = useCase("cust-01", pointsToRedeem = 100, orderId = "order-01")

        assertIs<Result.Success<Int>>(result)
        assertEquals(0, result.data)
        assertEquals(0, repo.pointsStore["cust-01"])
    }

    @Test
    fun `redeem more points than balance - returns ValidationException`() = runTest {
        val (useCase, _) = makeUseCase()
        // Repo has 0 points for new customer

        val result = useCase("cust-01", pointsToRedeem = 50, orderId = "order-01")

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertTrue(result.exception.message?.contains("Insufficient") == true)
    }

    @Test
    fun `redeem zero points - returns ValidationException without DB access`() = runTest {
        val (useCase, repo) = makeUseCase()
        repo.pointsStore["cust-01"] = 1000

        val result = useCase("cust-01", pointsToRedeem = 0, orderId = "order-01")

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertTrue(repo.ledger.isEmpty(), "No ledger entry for 0 redemption")
    }

    @Test
    fun `redeem negative points - returns ValidationException`() = runTest {
        val (useCase, repo) = makeUseCase()
        repo.pointsStore["cust-01"] = 1000

        val result = useCase("cust-01", pointsToRedeem = -5, orderId = "order-01")

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
    }

    @Test
    fun `DB error from getBalance - propagated as Result Error`() = runTest {
        val repo = FakeLoyaltyRepository().also { it.shouldFail = true }
        val useCase = RedeemRewardPointsUseCase(repo)

        val result = useCase("cust-01", pointsToRedeem = 10, orderId = "order-01")

        assertIs<Result.Error>(result)
    }
}
