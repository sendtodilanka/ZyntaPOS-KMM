package com.zyntasolutions.zyntapos.domain.usecase.coupons

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeCouponRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildCoupon
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [SaveCouponUseCase].
 *
 * Covers:
 * - Happy path insert: coupon persisted correctly
 * - Happy path update: existing coupon updated
 * - Blank code: [ValidationException] without DB write
 * - Blank name: [ValidationException] without DB write
 * - Zero discount value: [ValidationException]
 * - validFrom >= validTo: [ValidationException]
 * - DB error: propagated as [Result.Error]
 */
class SaveCouponUseCaseTest {

    private fun makeUseCase(repo: FakeCouponRepository = FakeCouponRepository()) =
        SaveCouponUseCase(repo) to repo

    @Test
    fun `insert new coupon with valid data - persisted and returns Success`() = runTest {
        val (useCase, repo) = makeUseCase()
        val coupon = buildCoupon(code = "NEW10", name = "New Customer Discount")

        val result = useCase(coupon, isNew = true)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.coupons.size)
        assertEquals("NEW10", repo.coupons.first().code)
    }

    @Test
    fun `update existing coupon - replaces old record`() = runTest {
        val (useCase, repo) = makeUseCase()
        val original = buildCoupon(id = "c-01", code = "ORIG", name = "Original")
        repo.coupons.add(original)

        val updated = original.copy(name = "Updated", discountValue = 30.0)
        val result = useCase(updated, isNew = false)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.coupons.size)
        assertEquals("Updated", repo.coupons.first().name)
        assertEquals(30.0, repo.coupons.first().discountValue)
    }

    @Test
    fun `blank code - returns ValidationException without DB write`() = runTest {
        val (useCase, repo) = makeUseCase()
        val coupon = buildCoupon(code = "   ")

        val result = useCase(coupon, isNew = true)

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertTrue(repo.coupons.isEmpty(), "No write should occur for blank code")
    }

    @Test
    fun `blank name - returns ValidationException without DB write`() = runTest {
        val (useCase, repo) = makeUseCase()
        val coupon = buildCoupon(name = "   ")

        val result = useCase(coupon, isNew = true)

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertTrue(repo.coupons.isEmpty())
    }

    @Test
    fun `zero discount value - returns ValidationException`() = runTest {
        val (useCase, repo) = makeUseCase()
        // Coupon model requires discountValue >= 0, so we use a tiny positive value
        // to pass construction but test that <= 0.0 check triggers (exactly 0.0)
        // Actually 0.0 passes the require(>= 0.0) check but fails the use case guard (> 0.0)
        val coupon = buildCoupon(discountValue = 0.0)

        val result = useCase(coupon, isNew = true)

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertTrue(repo.coupons.isEmpty())
    }

    @Test
    fun `validFrom equals validTo - domain model rejects at construction`() {
        // Coupon.init requires validFrom < validTo. Equal values violate the invariant.
        val now = 1_700_000_000_000L
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            buildCoupon(validFrom = now, validTo = now)
        }
    }

    @Test
    fun `DB error - propagated as Result Error`() = runTest {
        val repo = FakeCouponRepository().also { it.shouldFail = true }
        val useCase = SaveCouponUseCase(repo)
        val coupon = buildCoupon(name = "Good Coupon", discountValue = 10.0)

        val result = useCase(coupon, isNew = true)

        assertIs<Result.Error>(result)
    }
}
