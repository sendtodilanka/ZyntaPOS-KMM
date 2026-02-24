package com.zyntasolutions.zyntapos.domain.usecase.coupons

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.Coupon
import com.zyntasolutions.zyntapos.domain.repository.CouponRepository

/**
 * Inserts or updates a [Coupon] after validating its fields.
 */
class SaveCouponUseCase(
    private val repo: CouponRepository,
) {
    suspend operator fun invoke(coupon: Coupon, isNew: Boolean): Result<Unit> {
        if (coupon.code.isBlank()) {
            return Result.Error(ValidationException("Coupon code cannot be blank"))
        }
        if (coupon.name.isBlank()) {
            return Result.Error(ValidationException("Coupon name cannot be blank"))
        }
        if (coupon.discountValue <= 0.0) {
            return Result.Error(ValidationException("Discount value must be positive"))
        }
        if (coupon.validFrom >= coupon.validTo) {
            return Result.Error(ValidationException("Valid-from date must be before valid-to date"))
        }
        return if (isNew) repo.insert(coupon) else repo.update(coupon)
    }
}
