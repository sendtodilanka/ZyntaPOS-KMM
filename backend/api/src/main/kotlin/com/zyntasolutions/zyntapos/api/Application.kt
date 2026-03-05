package com.zyntasolutions.zyntapos.api

import com.zyntasolutions.zyntapos.api.db.DatabaseFactory
import com.zyntasolutions.zyntapos.api.di.appModule
import com.zyntasolutions.zyntapos.api.plugins.configureAuthentication
import com.zyntasolutions.zyntapos.api.plugins.configureCors
import com.zyntasolutions.zyntapos.api.plugins.configureMonitoring
import com.zyntasolutions.zyntapos.api.plugins.configureRateLimit
import com.zyntasolutions.zyntapos.api.plugins.configureRouting
import com.zyntasolutions.zyntapos.api.plugins.configureSecurity
import com.zyntasolutions.zyntapos.api.plugins.configureSerialization
import com.zyntasolutions.zyntapos.api.plugins.configureStatusPages
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main() {
    // ── Security: Block Java deserialization (TODO-009 Level 1a) ──────────
    // ZyntaPOS uses kotlinx.serialization exclusively. This closes the entire
    // gadget-chain exploit class (ysoserial etc.). Must be called BEFORE
    // embeddedServer starts to prevent any library code from deserializing.
    System.setProperty("jdk.serialFilter", "!*")

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

    configureSerialization()
    configureAuthentication()
    configureCors()
    configureSecurity()
    configureRateLimit()
    configureMonitoring()
    configureStatusPages()
    configureRouting()
}
