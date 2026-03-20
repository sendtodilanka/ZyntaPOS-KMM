package com.zyntasolutions.zyntapos.domain.usecase.multistore

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.TransitEvent
import com.zyntasolutions.zyntapos.domain.repository.TransitTrackingRepository
import kotlin.time.Clock

/**
 * Logs a system-generated transit event at IST workflow transition points (C1.4).
 *
 * Called internally by the ViewModel immediately after:
 * - `dispatchTransfer()` succeeds → logs [TransitEvent.EventType.DISPATCHED]
 * - `receiveTransfer()` succeeds  → logs [TransitEvent.EventType.RECEIVED]
 *
 * Unlike [AddTransitEventUseCase], this use case accepts auto-generated event types
 * and does NOT enforce the IN_TRANSIT status constraint, since the status may have
 * already transitioned by the time this is called.
 *
 * @param transitRepo Transit tracking event data access.
 */
class LogWorkflowTransitEventUseCase(
    private val transitRepo: TransitTrackingRepository,
) {
    suspend operator fun invoke(
        transferId: String,
        eventType: TransitEvent.EventType,
        recordedBy: String,
        note: String? = null,
    ): Result<Unit> {
        val event = TransitEvent(
            id = IdGenerator.newId(),
            transferId = transferId,
            eventType = eventType,
            location = null,
            note = note,
            recordedBy = recordedBy,
            recordedAt = Clock.System.now().toEpochMilliseconds(),
        )
        return transitRepo.addEvent(event)
    }
}
