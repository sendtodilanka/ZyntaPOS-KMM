package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.models.*
import com.zyntasolutions.zyntapos.api.service.AdminAuthService
import com.zyntasolutions.zyntapos.api.service.AdminStoresService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.ktor.ext.inject
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun Route.adminHealthRoutes() {
    val storesService: AdminStoresService by inject()
    val authService: AdminAuthService by inject()

    route("/admin/health") {

        get("/system") {
            resolveAdminUser(call, authService) ?: return@get
            val checkedAt = OffsetDateTime.now(ZoneOffset.UTC).toInstant().toString()
            val services = checkAllServices()
            val overall = when {
                services.any { it.status == "unhealthy" } -> "unhealthy"
                services.any { it.status == "degraded"  } -> "degraded"
                else                                       -> "healthy"
            }
            call.respond(HttpStatusCode.OK, SystemHealth(overall, services, checkedAt))
        }

        get("/stores") {
            resolveAdminUser(call, authService) ?: return@get
            val summaries = storesService.getAllStoreHealthSummaries()
            call.respond(HttpStatusCode.OK, summaries)
        }

        get("/stores/{storeId}") {
            resolveAdminUser(call, authService) ?: return@get
            val storeId = call.parameters["storeId"] ?: return@get call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("MISSING_ID", "Store ID required")
            )
            val detail = storesService.getStoreHealthDetail(storeId)
                ?: return@get call.respond(
                    HttpStatusCode.NotFound, ErrorResponse("NOT_FOUND", "Store not found")
                )
            call.respond(HttpStatusCode.OK, detail)
        }
    }
}

private suspend fun checkAllServices(): List<ServiceHealth> {
    val client = HttpClient(CIO) { engine { requestTimeout = 3_000 } }
    val checkedAt = OffsetDateTime.now(ZoneOffset.UTC).toInstant().toString()
    val services = mutableListOf<ServiceHealth>()

    suspend fun probe(name: String, url: String): ServiceHealth {
        val start = System.currentTimeMillis()
        return try {
            val result = withTimeoutOrNull(3_000L) {
                val resp = client.get(url)
                resp.status.isSuccess()
            }
            val latency = System.currentTimeMillis() - start
            if (result == true) {
                ServiceHealth(name, "healthy", latency, 100.0, checkedAt)
            } else {
                ServiceHealth(name, "unhealthy", latency, 0.0, checkedAt)
            }
        } catch (e: Exception) {
            ServiceHealth(name, "unhealthy", System.currentTimeMillis() - start, 0.0, checkedAt,
                details = mapOf("error" to (e.message ?: "timeout")))
        }
    }

    services += probe("api",     "http://localhost:8080/health")
    services += probe("license", "http://license:8082/health")
    services += probe("sync",    "http://sync:8083/health")

    client.close()
    return services
}
