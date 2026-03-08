package com.zyntasolutions.zyntapos.api.di

import com.zyntasolutions.zyntapos.api.config.AppConfig
import com.zyntasolutions.zyntapos.api.service.AdminAlertsService
import com.zyntasolutions.zyntapos.api.service.AdminAuditService
import com.zyntasolutions.zyntapos.api.service.AdminAuthService
import com.zyntasolutions.zyntapos.api.service.AdminConfigService
import com.zyntasolutions.zyntapos.api.service.AdminMetricsService
import com.zyntasolutions.zyntapos.api.service.AdminStoresService
import com.zyntasolutions.zyntapos.api.service.AlertGenerationJob
import com.zyntasolutions.zyntapos.api.service.GoogleOAuthService
import com.zyntasolutions.zyntapos.api.service.MfaService
import com.zyntasolutions.zyntapos.api.service.ProductService
import com.zyntasolutions.zyntapos.api.service.SyncService
import com.zyntasolutions.zyntapos.api.service.UserService
import org.koin.dsl.module

val appModule = module {
    single { AppConfig.fromEnvironment() }
    single { UserService() }
    single { SyncService() }
    single { ProductService() }
    single { AdminAuthService(get(), get()) }
    single { AdminAuditService() }
    single { AdminStoresService() }
    single { AdminConfigService() }
    single { AdminAlertsService() }
    single { AdminMetricsService() }
    single { MfaService() }
    single { GoogleOAuthService(get()) }
    single { AlertGenerationJob(get()) }
}
