package com.zyntasolutions.zyntapos.domain.usecase.coupons

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeCouponRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildCoupon
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Unit tests for [ValidateCouponUseCase].
 *
 * Covers:
 * - Happy path: valid coupon returns the [Coupon] object
 * - Coupon not found: ValidationException
 * - Inactive coupon: ValidationException with "active" in message
 * - Expired (past validTo): ValidationException
 * - Not yet valid (future validFrom): ValidationException
 * - Usage limit reached: ValidationException
 * - Minimum purchase not met: ValidationException
 * - Per-customer limit reached: ValidationException
 * - DB error on getByCode: propagated
 */
class ValidateCouponUseCaseTest {

    private val now = Clock.System.now().toEpochMilliseconds()

    private fun makeUseCase(repo: FakeCouponRepository = FakeCouponRepository()) =
        ValidateCouponUseCase(repo) to repo

    @Test
    fun `valid coupon returns Success with the Coupon`() = runTest {
        val (useCase, repo) = makeUseCase()
        val coupon = buildCoupon(code = "VALID", isActive = true)
        repo.coupons.add(coupon)

        val result = useCase("VALID", cartTotal = 100.0, nowMillis = now)

        assertIs<Result.Success<*>>(result)
        assertEquals(coupon, result.data)
    }

    @Test
    fun `coupon not found - returns ValidationException`() = runTest {
        val (useCase, _) = makeUseCase()

        val result = useCase("GHOST", cartTotal = 100.0, nowMillis = now)

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
    }

    @Test
    fun `inactive coupon - returns ValidationException mentioning active`() = runTest {
        val (useCase, repo) = makeUseCase()
        repo.coupons.add(buildCoupon(code = "OFF", isActive = false))

        val result = useCase("OFF", cartTotal = 100.0, nowMillis = now)

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertContains(result.exception.message ?: "", "active", ignoreCase = true)
    }

    @Test
    fun `expired coupon - returns ValidationException`() = runTest {
        val (useCase, repo) = makeUseCase()
        val past = now - 172_800_000L  // 2 days ago
        val expired = buildCoupon(
            code = "EXP",
            validFrom = past - 86_400_000L,
            validTo = past,              // expired yesterday
        )
        repo.coupons.add(expired)

        val result = useCase("EXP", cartTotal = 100.0, nowMillis = now)

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
    }

    @Test
    fun `coupon not yet valid - returns ValidationException`() = runTest {
        val (useCase, repo) = makeUseCase()
        val future = now + 86_400_000L
        val notYet = buildCoupon(
            code = "FUTURE",
            validFrom = future,
            validTo = future + 86_400_000L,
        )
        repo.coupons.add(notYet)

        val result = useCase("FUTURE", cartTotal = 100.0, nowMillis = now)

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
    }

    @Test
    fun `usage limit reached - returns ValidationException`() = runTest {
        val (useCase, repo) = makeUseCase()
        val maxed = buildCoupon(code = "MAXED", usageLimit = 5, usageCount = 5)
        repo.coupons.add(maxed)

        val result = useCase("MAXED", cartTotal = 100.0, nowMillis = now)

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertContains(result.exception.message ?: "", "limit", ignoreCase = true)
    }

    @Test
    fun `cart total below minimum purchase - returns ValidationException`() = runTest {
        val (useCase, repo) = makeUseCase()
        repo.coupons.add(buildCoupon(code = "MIN500", minimumPurchase = 500.0))

        val result = useCase("MIN500", cartTotal = 200.0, nowMillis = now)

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertContains(result.exception.message ?: "", "500", ignoreCase = true)
    }

    @Test
    fun `per-customer limit reached - returns ValidationException`() = runTest {
        val (useCase, repo) = makeUseCase()
        val coupon = buildCoupon(code = "ONCE", perCustomerLimit = 1)
        repo.coupons.add(coupon)
        // Simulate 1 prior usage by this customer
        repo.usages.add(
            com.zyntasolutions.zyntapos.domain.model.CouponUsage(
                id = "u1", couponId = coupon.id, orderId = "ord-1",
                customerId = "cust-99", discountAmount = 20.0, usedAt = now - 1000L,
            )
        )

        val result = useCase("ONCE", cartTotal = 100.0, customerId = "cust-99", nowMillis = now)

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
    }

    @Test
    fun `DB error on getByCode - propagated as Result Error`() = runTest {
        val repo = FakeCouponRepository().also { it.shouldFail = true }
        val useCase = ValidateCouponUseCase(repo)

        val result = useCase("ANY", cartTotal = 100.0, nowMillis = now)

        assertIs<Result.Error>(result)
    }
}
