package com.zyntasolutions.zyntapos.domain.usecase.multistore

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.repository.TransitTrackingRepository

/**
 * Returns the count of stock transfers currently IN_TRANSIT (C1.4).
 *
 * Used to populate the "In-Transit Items" dashboard count widget.
 *
 * @param transitRepo Transit tracking event data access.
 */
class GetInTransitCountUseCase(
    private val transitRepo: TransitTrackingRepository,
) {
    suspend operator fun invoke(): Result<Int> = transitRepo.getInTransitCount()
}
