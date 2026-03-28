package com.zyntasolutions.zyntapos.domain.usecase.crm

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.model.CustomerSegment
import com.zyntasolutions.zyntapos.domain.model.SegmentField
import com.zyntasolutions.zyntapos.domain.model.SegmentOperator
import com.zyntasolutions.zyntapos.domain.model.SegmentRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * ZyntaPOS — EvaluateCustomerSegmentUseCase Unit Tests (commonTest)
 *
 * [EvaluateCustomerSegmentUseCase] is a pure computation class (no-arg constructor, no I/O).
 * Tests validate the AND-logic rule engine, all SegmentField types, and all operators.
 *
 * Coverage:
 *  A.  Empty rules segment always matches any customer
 *  B.  TOTAL_SPEND GREATER_THAN — customer qualifies
 *  C.  TOTAL_SPEND GREATER_THAN — customer does not qualify
 *  D.  TOTAL_SPEND EQUALS — exact match qualifies
 *  E.  TOTAL_SPEND NOT_EQUALS — different value qualifies
 *  F.  TOTAL_SPEND LESS_THAN — value below threshold qualifies
 *  G.  TOTAL_SPEND CONTAINS — always false for numeric field
 *  H.  ORDER_COUNT GREATER_THAN — qualifies
 *  I.  LAST_PURCHASE_DAYS_AGO — null returns false (never purchased)
 *  J.  LAST_PURCHASE_DAYS_AGO LESS_THAN — recent purchase qualifies
 *  K.  LOYALTY_POINTS GREATER_THAN — qualifies
 *  L.  LOYALTY_TIER EQUALS — case-insensitive match qualifies
 *  M.  LOYALTY_TIER NOT_EQUALS — different tier qualifies
 *  N.  CITY CONTAINS — partial city name match qualifies
 *  O.  CITY EQUALS — exact city match (case-insensitive) qualifies
 *  P.  TAG EQUALS — exact tag match qualifies
 *  Q.  TAG NOT_EQUALS — absent tag qualifies
 *  R.  TAG CONTAINS — partial tag match qualifies
 *  S.  TAG GREATER_THAN — always false
 *  T.  Multiple rules (AND) — all must pass
 *  U.  Multiple rules (AND) — one failing rule disqualifies
 *  V.  Unparseable numeric threshold returns false
 *  W.  filterMatching — empty rules returns all customers
 *  X.  filterMatching — only matching customers returned
 */
class EvaluateCustomerSegmentUseCaseEdgeCaseTest {

    private val useCase = EvaluateCustomerSegmentUseCase()

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun customer(
        id: String = "cust-1",
        loyaltyPoints: Int = 0,
    ) = Customer(id = id, name = "Test Customer", phone = "0777000001", loyaltyPoints = loyaltyPoints)

    private fun context(
        customer: Customer = customer(),
        totalSpend: Double = 0.0,
        orderCount: Int = 0,
        lastPurchaseDaysAgo: Int? = null,
        loyaltyTier: String? = null,
        city: String? = null,
        tags: List<String> = emptyList(),
    ) = EvaluateCustomerSegmentUseCase.CustomerContext(
        customer = customer,
        totalSpend = totalSpend,
        orderCount = orderCount,
        lastPurchaseDaysAgo = lastPurchaseDaysAgo,
        loyaltyTier = loyaltyTier,
        city = city,
        tags = tags,
    )

    private fun segment(vararg rules: SegmentRule) = CustomerSegment(
        id = "seg-1",
        name = "Test Segment",
        rules = rules.toList(),
    )

    private fun rule(field: SegmentField, operator: SegmentOperator, value: String) =
        SegmentRule(field = field, operator = operator, value = value)

    // ── Empty rules ───────────────────────────────────────────────────────────

    @Test
    fun `A - empty rules segment always matches`() {
        val result = useCase(segment(), context())
        assertIs<Result.Success<Boolean>>(result)
        assertEquals(true, result.data)
    }

    // ── TOTAL_SPEND ───────────────────────────────────────────────────────────

    @Test
    fun `B - TOTAL_SPEND GREATER_THAN qualifies when spend is above threshold`() {
        val r = rule(SegmentField.TOTAL_SPEND, SegmentOperator.GREATER_THAN, "100")
        val result = useCase(segment(r), context(totalSpend = 150.0))
        assertIs<Result.Success<Boolean>>(result)
        assertEquals(true, result.data)
    }

    @Test
    fun `C - TOTAL_SPEND GREATER_THAN fails when spend is below threshold`() {
        val r = rule(SegmentField.TOTAL_SPEND, SegmentOperator.GREATER_THAN, "100")
        val result = useCase(segment(r), context(totalSpend = 50.0))
        assertIs<Result.Success<Boolean>>(result)
        assertEquals(false, result.data)
    }

