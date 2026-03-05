package com.zyntasolutions.zyntapos.api.plugins

import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.defaultheaders.DefaultHeaders

/**
 * Security response headers (TODO-009 Level 2 — HTTP Security Headers).
 * These headers harden the API against common web attacks.
 */
fun Application.configureSecurity() {
    install(DefaultHeaders) {
        // Prevent MIME-type sniffing
        header("X-Content-Type-Options", "nosniff")
        // Prevent clickjacking
        header("X-Frame-Options", "DENY")
        // Enforce HTTPS for 1 year
        header("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload")
        // Restrict to same-origin fetches only (REST API has no embeds/forms)
        header("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")
        // Block cross-site scripting (legacy browsers)
        header("X-XSS-Protection", "0")  // 0 = disable buggy XSS auditor; rely on CSP
        // Limit referrer info
        header("Referrer-Policy", "no-referrer")
        // Disable permissions API
        header("Permissions-Policy", "geolocation=(), microphone=(), camera=()")
        // Remove server banner
        header(HttpHeaders.Server, "ZyntaPOS-API")
    }
}
