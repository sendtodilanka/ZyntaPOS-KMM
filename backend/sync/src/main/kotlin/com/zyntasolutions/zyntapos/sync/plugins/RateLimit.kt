package com.zyntasolutions.zyntapos.sync.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import kotlin.time.Duration.Companion.minutes

/**
 * Rate limiting for the sync server (TODO-009 Level 2).
 *
 * WebSocket connections are long-lived; the rate limit applies to the
 * initial connection handshake (HTTP Upgrade). A legitimate POS device
 * opens one persistent connection — this prevents connection-flood attacks.
 *
 *  • "ws" — 20 new WebSocket connections / 1 minute per IP
 */
fun Application.configureRateLimit() {
    install(RateLimit) {
        register(RateLimitName("ws")) {
            rateLimiter(limit = 20, refillPeriod = 1.minutes)
        }
    }
}
