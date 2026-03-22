package com.zyntasolutions.zyntapos.api.di

import com.zyntasolutions.zyntapos.api.config.AppConfig
import com.zyntasolutions.zyntapos.api.repository.AdminAuditRepository
import com.zyntasolutions.zyntapos.api.repository.AdminAuditRepositoryImpl
import com.zyntasolutions.zyntapos.api.repository.AdminStoresRepository
import com.zyntasolutions.zyntapos.api.repository.AdminStoresRepositoryImpl
import com.zyntasolutions.zyntapos.api.repository.AdminTicketRepository
import com.zyntasolutions.zyntapos.api.repository.AdminTicketRepositoryImpl
import com.zyntasolutions.zyntapos.api.repository.EmailThreadRepository
import com.zyntasolutions.zyntapos.api.repository.EmailThreadRepositoryImpl
import com.zyntasolutions.zyntapos.api.repository.AdminUserRepository
import com.zyntasolutions.zyntapos.api.repository.AdminUserRepositoryImpl
import com.zyntasolutions.zyntapos.api.repository.TicketCommentRepository
import com.zyntasolutions.zyntapos.api.repository.TicketCommentRepositoryImpl
import com.zyntasolutions.zyntapos.api.repository.ConflictLogRepository
import com.zyntasolutions.zyntapos.api.repository.DeadLetterRepository
import com.zyntasolutions.zyntapos.api.repository.PricingRuleRepository
import com.zyntasolutions.zyntapos.api.repository.ReplenishmentRepository
import com.zyntasolutions.zyntapos.api.repository.WarehouseStockRepository
import com.zyntasolutions.zyntapos.api.repository.EntitySnapshotRepository
import com.zyntasolutions.zyntapos.api.repository.PosUserRepository
import com.zyntasolutions.zyntapos.api.repository.PosUserRepositoryImpl
import com.zyntasolutions.zyntapos.api.repository.ProductRepository
import com.zyntasolutions.zyntapos.api.repository.ProductRepositoryImpl
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
import com.zyntasolutions.zyntapos.api.service.DiagnosticSessionService
import com.zyntasolutions.zyntapos.api.service.ChatwootService
import com.zyntasolutions.zyntapos.api.service.EmailRetryJob
import com.zyntasolutions.zyntapos.api.service.EmailService
import com.zyntasolutions.zyntapos.api.service.InboundEmailProcessor
import com.zyntasolutions.zyntapos.api.service.PlayIntegrityService
import com.zyntasolutions.zyntapos.api.service.ForceSyncNotifier
import com.zyntasolutions.zyntapos.api.service.LicenseValidationClient
import com.zyntasolutions.zyntapos.api.service.MfaService
import com.zyntasolutions.zyntapos.api.service.AdminTransferService
import com.zyntasolutions.zyntapos.api.service.MasterProductService
import com.zyntasolutions.zyntapos.api.service.ProductService
import com.zyntasolutions.zyntapos.api.service.UserService
import com.zyntasolutions.zyntapos.api.sync.DeltaEngine
import com.zyntasolutions.zyntapos.api.sync.EntityApplier
import com.zyntasolutions.zyntapos.api.sync.ServerConflictResolver
import com.zyntasolutions.zyntapos.api.sync.SyncMetrics
import com.zyntasolutions.zyntapos.api.sync.SyncProcessor
import com.zyntasolutions.zyntapos.api.sync.SyncValidator
import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.TimeoutOptions
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.support.ConnectionPoolSupport
import org.apache.commons.pool2.impl.GenericObjectPool
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.koin.dsl.module
import org.slf4j.LoggerFactory
import java.time.Duration

private val log = LoggerFactory.getLogger("AppModule")

val appModule = module {
    single { AppConfig.fromEnvironment() }

    // ── Redis connection pool (D9: GenericObjectPool via Lettuce ConnectionPoolSupport) ──
    // Replaces the single shared StatefulRedisConnection to eliminate publish() serialization
    // under concurrent sync load. Pool size configurable via REDIS_POOL_SIZE env var.
    single<GenericObjectPool<StatefulRedisConnection<String, String>>?> {
        try {
            val uri = RedisURI.create(get<AppConfig>().redisUrl)
            uri.timeout = Duration.ofSeconds(
                System.getenv("REDIS_TIMEOUT_SECONDS")?.toLongOrNull() ?: 5
            )
            val client = RedisClient.create(uri)
            client.options = ClientOptions.builder()
                .autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .timeoutOptions(TimeoutOptions.enabled(Duration.ofSeconds(5)))
                .build()
            val poolConfig = GenericObjectPoolConfig<StatefulRedisConnection<String, String>>().apply {
                maxTotal = System.getenv("REDIS_POOL_SIZE")?.toIntOrNull() ?: 8
                maxIdle  = 4
                minIdle  = 1
                testOnBorrow = true
            }
            ConnectionPoolSupport.createGenericObjectPool({ client.connect() }, poolConfig)
        } catch (e: Exception) {
            log.warn("Redis unavailable — sync notifications disabled: ${e.message}")
            null
        }
    }

    // ── Repositories (S3-15) ────────────────────────────────────────────────
    single<AdminAuditRepository> { AdminAuditRepositoryImpl() }
    single<AdminStoresRepository> { AdminStoresRepositoryImpl() }
    single<AdminTicketRepository> { AdminTicketRepositoryImpl() }
    single<TicketCommentRepository> { TicketCommentRepositoryImpl() }
    single<EmailThreadRepository> { EmailThreadRepositoryImpl() }
    single<AdminUserRepository> { AdminUserRepositoryImpl() }
    single<PosUserRepository> { PosUserRepositoryImpl() }
    single<ProductRepository> { ProductRepositoryImpl() }

    single { WarehouseStockRepository() }

    // Replenishment rules: per-product auto-PO thresholds (C1.5)
    single { ReplenishmentRepository() }

    // Pricing rules: store-specific and time-bounded price overrides (C2.1)
    single { PricingRuleRepository() }

    // Exchange rates: multi-currency conversion (C2.2 — platform-level config per ADR-009)
    single { com.zyntasolutions.zyntapos.api.repository.ExchangeRateRepository() }

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
            redisPool        = get(),
        )
    }

    // ── Services ──────────────────────────────────────────────────────────────
    single { EmailService(get()) }
    single { EmailRetryJob(get()) }
    single { PlayIntegrityService(get()) }          // Play Integrity API verification (TODO-008)
    single { ChatwootService(get()) }               // Chatwoot support inbox (TODO-008a)
    single { InboundEmailProcessor(                 // CF Worker → ticket pipeline (TODO-008a)
        config = get(),
        ticketRepo = get(),
        emailService = get(),
        chatwootService = get(),
        ticketService = get(),
    ) }
    single { DiagnosticSessionService(get()) }
    single { LicenseValidationClient() }
    single { UserService(posUserRepo = get()) }
    single { ProductService(productRepo = get()) }
    single { MasterProductService() }
    single { AdminTransferService() }
    single { AdminAuthService(config = get(), auditService = get(), adminUserRepo = get(), emailService = get()) }
    single { AdminAuditService(auditRepo = get()) }
    single { AdminStoresService(storesRepo = get()) }
    single { AdminConfigService() }
    single { AdminAlertsService() }
    single { AdminMetricsService() }
    single { MfaService() }
    single { AdminTicketService(ticketRepo = get(), commentRepo = get(), emailThreadRepo = get(), emailService = get()) }
    single { AlertGenerationJob(alertsService = get(), ticketService = get()) }
    single { ForceSyncNotifier(redisPool = get()) }
}
