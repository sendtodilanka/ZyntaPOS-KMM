package com.zyntasolutions.zyntapos.domain.usecase.coupons

import com.zyntasolutions.zyntapos.domain.model.Promotion
import com.zyntasolutions.zyntapos.domain.repository.CouponRepository
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock

/**
 * C2.4: Retrieves active promotions applicable to a specific store.
 *
 * Returns both global promotions (storeIds is empty) and promotions
 * that target the given [storeId]. Results are ordered by priority descending.
 *
 * Per ADR-009: Promotion management is a store-level operation.
 * This use case runs in the KMM app, not the admin panel.
 */
class GetStorePromotionsUseCase(
    private val couponRepository: CouponRepository,
) {
    operator fun invoke(
        storeId: String,
        nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
    ): Flow<List<Promotion>> =
        couponRepository.getActivePromotionsForStore(nowEpochMillis, storeId)
}
