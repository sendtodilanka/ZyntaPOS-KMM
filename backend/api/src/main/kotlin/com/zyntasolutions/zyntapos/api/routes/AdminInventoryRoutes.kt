package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.auth.AdminPermissions
import com.zyntasolutions.zyntapos.api.repository.WarehouseStockRepository
import com.zyntasolutions.zyntapos.api.repository.WarehouseStockRow
import com.zyntasolutions.zyntapos.api.service.AdminAuthService
import com.zyntasolutions.zyntapos.common.ErrorResponse
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

fun Route.adminInventoryRoutes() {
    val repo: WarehouseStockRepository by inject()
    val authService: AdminAuthService by inject()

    route("/admin/inventory") {

        /**
         * GET /admin/inventory/global
         *
         * Cross-store/warehouse stock level view for the admin panel (C1.2).
         *
         * Query params:
         *   - productId (optional) — filter to a single product across all stores/warehouses
         *   - storeId   (optional) — scope to a single store's warehouses
         *   - page      (optional, default 0)    — zero-based page index
         *   - size      (optional, default 50)   — page size, clamped 1..200 (F-001)
         *
         * Response `total` and `lowStock` reflect the full (filtered) result set so
         * the UI can show "X low-stock out of N rows" even when the user is
         * paginating; `items` is the current page only.
         *
         * Roles: ADMIN, OPERATOR, FINANCE  (inventory:read)
         */
        get("/global") {
            val user = resolveAdminUser(call, authService) ?: return@get
            if (!AdminPermissions.check(user.role, "inventory:read")) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("FORBIDDEN", "inventory:read permission required")
                )
            }

            val productId = call.request.queryParameters["productId"]
            val storeId   = call.request.queryParameters["storeId"]
            val page      = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val size      = call.request.queryParameters["size"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50

            val rows = if (storeId != null) {
                repo.getByStore(storeId, productId)
            } else {
                repo.getGlobal(productId)
            }

            val total    = rows.size
            val lowStock = rows.count { it.isLowStock }
            // F-001: paginate here so the UI can render Previous/Next controls
            // without overflowing the browser when the filtered set is large.
            val pageItems = rows.drop(page * size).take(size)
            val totalPages = if (total == 0) 0 else (total + size - 1) / size

            call.respond(HttpStatusCode.OK, GlobalInventoryResponse(
                total      = total,
                lowStock   = lowStock,
                page       = page,
                size       = size,
                totalPages = totalPages,
                items      = pageItems.map { it.toDto() },
            ))
        }
    }
}

// ── Response DTOs ──────────────────────────────────────────────────────────────

@Serializable
data class GlobalInventoryResponse(
    val total: Int,
    val lowStock: Int,
    val page: Int,
    val size: Int,
    val totalPages: Int,
    val items: List<WarehouseStockDto>,
)

@Serializable
data class WarehouseStockDto(
    val id: String,
    val storeId: String,
    val warehouseId: String,
    val productId: String,
    val quantity: Double,
    val minQuantity: Double,
    val isLowStock: Boolean,
    val updatedAt: Long,
)

private fun WarehouseStockRow.toDto() = WarehouseStockDto(
    id          = id,
    storeId     = storeId,
    warehouseId = warehouseId,
    productId   = productId,
    quantity    = quantity.toDouble(),
    minQuantity = minQuantity.toDouble(),
    isLowStock  = isLowStock,
    updatedAt   = updatedAt,
)
