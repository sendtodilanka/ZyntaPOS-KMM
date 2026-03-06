package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.models.PushRequest
import com.zyntasolutions.zyntapos.api.service.SyncService
import com.zyntasolutions.zyntapos.common.validation.validateOr422
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
        post("/push") {
            val principal = call.principal<JWTPrincipal>()!!
            val storeId = principal.payload.getClaim("storeId").asString()
            val request = call.receive<PushRequest>()

            if (!call.validateOr422 {
                requireNotBlank("deviceId", request.deviceId)
                requireMaxLength("deviceId", request.deviceId, 256)
                requireNotEmpty("operations", request.operations)
                requireMaxSize("operations", request.operations, 1000)
            }) return@post

            val result = syncService.push(storeId, request)
            call.respond(HttpStatusCode.OK, result)
        }

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
