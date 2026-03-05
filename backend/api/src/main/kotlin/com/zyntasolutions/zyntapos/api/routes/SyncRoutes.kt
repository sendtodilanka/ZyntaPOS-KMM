package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.models.PullResponse
import com.zyntasolutions.zyntapos.api.models.PushRequest
import com.zyntasolutions.zyntapos.api.models.PushResponse
import com.zyntasolutions.zyntapos.api.service.SyncService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.syncRoutes() {
    val syncService: SyncService by inject()

    route("/sync") {
        // Push local operations to server
        post("/push") {
            val principal = call.principal<JWTPrincipal>()!!
            val storeId = principal.payload.getClaim("storeId").asString()
            val request = call.receive<PushRequest>()

            require(request.operations.isNotEmpty()) { "Operations list cannot be empty" }
            require(request.operations.size <= 1000) { "Too many operations in single push (max 1000)" }

            val result = syncService.push(storeId, request)
            call.respond(HttpStatusCode.OK, result)
        }

        // Pull server-side operations since a given vector clock
        get("/pull") {
            val principal = call.principal<JWTPrincipal>()!!
            val storeId = principal.payload.getClaim("storeId").asString()
            val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 100

            val result = syncService.pull(storeId, since, limit)
            call.respond(HttpStatusCode.OK, result)
        }
    }
}