    @Test
    fun `D - TOTAL_SPEND EQUALS exact match qualifies`() {
        val r = rule(SegmentField.TOTAL_SPEND, SegmentOperator.EQUALS, "200.0")
        val result = useCase(segment(r), context(totalSpend = 200.0))
        assertIs<Result.Success<Boolean>>(result)
        assertEquals(true, result.data)
    }

    @Test
    fun `E - TOTAL_SPEND NOT_EQUALS different value qualifies`() {
        val r = rule(SegmentField.TOTAL_SPEND, SegmentOperator.NOT_EQUALS, "100.0")
        val result = useCase(segment(r), context(totalSpend = 200.0))
        assertIs<Result.Success<Boolean>>(result)
        assertEquals(true, result.data)
    }

    @Test
    fun `F - TOTAL_SPEND LESS_THAN qualifies when below threshold`() {
        val r = rule(SegmentField.TOTAL_SPEND, SegmentOperator.LESS_THAN, "500.0")
        val result = useCase(segment(r), context(totalSpend = 100.0))
        assertIs<Result.Success<Boolean>>(result)
        assertEquals(true, result.data)
    }

    @Test
    fun `G - TOTAL_SPEND CONTAINS always returns false (not meaningful for numeric)`() {
        val r = rule(SegmentField.TOTAL_SPEND, SegmentOperator.CONTAINS, "100")
        val result = useCase(segment(r), context(totalSpend = 100.0))
        assertIs<Result.Success<Boolean>>(result)
        assertEquals(false, result.data)
    }

    // ── ORDER_COUNT ───────────────────────────────────────────────────────────

    @Test
    fun `H - ORDER_COUNT GREATER_THAN qualifies`() {
        val r = rule(SegmentField.ORDER_COUNT, SegmentOperator.GREATER_THAN, "5")
        val result = useCase(segment(r), context(orderCount = 10))
        assertIs<Result.Success<Boolean>>(result)
        assertEquals(true, result.data)
    }

    // ── LAST_PURCHASE_DAYS_AGO ────────────────────────────────────────────────

    @Test
    fun `I - LAST_PURCHASE_DAYS_AGO null (never purchased) returns false`() {
        val r = rule(SegmentField.LAST_PURCHASE_DAYS_AGO, SegmentOperator.LESS_THAN, "30")
        val result = useCase(segment(r), context(lastPurchaseDaysAgo = null))
        assertIs<Result.Success<Boolean>>(result)
        assertEquals(false, result.data)
    }

    @Test
    fun `J - LAST_PURCHASE_DAYS_AGO LESS_THAN recent purchase qualifies`() {
        val r = rule(SegmentField.LAST_PURCHASE_DAYS_AGO, SegmentOperator.LESS_THAN, "30")
        val result = useCase(segment(r), context(lastPurchaseDaysAgo = 10))
        assertIs<Result.Success<Boolean>>(result)
        assertEquals(true, result.data)
    }

    // ── LOYALTY_POINTS ────────────────────────────────────────────────────────

    @Test
    fun `K - LOYALTY_POINTS GREATER_THAN qualifies`() {
        val r = rule(SegmentField.LOYALTY_POINTS, SegmentOperator.GREATER_THAN, "100")
        val cust = customer(loyaltyPoints = 500)
        val result = useCase(segment(r), context(customer = cust))
        assertIs<Result.Success<Boolean>>(result)
        assertEquals(true, result.data)
    }

    // ── LOYALTY_TIER ──────────────────────────────────────────────────────────

    @Test
    fun `L - LOYALTY_TIER EQUALS case-insensitive match qualifies`() {
        val r = rule(SegmentField.LOYALTY_TIER, SegmentOperator.EQUALS, "GOLD")
        val result = useCase(segment(r), context(loyaltyTier = "gold"))
        assertIs<Result.Success<Boolean>>(result)
        assertEquals(true, result.data)
    }

    @Test
    fun `M - LOYALTY_TIER NOT_EQUALS different tier qualifies`() {
        val r = rule(SegmentField.LOYALTY_TIER, SegmentOperator.NOT_EQUALS, "GOLD")
        val result = useCase(segment(r), context(loyaltyTier = "SILVER"))
        assertIs<Result.Success<Boolean>>(result)
        assertEquals(true, result.data)
    }

    // ── CITY ──────────────────────────────────────────────────────────────────

