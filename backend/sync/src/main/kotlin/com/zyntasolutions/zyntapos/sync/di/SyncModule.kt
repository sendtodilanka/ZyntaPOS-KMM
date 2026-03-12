package com.zyntasolutions.zyntapos.sync.di

import com.zyntasolutions.zyntapos.sync.config.SyncConfig
import com.zyntasolutions.zyntapos.sync.hub.RedisPubSubListener
import com.zyntasolutions.zyntapos.sync.hub.WebSocketHub
import org.koin.dsl.module

val syncModule = module {
    single { SyncConfig.fromEnvironment() }

    // WebSocketHub tracks connections per (storeId, deviceId)
    single { WebSocketHub() }

    // Redis delta fan-out listener — broadcasts push notifications + force-sync to WS devices
    // A6: ForceSyncSubscriber removed — RedisPubSubListener handles both sync:delta:* and sync:commands
    single { RedisPubSubListener(get<SyncConfig>().redisUrl, get()) }
}
