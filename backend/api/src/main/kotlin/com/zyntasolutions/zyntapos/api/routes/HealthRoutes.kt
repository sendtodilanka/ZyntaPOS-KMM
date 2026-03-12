package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.db.DatabaseFactory
import com.zyntasolutions.zyntapos.api.sync.SyncMetrics
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

// S2-13: Health responses include dependency status indicators + version + uptime
@Serializable
data class HealthResponse(
    val status: String,
    val db: String,
    val redis: String = "unknown",
    val service: String = "zyntapos-api",
    val version: String = "1.0.0",
)

@Serializable
data class DeepHealthResponse(
    val status: String,
    val db: String,
    val redis: String,
    val service: String = "zyntapos-api",
    val version: String = "1.0.0",
    val uptimeMs: Long,
)

private val startTime = System.currentTimeMillis()

fun Route.healthRoutes() {
    val syncMetrics: SyncMetrics by inject()
    val redisConnection: StatefulRedisConnection<String, String>? by inject()

    get("/health") {
        val dbOk = try {
            DatabaseFactory.ping()
            "ok"
        } catch (_: Exception) {
            "degraded"
        }
        val redisOk = checkRedis(redisConnection)
        val overallStatus = if (dbOk == "ok" && redisOk == "ok") "ok" else "degraded"
        val statusCode = if (overallStatus == "ok") HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
        call.respond(
            statusCode,
            HealthResponse(
                status = overallStatus,
                db = dbOk,
                redis = redisOk,
            )
        )
    }

    // Deep health check — validates all downstream dependencies
    get("/health/deep") {
        val dbOk = try {
            DatabaseFactory.ping()
            "ok"
        } catch (_: Exception) {
            "degraded"
        }
        val redisOk = checkRedis(redisConnection)
        val overallStatus = if (dbOk == "ok" && redisOk == "ok") "ok" else "degraded"
        val statusCode = if (overallStatus == "ok") HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
        call.respond(
            statusCode,
            DeepHealthResponse(
                status = overallStatus,
                db = dbOk,
                redis = redisOk,
                uptimeMs = System.currentTimeMillis() - startTime,
            )
        )
    }

    // Sync-specific health metrics (queue depth, conflict rate, WS connections)
    get("/health/sync") {
        val metrics = syncMetrics.snapshot()
        call.respond(HttpStatusCode.OK, metrics)
    }

    // Lightweight liveness probe (no DB check) — used by load balancer
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
