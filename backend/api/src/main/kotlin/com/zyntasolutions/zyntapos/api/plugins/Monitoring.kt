package com.zyntasolutions.zyntapos.api.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.slf4j.event.Level

fun Application.configureMonitoring() {
    val promRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    install(MicrometerMetrics) {
        registry = promRegistry
    }

    install(CallLogging) {
        level = Level.INFO
        filter { call -> !call.request.local.uri.startsWith("/health") && !call.request.local.uri.startsWith("/metrics") }
    }

    routing {
        get("/metrics") {
            call.respond(promRegistry.scrape())
        }
    }
}
