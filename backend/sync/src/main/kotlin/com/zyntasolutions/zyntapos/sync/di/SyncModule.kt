package com.zyntasolutions.zyntapos.sync.di

import com.zyntasolutions.zyntapos.sync.config.SyncConfig
import com.zyntasolutions.zyntapos.sync.hub.DiagnosticRelay
import com.zyntasolutions.zyntapos.sync.hub.RedisPubSubListener
import com.zyntasolutions.zyntapos.sync.hub.SyncForwarder
import com.zyntasolutions.zyntapos.sync.hub.WebSocketHub
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import org.koin.dsl.module

val syncModule = module {
    single { SyncConfig.fromEnvironment() }

    // WebSocketHub tracks connections per (storeId, deviceId)
    single { WebSocketHub() }

    // DiagnosticRelay bridges technician ↔ POS device for diagnostic sessions (TODO-006)
    single { DiagnosticRelay(get()) }

    // Redis connection for health checks
    single<StatefulRedisConnection<String, String>?> {
        try { RedisClient.create(get<SyncConfig>().redisUrl).connect() } catch (_: Exception) { null }
    }

    // SyncForwarder proxies WebSocket sync push/pull to API service REST endpoints
    single { SyncForwarder(get<SyncConfig>().apiBaseUrl) }

    // Redis delta fan-out listener — broadcasts push notifications + force-sync to WS devices
    // A6: ForceSyncSubscriber removed — RedisPubSubListener handles both sync:delta:* and sync:commands
    single { RedisPubSubListener(get<SyncConfig>().redisUrl, get()) }
}
