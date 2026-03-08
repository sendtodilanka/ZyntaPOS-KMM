package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.service.AdminAuthService
import com.zyntasolutions.zyntapos.api.service.AdminMetricsService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.adminMetricsRoutes() {
    val metricsService: AdminMetricsService by inject()
    val authService: AdminAuthService by inject()

    route("/admin/metrics") {

        get("/dashboard") {
            resolveAdminUser(call, authService) ?: return@get
            val period = call.request.queryParameters["period"] ?: "today"
            call.respond(HttpStatusCode.OK, metricsService.getDashboardKPIs(period))
        }

        get("/sales") {
            resolveAdminUser(call, authService) ?: return@get
            val period  = call.request.queryParameters["period"] ?: "30d"
            val storeId = call.request.queryParameters["storeId"]
            call.respond(HttpStatusCode.OK, metricsService.getSalesChart(period, storeId))
        }

        get("/stores") {
            resolveAdminUser(call, authService) ?: return@get
            val period = call.request.queryParameters["period"] ?: "30d"
            call.respond(HttpStatusCode.OK, metricsService.getStoreComparison(period))
        }
    }

    route("/admin/reports") {

        get("/sales") {
            resolveAdminUser(call, authService) ?: return@get
            val from    = call.request.queryParameters["from"]
            val to      = call.request.queryParameters["to"]
            val storeId = call.request.queryParameters["storeId"]
            val page    = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val size    = call.request.queryParameters["size"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50
            call.respond(HttpStatusCode.OK, metricsService.getSalesReport(from, to, storeId, page, size))
        }

        get("/products") {
            resolveAdminUser(call, authService) ?: return@get
            val from    = call.request.queryParameters["from"]
            val to      = call.request.queryParameters["to"]
            val storeId = call.request.queryParameters["storeId"]
            val page    = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val size    = call.request.queryParameters["size"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50
            call.respond(HttpStatusCode.OK, metricsService.getProductPerformance(from, to, storeId, page, size))
        }
    }
}
