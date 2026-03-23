package com.zyntasolutions.zyntapos.domain.usecase.crm

import kotlin.test.Test
import kotlin.test.assertEquals

class CalculateLoyaltyDiscountUseCaseTest {

    private val useCase = CalculateLoyaltyDiscountUseCase()

    @Test
    fun `converts points to monetary discount at default rate`() {
        // 500 points / 100 = 5.0 currency units
        val discount = useCase(pointsToRedeem = 500, orderTotal = 100.0)
        assertEquals(5.0, discount)
    }

    @Test
    fun `caps discount at order total`() {
        // 10000 points / 100 = 100.0, but order is only 25.0
        val discount = useCase(pointsToRedeem = 10000, orderTotal = 25.0)
        assertEquals(25.0, discount)
    }

    @Test
    fun `zero points returns zero discount`() {
        val discount = useCase(pointsToRedeem = 0, orderTotal = 100.0)
        assertEquals(0.0, discount)
    }

    @Test
    fun `negative points returns zero discount`() {
        val discount = useCase(pointsToRedeem = -50, orderTotal = 100.0)
        assertEquals(0.0, discount)
    }

    @Test
    fun `zero order total returns zero discount`() {
        val discount = useCase(pointsToRedeem = 500, orderTotal = 0.0)
        assertEquals(0.0, discount)
    }

    @Test
    fun `custom conversion rate works`() {
        // 200 points / 50 = 4.0 currency units
        val discount = useCase(pointsToRedeem = 200, orderTotal = 100.0, pointsPerCurrencyUnit = 50)
        assertEquals(4.0, discount)
    }

    @Test
    fun `fractional conversion rounds correctly`() {
        // 150 points / 100 = 1.5 currency units
        val discount = useCase(pointsToRedeem = 150, orderTotal = 100.0)
        assertEquals(1.5, discount)
    }

    @Test
    fun `small points value`() {
        // 1 point / 100 = 0.01 currency units
        val discount = useCase(pointsToRedeem = 1, orderTotal = 100.0)
        assertEquals(0.01, discount)
    }
}
