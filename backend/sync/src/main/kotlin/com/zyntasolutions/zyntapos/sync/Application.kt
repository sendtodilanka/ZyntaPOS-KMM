package com.zyntasolutions.zyntapos.sync

import com.zyntasolutions.zyntapos.sync.di.syncModule
import com.zyntasolutions.zyntapos.sync.plugins.configureAuthentication
import com.zyntasolutions.zyntapos.sync.plugins.configureMonitoring
import com.zyntasolutions.zyntapos.sync.plugins.configureRateLimit
import com.zyntasolutions.zyntapos.sync.plugins.configureRouting
import com.zyntasolutions.zyntapos.sync.plugins.configureSecurity
import com.zyntasolutions.zyntapos.sync.plugins.configureSerialization
import com.zyntasolutions.zyntapos.sync.plugins.configureStatusPages
import com.zyntasolutions.zyntapos.sync.plugins.configureWebSockets
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main() {
    // Block Java deserialization (TODO-009 Level 1a)
    System.setProperty("jdk.serialFilter", "!*")

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
    configureRouting()
}
