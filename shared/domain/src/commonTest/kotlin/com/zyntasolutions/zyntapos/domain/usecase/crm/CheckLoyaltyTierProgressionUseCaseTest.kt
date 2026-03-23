package com.zyntasolutions.zyntapos.domain.usecase.crm

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeLoyaltyRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildLoyaltyTier
import kotlinx.coroutines.test.runTest
import com.zyntasolutions.zyntapos.domain.model.LoyaltyTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class CheckLoyaltyTierProgressionUseCaseTest {

    private fun makeUseCase(repo: FakeLoyaltyRepository = FakeLoyaltyRepository()) =
        CheckLoyaltyTierProgressionUseCase(repo) to repo

    @Test
    fun `returns null when no tiers defined`() = runTest {
        val (useCase, repo) = makeUseCase()
        repo.pointsStore["cust-01"] = 500

        val result = useCase("cust-01")
        assertIs<Result.Success<LoyaltyTier?>>(result)
        assertNull(result.data)
    }

    @Test
    fun `returns null when balance below all tiers`() = runTest {
        val (useCase, repo) = makeUseCase()
        repo.pointsStore["cust-01"] = 50
        repo.tiers.add(buildLoyaltyTier(id = "silver", name = "Silver", minPoints = 100))
        repo.tiers.add(buildLoyaltyTier(id = "gold", name = "Gold", minPoints = 500))

        val result = useCase("cust-01")
        assertIs<Result.Success<LoyaltyTier?>>(result)
        assertNull(result.data)
    }

    @Test
    fun `returns correct tier for balance`() = runTest {
        val (useCase, repo) = makeUseCase()
        repo.pointsStore["cust-01"] = 350
        repo.tiers.add(buildLoyaltyTier(id = "bronze", name = "Bronze", minPoints = 0))
        repo.tiers.add(buildLoyaltyTier(id = "silver", name = "Silver", minPoints = 100))
        repo.tiers.add(buildLoyaltyTier(id = "gold", name = "Gold", minPoints = 500))

        val result = useCase("cust-01")
        assertIs<Result.Success<LoyaltyTier?>>(result)
        assertEquals("Silver", result.data?.name)
    }

    @Test
    fun `returns highest matching tier`() = runTest {
        val (useCase, repo) = makeUseCase()
        repo.pointsStore["cust-01"] = 1000
        repo.tiers.add(buildLoyaltyTier(id = "bronze", name = "Bronze", minPoints = 0))
        repo.tiers.add(buildLoyaltyTier(id = "silver", name = "Silver", minPoints = 100))
        repo.tiers.add(buildLoyaltyTier(id = "gold", name = "Gold", minPoints = 500))

        val result = useCase("cust-01")
        assertIs<Result.Success<LoyaltyTier?>>(result)
        assertEquals("Gold", result.data?.name)
    }

    @Test
    fun `propagates DB error`() = runTest {
        val (useCase, repo) = makeUseCase()
        repo.shouldFail = true

        val result = useCase("cust-01")
        assertIs<Result.Error>(result)
    }
}
