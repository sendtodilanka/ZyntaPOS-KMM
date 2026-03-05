package com.zyntasolutions.zyntapos.license.plugins

import com.zyntasolutions.zyntapos.license.routes.healthRoutes
import com.zyntasolutions.zyntapos.license.routes.licenseRoutes
import io.ktor.server.application.Application
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        healthRoutes()
        route("/v1") {
            // Strict rate limit on all license endpoints to prevent key enumeration
            rateLimit(RateLimitName("activate")) {
                licenseRoutes()
            }
        }
    }
}
