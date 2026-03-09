package com.zyntasolutions.zyntapos.sync.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Typed WebSocket message envelopes for the sync real-time channel (TODO-007g).
 *
 * Messages flow server → client unless otherwise noted.
 */
@Serializable
sealed class WsMessage {
    abstract val type: String
}

/**
 * Sent to the connecting device as the first message on successful handshake.
 */
@Serializable
@SerialName("ack")
data class WsAck(
    override val type: String = "ack",
    val storeId: String,
    val deviceId: String,
    val connectedAt: Long,
) : WsMessage()

/**
 * Sent to other store devices when a push batch lands and the delta is small (≤10 ops).
 * Client may apply [operations] directly without a separate pull.
 */
@Serializable
@SerialName("delta")
data class WsDelta(
    override val type: String = "delta",
    val storeId: String,
    val operationCount: Int,
    val latestSeq: Long,
) : WsMessage()

/**
 * Sent to other store devices when a push batch lands and the delta is large (>10 ops).
 * Client should trigger a pull request using [latestSeq] as the cursor.
 */
@Serializable
@SerialName("notify")
data class WsNotify(
    override val type: String = "notify",
    val storeId: String,
    val message: String = "sync_available",
    val latestSeq: Long,
) : WsMessage()

/**
 * Sent by admin-triggered force-sync. Client should pull immediately.
 */
@Serializable
@SerialName("force_sync")
data class WsForceSync(
    override val type: String = "force_sync",
    val storeId: String,
) : WsMessage()

/** Keep-alive — client sends, server replies with [WsPong]. */
@Serializable
@SerialName("ping")
data class WsPing(override val type: String = "ping") : WsMessage()

/** Keep-alive reply. */
@Serializable
@SerialName("pong")
data class WsPong(override val type: String = "pong") : WsMessage()

/** Redis pub/sub notification payload published by the API service. */
@Serializable
data class SyncNotification(
    val storeId: String,
    val senderDeviceId: String,
    val operationCount: Int,
    val latestSeq: Long,
)
