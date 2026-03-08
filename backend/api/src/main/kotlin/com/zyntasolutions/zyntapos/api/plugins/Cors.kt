package com.zyntasolutions.zyntapos.api.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

fun Application.configureCors() {
    install(CORS) {
        allowCredentials = true
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-Device-ID")
        allowHeader("X-License-Key")
        allowHeader("X-XSRF-Token")
        // Only allow requests from the ZyntaPOS app and admin panel
        allowHost("panel.zyntapos.com", schemes = listOf("https"))
        // Desktop app and local dev — Ktor's allowHost rejects single-segment
        // hostnames like "localhost", so use allowOrigins predicate instead.
        allowOrigins { origin ->
            origin == "http://localhost" ||
                origin == "https://localhost" ||
                origin.startsWith("http://localhost:") ||
                origin.startsWith("https://localhost:")
        }
        maxAgeInSeconds = 3600L
    }
}
