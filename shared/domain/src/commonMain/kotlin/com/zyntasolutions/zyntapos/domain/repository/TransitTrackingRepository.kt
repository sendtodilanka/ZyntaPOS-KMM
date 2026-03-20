package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.TransitEvent
import kotlinx.coroutines.flow.Flow

/**
 * Contract for in-transit stock tracking events (C1.4).
 *
 * Each [TransitEvent] records a single checkpoint or location update during
 * the IN_TRANSIT phase of the IST multi-step workflow (C1.3).
 */
interface TransitTrackingRepository {

    /**
     * Emits the ordered list of tracking events for [transferId] (oldest first).
     * Returns an empty list if no events have been recorded yet.
     */
    fun getEventsForTransfer(transferId: String): Flow<List<TransitEvent>>

    /**
     * Persists a new tracking event and enqueues a sync operation.
     * The event is immediately visible via [getEventsForTransfer].
     */
    suspend fun addEvent(event: TransitEvent): Result<Unit>

    /**
     * Returns the total count of transfers currently in IN_TRANSIT status.
     * Used to populate the dashboard "In-Transit Items" count widget.
     */
    suspend fun getInTransitCount(): Result<Int>
}
