package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeMasterProductRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakePricingRuleRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeProductRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeRegionalTaxOverrideRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeStoreProductOverrideRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeTaxGroupRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildCartItem
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildPricingRule
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildProduct
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildTaxGroup
import com.zyntasolutions.zyntapos.domain.usecase.inventory.GetEffectiveProductPriceUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [AddItemToCartUseCase] — stock validation and cart management.
 */
class AddItemToCartUseCaseTest {

    private fun makeUseCase(repo: FakeProductRepository = FakeProductRepository()) =
        AddItemToCartUseCase(repo)

    // ─── Happy Paths ──────────────────────────────────────────────────────────

    @Test
    fun `add new item to empty cart - item appears in cart`() = runTest {
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1", price = 9.99, stockQty = 50.0))
        }
        val result = makeUseCase(repo)(currentCart = emptyList(), productId = "p1")
        assertIs<Result.Success<*>>(result)
        val cart = (result as Result.Success).data
        assertEquals(1, cart.size)
        assertEquals("p1", cart[0].productId)
        assertEquals(9.99, cart[0].unitPrice, 0.001)
        assertEquals(1.0, cart[0].quantity, 0.001)
    }

    @Test
    fun `add existing item - quantities are summed`() = runTest {
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1", stockQty = 50.0))
        }
        val existingCart = listOf(buildCartItem(productId = "p1", quantity = 3.0))
        val result = makeUseCase(repo)(currentCart = existingCart, productId = "p1", quantity = 2.0)
        assertIs<Result.Success<*>>(result)
        val cart = (result as Result.Success).data
        assertEquals(1, cart.size)
        assertEquals(5.0, cart[0].quantity, 0.001)
    }

    @Test
    fun `add multiple different items - cart contains all items`() = runTest {
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1", stockQty = 50.0))
            it.addProduct(buildProduct(id = "p2", stockQty = 50.0))
        }
        var cart = (makeUseCase(repo)(emptyList(), "p1") as Result.Success).data
        cart = (makeUseCase(repo)(cart, "p2") as Result.Success).data
        assertEquals(2, cart.size)
    }

    @Test
    fun `add item with quantity 5 when stock is exactly 5 - succeeds`() = runTest {
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1", stockQty = 5.0))
        }
        val result = makeUseCase(repo)(currentCart = emptyList(), productId = "p1", quantity = 5.0)
        assertIs<Result.Success<*>>(result)
    }

    // ─── Stock Limit Scenarios ────────────────────────────────────────────────

    @Test
    fun `add quantity exceeding stock - returns OUT_OF_STOCK error`() = runTest {
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1", stockQty = 3.0))
        }
        val result = makeUseCase(repo)(currentCart = emptyList(), productId = "p1", quantity = 4.0)
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("OUT_OF_STOCK", ex.rule)
    }

    @Test
    fun `combined cart + new quantity exceeds stock - returns OUT_OF_STOCK error`() = runTest {
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1", stockQty = 5.0))
        }
        // 3 already in cart + 3 new = 6 > 5 stock
        val existingCart = listOf(buildCartItem(productId = "p1", quantity = 3.0))
        val result = makeUseCase(repo)(currentCart = existingCart, productId = "p1", quantity = 3.0)
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("OUT_OF_STOCK", ex.rule)
    }

    @Test
    fun `zero stock product - returns OUT_OF_STOCK error`() = runTest {
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1", stockQty = 0.0))
        }
        val result = makeUseCase(repo)(currentCart = emptyList(), productId = "p1", quantity = 1.0)
        assertIs<Result.Error>(result)
        assertEquals("OUT_OF_STOCK", ((result as Result.Error).exception as ValidationException).rule)
    }

    // ─── Validation Errors ────────────────────────────────────────────────────

    @Test
    fun `zero quantity - returns MIN_VALUE error`() = runTest {
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1"))
        }
        val result = makeUseCase(repo)(currentCart = emptyList(), productId = "p1", quantity = 0.0)
        assertIs<Result.Error>(result)
        assertEquals("MIN_VALUE", ((result as Result.Error).exception as ValidationException).rule)
    }

    @Test
    fun `negative quantity - returns MIN_VALUE error`() = runTest {
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1"))
        }
        val result = makeUseCase(repo)(currentCart = emptyList(), productId = "p1", quantity = -1.0)
        assertIs<Result.Error>(result)
        assertEquals("MIN_VALUE", ((result as Result.Error).exception as ValidationException).rule)
    }

    @Test
    fun `inactive product - returns PRODUCT_INACTIVE error`() = runTest {
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1", isActive = false, stockQty = 50.0))
        }
        val result = makeUseCase(repo)(currentCart = emptyList(), productId = "p1")
        assertIs<Result.Error>(result)
        assertEquals("PRODUCT_INACTIVE", ((result as Result.Error).exception as ValidationException).rule)
    }

    @Test
    fun `product not found - returns database error`() = runTest {
        val result = makeUseCase()(currentCart = emptyList(), productId = "nonexistent")
        assertIs<Result.Error>(result)
    }

    @Test
    fun `existing cart items are preserved when adding new product`() = runTest {
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1", stockQty = 50.0))
            it.addProduct(buildProduct(id = "p2", stockQty = 50.0))
        }
        val existingCart = listOf(buildCartItem(productId = "p2", quantity = 1.0))
        val result = makeUseCase(repo)(currentCart = existingCart, productId = "p1") as Result.Success
        assertEquals(2, result.data.size)
        assertTrue(result.data.any { it.productId == "p2" })
    }

    // ─── C2.1 Effective Pricing (storeId) ──────────────────────────────────────

    @Test
    fun `pricing rule overrides product price when storeId is provided`() = runTest {
        val pricingRuleRepo = FakePricingRuleRepository().also {
            it.rules.add(buildPricingRule(productId = "p1", storeId = "store-1", price = 7.50))
        }
        val effectivePriceUseCase = GetEffectiveProductPriceUseCase(
            masterProductRepository = FakeMasterProductRepository(),
            storeProductOverrideRepository = FakeStoreProductOverrideRepository(),
            pricingRuleRepository = pricingRuleRepo,
        )
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1", price = 9.99, stockQty = 50.0))
        }
        val useCase = AddItemToCartUseCase(repo, effectivePriceUseCase)
        val result = useCase(currentCart = emptyList(), productId = "p1", storeId = "store-1")
        assertIs<Result.Success<*>>(result)
        val cart = (result as Result.Success).data
        assertEquals(7.50, cart[0].unitPrice, 0.001)
    }

    @Test
    fun `falls back to product price when storeId is blank`() = runTest {
        val pricingRuleRepo = FakePricingRuleRepository().also {
            it.rules.add(buildPricingRule(productId = "p1", storeId = "store-1", price = 7.50))
        }
        val effectivePriceUseCase = GetEffectiveProductPriceUseCase(
            masterProductRepository = FakeMasterProductRepository(),
            storeProductOverrideRepository = FakeStoreProductOverrideRepository(),
            pricingRuleRepository = pricingRuleRepo,
        )
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1", price = 9.99, stockQty = 50.0))
        }
        val useCase = AddItemToCartUseCase(repo, effectivePriceUseCase)
        val result = useCase(currentCart = emptyList(), productId = "p1", storeId = "")
        assertIs<Result.Success<*>>(result)
        val cart = (result as Result.Success).data
        assertEquals(9.99, cart[0].unitPrice, 0.001)
    }

    @Test
    fun `falls back to product price when getEffectivePrice is null`() = runTest {
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1", price = 9.99, stockQty = 50.0))
        }
        val useCase = AddItemToCartUseCase(repo, getEffectivePrice = null)
        val result = useCase(currentCart = emptyList(), productId = "p1", storeId = "store-1")
        assertIs<Result.Success<*>>(result)
        val cart = (result as Result.Success).data
        assertEquals(9.99, cart[0].unitPrice, 0.001)
    }

    // ─── C2.3 Tax Rate Resolution ─────────────────────────────────────────────

    @Test
    fun `tax rate resolved from TaxGroup when taxGroupRepository is provided`() = runTest {
        val taxGroupRepo = FakeTaxGroupRepository().also {
            it.addTaxGroup(buildTaxGroup(id = "tax-01", rate = 15.0, isInclusive = false))
        }
        val taxRateUseCase = GetEffectiveTaxRateUseCase(
            regionalTaxOverrideRepository = FakeRegionalTaxOverrideRepository(),
        )
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1", stockQty = 50.0, taxGroupId = "tax-01"))
        }
        val useCase = AddItemToCartUseCase(
            productRepository = repo,
            taxGroupRepository = taxGroupRepo,
            getEffectiveTaxRate = taxRateUseCase,
        )
        val result = useCase(currentCart = emptyList(), productId = "p1", storeId = "store-1")
        assertIs<Result.Success<*>>(result)
        val cart = (result as Result.Success).data
        assertEquals(15.0, cart[0].taxRate, 0.001)
        assertEquals(false, cart[0].isTaxInclusive)
    }

    @Test
    fun `inclusive tax group sets isTaxInclusive true on cart item`() = runTest {
        val taxGroupRepo = FakeTaxGroupRepository().also {
            it.addTaxGroup(buildTaxGroup(id = "tax-vat", rate = 10.0, isInclusive = true))
        }
        val taxRateUseCase = GetEffectiveTaxRateUseCase(
            regionalTaxOverrideRepository = FakeRegionalTaxOverrideRepository(),
        )
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1", stockQty = 50.0, taxGroupId = "tax-vat"))
        }
        val useCase = AddItemToCartUseCase(
            productRepository = repo,
            taxGroupRepository = taxGroupRepo,
            getEffectiveTaxRate = taxRateUseCase,
        )
        val result = useCase(currentCart = emptyList(), productId = "p1", storeId = "store-1")
        assertIs<Result.Success<*>>(result)
        val cart = (result as Result.Success).data
        assertEquals(10.0, cart[0].taxRate, 0.001)
        assertEquals(true, cart[0].isTaxInclusive)
    }

    @Test
    fun `product with no taxGroupId gets zero tax rate`() = runTest {
        val taxGroupRepo = FakeTaxGroupRepository()
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1", stockQty = 50.0, taxGroupId = null))
        }
        val useCase = AddItemToCartUseCase(
            productRepository = repo,
            taxGroupRepository = taxGroupRepo,
        )
        val result = useCase(currentCart = emptyList(), productId = "p1")
        assertIs<Result.Success<*>>(result)
        val cart = (result as Result.Success).data
        assertEquals(0.0, cart[0].taxRate, 0.001)
        assertEquals(false, cart[0].isTaxInclusive)
    }

    @Test
    fun `inactive tax group results in zero tax rate`() = runTest {
        val taxGroupRepo = FakeTaxGroupRepository().also {
            it.addTaxGroup(buildTaxGroup(id = "tax-01", rate = 15.0, isActive = false))
        }
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1", stockQty = 50.0, taxGroupId = "tax-01"))
        }
        val useCase = AddItemToCartUseCase(
            productRepository = repo,
            taxGroupRepository = taxGroupRepo,
        )
        val result = useCase(currentCart = emptyList(), productId = "p1")
        assertIs<Result.Success<*>>(result)
        val cart = (result as Result.Success).data
        assertEquals(0.0, cart[0].taxRate, 0.001)
    }

    @Test
    fun `falls back to zero tax when taxGroupRepository is null`() = runTest {
        val repo = FakeProductRepository().also {
            it.addProduct(buildProduct(id = "p1", stockQty = 50.0, taxGroupId = "tax-01"))
        }
        val useCase = AddItemToCartUseCase(productRepository = repo)
        val result = useCase(currentCart = emptyList(), productId = "p1")
        assertIs<Result.Success<*>>(result)
        val cart = (result as Result.Success).data
        assertEquals(0.0, cart[0].taxRate, 0.001)
    }
}
