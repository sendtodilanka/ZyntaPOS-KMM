package com.zyntasolutions.zyntapos.api.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

fun Application.configureCors() {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-Device-ID")
        allowHeader("X-License-Key")
        // Only allow requests from the ZyntaPOS app and admin panel
        allowHost("panel.zyntapos.com", schemes = listOf("https"))
        // Desktop app makes direct API calls �� allow localhost for dev
        allowHost("localhost", schemes = listOf("http", "https"))
        maxAgeInSeconds = 3600L
    }
}
