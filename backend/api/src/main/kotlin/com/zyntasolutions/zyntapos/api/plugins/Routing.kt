package com.zyntasolutions.zyntapos.api.plugins

import com.zyntasolutions.zyntapos.api.routes.adminAlertsRoutes
import com.zyntasolutions.zyntapos.api.routes.adminAuditRoutes
import com.zyntasolutions.zyntapos.api.routes.adminAuthRoutes
import com.zyntasolutions.zyntapos.api.routes.adminConfigRoutes
import com.zyntasolutions.zyntapos.api.routes.adminHealthRoutes
import com.zyntasolutions.zyntapos.api.routes.adminMetricsRoutes
import com.zyntasolutions.zyntapos.api.routes.adminStoresRoutes
import com.zyntasolutions.zyntapos.api.routes.adminSyncRoutes
import com.zyntasolutions.zyntapos.api.routes.adminTicketRoutes
import com.zyntasolutions.zyntapos.api.routes.authRoutes
import com.zyntasolutions.zyntapos.api.routes.healthRoutes
import com.zyntasolutions.zyntapos.api.routes.productRoutes
import com.zyntasolutions.zyntapos.api.routes.syncRoutes
import com.zyntasolutions.zyntapos.api.routes.wellKnownRoutes
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

        // RFC 5785 well-known — public key for offline JWT verification (ADR-008)
        rateLimit(RateLimitName("api")) {
            wellKnownRoutes()
        }

        // Admin panel auth routes — strict rate limit + CSRF protection
        rateLimit(RateLimitName("auth")) {
            withCsrfProtection {
                adminAuthRoutes()
            }
        }

        // Admin panel data routes — standard API rate limit + CSRF protection
        rateLimit(RateLimitName("api")) {
            withCsrfProtection {
                adminStoresRoutes()
                adminHealthRoutes()
                adminAuditRoutes()
                adminMetricsRoutes()
                adminAlertsRoutes()
                adminSyncRoutes()
                adminConfigRoutes()
                adminTicketRoutes()
            }
        }

        // Admin panel data routes — standard API rate limit, cookie-auth validated inside each route
        rateLimit(RateLimitName("api")) {
            adminStoresRoutes()
            adminHealthRoutes()
            adminAuditRoutes()
            adminMetricsRoutes()
            adminAlertsRoutes()
            adminSyncRoutes()
            adminConfigRoutes()
            adminTicketRoutes()
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
