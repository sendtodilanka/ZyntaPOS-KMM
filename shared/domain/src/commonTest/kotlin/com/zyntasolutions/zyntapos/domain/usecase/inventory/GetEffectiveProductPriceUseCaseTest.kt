package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeMasterProductRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakePricingRuleRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeStoreProductOverrideRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildMasterProduct
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildPricingRule
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildProduct
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildStoreOverride
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for [GetEffectiveProductPriceUseCase] — 4-level price resolution:
 * store override → pricing rule → master product → product.price.
 */
class GetEffectiveProductPriceUseCaseTest {

    private val masterRepo = FakeMasterProductRepository()
    private val overrideRepo = FakeStoreProductOverrideRepository()
    private val pricingRepo = FakePricingRuleRepository()
    private val useCase = GetEffectiveProductPriceUseCase(masterRepo, overrideRepo, pricingRepo)

    private val storeId = "store-1"
    private val now = 1_000_000L

    // ── Level 4: Fallback to product.price ────────────────────────────────

    @Test
    fun `product without masterProductId returns product price`() = runTest {
        val product = buildProduct(price = 42.0, masterProductId = null)
        assertEquals(42.0, useCase(product, storeId, now))
    }

    // ── Level 3: Master product base price ────────────────────────────────

    @Test
    fun `product with masterProductId but no overrides returns master base price`() = runTest {
        masterRepo.masterProducts.add(buildMasterProduct(id = "mp-1", basePrice = 200.0))
        val product = buildProduct(price = 150.0, masterProductId = "mp-1")

        assertEquals(200.0, useCase(product, storeId, now))
    }

    // ── Level 2: Pricing rule ─────────────────────────────────────────────

    @Test
    fun `active pricing rule for product overrides master price`() = runTest {
        masterRepo.masterProducts.add(buildMasterProduct(id = "mp-1", basePrice = 200.0))
        pricingRepo.rules.add(buildPricingRule(productId = "p-1", price = 180.0, isActive = true))
        val product = buildProduct(id = "p-1", price = 150.0, masterProductId = "mp-1")

        assertEquals(180.0, useCase(product, storeId, now))
    }

    @Test
    fun `store-specific pricing rule takes precedence over global rule`() = runTest {
        pricingRepo.rules.add(buildPricingRule(id = "pr-global", productId = "p-1", storeId = null, price = 90.0))
        pricingRepo.rules.add(buildPricingRule(id = "pr-store", productId = "p-1", storeId = storeId, price = 85.0))
        val product = buildProduct(id = "p-1", price = 100.0)

        assertEquals(85.0, useCase(product, storeId, now))
    }

    @Test
    fun `higher priority rule wins over lower priority rule`() = runTest {
        pricingRepo.rules.add(buildPricingRule(id = "pr-low", productId = "p-1", storeId = storeId, price = 90.0, priority = 1))
        pricingRepo.rules.add(buildPricingRule(id = "pr-high", productId = "p-1", storeId = storeId, price = 75.0, priority = 10))
        val product = buildProduct(id = "p-1", price = 100.0)

        assertEquals(75.0, useCase(product, storeId, now))
    }

    @Test
    fun `expired pricing rule is ignored`() = runTest {
        pricingRepo.rules.add(
            buildPricingRule(
                productId = "p-1",
                price = 50.0,
                validFrom = 100L,
                validTo = 999L, // Expired before 'now' (1_000_000)
            )
        )
        val product = buildProduct(id = "p-1", price = 100.0)

        assertEquals(100.0, useCase(product, storeId, now))
    }

    @Test
    fun `future pricing rule is ignored`() = runTest {
        pricingRepo.rules.add(
            buildPricingRule(
                productId = "p-1",
                price = 50.0,
                validFrom = 2_000_000L, // Starts after 'now'
            )
        )
        val product = buildProduct(id = "p-1", price = 100.0)

        assertEquals(100.0, useCase(product, storeId, now))
    }

    @Test
    fun `inactive pricing rule is ignored`() = runTest {
        pricingRepo.rules.add(buildPricingRule(productId = "p-1", price = 50.0, isActive = false))
        val product = buildProduct(id = "p-1", price = 100.0)

        assertEquals(100.0, useCase(product, storeId, now))
    }

    // ── Level 1: Store product override (highest priority) ────────────────

    @Test
    fun `store override localPrice takes highest precedence`() = runTest {
        masterRepo.masterProducts.add(buildMasterProduct(id = "mp-1", basePrice = 200.0))
        pricingRepo.rules.add(buildPricingRule(productId = "p-1", price = 180.0))
        overrideRepo.overrides.add(buildStoreOverride(masterProductId = "mp-1", storeId = storeId, localPrice = 160.0))
        val product = buildProduct(id = "p-1", price = 150.0, masterProductId = "mp-1")

        assertEquals(160.0, useCase(product, storeId, now))
    }

    @Test
    fun `store override with null localPrice falls through to pricing rule`() = runTest {
        masterRepo.masterProducts.add(buildMasterProduct(id = "mp-1", basePrice = 200.0))
        pricingRepo.rules.add(buildPricingRule(productId = "p-1", price = 180.0))
        overrideRepo.overrides.add(buildStoreOverride(masterProductId = "mp-1", storeId = storeId, localPrice = null))
        val product = buildProduct(id = "p-1", price = 150.0, masterProductId = "mp-1")

        assertEquals(180.0, useCase(product, storeId, now))
    }

    // ── Edge cases ─────────────────────────────────────────────────────────

    @Test
    fun `pricing rule for different product is ignored`() = runTest {
        pricingRepo.rules.add(buildPricingRule(productId = "other-product", price = 50.0))
        val product = buildProduct(id = "p-1", price = 100.0)

        assertEquals(100.0, useCase(product, storeId, now))
    }

    @Test
    fun `global rule applies when no store-specific rule exists`() = runTest {
        pricingRepo.rules.add(buildPricingRule(productId = "p-1", storeId = null, price = 88.0))
        val product = buildProduct(id = "p-1", price = 100.0)

        assertEquals(88.0, useCase(product, storeId, now))
    }
}
