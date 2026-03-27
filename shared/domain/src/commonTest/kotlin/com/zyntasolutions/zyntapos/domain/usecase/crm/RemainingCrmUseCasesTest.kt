package com.zyntasolutions.zyntapos.domain.usecase.crm

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.model.CustomerSegment
import com.zyntasolutions.zyntapos.domain.model.SegmentField
import com.zyntasolutions.zyntapos.domain.model.SegmentOperator
import com.zyntasolutions.zyntapos.domain.model.SegmentRule
import com.zyntasolutions.zyntapos.core.pagination.PageRequest
import com.zyntasolutions.zyntapos.core.pagination.PaginatedResult
import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeCustomerWalletRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeOrderRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildCustomer
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildCustomerWallet
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeCustomerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// Fixtures
// ─────────────────────────────────────────────────────────────────────────────

private fun buildSegment(
    id: String = "seg-01",
    rules: List<SegmentRule> = emptyList(),
) = CustomerSegment(id = id, name = "Test Segment", rules = rules)

private fun buildContext(
    customer: Customer = buildCustomer(),
    totalSpend: Double = 0.0,
    orderCount: Int = 0,
    loyaltyTier: String? = null,
    city: String? = null,
    tags: List<String> = emptyList(),
) = EvaluateCustomerSegmentUseCase.CustomerContext(
    customer = customer,
    totalSpend = totalSpend,
    orderCount = orderCount,
    loyaltyTier = loyaltyTier,
    city = city,
    tags = tags,
)

