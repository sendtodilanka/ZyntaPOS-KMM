package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.models.ErrorResponse
import com.zyntasolutions.zyntapos.api.models.PushRequest
import com.zyntasolutions.zyntapos.api.sync.DeltaEngine
import com.zyntasolutions.zyntapos.api.sync.SyncProcessor
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
    val syncProcessor: SyncProcessor by inject()
    val deltaEngine: DeltaEngine by inject()

    route("/sync") {

        // POST /v1/sync/push — accepts a batch of sync operations from a POS terminal
        post("/push") {
            val principal = call.principal<JWTPrincipal>()!!
            val storeId   = principal.payload.getClaim("storeId").asString()

            val request = call.receive<PushRequest>()

            if (!call.validateOr422 {
                requireNotBlank("deviceId", request.deviceId)
                requireMaxLength("deviceId", request.deviceId, 256)
                requireNotEmpty("operations", request.operations)
                requireMaxSize("operations", request.operations, 50)
            }) return@post

            val result = syncProcessor.processPush(storeId, request)
            call.respond(HttpStatusCode.OK, result)
        }

        // GET /v1/sync/pull?since=<cursor>&limit=50&deviceId=<device>
        get("/pull") {
            val principal = call.principal<JWTPrincipal>()!!
            val storeId   = principal.payload.getClaim("storeId").asString()
            val deviceId  = call.request.queryParameters["deviceId"] ?: "unknown"
            val since     = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
            val limit     = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50

            if (storeId.isBlank()) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse("MISSING_STORE", "storeId claim required"))
                return@get
            }

            val result = deltaEngine.computeDelta(
                storeId  = storeId,
                deviceId = deviceId,
                since    = since,
                limit    = limit,
            )
            call.respond(HttpStatusCode.OK, result)
        }
    }
}
