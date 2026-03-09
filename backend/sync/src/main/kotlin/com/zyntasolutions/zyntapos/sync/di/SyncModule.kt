package com.zyntasolutions.zyntapos.sync.di

import com.zyntasolutions.zyntapos.sync.config.SyncConfig
import com.zyntasolutions.zyntapos.sync.hub.RedisPubSubListener
import com.zyntasolutions.zyntapos.sync.hub.WebSocketHub
import com.zyntasolutions.zyntapos.sync.pubsub.ForceSyncSubscriber
import com.zyntasolutions.zyntapos.sync.session.SyncSessionManager
import org.koin.dsl.module

val syncModule = module {
    single { SyncConfig.fromEnvironment() }

    // WebSocketHub tracks connections per (storeId, deviceId) — TODO-007g
    single { WebSocketHub() }

    // Legacy per-store-only session manager (kept for ForceSyncSubscriber compat)
    single { SyncSessionManager() }

    // Redis delta fan-out listener — broadcasts push notifications to WS devices
    single { RedisPubSubListener(get<SyncConfig>().redisUrl, get()) }

    // Force-sync subscriber — handles admin-triggered force-sync commands
    single { ForceSyncSubscriber(get<SyncConfig>().redisUrl, get()) }
}