// ─────────────────────────────────────────────────────────────────────────────
// EvaluateCustomerSegmentUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class EvaluateCustomerSegmentUseCaseTest {

    private val useCase = EvaluateCustomerSegmentUseCase()

    @Test
    fun `emptyRules_alwaysReturnsTrue`() {
        val result = useCase(buildSegment(rules = emptyList()), buildContext())
        assertIs<Result.Success<*>>(result)
        assertTrue((result as Result.Success).data)
    }

    @Test
    fun `totalSpendGreaterThan_matchesHighSpender`() {
        val rule = SegmentRule(SegmentField.TOTAL_SPEND, SegmentOperator.GREATER_THAN, "500.0")
        val context = buildContext(totalSpend = 600.0)
        val result = useCase(buildSegment(rules = listOf(rule)), context)
        assertIs<Result.Success<*>>(result)
        assertTrue((result as Result.Success).data)
    }

    @Test
    fun `totalSpendGreaterThan_noMatchLowSpender`() {
        val rule = SegmentRule(SegmentField.TOTAL_SPEND, SegmentOperator.GREATER_THAN, "500.0")
        val context = buildContext(totalSpend = 400.0)
        val result = useCase(buildSegment(rules = listOf(rule)), context)
        assertIs<Result.Success<*>>(result)
        assertFalse((result as Result.Success).data)
    }

    @Test
    fun `orderCountEquals_matchesExactCount`() {
        val rule = SegmentRule(SegmentField.ORDER_COUNT, SegmentOperator.EQUALS, "5")
        val context = buildContext(orderCount = 5)
        val result = useCase(buildSegment(rules = listOf(rule)), context)
        assertIs<Result.Success<*>>(result)
        assertTrue((result as Result.Success).data)
    }

    @Test
    fun `loyaltyTierEquals_caseInsensitiveMatch`() {
        val rule = SegmentRule(SegmentField.LOYALTY_TIER, SegmentOperator.EQUALS, "gold")
        val context = buildContext(loyaltyTier = "GOLD")
        val result = useCase(buildSegment(rules = listOf(rule)), context)
        assertIs<Result.Success<*>>(result)
        assertTrue((result as Result.Success).data)
    }

    @Test
    fun `cityContains_matchesPartialCityName`() {
        val rule = SegmentRule(SegmentField.CITY, SegmentOperator.CONTAINS, "colombo")
        val context = buildContext(city = "Colombo North")
        val result = useCase(buildSegment(rules = listOf(rule)), context)
        assertIs<Result.Success<*>>(result)
        assertTrue((result as Result.Success).data)
    }

    @Test
    fun `tagEquals_matchesTag`() {
        val rule = SegmentRule(SegmentField.TAG, SegmentOperator.EQUALS, "vip")
        val context = buildContext(tags = listOf("vip", "premium"))
        val result = useCase(buildSegment(rules = listOf(rule)), context)
        assertIs<Result.Success<*>>(result)
        assertTrue((result as Result.Success).data)
    }

    @Test
    fun `multipleRules_allMustMatch`() {
        val rules = listOf(
            SegmentRule(SegmentField.TOTAL_SPEND, SegmentOperator.GREATER_THAN, "100.0"),
            SegmentRule(SegmentField.ORDER_COUNT, SegmentOperator.GREATER_THAN, "3"),
        )
        // Both match
        val matching = buildContext(totalSpend = 200.0, orderCount = 5)
        assertTrue(((useCase(buildSegment(rules = rules), matching)) as Result.Success).data)

        // Only first matches
        val partial = buildContext(totalSpend = 200.0, orderCount = 2)
        assertFalse(((useCase(buildSegment(rules = rules), partial)) as Result.Success).data)
    }

    @Test
    fun `filterMatching_returnsOnlyMatchingCustomers`() {
        val rule = SegmentRule(SegmentField.TOTAL_SPEND, SegmentOperator.GREATER_THAN, "100.0")
        val segment = buildSegment(rules = listOf(rule))

        val contexts = listOf(
            buildContext(customer = buildCustomer(id = "c1"), totalSpend = 200.0),
            buildContext(customer = buildCustomer(id = "c2"), totalSpend = 50.0),
            buildContext(customer = buildCustomer(id = "c3"), totalSpend = 150.0),
        )

        val result = useCase.filterMatching(segment, contexts)
        assertIs<Result.Success<*>>(result)
        val matching = (result as Result.Success).data
        assertEquals(2, matching.size)
        assert(matching.any { it.id == "c1" })
        assert(matching.any { it.id == "c3" })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WalletTopUpUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class WalletTopUpUseCaseTest {

    private fun makeUseCase(repo: FakeCustomerWalletRepository = FakeCustomerWalletRepository()) =
        WalletTopUpUseCase(repo)

    @Test
    fun `positiveAmount_creditsWallet`() = runTest {
        val repo = FakeCustomerWalletRepository()
        repo.wallets["cust-01"] = buildCustomerWallet(customerId = "cust-01", balance = 100.0)

        val result = makeUseCase(repo).invoke("cust-01", 50.0)
        assertIs<Result.Success<*>>(result)
        assertEquals(150.0, repo.wallets["cust-01"]?.balance ?: 0.0, 0.001)
    }

    @Test
    fun `zeroAmount_returnsValidationError`() = runTest {
        val result = makeUseCase().invoke("cust-01", 0.0)
        assertIs<Result.Error>(result)
        assertIs<ValidationException>((result as Result.Error).exception)
    }

    @Test
    fun `negativeAmount_returnsValidationError`() = runTest {
        val result = makeUseCase().invoke("cust-01", -10.0)
        assertIs<Result.Error>(result)
        assertIs<ValidationException>((result as Result.Error).exception)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GetCustomerPurchaseHistoryUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class GetCustomerPurchaseHistoryUseCaseTest {

    @Test
    fun `invoke_returnsOrdersFlowForCustomer`() = runTest {
        val repo = FakeOrderRepository()

        GetCustomerPurchaseHistoryUseCase(repo).invoke("cust-01").test {
            val list = awaitItem()
            // FakeOrderRepository returns empty list by default
            assertEquals(0, list.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ExportCustomerDataUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

/** OrderRepository fake that returns completing flows (unlike FakeOrderRepository which uses MutableStateFlow). */
private class CompletingOrderRepo : OrderRepository {
    override fun getAll(filters: Map<String, String>): Flow<List<Order>> = flowOf(emptyList())
    override fun getByDateRange(from: Instant, to: Instant): Flow<List<Order>> = flowOf(emptyList())
    override suspend fun create(order: Order): Result<Order> = Result.Success(order)
    override suspend fun getById(id: String): Result<Order> =
        Result.Error(com.zyntasolutions.zyntapos.core.result.DatabaseException("Not found"))
    override suspend fun update(order: Order): Result<Unit> = Result.Success(Unit)
    override suspend fun void(id: String, reason: String): Result<Unit> = Result.Success(Unit)
    override suspend fun holdOrder(cart: List<CartItem>): Result<String> = Result.Success("hold-01")
    override suspend fun retrieveHeld(holdId: String): Result<Order> =
        Result.Error(com.zyntasolutions.zyntapos.core.result.DatabaseException("Not found"))
    override suspend fun getPage(
        pageRequest: PageRequest,
        from: Instant?,
        to: Instant?,
        customerId: String?,
    ): PaginatedResult<Order> = PaginatedResult(emptyList(), 0L, false)
}

class ExportCustomerDataUseCaseTest {

    @Test
    fun `customerExists_returnsExportWithProfile`() = runTest {
        val customerRepo = FakeCustomerRepository()
        val customer = buildCustomer(id = "cust-01")
        customerRepo.customers.add(customer)

        val result = ExportCustomerDataUseCase(customerRepo, CompletingOrderRepo()).invoke("cust-01")
        assertIs<Result.Success<*>>(result)
        val export = (result as Result.Success).data
        assertEquals("cust-01", export.customer.id)
        assertTrue(export.exportedAt > 0L)
    }

    @Test
    fun `customerNotFound_returnsError`() = runTest {
        val customerRepo = FakeCustomerRepository()

        val result = ExportCustomerDataUseCase(customerRepo, CompletingOrderRepo()).invoke("unknown")
        assertIs<Result.Error>(result)
    }
}
