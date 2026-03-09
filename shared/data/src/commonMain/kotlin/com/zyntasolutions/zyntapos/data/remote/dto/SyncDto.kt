package com.zyntasolutions.zyntapos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CustomerDto(
    @SerialName("id")             val id: String,
    @SerialName("name")           val name: String,
    @SerialName("phone")          val phone: String? = null,
    @SerialName("email")          val email: String? = null,
    @SerialName("address")        val address: String? = null,
    @SerialName("group_id")       val groupId: String? = null,
    @SerialName("loyalty_points") val loyaltyPoints: Int = 0,
    @SerialName("notes")          val notes: String? = null,
    @SerialName("is_active")      val isActive: Boolean = true,
    @SerialName("updated_at")     val updatedAt: Long,
)

// ─────────────────────────────────────────────────────────────────────────────
// Sync DTOs
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Represents a single outbox operation to be pushed to the server.
 *
 * Mirrors the `pending_operations` schema row but shaped for wire transport.
 */
@Serializable
data class SyncOperationDto(
    @SerialName("id")           val id: String,
    @SerialName("entity_type")  val entityType: String,
    @SerialName("entity_id")    val entityId: String,
    @SerialName("operation")    val operation: String,      // CREATE | UPDATE | DELETE
    @SerialName("payload")      val payload: String,        // JSON-serialized entity snapshot
    @SerialName("created_at")   val createdAt: Long,
    @SerialName("retry_count")  val retryCount: Int = 0,
)

/**
 * Server response after processing a batch of pushed operations.
 *
 * - [accepted] — IDs successfully applied server-side (mark SYNCED locally)
 * - [rejected] — IDs the server rejected permanently (mark FAILED locally)
 * - [conflicts] — IDs where the server returned a canonical version (apply [deltaOperations])
 * - [deltaOperations] — Server-side changes the client should apply (pull deltas bundled with push ack)
 * - [serverTimestamp] — Unix epoch ms; store as [SecurePreferences.Keys.LAST_SYNC_TS]
 */
@Serializable
data class SyncResponseDto(
    @SerialName("accepted")           val accepted: List<String> = emptyList(),
    @SerialName("rejected")           val rejected: List<String> = emptyList(),
    @SerialName("conflicts")          val conflicts: List<String> = emptyList(),
    @SerialName("delta_operations")   val deltaOperations: List<SyncOperationDto> = emptyList(),
    @SerialName("server_timestamp")   val serverTimestamp: Long,
)

/**
 * Response for `GET /api/v1/sync/pull`.
 *
 * [cursor] (aka [serverVectorClock]) is the new cursor value to pass as `?since=` on the
 * next pull. Replaces the old timestamp-based approach; use this value instead of
 * [serverTimestamp] for the next pull request to guarantee monotonic ordering.
 *
 * [hasMore] — when true the client must issue another pull with `?since=[cursor]`
 * until [hasMore] is false to drain the full delta.
 */
@Serializable
data class SyncPullResponseDto(
    @SerialName("operations")           val operations: List<SyncOperationDto> = emptyList(),
    @SerialName("server_timestamp")     val serverTimestamp: Long,
    @SerialName("server_vector_clock")  val cursor: Long = serverTimestamp,
    @SerialName("has_more")             val hasMore: Boolean = false,
)
