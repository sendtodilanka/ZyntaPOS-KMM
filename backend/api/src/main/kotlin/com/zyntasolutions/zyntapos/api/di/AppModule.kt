package com.zyntasolutions.zyntapos.api.di

import com.zyntasolutions.zyntapos.api.config.AppConfig
import com.zyntasolutions.zyntapos.api.service.AdminAuthService
import com.zyntasolutions.zyntapos.api.service.ProductService
import com.zyntasolutions.zyntapos.api.service.SyncService
import com.zyntasolutions.zyntapos.api.service.UserService
import org.koin.dsl.module

val appModule = module {
    single { AppConfig.fromEnvironment() }
    single { UserService() }
    single { SyncService() }
    single { ProductService() }
    single { AdminAuthService(get()) }
}
