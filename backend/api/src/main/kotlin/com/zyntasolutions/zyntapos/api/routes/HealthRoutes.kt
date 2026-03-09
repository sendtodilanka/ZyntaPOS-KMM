package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.db.DatabaseFactory
import com.zyntasolutions.zyntapos.api.sync.SyncMetrics
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Serializable
data class HealthResponse(
    val status: String,
    val db: String
)

fun Route.healthRoutes() {
    val syncMetrics: SyncMetrics by inject()

    get("/health") {
        val dbOk = try {
            DatabaseFactory.ping()
            "ok"
        } catch (_: Exception) {
            "degraded"
        }
        val overallStatus = if (dbOk == "ok") "ok" else "degraded"
        val statusCode = if (dbOk == "ok") HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
        call.respond(
            statusCode,
            HealthResponse(
                status = overallStatus,
                db = dbOk
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
