package com.zyntasolutions.zyntapos.sync.plugins

import com.zyntasolutions.zyntapos.sync.routes.diagnosticDeviceWebSocketRoutes
import com.zyntasolutions.zyntapos.sync.routes.diagnosticWebSocketRoutes
import com.zyntasolutions.zyntapos.sync.routes.healthRoutes
import com.zyntasolutions.zyntapos.sync.routes.syncWebSocketRoutes
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        // OpenAPI documentation — Swagger UI served at /docs
        swaggerUI(path = "docs", swaggerFile = "openapi/sync-spec.yaml")

        healthRoutes()
        // Rate-limit WebSocket connection upgrades to prevent connection floods
        rateLimit(RateLimitName("ws")) {
            authenticate("jwt-rs256") {
                syncWebSocketRoutes()
                // Diagnostic relay WebSocket for technician sessions (TODO-006)
                diagnosticWebSocketRoutes()
            }
            // Device diagnostic endpoint — uses JIT token auth (query param), not JWT bearer.
            // Placed outside authenticate() because the JIT token is validated by DiagnosticRelay.
            diagnosticDeviceWebSocketRoutes()
        }
    }
}
