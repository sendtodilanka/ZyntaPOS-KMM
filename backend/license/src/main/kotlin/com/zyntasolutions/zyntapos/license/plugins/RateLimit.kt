package com.zyntasolutions.zyntapos.license.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.request.receiveText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.minutes

/**
 * Rate limiting for the license server (TODO-009 Level 2).
 *
 * License activation and heartbeat calls are rare operations (once per device
 * per day for heartbeat, once per install for activation). Strict limits here
 * prevent license key enumeration attacks.
 *
 *  • "activate"          — 5 requests / 10 minutes per IP
 *  • "activate-by-key"   — 5 requests / 10 minutes per licenseKey (B6: prevents distributed brute-force)
 *  • "heartbeat"         — 5 requests / 10 minutes per IP
 *  • "heartbeat-by-key"  — 60 requests / 10 minutes per licenseKey (B6: per-device throttle)
 */
fun Application.configureRateLimit() {
    install(RateLimit) {
        // Activation: very strict — license key enumeration is a real threat
        register(RateLimitName("activate")) {
            rateLimiter(limit = 5, refillPeriod = 10.minutes)
        }

        // Activation per license key: prevents distributed key brute-force (B6)
        register(RateLimitName("activate-by-key")) {
            rateLimiter(limit = 5, refillPeriod = 10.minutes)
            requestKey { call ->
                runCatching {
                    val body = call.receiveText()
                    Json.parseToJsonElement(body).jsonObject["licenseKey"]?.jsonPrimitive?.content
                }.getOrNull() ?: call.request.local.remoteAddress
            }
        }

        // Heartbeat: devices send once per 24h; allow 5 per 10min for retries
        register(RateLimitName("heartbeat")) {
            rateLimiter(limit = 5, refillPeriod = 10.minutes)
        }

        // Heartbeat per license key: 60 per device per 10min (generous for legitimate retry bursts)
        register(RateLimitName("heartbeat-by-key")) {
            rateLimiter(limit = 60, refillPeriod = 10.minutes)
            requestKey { call ->
                runCatching {
                    val body = call.receiveText()
                    Json.parseToJsonElement(body).jsonObject["licenseKey"]?.jsonPrimitive?.content
                }.getOrNull() ?: call.request.local.remoteAddress
            }
        }
    }
}
