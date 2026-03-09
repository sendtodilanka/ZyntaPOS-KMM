package com.zyntasolutions.zyntapos.api.service

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import org.slf4j.LoggerFactory

private const val SYNC_COMMANDS_CHANNEL = "sync:commands"

/**
 * Publishes force-sync notifications to the Redis `sync:commands` pub/sub channel.
 *
 * The Sync service subscribes to this channel and broadcasts the message to all
 * WebSocket clients connected for the given store. This decouples the API service
 * from the WebSocket session state managed by the Sync service.
 *
 * Message format: `{"type":"force_sync","storeId":"<store-id>"}`
 */
class ForceSyncNotifier(redisUrl: String) {

    private val logger = LoggerFactory.getLogger(ForceSyncNotifier::class.java)
    private val client: RedisClient = RedisClient.create(redisUrl)
    private val connection: StatefulRedisConnection<String, String> = client.connect()
    private val commands: RedisCommands<String, String> = connection.sync()

    /**
     * Publishes a force-sync message to the [SYNC_COMMANDS_CHANNEL] Redis channel.
     * Connected POS devices for the given store will receive a WebSocket push.
     *
     * @param storeId The store whose devices should perform an immediate sync.
     * @return Number of subscribers that received the message (0 if no devices connected).
     */
    fun publish(storeId: String): Long {
        val message = """{"type":"force_sync","storeId":"$storeId"}"""
        return try {
            val receivers = commands.publish(SYNC_COMMANDS_CHANNEL, message)
            logger.info("force_sync published: storeId=$storeId receivers=$receivers")
            receivers ?: 0L
        } catch (e: Exception) {
            logger.error("Failed to publish force_sync for storeId=$storeId: ${e.message}")
            0L
        }
    }

    fun close() {
        try { connection.close() } catch (_: Exception) {}
        try { client.shutdown() } catch (_: Exception) {}
    }
}
