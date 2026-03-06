package com.zyntasolutions.zyntapos.license.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import kotlin.time.Duration.Companion.minutes

/**
 * Rate limiting for the license server (TODO-009 Level 2).
 *
 * License activation and heartbeat calls are rare operations (once per device
 * per day for heartbeat, once per install for activation). Strict limits here
 * prevent license key enumeration attacks.
 *
 *  • "activate" — 5 requests / 10 minutes per IP (prevents key brute-force)
 *  • "heartbeat" — 5 requests / 10 minutes per IP (devices heartbeat once per 24h)
 */
fun Application.configureRateLimit() {
    install(RateLimit) {
        // Activation: very strict — license key enumeration is a real threat
        register(RateLimitName("activate")) {
            rateLimiter(limit = 5, refillPeriod = 10.minutes)
        }

        // Heartbeat: devices send once per 24h; allow 5 per 10min for retries
        register(RateLimitName("heartbeat")) {
            rateLimiter(limit = 5, refillPeriod = 10.minutes)
        }
    }
}
