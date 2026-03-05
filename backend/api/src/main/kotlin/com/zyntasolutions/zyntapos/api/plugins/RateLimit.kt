package com.zyntasolutions.zyntapos.api.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Rate limiting (TODO-009 Level 2).
 *
 * Two tiers:
 *  • "auth" — stricter limits on login/refresh endpoints (brute-force protection)
 *  • "api"  — standard limits for all other authenticated endpoints
 *
 * Limits are per client IP. Behind Caddy, the real IP is forwarded via
 * the `X-Forwarded-For` header (Caddy sets `trusted_proxies` in Caddyfile).
 */
fun Application.configureRateLimit() {
    install(RateLimit) {
        // Auth endpoints: 10 requests / 1 minute per IP
        register(RateLimitName("auth")) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
        }

        // API endpoints: 300 requests / 1 minute per IP
        register(RateLimitName("api")) {
            rateLimiter(limit = 300, refillPeriod = 1.minutes)
        }

        // Sync/push endpoints: 60 requests / 1 minute per IP (write-heavy ops throttled)
        register(RateLimitName("sync")) {
            rateLimiter(limit = 60, refillPeriod = 1.minutes)
        }
    }
}
