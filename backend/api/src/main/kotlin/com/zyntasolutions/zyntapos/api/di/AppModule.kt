package com.zyntasolutions.zyntapos.api.di

import com.zyntasolutions.zyntapos.api.config.AppConfig
import com.zyntasolutions.zyntapos.api.repository.ConflictLogRepository
import com.zyntasolutions.zyntapos.api.repository.DeadLetterRepository
import com.zyntasolutions.zyntapos.api.repository.EntitySnapshotRepository
import com.zyntasolutions.zyntapos.api.repository.SyncCursorRepository
import com.zyntasolutions.zyntapos.api.repository.SyncOperationRepository
import com.zyntasolutions.zyntapos.api.service.AdminAlertsService
import com.zyntasolutions.zyntapos.api.service.AdminAuditService
import com.zyntasolutions.zyntapos.api.service.AdminAuthService
import com.zyntasolutions.zyntapos.api.service.AdminConfigService
import com.zyntasolutions.zyntapos.api.service.AdminMetricsService
import com.zyntasolutions.zyntapos.api.service.AdminStoresService
import com.zyntasolutions.zyntapos.api.service.AdminTicketService
import com.zyntasolutions.zyntapos.api.service.AlertGenerationJob
import com.zyntasolutions.zyntapos.api.service.ForceSyncNotifier
import com.zyntasolutions.zyntapos.api.service.GoogleOAuthService
import com.zyntasolutions.zyntapos.api.service.MfaService
import com.zyntasolutions.zyntapos.api.service.ProductService
import com.zyntasolutions.zyntapos.api.service.SyncService
import com.zyntasolutions.zyntapos.api.service.UserService
import com.zyntasolutions.zyntapos.api.sync.DeltaEngine
import com.zyntasolutions.zyntapos.api.sync.EntityApplier
import com.zyntasolutions.zyntapos.api.sync.ServerConflictResolver
import com.zyntasolutions.zyntapos.api.sync.SyncMetrics
import com.zyntasolutions.zyntapos.api.sync.SyncProcessor
import com.zyntasolutions.zyntapos.api.sync.SyncValidator
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import org.koin.dsl.module
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("AppModule")

val appModule = module {
    single { AppConfig.fromEnvironment() }

    // ── Redis connection for pub/sub publishing ───────────────────────────────
    single<StatefulRedisConnection<String, String>?> {
        try {
            RedisClient.create(get<AppConfig>().redisUrl).connect()
        } catch (e: Exception) {
            log.warn("Redis connection unavailable — sync notifications disabled: ${e.message}")
            null
        }
    }

    // ── Sync engine repositories ──────────────────────────────────────────────
    single { SyncOperationRepository() }
    single { SyncCursorRepository() }
    single { ConflictLogRepository() }
    single { DeadLetterRepository() }
    single { EntitySnapshotRepository() }

    // ── Sync engine components ────────────────────────────────────────────────
    single { SyncMetrics() }
    single { SyncValidator() }
    single { ServerConflictResolver(conflictLogRepo = get()) }
    single { EntityApplier() }
    single { DeltaEngine(syncOpRepo = get(), cursorRepo = get(), metrics = get()) }
    single {
        SyncProcessor(
            syncOpRepo       = get(),
            conflictResolver = get(),
            validator        = get(),
            entityApplier    = get(),
            deadLetterRepo   = get(),
            metrics          = get(),
            redisConnection  = get(),
        )
    }

    // ── Services ──────────────────────────────────────────────────────────────
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
    single { AdminTicketService() }
    single { AlertGenerationJob(get()) }
    single { ForceSyncNotifier(get<AppConfig>().redisUrl) }
}
