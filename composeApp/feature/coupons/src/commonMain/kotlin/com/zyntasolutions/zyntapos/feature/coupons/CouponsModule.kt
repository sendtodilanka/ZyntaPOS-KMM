package com.zyntasolutions.zyntapos.feature.coupons

import com.zyntasolutions.zyntapos.domain.usecase.coupons.CalculateCouponDiscountUseCase
import com.zyntasolutions.zyntapos.domain.usecase.coupons.SaveCouponUseCase
import com.zyntasolutions.zyntapos.domain.usecase.coupons.ValidateCouponUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Koin DI module for `:composeApp:feature:coupons`.
 *
 * ### Use Cases (factory — new instance per injection)
 * - [SaveCouponUseCase]               — validates and persists coupons
 * - [ValidateCouponUseCase]           — checks coupon applicability at POS
 * - [CalculateCouponDiscountUseCase]  — pure discount calculator
 *
 * ### Repository Dependencies (resolved from `:shared:data` DI graph)
 * - `CouponRepository` — coupon + promotion CRUD and redemption ledger
 */
val couponsModule = module {
    // ── Use Cases ─────────────────────────────────────────────────────────────
    factoryOf(::SaveCouponUseCase)
    factoryOf(::ValidateCouponUseCase)
    factory { CalculateCouponDiscountUseCase() }

    // ── ViewModel ─────────────────────────────────────────────────────────────
    viewModelOf(::CouponViewModel)
}
