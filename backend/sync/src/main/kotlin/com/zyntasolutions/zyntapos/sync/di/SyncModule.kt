package com.zyntasolutions.zyntapos.sync.di

import com.zyntasolutions.zyntapos.sync.config.SyncConfig
import com.zyntasolutions.zyntapos.sync.pubsub.ForceSyncSubscriber
import com.zyntasolutions.zyntapos.sync.session.SyncSessionManager
import org.koin.dsl.module

val syncModule = module {
    single { SyncConfig.fromEnvironment() }
    single { SyncSessionManager() }
    single { ForceSyncSubscriber(get<SyncConfig>().redisUrl, get()) }
}
