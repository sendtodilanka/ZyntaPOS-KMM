package com.zyntasolutions.zyntapos.license.routes

import com.zyntasolutions.zyntapos.license.db.LicenseDatabaseFactory
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

// S2-13: Added service identifier + version
@Serializable
data class HealthResponse(
    val status: String,
    val db: String,
    val service: String = "zyntapos-license",
    val version: String = "1.0.0",
)

fun Route.healthRoutes() {
    get("/health") {
        val dbOk = try { LicenseDatabaseFactory.ping(); "ok" } catch (_: Exception) { "degraded" }
        val statusCode = if (dbOk == "ok") HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
        call.respond(statusCode, HealthResponse(
            status = if (dbOk == "ok") "ok" else "degraded",
            db = dbOk
        ))
    }
    get("/ping") {
        call.respond(HttpStatusCode.OK, "ok")
    }
}
