package com.zyntasolutions.zyntapos.api.plugins

import com.zyntasolutions.zyntapos.api.routes.adminAuthRoutes
import com.zyntasolutions.zyntapos.api.routes.authRoutes
import com.zyntasolutions.zyntapos.api.routes.healthRoutes
import com.zyntasolutions.zyntapos.api.routes.productRoutes
import com.zyntasolutions.zyntapos.api.routes.syncRoutes
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        // Public routes (no rate limit — health/ping are trivially cheap)
        healthRoutes()

        // Admin panel auth routes — strict rate limit (auth tier)
        rateLimit(RateLimitName("auth")) {
            adminAuthRoutes()
        }

        route("/v1") {
            // Auth endpoints: strict rate limit (10 req/min) to prevent brute-force
            rateLimit(RateLimitName("auth")) {
                authRoutes()
            }

            // Protected endpoints: standard API rate limit (300 req/min)
            authenticate("jwt-rs256") {
                rateLimit(RateLimitName("api")) {
                    productRoutes()
                }
                // Sync push is write-heavy — use sync tier (60 req/min)
                rateLimit(RateLimitName("sync")) {
                    syncRoutes()
                }
            }
        }
    }
}
