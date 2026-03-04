package com.zyntasolutions.zyntapos.sync.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
data class SyncHealthResponse(val status: String, val service: String, val version: String)

fun Route.healthRoutes() {
    get("/health") {
        call.respond(HttpStatusCode.OK, SyncHealthResponse(
            status = "ok",
            service = "zyntapos-sync",
            version = "1.0.0"
        ))
    }
    get("/ping") {
        call.respond(HttpStatusCode.OK, "ok")
    }
}
