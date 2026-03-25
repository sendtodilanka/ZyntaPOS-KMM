package com.zyntasolutions.zyntapos.sync.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Typed WebSocket message envelopes for the sync real-time channel (TODO-007g).
 *
 * Messages flow server → client unless otherwise noted.
 */
@Serializable
sealed class WsMessage

/**
 * Sent to the connecting device as the first message on successful handshake.
 */
@Serializable
@SerialName("ack")
data class WsAck(
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
    val storeId: String,
    val operationCount: Int,
    val latestSeq: Long,
    val entityTypes: List<String> = emptyList(),
) : WsMessage()

/**
 * Sent to other store devices when a push batch lands and the delta is large (>10 ops).
 * Client should trigger a pull request using [latestSeq] as the cursor.
 */
@Serializable
@SerialName("notify")
data class WsNotify(
    val storeId: String,
    val message: String = "sync_available",
    val latestSeq: Long,
    val entityTypes: List<String> = emptyList(),
) : WsMessage()

/**
 * Sent by admin-triggered force-sync. Client should pull immediately.
 */
@Serializable
@SerialName("force_sync")
data class WsForceSync(
    val storeId: String,
) : WsMessage()

/** Keep-alive — client sends, server replies with [WsPong]. */
@Serializable
@SerialName("ping")
data class WsPing(val ts: Long = System.currentTimeMillis()) : WsMessage()

/** Keep-alive reply. */
@Serializable
@SerialName("pong")
data class WsPong(val ts: Long = System.currentTimeMillis()) : WsMessage()

// ── Diagnostic relay messages (TODO-006) ────────────────────────────────────

/**
 * Diagnostic command sent by a technician (via admin panel) to the POS app.
 * Commands are scoped to the session's dataScope — only safe, read-only operations.
 */
@Serializable
@SerialName("diag_command")
data class WsDiagCommand(
    val sessionId: String,
    val command: String,
    val payload: String = "{}",
) : WsMessage()

/**
 * Diagnostic response sent by the POS app back to the technician.
 */
@Serializable
@SerialName("diag_response")
data class WsDiagResponse(
    val sessionId: String,
    val command: String,
    val result: String,
    val success: Boolean = true,
) : WsMessage()

/**
 * Notification that a diagnostic session has started or ended for a store.
 */
@Serializable
@SerialName("diag_session_event")
data class WsDiagSessionEvent(
    val sessionId: String,
    val storeId: String,
    val event: String,
) : WsMessage()

// ── Dashboard real-time update (C5.4) ──────────────────────────────────────

/**
 * Sent to dashboard-subscribed clients when a new order completes at the store.
 * Clients should silently reload KPI data on receipt.
 */
@Serializable
@SerialName("dashboard_update")
data class WsDashboardUpdate(
    val storeId: String,
    /** Epoch milliseconds of the event that triggered this update (e.g. order completion). */
    val triggeredAt: Long = System.currentTimeMillis(),
    /** Which KPI areas are affected — helps clients decide what to reload. */
    val affectedAreas: List<String> = listOf("sales", "orders"),
) : WsMessage()

// ── Redis pub/sub payloads ──────────────────────────────────────────────────

/** Redis pub/sub notification payload published by the API service. */
@Serializable
data class SyncNotification(
    val storeId: String,
    val senderDeviceId: String,
    val operationCount: Int,
    val latestSeq: Long,
    val entityTypes: List<String> = emptyList(),
)

/**
 * Redis pub/sub payload published by the API service on `dashboard:update:{storeId}`
 * whenever an order completes. The sync service fans this out to dashboard WebSocket clients.
 */
@Serializable
data class DashboardUpdateNotification(
    val storeId: String,
    val triggeredAt: Long = System.currentTimeMillis(),
    val affectedAreas: List<String> = listOf("sales", "orders"),
)
