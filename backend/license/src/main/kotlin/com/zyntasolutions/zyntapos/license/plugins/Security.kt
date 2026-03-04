package com.zyntasolutions.zyntapos.license.plugins

import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.defaultheaders.DefaultHeaders

fun Application.configureSecurity() {
    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload")
        header("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")
        header("X-XSS-Protection", "0")
        header("Referrer-Policy", "no-referrer")
        header(HttpHeaders.Server, "ZyntaPOS-License")
    }
}
