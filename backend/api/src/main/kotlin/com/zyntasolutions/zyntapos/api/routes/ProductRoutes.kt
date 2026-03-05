package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.models.PagedResponse
import com.zyntasolutions.zyntapos.api.service.ProductService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.productRoutes() {
    val productService: ProductService by inject()

    route("/products") {
        get {
            val principal = call.principal<JWTPrincipal>()!!
            val storeId = principal.payload.getClaim("storeId").asString()
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
            val size = call.request.queryParameters["size"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50
            val updatedSince = call.request.queryParameters["updatedSince"]?.toLongOrNull()

            val result = productService.list(storeId, page, size, updatedSince)
            call.respond(HttpStatusCode.OK, result)
        }
    }
}
