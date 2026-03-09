package com.zyntasolutions.zyntapos.api.sync

import com.zyntasolutions.zyntapos.api.models.PushRequest
import com.zyntasolutions.zyntapos.api.models.PushResponse
import com.zyntasolutions.zyntapos.api.models.SyncOperation
import com.zyntasolutions.zyntapos.api.repository.DeadLetterRepository
import com.zyntasolutions.zyntapos.api.repository.SyncOperationRepository
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Orchestrates the full push processing pipeline (TODO-007g Step 8).
 *
 * Pipeline per batch:
 * 1. Validate all operations (schema, entity types, payload)
 * 2. Deduplicate already-processed operation IDs (idempotent retry safety)
 * 3. For each new op: detect conflict → resolve (LWW) → persist
 * 4. Apply to normalized entity tables via [EntityApplier]
 * 5. Publish change notification to Redis for WebSocket fan-out
 * 6. Return [PushResponse] with accepted/rejected/conflict counts
 */
class SyncProcessor(
    private val syncOpRepo: SyncOperationRepository,
    private val conflictResolver: ServerConflictResolver,
    private val validator: SyncValidator,
    private val entityApplier: EntityApplier,
    private val deadLetterRepo: DeadLetterRepository,
    private val metrics: SyncMetrics,
    private val redisConnection: StatefulRedisConnection<String, String>?,
) {
    private val logger = LoggerFactory.getLogger(SyncProcessor::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    @kotlinx.serialization.Serializable
    data class SyncNotification(
        val storeId: String,
        val senderDeviceId: String,
        val operationCount: Int,
        val latestSeq: Long,
    )

    suspend fun processPush(storeId: String, request: PushRequest): PushResponse {
        val t0 = System.currentTimeMillis()

        // Step 1: Validate
        val (validOps, invalidOps) = validator.validateBatch(request.operations)
        val rejected = mutableListOf<String>()

        // Send invalid ops to dead letter queue
        for (inv in invalidOps) {
            rejected.add(inv.id)
            val op = request.operations.find { it.id == inv.id }
            if (op != null) {
                try {
                    deadLetterRepo.insert(storeId, request.deviceId, op, inv.reason)
                    metrics.deadLettersTotal.incrementAndGet()
                } catch (e: Exception) {
                    logger.warn("Failed to save dead letter for op ${inv.id}: ${e.message}")
                }
            }
        }

        // Step 2: Dedup — skip operations the server already processed
        val existingIds = syncOpRepo.findExistingIds(validOps.map { it.id })
        val alreadyAccepted = validOps.filter { it.id in existingIds }.map { it.id }
        val newOps = validOps.filter { it.id !in existingIds }

        // Step 3: Process each new operation
        val accepted   = mutableListOf<String>()
        val conflicts  = mutableListOf<String>()

        for (op in newOps) {
            try {
                val latestSnapshot = syncOpRepo.findLatestForEntity(storeId, op.entityType, op.entityId)

                if (latestSnapshot != null && latestSnapshot.deviceId != request.deviceId) {
                    val existingIsNewer = latestSnapshot.clientTimestamp > op.clientTimestamp ||
                        (latestSnapshot.clientTimestamp == op.clientTimestamp &&
                         latestSnapshot.deviceId > request.deviceId)

                    if (existingIsNewer) {
                        // Conflict: existing server state wins or needs field merge
                        val resolution = conflictResolver.resolve(storeId, op, latestSnapshot, request.deviceId)
                        syncOpRepo.insertWithConflict(
                            storeId         = storeId,
                            deviceId        = request.deviceId,
                            op              = op,
                            conflictId      = resolution.conflictId,
                            resolvedPayload = resolution.winnerPayload,
                        )
                        conflicts.add(op.id)
                        metrics.conflictsTotal.incrementAndGet()
                    } else {
                        // Incoming wins over existing — accept normally
                        syncOpRepo.insert(storeId, request.deviceId, op)
                        accepted.add(op.id)
                    }
                } else {
                    // No conflict — accept directly
                    syncOpRepo.insert(storeId, request.deviceId, op)
                    accepted.add(op.id)
                }
            } catch (e: Exception) {
                logger.warn("Failed to process op ${op.id}: ${e.message}")
                rejected.add(op.id)
                metrics.opsRejected.incrementAndGet()
            }
        }

        // Step 4: Apply accepted ops to normalized entity tables
        val opsToApply = newOps.filter { it.id in accepted }
        if (opsToApply.isNotEmpty()) {
            try {
                entityApplier.applyBatch(storeId, opsToApply)
            } catch (e: Exception) {
                logger.warn("EntityApplier batch failed for store $storeId: ${e.message}")
            }
        }

        // Step 5: Publish to Redis for WebSocket fan-out
        val latestSeq = syncOpRepo.getLatestSeq(storeId)
        publishToRedis(storeId, request.deviceId, accepted.size + conflicts.size, latestSeq)

        // Update metrics
        metrics.opsAccepted.addAndGet(accepted.size.toLong())
        metrics.opsRejected.addAndGet(rejected.size.toLong())
        metrics.recordPushDuration(System.currentTimeMillis() - t0)

        logger.info(
            "Push: store=$storeId device=${request.deviceId} " +
            "accepted=${accepted.size} conflicts=${conflicts.size} rejected=${rejected.size}"
        )

        return PushResponse(
            accepted          = accepted.size + alreadyAccepted.size,
            rejected          = rejected.size,
            conflicts         = conflicts,
            serverVectorClock = latestSeq,
        )
    }

    private fun publishToRedis(storeId: String, senderDeviceId: String, opCount: Int, latestSeq: Long) {
        try {
            val notification = SyncNotification(
                storeId         = storeId,
                senderDeviceId  = senderDeviceId,
                operationCount  = opCount,
                latestSeq       = latestSeq,
            )
            redisConnection?.async()?.publish(
                "sync:delta:$storeId",
                json.encodeToString(notification)
            )
        } catch (e: Exception) {
            logger.warn("Failed to publish sync notification to Redis: ${e.message}")
        }
    }
}