    @Test
    fun `N - CITY CONTAINS partial name match qualifies`() {
        val r = rule(SegmentField.CITY, SegmentOperator.CONTAINS, "colombo")
        val result = useCase(segment(r), context(city = "Colombo 07"))
        assertIs<Result.Success<Boolean>>(result)
        assertEquals(true, result.data)
    }

    @Test
    fun `O - CITY EQUALS exact match case-insensitive qualifies`() {
        val r = rule(SegmentField.CITY, SegmentOperator.EQUALS, "kandy")
        val result = useCase(segment(r), context(city = "KANDY"))
        assertIs<Result.Success<Boolean>>(result)
        assertEquals(true, result.data)
    }

    // ── TAG ───────────────────────────────────────────────────────────────────

    @Test
    fun `P - TAG EQUALS exact tag match qualifies`() {
        val r = rule(SegmentField.TAG, SegmentOperator.EQUALS, "vip")
        val result = useCase(segment(r), context(tags = listOf("VIP", "loyal")))
        assertIs<Result.Success<Boolean>>(result)
        assertEquals(true, result.data)
    }

    @Test
    fun `Q - TAG NOT_EQUALS absent tag qualifies`() {
        val r = rule(SegmentField.TAG, SegmentOperator.NOT_EQUALS, "churned")
        val result = useCase(segment(r), context(tags = listOf("vip")))
        assertIs<Result.Success<Boolean>>(result)
        assertEquals(true, result.data)
    }

    @Test
    fun `R - TAG CONTAINS partial tag match qualifies`() {
        val r = rule(SegmentField.TAG, SegmentOperator.CONTAINS, "premium")
        val result = useCase(segment(r), context(tags = listOf("premium-customer")))
        assertIs<Result.Success<Boolean>>(result)
        assertEquals(true, result.data)
    }

    @Test
    fun `S - TAG GREATER_THAN always returns false`() {
        val r = rule(SegmentField.TAG, SegmentOperator.GREATER_THAN, "vip")
        val result = useCase(segment(r), context(tags = listOf("vip")))
        assertIs<Result.Success<Boolean>>(result)
        assertEquals(false, result.data)
    }

    // ── AND logic ─────────────────────────────────────────────────────────────

    @Test
    fun `T - multiple rules all passing qualifies customer`() {
        val r1 = rule(SegmentField.TOTAL_SPEND, SegmentOperator.GREATER_THAN, "100")
        val r2 = rule(SegmentField.ORDER_COUNT, SegmentOperator.GREATER_THAN, "3")
        val result = useCase(segment(r1, r2), context(totalSpend = 500.0, orderCount = 10))
        assertIs<Result.Success<Boolean>>(result)
        assertEquals(true, result.data)
    }

    @Test
    fun `U - multiple rules with one failing disqualifies customer`() {
        val r1 = rule(SegmentField.TOTAL_SPEND, SegmentOperator.GREATER_THAN, "100")
        val r2 = rule(SegmentField.ORDER_COUNT, SegmentOperator.GREATER_THAN, "20")
        // orderCount = 5 fails the second rule
        val result = useCase(segment(r1, r2), context(totalSpend = 500.0, orderCount = 5))
        assertIs<Result.Success<Boolean>>(result)
        assertEquals(false, result.data)
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    fun `V - unparseable numeric threshold returns false`() {
        val r = rule(SegmentField.TOTAL_SPEND, SegmentOperator.GREATER_THAN, "not-a-number")
        val result = useCase(segment(r), context(totalSpend = 999.0))
        assertIs<Result.Success<Boolean>>(result)
        assertEquals(false, result.data)
    }

    // ── filterMatching ────────────────────────────────────────────────────────

    @Test
    fun `W - filterMatching empty rules returns all customers`() {
        val c1 = customer(id = "c1")
        val c2 = customer(id = "c2")
        val contexts = listOf(context(customer = c1), context(customer = c2))
        val result = useCase.filterMatching(segment(), contexts)
        assertIs<Result.Success<List<Customer>>>(result)
        assertEquals(2, result.data.size)
    }

    @Test
    fun `X - filterMatching returns only matching customers`() {
        val highSpender = customer(id = "high")
        val lowSpender = customer(id = "low")
        val r = rule(SegmentField.TOTAL_SPEND, SegmentOperator.GREATER_THAN, "500")
        val contexts = listOf(
            context(customer = highSpender, totalSpend = 1000.0),
            context(customer = lowSpender, totalSpend = 100.0),
        )
        val result = useCase.filterMatching(segment(r), contexts)
        assertIs<Result.Success<List<Customer>>>(result)
        assertEquals(1, result.data.size)
        assertEquals("high", result.data.first().id)
    }
}
