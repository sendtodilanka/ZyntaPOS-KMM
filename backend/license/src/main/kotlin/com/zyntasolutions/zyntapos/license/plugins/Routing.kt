package com.zyntasolutions.zyntapos.license.plugins

import com.zyntasolutions.zyntapos.license.routes.adminLicenseRoutes
import com.zyntasolutions.zyntapos.license.routes.healthRoutes
import com.zyntasolutions.zyntapos.license.routes.licenseRoutes
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        // OpenAPI documentation — Swagger UI served at /docs
        swaggerUI(path = "docs", swaggerFile = "openapi/license-spec.yaml")

        healthRoutes()

        // Admin panel license management — cookie-based JWT + CSRF protection (A4)
        rateLimit(RateLimitName("activate")) {
            withCsrfProtection {
                adminLicenseRoutes()
            }
        }

        route("/v1") {
            authenticate("jwt-rs256") {
                // Strict rate limit on all license endpoints to prevent key enumeration
                rateLimit(RateLimitName("activate")) {
                    licenseRoutes()
                }
            }
        }
    }
}
