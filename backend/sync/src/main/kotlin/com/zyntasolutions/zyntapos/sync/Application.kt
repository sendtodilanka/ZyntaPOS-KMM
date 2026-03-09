package com.zyntasolutions.zyntapos.sync

import io.sentry.Sentry
import com.zyntasolutions.zyntapos.sync.di.syncModule
import com.zyntasolutions.zyntapos.sync.hub.RedisPubSubListener
import com.zyntasolutions.zyntapos.sync.pubsub.ForceSyncSubscriber
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
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
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

    configureSerialization()
    configureAuthentication()
    configureSecurity()
    configureRateLimit()
    configureMonitoring()
    configureWebSockets()
    configureStatusPages()
    configureContentLengthLimit()
    configureRouting()

    // Start Redis delta fan-out listener (TODO-007g) — broadcasts push deltas to WS devices
    getKoin().get<RedisPubSubListener>().start()

    // Start force-sync subscriber — admin-triggered force-sync commands
    getKoin().get<ForceSyncSubscriber>().start()
}
