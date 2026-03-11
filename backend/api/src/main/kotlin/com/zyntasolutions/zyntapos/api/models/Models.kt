package com.zyntasolutions.zyntapos.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String
)

// ── POS Auth ───────────────────────────────────────────────────────────────────

/**
 * Aligned with KMP [AuthRequestDto]:
 *   email + password are mandatory; licenseKey and deviceId are optional.
 */
@Serializable
data class LoginRequest(
    @SerialName("email")       val email: String,
    @SerialName("password")    val password: String,
    @SerialName("license_key") val licenseKey: String? = null,
    @SerialName("device_id")   val deviceId: String? = null,
)

/**
 * Aligned with KMP [AuthResponseDto] — includes nested [UserResponseDto].
 */
@Serializable
data class LoginResponse(
    @SerialName("access_token")  val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in")    val expiresIn: Long,
    @SerialName("user")          val user: UserResponseDto,
)

/** Slim refresh response — aligned with KMP [AuthRefreshResponseDto]. */
@Serializable
data class RefreshResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in")   val expiresIn: Long,
)

@Serializable
data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

/**
 * Nested user object in login response — aligned with KMP [UserDto].
 */
@Serializable
data class UserResponseDto(
    @SerialName("id")         val id: String,
    @SerialName("name")       val name: String,
    @SerialName("email")      val email: String,
    @SerialName("role")       val role: String,
    @SerialName("store_id")   val storeId: String,
    @SerialName("is_active")  val isActive: Boolean,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
)

// ── Sync ───────────────────────────────────────────────────────────────────────

/**
 * Single sync operation — shared by push (inbound) and pull (outbound).
 *
 * Aligned with KMP [SyncOperationDto]:
 *   - [createdAt]  was `clientTimestamp` — epoch ms when the op was created on the client
 *   - [retryCount] was `vectorClock` in the old wire format — client retry counter
 *   - [serverSeq]  is @Transient — populated by the repo for cursor tracking, never sent over wire
 */
@Serializable
data class SyncOperation(
    @SerialName("id")           val id: String,
    @SerialName("entity_type")  val entityType: String,
    @SerialName("entity_id")    val entityId: String,
    @SerialName("operation")    val operation: String,  // CREATE | UPDATE | DELETE
    @SerialName("payload")      val payload: String,
    @SerialName("created_at")   val createdAt: Long,
    @SerialName("retry_count")  val retryCount: Int = 0,
    @Transient val serverSeq: Long = 0L,               // internal cursor — not serialized
)

@Serializable
data class PushRequest(
    @SerialName("device_id")  val deviceId: String,
    @SerialName("operations") val operations: List<SyncOperation>,
)

/**
 * Aligned with KMP [SyncResponseDto]:
 *   - [accepted]        IDs the server accepted
 *   - [rejected]        IDs permanently rejected (client marks FAILED)
 *   - [conflicts]       IDs that had conflicts (resolved server-side via LWW)
 *   - [deltaOperations] piggyback deltas on push ack (Phase 2; empty in Phase 1)
 *   - [serverTimestamp] epoch ms — client stores as LAST_SYNC_TS
 */
@Serializable
data class PushResponse(
    @SerialName("accepted")         val accepted: List<String>,
    @SerialName("rejected")         val rejected: List<String>,
    @SerialName("conflicts")        val conflicts: List<String>,
    @SerialName("delta_operations") val deltaOperations: List<SyncOperation> = emptyList(),
    @SerialName("server_timestamp") val serverTimestamp: Long,
)

/**
 * Aligned with KMP [SyncPullResponseDto].
 */
@Serializable
data class PullResponse(
    @SerialName("operations")          val operations: List<SyncOperation>,
    @SerialName("server_timestamp")    val serverTimestamp: Long,
    @SerialName("server_vector_clock") val serverVectorClock: Long,
    @SerialName("has_more")            val hasMore: Boolean,
)

// ── Products ───────────────────────────────────────────────────────────────────

/**
 * Aligned with KMP [ProductDto] — all 17 fields, snake_case JSON names.
 */
@Serializable
data class ProductDto(
    @SerialName("id")            val id: String,
    @SerialName("name")          val name: String,
    @SerialName("barcode")       val barcode: String? = null,
    @SerialName("sku")           val sku: String? = null,
    @SerialName("category_id")   val categoryId: String? = null,
    @SerialName("unit_id")       val unitId: String? = null,
    @SerialName("price")         val price: Double,
    @SerialName("cost_price")    val costPrice: Double = 0.0,
    @SerialName("tax_group_id")  val taxGroupId: String? = null,
    @SerialName("stock_qty")     val stockQty: Double = 0.0,
    @SerialName("min_stock_qty") val minStockQty: Double = 0.0,
    @SerialName("image_url")     val imageUrl: String? = null,
    @SerialName("description")   val description: String? = null,
    @SerialName("is_active")     val isActive: Boolean = true,
    @SerialName("created_at")    val createdAt: Long,
    @SerialName("updated_at")    val updatedAt: Long,
    @SerialName("sync_status")   val syncStatus: String = "SYNCED",
)

@Serializable
data class PagedResponse<T>(
    val data: List<T>,
    val page: Int,
    val size: Int,
    val total: Long,
    @SerialName("has_more") val hasMore: Boolean,
)
