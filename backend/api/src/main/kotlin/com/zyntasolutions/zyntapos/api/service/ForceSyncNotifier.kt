package com.zyntasolutions.zyntapos.api.service

import io.lettuce.core.api.StatefulRedisConnection
import org.apache.commons.pool2.impl.GenericObjectPool
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
 *
 * D9: Uses the shared [GenericObjectPool] from the Koin graph so that force-sync
 * publishes do not contend with [com.zyntasolutions.zyntapos.api.sync.SyncProcessor]
 * for a single connection under load.
 */
class ForceSyncNotifier(
    private val redisPool: GenericObjectPool<StatefulRedisConnection<String, String>>?,
) {

    private val logger = LoggerFactory.getLogger(ForceSyncNotifier::class.java)

    /**
     * Publishes a force-sync message to the [SYNC_COMMANDS_CHANNEL] Redis channel.
     * Connected POS devices for the given store will receive a WebSocket push.
     *
     * @param storeId The store whose devices should perform an immediate sync.
     * @return Number of subscribers that received the message (0 if no devices connected or Redis unavailable).
     */
    fun publish(storeId: String): Long {
        if (redisPool == null) return 0L
        val message = """{"type":"force_sync","storeId":"$storeId"}"""
        return try {
            val conn = redisPool.borrowObject()
            try {
                val receivers = conn.sync().publish(SYNC_COMMANDS_CHANNEL, message)
                logger.info("force_sync published: storeId=$storeId receivers=$receivers")
                receivers ?: 0L
            } finally {
                redisPool.returnObject(conn)
            }
        } catch (e: Exception) {
            logger.error("Failed to publish force_sync for storeId=$storeId: ${e.message}")
            0L
        }
    }
}
