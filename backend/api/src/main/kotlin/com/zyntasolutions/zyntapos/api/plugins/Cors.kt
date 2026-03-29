package com.zyntasolutions.zyntapos.api.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Cors")

fun Application.configureCors() {
    // SECURITY: localhost origins are only allowed when CORS_ALLOW_LOCALHOST=true is explicitly
    // set. Shipping allowCredentials=true with open localhost origins in production allows any
    // server running on the developer/operator machine (malicious npm postinstall scripts,
    // supply-chain compromised dev deps, local proxies) to make authenticated cross-origin
    // requests to the production API using the user's cookies.
    val allowLocalhost = System.getenv("CORS_ALLOW_LOCALHOST")?.lowercase() == "true"
    if (allowLocalhost) {
        logger.warn("CORS_ALLOW_LOCALHOST=true — localhost origins allowed. Ensure this is a development environment only.")
    }

    install(CORS) {
        allowCredentials = true
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-Device-ID")
        allowHeader("X-License-Key")
        allowHeader("X-XSRF-Token")
        // Only allow requests from the ZyntaPOS app and admin panel
        allowHost("panel.zyntapos.com", schemes = listOf("https"))
        // Desktop app and local dev — gated on env var so localhost origins are never present
        // in production where allowCredentials=true is a combination that enables CSRF via
        // local-machine attack vectors.
        if (allowLocalhost) {
            // Ktor's allowHost rejects single-segment hostnames like "localhost",
            // so use allowOrigins predicate instead.
            allowOrigins { origin ->
                origin == "http://localhost" ||
                    origin == "https://localhost" ||
                    origin.startsWith("http://localhost:") ||
                    origin.startsWith("https://localhost:")
            }
        }
        maxAgeInSeconds = 3600L
    }
}
