package com.zyntasolutions.zyntapos.api.sync

import com.zyntasolutions.zyntapos.api.models.SyncOperation
import com.zyntasolutions.zyntapos.api.service.Products
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.ZoneOffset

// ── Normalized entity tables (V12 migration) ─────────────────────────────

object Categories : Table("categories") {
    val id          = text("id")
    val storeId     = text("store_id")
    val name        = text("name")
    val parentId    = text("parent_id").nullable()
    val sortOrder   = integer("sort_order")
    val imageUrl    = text("image_url").nullable()
    val isActive    = bool("is_active")
    val syncVersion = long("sync_version")
    val createdAt   = timestampWithTimeZone("created_at").nullable()
    val updatedAt   = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object Customers : Table("customers") {
    val id            = text("id")
    val storeId       = text("store_id")
    val name          = text("name")
    val email         = text("email").nullable()
    val phone         = text("phone").nullable()
    val address       = text("address").nullable()
    val notes         = text("notes").nullable()
    val loyaltyPoints = integer("loyalty_points")
    val isActive      = bool("is_active")
    val syncVersion   = long("sync_version")
    val createdAt     = timestampWithTimeZone("created_at").nullable()
    val updatedAt     = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object Suppliers : Table("suppliers") {
    val id          = text("id")
    val storeId     = text("store_id")
    val name        = text("name")
    val contactName = text("contact_name").nullable()
    val phone       = text("phone").nullable()
    val email       = text("email").nullable()
    val address     = text("address").nullable()
    val notes       = text("notes").nullable()
    val isActive    = bool("is_active")
    val syncVersion = long("sync_version")
    val createdAt   = timestampWithTimeZone("created_at").nullable()
    val updatedAt   = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object Orders : Table("orders") {
    val id            = text("id")
    val storeId       = text("store_id")
    val orderNumber   = text("order_number").nullable()
    val customerId    = text("customer_id").nullable()
    val cashierId     = text("cashier_id").nullable()
    val status        = text("status")
    val orderType     = text("order_type")
    val subtotal      = decimal("subtotal", 12, 4)
    val taxTotal      = decimal("tax_total", 12, 4)
    val discountTotal = decimal("discount_total", 12, 4)
    val grandTotal    = decimal("grand_total", 12, 4)
    val notes         = text("notes").nullable()
    val isActive      = bool("is_active")
    val syncVersion   = long("sync_version")
    val createdAt     = timestampWithTimeZone("created_at").nullable()
    val updatedAt     = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object OrderItems : Table("order_items") {
    val id          = text("id")
    val orderId     = text("order_id")
    val productId   = text("product_id")
    val productName = text("product_name")
    val quantity    = decimal("quantity", 12, 4)
    val unitPrice   = decimal("unit_price", 12, 4)
    val discount    = decimal("discount", 12, 4)
    val tax         = decimal("tax", 12, 4)
    val subtotal    = decimal("subtotal", 12, 4)
    val notes       = text("notes").nullable()
    val syncVersion = long("sync_version")
    val createdAt   = timestampWithTimeZone("created_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

/**
 * Applies accepted sync operations to the normalized entity tables
 * so that server-side queries see up-to-date data.
 *
 * Entity snapshots are maintained by the PostgreSQL trigger defined in V4
 * migration (trg_sync_op_snapshot), so this class handles normalized tables
 * that the API uses for direct queries (products, categories, customers,
 * suppliers, orders).
 *
 * [applyInTransaction] is called from within an existing [newSuspendedTransaction]
 * so that the insert + apply are atomic — a failure rolls back both.
 */
class EntityApplier {
    private val logger = LoggerFactory.getLogger(EntityApplier::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Apply a single operation inside an already-open transaction.
     * Must be called within a [newSuspendedTransaction] block.
     * Re-throws on failure so the surrounding transaction is rolled back.
     */
    fun applyInTransaction(storeId: String, op: SyncOperation) {
        try {
            when (op.entityType) {
                "PRODUCT"  -> applyProduct(storeId, op)
                "CATEGORY" -> applyCategory(storeId, op)
                "CUSTOMER" -> applyCustomer(storeId, op)
                "SUPPLIER" -> applySupplier(storeId, op)
                "ORDER"    -> applyOrder(storeId, op)
                "ORDER_ITEM" -> applyOrderItem(op)
                else -> { /* entity_snapshots trigger handles any remaining types */ }
            }
        } catch (e: Exception) {
            logger.warn("EntityApplier: failed to apply ${op.entityType} op ${op.id}: ${e.message}")
            throw e  // re-throw to roll back the surrounding transaction
        }
    }

    // ── Product ───────────────────────────────────────────────────────────

    private fun applyProduct(storeId: String, op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                val productName = payload.str("name") ?: return
                Products.upsert(Products.id) {
                    it[Products.id]          = op.entityId
                    it[Products.storeId]     = storeId
                    it[Products.name]        = productName
                    it[Products.sku]         = payload.str("sku")
                    it[Products.barcode]     = payload.str("barcode")
                    it[Products.price]       = payload.dbl("price").toBigDecimal()
                    it[Products.costPrice]   = payload.dbl("cost_price").toBigDecimal()
                    it[Products.stockQty]    = payload.dbl("stock_qty").toBigDecimal()
                    it[Products.categoryId]  = payload.str("category_id")
                    it[Products.unitId]      = payload.str("unit_id")
                    it[Products.taxGroupId]  = payload.str("tax_group_id")
                    it[Products.minStockQty] = payload.dbl("min_stock_qty").toBigDecimal()
                    it[Products.imageUrl]    = payload.str("image_url")
                    it[Products.description] = payload.str("description")
                    it[Products.isActive]    = payload.bool("is_active")
                    it[Products.syncVersion] = op.createdAt
                    it[Products.updatedAt]   = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> softDelete(Products, Products.id, Products.isActive, Products.syncVersion, Products.updatedAt, op)
        }
    }

    // ── Category ──────────────────────────────────────────────────────────

    private fun applyCategory(storeId: String, op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                val name = payload.str("name") ?: return
                Categories.upsert(Categories.id) {
                    it[Categories.id]          = op.entityId
                    it[Categories.storeId]     = storeId
                    it[Categories.name]        = name
                    it[Categories.parentId]    = payload.str("parent_id")
                    it[Categories.sortOrder]   = payload.int("sort_order")
                    it[Categories.imageUrl]    = payload.str("image_url")
                    it[Categories.isActive]    = payload.bool("is_active")
                    it[Categories.syncVersion] = op.createdAt
                    it[Categories.updatedAt]   = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> softDelete(Categories, Categories.id, Categories.isActive, Categories.syncVersion, Categories.updatedAt, op)
        }
    }

    // ── Customer ──────────────────────────────────────────────────────────

    private fun applyCustomer(storeId: String, op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                val name = payload.str("name") ?: return
                Customers.upsert(Customers.id) {
                    it[Customers.id]            = op.entityId
                    it[Customers.storeId]       = storeId
                    it[Customers.name]          = name
                    it[Customers.email]         = payload.str("email")
                    it[Customers.phone]         = payload.str("phone")
                    it[Customers.address]       = payload.str("address")
                    it[Customers.notes]         = payload.str("notes")
                    it[Customers.loyaltyPoints] = payload.int("loyalty_points")
                    it[Customers.isActive]      = payload.bool("is_active")
                    it[Customers.syncVersion]   = op.createdAt
                    it[Customers.updatedAt]     = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> softDelete(Customers, Customers.id, Customers.isActive, Customers.syncVersion, Customers.updatedAt, op)
        }
    }

    // ── Supplier ──────────────────────────────────────────────────────────

    private fun applySupplier(storeId: String, op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                val name = payload.str("name") ?: return
                Suppliers.upsert(Suppliers.id) {
                    it[Suppliers.id]          = op.entityId
                    it[Suppliers.storeId]     = storeId
                    it[Suppliers.name]        = name
                    it[Suppliers.contactName] = payload.str("contact_name")
                    it[Suppliers.phone]       = payload.str("phone")
                    it[Suppliers.email]       = payload.str("email")
                    it[Suppliers.address]     = payload.str("address")
                    it[Suppliers.notes]       = payload.str("notes")
                    it[Suppliers.isActive]    = payload.bool("is_active")
                    it[Suppliers.syncVersion] = op.createdAt
                    it[Suppliers.updatedAt]   = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> softDelete(Suppliers, Suppliers.id, Suppliers.isActive, Suppliers.syncVersion, Suppliers.updatedAt, op)
        }
    }

    // ── Order ─────────────────────────────────────────────────────────────

    private fun applyOrder(storeId: String, op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                Orders.upsert(Orders.id) {
                    it[Orders.id]            = op.entityId
                    it[Orders.storeId]       = storeId
                    it[Orders.orderNumber]   = payload.str("order_number")
                    it[Orders.customerId]    = payload.str("customer_id")
                    it[Orders.cashierId]     = payload.str("cashier_id")
                    it[Orders.status]        = payload.str("status") ?: "PENDING"
                    it[Orders.orderType]     = payload.str("order_type") ?: "DINE_IN"
                    it[Orders.subtotal]      = payload.dbl("subtotal").toBigDecimal()
                    it[Orders.taxTotal]      = payload.dbl("tax_total").toBigDecimal()
                    it[Orders.discountTotal] = payload.dbl("discount_total").toBigDecimal()
                    it[Orders.grandTotal]    = payload.dbl("grand_total").toBigDecimal()
                    it[Orders.notes]         = payload.str("notes")
                    it[Orders.isActive]      = payload.bool("is_active")
                    it[Orders.syncVersion]   = op.createdAt
                    it[Orders.updatedAt]     = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> softDelete(Orders, Orders.id, Orders.isActive, Orders.syncVersion, Orders.updatedAt, op)
        }
    }

    // ── Order Item ────────────────────────────────────────────────────────

    private fun applyOrderItem(op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                val orderId = payload.str("order_id") ?: return
                OrderItems.upsert(OrderItems.id) {
                    it[OrderItems.id]          = op.entityId
                    it[OrderItems.orderId]     = orderId
                    it[OrderItems.productId]   = payload.str("product_id") ?: ""
                    it[OrderItems.productName] = payload.str("product_name") ?: ""
                    it[OrderItems.quantity]     = payload.dbl("quantity").toBigDecimal()
                    it[OrderItems.unitPrice]   = payload.dbl("unit_price").toBigDecimal()
                    it[OrderItems.discount]    = payload.dbl("discount").toBigDecimal()
                    it[OrderItems.tax]         = payload.dbl("tax").toBigDecimal()
                    it[OrderItems.subtotal]    = payload.dbl("subtotal").toBigDecimal()
                    it[OrderItems.notes]       = payload.str("notes")
                    it[OrderItems.syncVersion] = op.createdAt
                }
            }
            "DELETE" -> {
                OrderItems.deleteWhere { OrderItems.id eq op.entityId }
            }
        }
    }

    // ── Shared helpers ────────────────────────────────────────────────────

    private fun parsePayload(op: SyncOperation): JsonObject? = runCatching {
        json.parseToJsonElement(op.payload).jsonObject
    }.getOrNull()

    private fun softDelete(
        table: Table,
        idCol: Column<String>,
        activeCol: Column<Boolean>,
        versionCol: Column<Long>,
        updatedCol: Column<OffsetDateTime>,
        op: SyncOperation,
    ) {
        table.update({ idCol eq op.entityId }) {
            it[activeCol]  = false
            it[versionCol] = op.createdAt
            it[updatedCol] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.let { if (it is JsonNull) null else it.jsonPrimitive.content }

    private fun JsonObject.dbl(key: String): Double =
        this[key]?.jsonPrimitive?.doubleOrNull ?: 0.0

    private fun JsonObject.int(key: String, default: Int = 0): Int =
        this[key]?.jsonPrimitive?.intOrNull ?: default

    private fun JsonObject.bool(key: String, default: Boolean = true): Boolean =
        this[key]?.jsonPrimitive?.booleanOrNull ?: default
}
