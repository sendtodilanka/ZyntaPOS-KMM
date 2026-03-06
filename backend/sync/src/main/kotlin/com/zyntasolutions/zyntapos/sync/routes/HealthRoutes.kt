package com.zyntasolutions.zyntapos.sync.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
data class SyncHealthResponse(val status: String)

fun Route.healthRoutes() {
    get("/health") {
        call.respond(HttpStatusCode.OK, SyncHealthResponse(status = "ok"))
    }
    get("/ping") {
        call.respond(HttpStatusCode.OK, "ok")
    }
}
