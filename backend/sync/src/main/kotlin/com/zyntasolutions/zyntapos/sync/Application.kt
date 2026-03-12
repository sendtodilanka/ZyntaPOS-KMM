package com.zyntasolutions.zyntapos.sync

import io.sentry.Sentry
import com.zyntasolutions.zyntapos.sync.di.syncModule
import com.zyntasolutions.zyntapos.sync.hub.DiagnosticRelay
import com.zyntasolutions.zyntapos.sync.hub.RedisPubSubListener
import com.zyntasolutions.zyntapos.sync.hub.WebSocketHub
import com.zyntasolutions.zyntapos.sync.plugins.configureAuthentication
import com.zyntasolutions.zyntapos.sync.plugins.configureMonitoring
import com.zyntasolutions.zyntapos.sync.plugins.configureRateLimit
import com.zyntasolutions.zyntapos.sync.plugins.configureRouting
import com.zyntasolutions.zyntapos.sync.plugins.configureSecurity
import com.zyntasolutions.zyntapos.sync.plugins.configureSerialization
import com.zyntasolutions.zyntapos.sync.plugins.configureContentLengthLimit
import com.zyntasolutions.zyntapos.sync.plugins.configureStatusPages
import com.zyntasolutions.zyntapos.sync.plugins.configureWebSockets
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.getKoin
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main() {
    // Block Java deserialization (TODO-009 Level 1a)
    System.setProperty("jdk.serialFilter", "!*")

    // ── Sentry crash reporter — init before embeddedServer (ADR-011 rule #4) ─
    Sentry.init { options ->
        options.dsn         = System.getenv("SENTRY_DSN") ?: ""
        options.environment = System.getenv("SENTRY_ENVIRONMENT") ?: "production"
        options.release     = "zyntapos-sync@1.0.0"
        // S2-15: Configure sampling + PII scrubbing
        options.tracesSampleRate = (System.getenv("SENTRY_TRACES_SAMPLE_RATE")?.toDoubleOrNull() ?: 0.1)
        options.isSendDefaultPii = false
        options.setBeforeSend { event, _ ->
            event.user?.let { user ->
                user.email = null
                user.ipAddress = null
                user.username = null
            }
            event
        }
    }

    embeddedServer(
        factory = CIO,
        port = System.getenv("PORT")?.toIntOrNull() ?: 8082,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    install(Koin) {
        slf4jLogger()
        modules(syncModule)
    }

    // S4-2: Request correlation ID for cross-service tracing
    install(com.zyntasolutions.zyntapos.common.CorrelationId)

    configureSerialization()
    configureAuthentication()
    configureSecurity()
    configureRateLimit()
    configureMonitoring()
    configureWebSockets()
    configureStatusPages()
    configureContentLengthLimit()
    configureRouting()

    // Start Redis delta fan-out listener — broadcasts push deltas + force-sync to WS devices
    // A6: ForceSyncSubscriber removed — RedisPubSubListener already handles sync:commands
    // S2-14: start() is now a suspend fun — launch in coroutine to avoid blocking startup
    val redisListener = getKoin().get<RedisPubSubListener>()
    launch { redisListener.start() }

    // A7: Graceful shutdown — close Redis connections, diagnostic relay, flush Sentry, close WebSocketHub
    val hub = getKoin().get<WebSocketHub>()
    val diagnosticRelay = getKoin().get<DiagnosticRelay>()
    monitor.subscribe(ApplicationStopping) {
        redisListener.stop()
        diagnosticRelay.close()
        hub.close()
        Sentry.close()
    }
}
