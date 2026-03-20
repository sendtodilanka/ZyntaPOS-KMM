package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.Transit_tracking
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.model.TransitEvent
import com.zyntasolutions.zyntapos.domain.repository.TransitTrackingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TransitTrackingRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : TransitTrackingRepository {

    private val tq get() = db.transit_trackingQueries

    override fun getEventsForTransfer(transferId: String): Flow<List<TransitEvent>> =
        tq.getEventsForTransfer(transferId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override suspend fun addEvent(event: TransitEvent): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            tq.insertTransitEvent(
                id = event.id,
                transfer_id = event.transferId,
                event_type = event.eventType.name,
                location = event.location,
                note = event.note,
                recorded_by = event.recordedBy,
                recorded_at = event.recordedAt,
                sync_status = "PENDING",
            )
            syncEnqueuer.enqueue(SyncOperation.EntityType.TRANSIT_EVENT, event.id, SyncOperation.Operation.INSERT)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Insert transit event failed", cause = t)) },
        )
    }

    override suspend fun getInTransitCount(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            tq.getInTransitCount().executeAsOne().toInt()
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    private fun toDomain(row: Transit_tracking) = TransitEvent(
        id = row.id,
        transferId = row.transfer_id,
        eventType = runCatching { TransitEvent.EventType.valueOf(row.event_type) }
            .getOrDefault(TransitEvent.EventType.LOCATION_UPDATE),
        location = row.location,
        note = row.note,
        recordedBy = row.recorded_by,
        recordedAt = row.recorded_at,
    )
}
