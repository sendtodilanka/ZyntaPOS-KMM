package com.zyntasolutions.zyntapos.sync.pubsub

import com.zyntasolutions.zyntapos.sync.session.SyncSessionManager
import io.lettuce.core.RedisClient
import io.lettuce.core.pubsub.RedisPubSubListener
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

private const val SYNC_COMMANDS_CHANNEL = "sync:commands"

/**
 * Subscribes to the Redis `sync:commands` pub/sub channel and broadcasts
 * received force-sync messages to all WebSocket sessions for the target store.
 *
 * Published by [com.zyntasolutions.zyntapos.api.service.ForceSyncNotifier] in
 * the API service whenever an admin triggers `POST /admin/sync/{storeId}/force`.
 *
 * Message format: `{"type":"force_sync","storeId":"<store-id>"}`
 */
class ForceSyncSubscriber(
    private val redisUrl: String,
    private val sessionManager: SyncSessionManager,
) {

    private val logger = LoggerFactory.getLogger(ForceSyncSubscriber::class.java)
    private val client = RedisClient.create(redisUrl)
    private var connection: StatefulRedisPubSubConnection<String, String>? = null

    /** Starts listening to the [SYNC_COMMANDS_CHANNEL] Redis channel. */
    fun start() {
        connection = client.connectPubSub()
        connection?.addListener(object : RedisPubSubListener<String, String> {

            override fun message(channel: String, message: String) {
                handleMessage(message)
            }

            override fun message(pattern: String, channel: String, message: String) {
                handleMessage(message)
            }

            override fun subscribed(channel: String, count: Long) {
                logger.info("Subscribed to Redis channel: $channel")
            }

            override fun psubscribed(pattern: String, count: Long) {}
            override fun unsubscribed(channel: String, count: Long) {}
            override fun punsubscribed(pattern: String, count: Long) {}
        })
        connection?.async()?.subscribe(SYNC_COMMANDS_CHANNEL)
        logger.info("ForceSyncSubscriber started — listening on $SYNC_COMMANDS_CHANNEL")
    }

    private fun handleMessage(message: String) {
        try {
            val json = Json.parseToJsonElement(message).jsonObject
            val type = json["type"]?.jsonPrimitive?.content
            val storeId = json["storeId"]?.jsonPrimitive?.content

            if (type == "force_sync" && storeId != null) {
                logger.info("force_sync received for storeId=$storeId — broadcasting to WS sessions")
                sessionManager.broadcast(storeId, message)
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse sync command message: ${e.message} — raw: ${message.take(200)}")
        }
    }

    /** Stops the subscription and releases Redis resources. */
    fun stop() {
        try { connection?.close() } catch (_: Exception) {}
        try { client.shutdown() } catch (_: Exception) {}
        logger.info("ForceSyncSubscriber stopped")
    }
}
