package com.zyntasolutions.zyntapos.license.routes

import com.zyntasolutions.zyntapos.license.db.LicenseDatabaseFactory
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String, val service: String, val version: String, val db: String)

fun Route.healthRoutes() {
    get("/health") {
        val dbOk = try { LicenseDatabaseFactory.ping(); "ok" } catch (_: Exception) { "degraded" }
        val statusCode = if (dbOk == "ok") HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
        call.respond(statusCode, HealthResponse(
            status = if (dbOk == "ok") "ok" else "degraded",
            service = "zyntapos-license",
            version = "1.0.0",
            db = dbOk
        ))
    }
    get("/ping") {
        call.respond(HttpStatusCode.OK, "ok")
    }
}
