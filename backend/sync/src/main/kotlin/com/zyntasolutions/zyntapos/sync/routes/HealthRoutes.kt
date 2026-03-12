package com.zyntasolutions.zyntapos.sync.routes

import com.zyntasolutions.zyntapos.sync.hub.WebSocketHub
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

// S2-13: Added service identifier + version
@Serializable
data class SyncHealthResponse(
    val status: String,
    val redis: String = "unknown",
    val activeConnections: Int = 0,
    val activeStores: Int = 0,
    val service: String = "zyntapos-sync",
    val version: String = "1.0.0",
)

fun Route.healthRoutes() {
    val hub: WebSocketHub by inject()
    val redisConnection: StatefulRedisConnection<String, String>? by inject()

    get("/health") {
        val redisOk = checkRedis(redisConnection)
        val status = if (redisOk == "ok") "ok" else "degraded"
        val statusCode = if (status == "ok") HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
        call.respond(statusCode, SyncHealthResponse(
            status = status,
            redis = redisOk,
            activeConnections = hub.totalConnections(),
            activeStores = hub.activeStoreCount(),
        ))
    }
    get("/ping") {
        call.respond(HttpStatusCode.OK, "ok")
    }
}

private fun checkRedis(connection: StatefulRedisConnection<String, String>?): String {
    if (connection == null) return "not_configured"
    return try {
        val pong = connection.sync().ping()
        if (pong == "PONG") "ok" else "degraded"
    } catch (_: Exception) {
        "degraded"
    }
}
