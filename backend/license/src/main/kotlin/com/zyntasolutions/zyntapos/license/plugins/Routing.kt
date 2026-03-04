package com.zyntasolutions.zyntapos.license.plugins

import com.zyntasolutions.zyntapos.license.routes.healthRoutes
import com.zyntasolutions.zyntapos.license.routes.licenseRoutes
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        healthRoutes()
        route("/v1") {
            // License activation and heartbeat are called by the POS app
            // using the license key as authentication (not JWT)
            licenseRoutes()
        }
    }
}
