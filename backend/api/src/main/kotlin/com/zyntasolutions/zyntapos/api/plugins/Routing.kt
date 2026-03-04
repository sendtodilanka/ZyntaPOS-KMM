package com.zyntasolutions.zyntapos.api.plugins

import com.zyntasolutions.zyntapos.api.routes.authRoutes
import com.zyntasolutions.zyntapos.api.routes.healthRoutes
import com.zyntasolutions.zyntapos.api.routes.productRoutes
import com.zyntasolutions.zyntapos.api.routes.syncRoutes
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        // Public routes
        healthRoutes()
        route("/v1") {
            authRoutes()
            // Protected routes
            authenticate("jwt-rs256") {
                productRoutes()
                syncRoutes()
            }
        }
    }
}
