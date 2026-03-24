package com.zyntasolutions.zyntapos.sync.di

import com.zyntasolutions.zyntapos.sync.config.SyncConfig
import com.zyntasolutions.zyntapos.sync.hub.DiagnosticRelay
import com.zyntasolutions.zyntapos.sync.hub.RedisPubSubListener
import com.zyntasolutions.zyntapos.sync.hub.SyncForwarder
import com.zyntasolutions.zyntapos.sync.hub.WebSocketHub
import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.TimeoutOptions
import io.lettuce.core.api.StatefulRedisConnection
import org.koin.dsl.module
import java.time.Duration

val syncModule = module {
    single { SyncConfig.fromEnvironment() }

    // WebSocketHub tracks connections per (storeId, deviceId)
    single { WebSocketHub() }

    // DiagnosticRelay bridges technician ↔ POS device for diagnostic sessions (TODO-006)
    single {
        val config = get<SyncConfig>()
        DiagnosticRelay(
            hub = get(),
            jwtPublicKey = config.jwtPublicKey,
            jwtIssuer = config.adminJwtIssuer,
        )
    }

    // S3-13: Redis connection with configured timeouts and auto-reconnect
    single<StatefulRedisConnection<String, String>?> {
        try {
            val uri = RedisURI.create(get<SyncConfig>().redisUrl)
            uri.timeout = Duration.ofSeconds(
                System.getenv("REDIS_TIMEOUT_SECONDS")?.toLongOrNull() ?: 5
            )
            val client = RedisClient.create(uri)
            client.options = ClientOptions.builder()
                .autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .timeoutOptions(TimeoutOptions.enabled(Duration.ofSeconds(5)))
                .build()
            client.connect()
        } catch (_: Exception) { null }
    }

    // SyncForwarder proxies WebSocket sync push/pull to API service REST endpoints
    single { SyncForwarder(get<SyncConfig>().apiBaseUrl) }

    // Redis delta fan-out listener — broadcasts push notifications + force-sync to WS devices
    // A6: ForceSyncSubscriber removed — RedisPubSubListener handles both sync:delta:* and sync:commands
    single { RedisPubSubListener(get<SyncConfig>().redisUrl, get()) }
}
