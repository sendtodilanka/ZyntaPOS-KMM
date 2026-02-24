package com.zyntasolutions.zyntapos.domain.usecase.coupons

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.Coupon
import com.zyntasolutions.zyntapos.domain.repository.CouponRepository
import kotlinx.datetime.Clock

/**
 * Validates that a coupon [code] can be applied to an order.
 *
 * Checks:
 * 1. Coupon exists
 * 2. Coupon is active
 * 3. Current time is within validity window
 * 4. Usage limit has not been exceeded
 * 5. Per-customer limit has not been exceeded (if [customerId] provided)
 * 6. Cart total meets the minimum purchase threshold
 *
 * Returns the [Coupon] on success; a [ValidationException] describing the
 * specific rejection reason on failure.
 */
class ValidateCouponUseCase(
    private val couponRepo: CouponRepository,
) {
    suspend operator fun invoke(
        code: String,
        cartTotal: Double,
        customerId: String? = null,
        nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    ): Result<Coupon> {
        val couponResult = couponRepo.getByCode(code)
        val coupon = when (couponResult) {
            is Result.Success -> couponResult.data
            is Result.Error -> return Result.Error(ValidationException("Coupon code '$code' not found"))
            is Result.Loading -> return Result.Loading
        }

        if (!coupon.isActive) {
            return Result.Error(ValidationException("Coupon '$code' is no longer active"))
        }
        if (nowMillis < coupon.validFrom || nowMillis > coupon.validTo) {
            return Result.Error(ValidationException("Coupon '$code' is not valid at this time"))
        }
        if (!coupon.hasAvailableRedemptions()) {
            return Result.Error(ValidationException("Coupon '$code' has reached its usage limit"))
        }
        if (cartTotal < coupon.minimumPurchase) {
            return Result.Error(
                ValidationException(
                    "Minimum purchase of ${coupon.minimumPurchase} required; cart total is $cartTotal"
                )
            )
        }
        if (customerId != null && coupon.perCustomerLimit != null) {
            val usageCountResult = couponRepo.getCustomerUsageCount(coupon.id, customerId)
            val usageCount = when (usageCountResult) {
                is Result.Success -> usageCountResult.data
                is Result.Error -> return usageCountResult
                is Result.Loading -> return Result.Loading
            }
            if (usageCount >= coupon.perCustomerLimit) {
                return Result.Error(
                    ValidationException("You have already used this coupon the maximum number of times")
                )
            }
        }
        return Result.Success(coupon)
    }
}
