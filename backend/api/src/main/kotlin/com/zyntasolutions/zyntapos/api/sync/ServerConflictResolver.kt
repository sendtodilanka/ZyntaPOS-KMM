package com.zyntasolutions.zyntapos.api.sync

import com.zyntasolutions.zyntapos.api.models.SyncOperation
import com.zyntasolutions.zyntapos.api.repository.ConflictLogEntry
import com.zyntasolutions.zyntapos.api.repository.ConflictLogRepository
import com.zyntasolutions.zyntapos.api.repository.SyncOperationSnapshot
import kotlinx.serialization.json.*

/**
 * Server-side Last-Write-Wins conflict resolver.
 *
 * Mirrors the client-side ConflictResolver algorithm so both ends converge
 * to the same winner given the same inputs.
 *
 * Resolution rules:
 * 1. Later [clientTimestamp] wins (LWW).
 * 2. If timestamps equal, lexicographically larger [deviceId] wins (stable tiebreak).
 * 3. For PRODUCT entities only: field-level merge fills null fields from loser.
 */
class ServerConflictResolver(
    private val conflictLogRepo: ConflictLogRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    enum class ResolutionStrategy { LWW_TIMESTAMP, DEVICE_ID_TIEBREAK, FIELD_MERGE }

    data class ConflictResolution(
        val conflictId: String,
        val winnerPayload: String,
        val incomingWins: Boolean,
        val strategy: ResolutionStrategy,
    )

    suspend fun resolve(
        storeId: String,
        incoming: SyncOperation,
        existing: SyncOperationSnapshot,
        incomingDeviceId: String,
    ): ConflictResolution {
        val incomingWins = when {
            incoming.clientTimestamp > existing.clientTimestamp -> true
            incoming.clientTimestamp < existing.clientTimestamp -> false
            else -> incomingDeviceId > existing.deviceId
        }

        val strategy = when {
            incoming.clientTimestamp != existing.clientTimestamp -> ResolutionStrategy.LWW_TIMESTAMP
            else -> ResolutionStrategy.DEVICE_ID_TIEBREAK
        }

        val (finalPayload, finalStrategy) = if (incoming.entityType == "PRODUCT" && !incomingWins) {
            val merged = mergeProductFields(
                winner = existing.payload,
                loser = incoming.payload
            )
            merged to ResolutionStrategy.FIELD_MERGE
        } else {
            val winner = if (incomingWins) incoming.payload else existing.payload
            winner to strategy
        }

        val logEntry = ConflictLogEntry(
            storeId          = storeId,
            entityType       = incoming.entityType,
            entityId         = incoming.entityId,
            localOpId        = incoming.id,
            serverOpId       = existing.opId,
            localDeviceId    = incomingDeviceId,
            serverDeviceId   = existing.deviceId,
            localTimestamp   = incoming.clientTimestamp,
            serverTs         = existing.clientTimestamp,
            resolution       = finalStrategy.name,
            localPayload     = incoming.payload,
            serverPayload    = existing.payload,
            mergedPayload    = if (finalStrategy == ResolutionStrategy.FIELD_MERGE) finalPayload else null,
        )

        val conflictId = conflictLogRepo.insert(logEntry)

        return ConflictResolution(
            conflictId    = conflictId,
            winnerPayload = finalPayload,
            incomingWins  = incomingWins,
            strategy      = finalStrategy,
        )
    }

    /**
     * For PRODUCT entities: merge non-null fields from loser into winner.
     * e.g., if winner has null imageUrl but loser has a value, keep the loser's.
     */
    internal fun mergeProductFields(winner: String, loser: String): String {
        return try {
            val winnerMap = json.parseToJsonElement(winner).jsonObject.toMutableMap()
            val loserMap  = json.parseToJsonElement(loser).jsonObject

            for ((key, value) in loserMap) {
                val current = winnerMap[key]
                if (current == null || current is JsonNull) {
                    winnerMap[key] = value
                }
            }

            json.encodeToString(JsonObject(winnerMap))
        } catch (_: Exception) {
            winner // Fallback: keep winner unchanged if JSON parsing fails
        }
    }
}
