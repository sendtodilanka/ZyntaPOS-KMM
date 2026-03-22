package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.domain.model.RegionalTaxOverride
import com.zyntasolutions.zyntapos.domain.model.TaxGroup
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeRegionalTaxOverrideRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetEffectiveTaxRateUseCaseTest {

    private lateinit var repo: FakeRegionalTaxOverrideRepository
    private lateinit var useCase: GetEffectiveTaxRateUseCase

    private val globalVat = TaxGroup(id = "tg-1", name = "VAT 18%", rate = 18.0)
    private val nowMs = 1_700_000_000_000L

    @BeforeTest
    fun setup() {
        repo = FakeRegionalTaxOverrideRepository()
        useCase = GetEffectiveTaxRateUseCase(repo)
    }

    @Test
    fun `no override returns global tax rate`() = runTest {
        val rate = useCase(globalVat, "store-1", nowMs)
        assertEquals(18.0, rate)
    }

    @Test
    fun `store override replaces global rate`() = runTest {
        repo.addOverride(
            RegionalTaxOverride(
                id = "o-1", taxGroupId = "tg-1", storeId = "store-1",
                effectiveRate = 15.0,
            )
        )
        val rate = useCase(globalVat, "store-1", nowMs)
        assertEquals(15.0, rate)
    }

    @Test
    fun `override for different store does not apply`() = runTest {
        repo.addOverride(
            RegionalTaxOverride(
                id = "o-1", taxGroupId = "tg-1", storeId = "store-2",
                effectiveRate = 10.0,
            )
        )
        val rate = useCase(globalVat, "store-1", nowMs)
        assertEquals(18.0, rate)
    }

    @Test
    fun `time-bounded override applies when within range`() = runTest {
        repo.addOverride(
            RegionalTaxOverride(
                id = "o-1", taxGroupId = "tg-1", storeId = "store-1",
                effectiveRate = 12.0,
                validFrom = nowMs - 1000,
                validTo = nowMs + 1000,
            )
        )
        val rate = useCase(globalVat, "store-1", nowMs)
        assertEquals(12.0, rate)
    }

    @Test
    fun `expired override does not apply`() = runTest {
        repo.addOverride(
            RegionalTaxOverride(
                id = "o-1", taxGroupId = "tg-1", storeId = "store-1",
                effectiveRate = 12.0,
                validFrom = nowMs - 10000,
                validTo = nowMs - 5000,
            )
        )
        val rate = useCase(globalVat, "store-1", nowMs)
        assertEquals(18.0, rate)
    }

    @Test
    fun `future override does not apply yet`() = runTest {
        repo.addOverride(
            RegionalTaxOverride(
                id = "o-1", taxGroupId = "tg-1", storeId = "store-1",
                effectiveRate = 20.0,
                validFrom = nowMs + 5000,
                validTo = nowMs + 10000,
            )
        )
        val rate = useCase(globalVat, "store-1", nowMs)
        assertEquals(18.0, rate)
    }

    @Test
    fun `inactive override is ignored`() = runTest {
        repo.addOverride(
            RegionalTaxOverride(
                id = "o-1", taxGroupId = "tg-1", storeId = "store-1",
                effectiveRate = 5.0,
                isActive = false,
            )
        )
        val rate = useCase(globalVat, "store-1", nowMs)
        assertEquals(18.0, rate)
    }

    @Test
    fun `zero rate override is valid`() = runTest {
        repo.addOverride(
            RegionalTaxOverride(
                id = "o-1", taxGroupId = "tg-1", storeId = "store-1",
                effectiveRate = 0.0,
            )
        )
        val rate = useCase(globalVat, "store-1", nowMs)
        assertEquals(0.0, rate)
    }
}
