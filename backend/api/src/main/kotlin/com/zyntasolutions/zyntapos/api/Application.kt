package com.zyntasolutions.zyntapos.api

import io.sentry.Sentry
import com.zyntasolutions.zyntapos.api.db.DatabaseFactory
import com.zyntasolutions.zyntapos.api.di.appModule
import com.zyntasolutions.zyntapos.api.service.AlertGenerationJob
import com.zyntasolutions.zyntapos.api.service.EmailRetryJob
import com.zyntasolutions.zyntapos.api.plugins.configureAuthentication
import com.zyntasolutions.zyntapos.api.plugins.configureCors
import com.zyntasolutions.zyntapos.api.plugins.configureMonitoring
import com.zyntasolutions.zyntapos.api.plugins.configureRateLimit
import com.zyntasolutions.zyntapos.api.plugins.configureRouting
import com.zyntasolutions.zyntapos.api.plugins.configureSecurity
import com.zyntasolutions.zyntapos.api.plugins.configureSerialization
import com.zyntasolutions.zyntapos.api.plugins.configureContentLengthLimit
import com.zyntasolutions.zyntapos.api.plugins.configureStatusPages
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

// ── Canary tokens (TODO-010 — security monitoring) ──────────────────────────
// These fake credentials trigger Falco alerts if the source is exfiltrated
// and an attacker attempts to use them. DO NOT REMOVE.
// AWS_ACCESS_KEY_ID=AKIAIOSFODNN7CANARY0
// AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYCANARYKEY0
// CANARY_WEBHOOK=https://canarytokens.com/about/tags/terms/qr6z3p8vk44d5x
// ─────────────────────────────────────────────────────────────────────────────

fun main() {
    // ── Security: Block Java deserialization (TODO-009 Level 1a) ──────────
    // ZyntaPOS uses kotlinx.serialization exclusively. This closes the entire
    // gadget-chain exploit class (ysoserial etc.). Must be called BEFORE
    // embeddedServer starts to prevent any library code from deserializing.
    System.setProperty("jdk.serialFilter", "!*")

    // ── Sentry crash reporter — init before embeddedServer (ADR-011 rule #4) ─
    // EU ingest endpoint (o4510976925237248.ingest.de.sentry.io).
    // DSN injected via SENTRY_DSN environment variable in docker-compose.
    Sentry.init { options ->
        options.dsn         = System.getenv("SENTRY_DSN") ?: ""
        options.environment = System.getenv("SENTRY_ENVIRONMENT") ?: "production"
        options.release     = "zyntapos-api@1.0.0"
        // S2-15: Configure sampling + PII scrubbing
        options.tracesSampleRate = (System.getenv("SENTRY_TRACES_SAMPLE_RATE")?.toDoubleOrNull() ?: 0.1)
        options.isSendDefaultPii = false
        options.setBeforeSend { event, _ ->
            // Scrub user PII — keep only user ID for correlation
            event.user?.let { user ->
                user.email = null
                user.ipAddress = null
                user.username = null
            }
            event
        }
    }

    embeddedServer(
        factory = CIO,  // CIO not Netty — avoids JNI/off-heap CVEs (TODO-009 Level 1b)
        port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    install(Koin) {
        slf4jLogger()
        modules(appModule)
    }

    DatabaseFactory.init()
    check(DatabaseFactory.ping()) { "Database not available after initialization" }

    // Start background jobs
    val alertJob: AlertGenerationJob by inject()
    alertJob.start(intervalSeconds = 60L)

    val emailRetryJob: EmailRetryJob by inject()
    emailRetryJob.start(intervalSeconds = 60L)

    // S4-2: Request correlation ID for cross-service tracing
    install(com.zyntasolutions.zyntapos.common.CorrelationId)

    configureSerialization()
    configureAuthentication()
    configureCors()
    configureSecurity()
    configureRateLimit()
    configureMonitoring()
    configureStatusPages()
    configureContentLengthLimit()
    configureRouting()
}
