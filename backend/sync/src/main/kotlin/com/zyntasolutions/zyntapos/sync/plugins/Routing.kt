package com.zyntasolutions.zyntapos.sync.plugins

import com.zyntasolutions.zyntapos.sync.routes.healthRoutes
import com.zyntasolutions.zyntapos.sync.routes.syncWebSocketRoutes
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        healthRoutes()
        authenticate("jwt-rs256") {
            syncWebSocketRoutes()
        }
    }
}
