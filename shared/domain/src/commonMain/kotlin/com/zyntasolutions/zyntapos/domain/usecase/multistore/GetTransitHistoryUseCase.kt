package com.zyntasolutions.zyntapos.domain.usecase.multistore

import com.zyntasolutions.zyntapos.domain.model.TransitEvent
import com.zyntasolutions.zyntapos.domain.repository.TransitTrackingRepository
import kotlinx.coroutines.flow.Flow

/**
 * Returns the reactive timeline of transit events for a stock transfer (C1.4).
 *
 * Events are ordered chronologically (oldest → newest), forming a visible
 * audit trail in [TransitTrackerScreen].
 *
 * @param transitRepo Transit tracking event data access.
 */
class GetTransitHistoryUseCase(
    private val transitRepo: TransitTrackingRepository,
) {
    operator fun invoke(transferId: String): Flow<List<TransitEvent>> =
        transitRepo.getEventsForTransfer(transferId)
}
