package com.zyntasolutions.zyntapos.license

import com.zyntasolutions.zyntapos.license.db.LicenseDatabaseFactory
import com.zyntasolutions.zyntapos.license.di.licenseModule
import com.zyntasolutions.zyntapos.license.plugins.configureAuthentication
import com.zyntasolutions.zyntapos.license.plugins.configureMonitoring
import com.zyntasolutions.zyntapos.license.plugins.configureRateLimit
import com.zyntasolutions.zyntapos.license.plugins.configureRouting
import com.zyntasolutions.zyntapos.license.plugins.configureSecurity
import com.zyntasolutions.zyntapos.license.plugins.configureSerialization
import com.zyntasolutions.zyntapos.license.plugins.configureStatusPages
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main() {
    // Block Java deserialization (TODO-009 Level 1a) — MUST be before embeddedServer
    System.setProperty("jdk.serialFilter", "!*")

    embeddedServer(
        factory = CIO,
        port = System.getenv("PORT")?.toIntOrNull() ?: 8083,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    install(Koin) {
        slf4jLogger()
        modules(licenseModule)
    }

    LicenseDatabaseFactory.init()
    check(LicenseDatabaseFactory.ping()) { "Database not available after initialization" }

    configureSerialization()
    configureAuthentication()
    configureSecurity()
    configureRateLimit()
    configureMonitoring()
    configureStatusPages()
    configureRouting()
}
