package com.zyntasolutions.zyntapos.sync.hub

import com.zyntasolutions.zyntapos.sync.models.DashboardUpdateNotification
import com.zyntasolutions.zyntapos.sync.models.SyncNotification
import com.zyntasolutions.zyntapos.sync.models.WsDashboardUpdate
import com.zyntasolutions.zyntapos.sync.models.WsDelta
import com.zyntasolutions.zyntapos.sync.models.WsMessage
import com.zyntasolutions.zyntapos.sync.models.WsNotify
import io.lettuce.core.RedisClient
import io.lettuce.core.pubsub.RedisPubSubListener
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

private const val SYNC_DELTA_PATTERN = "sync:delta:*"
private const val DASHBOARD_UPDATE_PATTERN = "dashboard:update:*"
private const val SYNC_COMMANDS_CHANNEL = "sync:commands"
private const val SMALL_DELTA_THRESHOLD = 10
private const val MAX_RECONNECT_ATTEMPTS = 8
private val BACKOFF_MS = listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 60_000L, 60_000L)

/**
 * Subscribes to two Redis channels via pattern matching and broadcasts
 * real-time updates to connected WebSocket devices via [WebSocketHub].
 *
 * Channels:
 * - `sync:delta:{storeId}` — published by API service on every push batch
 * - `sync:commands`        — admin force-sync commands
 *
 * Connection failures are handled with exponential-backoff retry (C6). The
 * service continues to serve WebSocket connections in degraded mode (no
 * real-time push) while Redis is unavailable.
 */
class RedisPubSubListener(
    private val redisUrl: String,
    private val hub: WebSocketHub,
) {
    private val logger = LoggerFactory.getLogger(RedisPubSubListener::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private val client = RedisClient.create(redisUrl)
    private var connection: StatefulRedisPubSubConnection<String, String>? = null

    // S2-14: Made async — Thread.sleep replaced with coroutine delay to prevent
    // blocking the startup thread and causing Docker health check failures.
    suspend fun start() {
        var attempt = 0
        while (attempt < MAX_RECONNECT_ATTEMPTS) {
            try {
                connect()
                return  // success — stop retrying
            } catch (e: Exception) {
                val backoff = BACKOFF_MS.getOrElse(attempt) { 60_000L }
                logger.warn(
                    "RedisPubSubListener failed to connect (attempt ${attempt + 1}/$MAX_RECONNECT_ATTEMPTS): " +
                    "${e.message}. Retrying in ${backoff}ms …"
                )
                delay(backoff)
                attempt++
            }
        }
        logger.error(
            "RedisPubSubListener gave up after $MAX_RECONNECT_ATTEMPTS attempts. " +
            "Real-time sync push is disabled — WebSocket clients will poll on reconnect."
        )
    }

    private fun connect() {
        connection = client.connectPubSub()
        connection?.addListener(object : RedisPubSubListener<String, String> {
            override fun message(channel: String, message: String) = handleDirect(channel, message)
            override fun message(pattern: String, channel: String, message: String) =
                handlePattern(channel, message)

            override fun subscribed(channel: String, count: Long) =
                logger.info("Subscribed to Redis channel: $channel")

            override fun psubscribed(pattern: String, count: Long) =
                logger.info("Pattern-subscribed to: $pattern")

            override fun unsubscribed(channel: String, count: Long) {}
            override fun punsubscribed(pattern: String, count: Long) {}
        })

        // Pattern subscribe for delta notifications per store
        connection?.async()?.psubscribe(SYNC_DELTA_PATTERN)
        // Pattern subscribe for dashboard KPI update events (C5.4)
        connection?.async()?.psubscribe(DASHBOARD_UPDATE_PATTERN)
        // Direct subscribe for admin force-sync commands
        connection?.async()?.subscribe(SYNC_COMMANDS_CHANNEL)

        logger.info(
            "RedisPubSubListener started — patterns: $SYNC_DELTA_PATTERN, " +
            "$DASHBOARD_UPDATE_PATTERN, $SYNC_COMMANDS_CHANNEL"
        )
    }

    private fun handlePattern(channel: String, message: String) {
        when {
            channel.startsWith("sync:delta:")     -> handleSyncDelta(channel, message)
            channel.startsWith("dashboard:update:") -> handleDashboardUpdate(channel, message)
        }
    }

    private fun handleSyncDelta(channel: String, message: String) {
        if (!channel.startsWith("sync:delta:")) return
        val storeId = channel.removePrefix("sync:delta:")
        try {
            val notification = json.decodeFromString<SyncNotification>(message)
            val wsMessage = if (notification.operationCount <= SMALL_DELTA_THRESHOLD) {
                json.encodeToString<WsMessage>(WsDelta(
                    storeId        = storeId,
                    operationCount = notification.operationCount,
                    latestSeq      = notification.latestSeq,
                    entityTypes    = notification.entityTypes,
                ))
            } else {
                json.encodeToString<WsMessage>(WsNotify(
                    storeId     = storeId,
                    message     = "sync_available",
                    latestSeq   = notification.latestSeq,
                    entityTypes = notification.entityTypes,
                ))
            }
            hub.broadcast(storeId, wsMessage, excludeDeviceId = notification.senderDeviceId)
        } catch (e: Exception) {
            logger.warn("Failed to handle delta message on $channel: ${e.message}")
        }
    }

    private fun handleDashboardUpdate(channel: String, message: String) {
        val storeId = channel.removePrefix("dashboard:update:")
        try {
            val notification = json.decodeFromString<DashboardUpdateNotification>(message)
            val wsMessage = json.encodeToString<WsMessage>(
                WsDashboardUpdate(
                    storeId      = storeId,
                    triggeredAt  = notification.triggeredAt,
                    affectedAreas = notification.affectedAreas,
                )
            )
            // Broadcast to ALL devices in the store (no sender exclusion — dashboard clients
            // don't push data, so all need to be notified).
            hub.broadcast(storeId, wsMessage)
            logger.debug("Dashboard update broadcast for store $storeId")
        } catch (e: Exception) {
            logger.warn("Failed to handle dashboard update on $channel: ${e.message}")
        }
    }

    private fun handleDirect(channel: String, message: String) {
        if (channel != SYNC_COMMANDS_CHANNEL) return
        try {
            val parsed = json.parseToJsonElement(message)
            val type    = (parsed.jsonObject["type"] as? JsonPrimitive)?.content
            val storeId = (parsed.jsonObject["storeId"] as? JsonPrimitive)?.content
            if (type == "force_sync" && storeId != null) {
                hub.broadcast(storeId, message)
            }
        } catch (e: Exception) {
            logger.warn("Failed to handle command message: ${e.message}")
        }
    }

    fun stop() {
        try { connection?.close() } catch (_: Exception) {}
        try { client.shutdown() } catch (_: Exception) {}
        logger.info("RedisPubSubListener stopped")
    }
}
