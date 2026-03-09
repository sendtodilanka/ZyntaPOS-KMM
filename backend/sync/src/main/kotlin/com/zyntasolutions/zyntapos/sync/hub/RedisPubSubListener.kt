package com.zyntasolutions.zyntapos.sync.hub

import com.zyntasolutions.zyntapos.sync.models.SyncNotification
import com.zyntasolutions.zyntapos.sync.models.WsDelta
import com.zyntasolutions.zyntapos.sync.models.WsNotify
import io.lettuce.core.RedisClient
import io.lettuce.core.pubsub.RedisPubSubListener
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private const val SYNC_DELTA_PATTERN = "sync:delta:*"
private const val SYNC_COMMANDS_CHANNEL = "sync:commands"
private const val SMALL_DELTA_THRESHOLD = 10

/**
 * Subscribes to two Redis channels via pattern matching and broadcasts
 * real-time updates to connected WebSocket devices via [WebSocketHub].
 *
 * Channels:
 * - `sync:delta:{storeId}` — published by API service on every push batch
 * - `sync:commands`        — admin force-sync commands (already handled by
 *                            [ForceSyncSubscriber], kept here for consolidated routing)
 */
class RedisPubSubListener(
    private val redisUrl: String,
    private val hub: WebSocketHub,
) {
    private val logger = LoggerFactory.getLogger(RedisPubSubListener::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private val client = RedisClient.create(redisUrl)
    private var connection: StatefulRedisPubSubConnection<String, String>? = null

    fun start() {
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
        // Direct subscribe for admin force-sync commands
        connection?.async()?.subscribe(SYNC_COMMANDS_CHANNEL)

        logger.info("RedisPubSubListener started — patterns: $SYNC_DELTA_PATTERN, $SYNC_COMMANDS_CHANNEL")
    }

    private fun handlePattern(channel: String, message: String) {
        if (!channel.startsWith("sync:delta:")) return
        val storeId = channel.removePrefix("sync:delta:")
        try {
            val notification = json.decodeFromString<SyncNotification>(message)
            val wsMessage = if (notification.operationCount <= SMALL_DELTA_THRESHOLD) {
                json.encodeToString(WsDelta(
                    storeId        = storeId,
                    operationCount = notification.operationCount,
                    latestSeq      = notification.latestSeq,
                ))
            } else {
                json.encodeToString(WsNotify(
                    storeId   = storeId,
                    message   = "sync_available",
                    latestSeq = notification.latestSeq,
                ))
            }
            hub.broadcast(storeId, wsMessage, excludeDeviceId = notification.senderDeviceId)
        } catch (e: Exception) {
            logger.warn("Failed to handle delta message on $channel: ${e.message}")
        }
    }

    private fun handleDirect(channel: String, message: String) {
        if (channel != SYNC_COMMANDS_CHANNEL) return
        try {
            val parsed = json.parseToJsonElement(message)
            val type    = parsed.jsonObject["type"]?.jsonPrimitive?.content
            val storeId = parsed.jsonObject["storeId"]?.jsonPrimitive?.content
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

    // JSON element helpers (avoid importing full serialization for this inline parse)
    private val kotlinx.serialization.json.JsonElement.jsonObject
        get() = (this as kotlinx.serialization.json.JsonObject)
    private val kotlinx.serialization.json.JsonElement.jsonPrimitive
        get() = (this as kotlinx.serialization.json.JsonPrimitive)
}
