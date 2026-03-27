package com.zyntasolutions.zyntapos.api.sync

import com.zyntasolutions.zyntapos.api.db.PricingRules
import com.zyntasolutions.zyntapos.api.db.RegionalTaxOverrides
import com.zyntasolutions.zyntapos.api.db.UserStoreAccessTable
import com.zyntasolutions.zyntapos.api.db.EmployeeStoreAssignments
import com.zyntasolutions.zyntapos.api.db.ReplenishmentRules
import com.zyntasolutions.zyntapos.api.db.WarehouseStock
import com.zyntasolutions.zyntapos.api.models.SyncOperation
import com.zyntasolutions.zyntapos.api.service.MasterProducts
import com.zyntasolutions.zyntapos.api.service.PurchaseOrders
import com.zyntasolutions.zyntapos.api.service.Products
import com.zyntasolutions.zyntapos.api.service.StockTransfers
import com.zyntasolutions.zyntapos.api.service.StoreProducts
import com.zyntasolutions.zyntapos.common.TimestampUtils
import com.zyntasolutions.zyntapos.common.bool
import com.zyntasolutions.zyntapos.common.dbl
import com.zyntasolutions.zyntapos.common.int
import com.zyntasolutions.zyntapos.common.lng
import com.zyntasolutions.zyntapos.common.str
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
    val storeId       = text("store_id").nullable()
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

object AuditEntries : Table("audit_entries") {
    val id            = text("id")
    val storeId       = text("store_id")
    val deviceId      = text("device_id")
    val eventType     = text("event_type")
    val userId        = text("user_id")
    val userName      = text("user_name").nullable()
    val userRole      = text("user_role").nullable()
    val entityType    = text("entity_type").nullable()
    val entityId      = text("entity_id").nullable()
    val details       = text("details")
    val previousValue = text("previous_value").nullable()
    val newValue      = text("new_value").nullable()
    val success       = bool("success")
    val ipAddress     = text("ip_address").nullable()
    val hash          = text("hash")
    val previousHash  = text("previous_hash")
    val timestamp     = long("timestamp")
    val syncVersion   = long("sync_version")
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

// ── Extended normalized entity tables (V23 migration) ────────────────────

object StockAdjustments : Table("stock_adjustments") {
    val id          = text("id")
    val storeId     = text("store_id")
    val productId   = text("product_id")
    val type        = text("type")
    val quantity    = decimal("quantity", 12, 4)
    val reason      = text("reason").nullable()
    val adjustedBy  = text("adjusted_by").nullable()
    val isActive    = bool("is_active")
    val syncVersion = long("sync_version")
    val createdAt   = timestampWithTimeZone("created_at").nullable()
    val updatedAt   = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object CashRegisters : Table("cash_registers") {
    val id               = text("id")
    val storeId          = text("store_id")
    val name             = text("name")
    val currentSessionId = text("current_session_id").nullable()
    val isActive         = bool("is_active")
    val syncVersion      = long("sync_version")
    val createdAt        = timestampWithTimeZone("created_at").nullable()
    val updatedAt        = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object RegisterSessions : Table("register_sessions") {
    val id              = text("id")
    val storeId         = text("store_id")
    val registerId      = text("register_id")
    val openedBy        = text("opened_by")
    val closedBy        = text("closed_by").nullable()
    val openingBalance  = decimal("opening_balance", 12, 4)
    val closingBalance  = decimal("closing_balance", 12, 4).nullable()
    val expectedBalance = decimal("expected_balance", 12, 4)
    val actualBalance   = decimal("actual_balance", 12, 4).nullable()
    val status          = text("status")
    val isActive        = bool("is_active")
    val syncVersion     = long("sync_version")
    val openedAt        = timestampWithTimeZone("opened_at").nullable()
    val closedAt        = timestampWithTimeZone("closed_at").nullable()
    val createdAt       = timestampWithTimeZone("created_at").nullable()
    val updatedAt       = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object CashMovements : Table("cash_movements") {
    val id          = text("id")
    val storeId     = text("store_id")
    val sessionId   = text("session_id")
    val type        = text("type")
    val amount      = decimal("amount", 12, 4)
    val reason      = text("reason").nullable()
    val recordedBy  = text("recorded_by").nullable()
    val isActive    = bool("is_active")
    val syncVersion = long("sync_version")
    val createdAt   = timestampWithTimeZone("created_at").nullable()
    val updatedAt   = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object TaxGroups : Table("tax_groups") {
    val id          = text("id")
    val storeId     = text("store_id")
    val name        = text("name")
    val rate        = decimal("rate", 6, 3)
    val isInclusive = bool("is_inclusive")
    val isActive    = bool("is_active")
    val syncVersion = long("sync_version")
    val createdAt   = timestampWithTimeZone("created_at").nullable()
    val updatedAt   = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object UnitsOfMeasure : Table("units_of_measure") {
    val id             = text("id")
    val storeId        = text("store_id")
    val name           = text("name")
    val abbreviation   = text("abbreviation").nullable()
    val isBaseUnit     = bool("is_base_unit")
    val conversionRate = decimal("conversion_rate", 12, 6)
    val isActive       = bool("is_active")
    val syncVersion    = long("sync_version")
    val createdAt      = timestampWithTimeZone("created_at").nullable()
    val updatedAt      = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object PaymentSplits : Table("payment_splits") {
    val id          = text("id")
    val storeId     = text("store_id")
    val orderId     = text("order_id")
    val method      = text("method")
    val amount      = decimal("amount", 12, 4)
    val reference   = text("reference").nullable()
    val isActive    = bool("is_active")
    val syncVersion = long("sync_version")
    val createdAt   = timestampWithTimeZone("created_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object Coupons : Table("coupons") {
    val id               = text("id")
    val storeId          = text("store_id")
    val code             = text("code")
    val name             = text("name")
    val discountType     = text("discount_type")
    val discountValue    = decimal("discount_value", 12, 4)
    val minimumPurchase  = decimal("minimum_purchase", 12, 4)
    val maximumDiscount  = decimal("maximum_discount", 12, 4).nullable()
    val usageLimit       = integer("usage_limit").nullable()
    val usageCount       = integer("usage_count")
    val perCustomerLimit = integer("per_customer_limit").nullable()
    val scope            = text("scope")
    val scopeIds         = text("scope_ids").nullable()
    val validFrom        = long("valid_from").nullable()
    val validTo          = long("valid_to").nullable()
    val isActive         = bool("is_active")
    val syncVersion      = long("sync_version")
    val createdAt        = timestampWithTimeZone("created_at").nullable()
    val updatedAt        = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object Expenses : Table("expenses") {
    val id          = text("id")
    val storeId     = text("store_id")
    val category    = text("category").nullable()
    val amount      = decimal("amount", 12, 4)
    val description = text("description").nullable()
    val recordedBy  = text("recorded_by").nullable()
    val isActive    = bool("is_active")
    val syncVersion = long("sync_version")
    val createdAt   = timestampWithTimeZone("created_at").nullable()
    val updatedAt   = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object Settings : Table("settings") {
    val id          = text("id")
    val storeId     = text("store_id")
    val key         = text("key")
    val value       = text("value").nullable()
    val isActive    = bool("is_active")
    val syncVersion = long("sync_version")
    val createdAt   = timestampWithTimeZone("created_at").nullable()
    val updatedAt   = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

// ── V24 normalized entity tables ──────────────────────────────────────────

object Employees : Table("employees") {
    val id          = text("id")
    val storeId     = text("store_id")
    val name        = text("name")
    val email       = text("email").nullable()
    val phone       = text("phone").nullable()
    val role        = text("role")
    val department  = text("department").nullable()
    val hireDate    = long("hire_date").nullable()
    val hourlyRate  = decimal("hourly_rate", 12, 4)
    val notes       = text("notes").nullable()
    val isActive    = bool("is_active")
    val syncVersion = long("sync_version")
    val createdAt   = timestampWithTimeZone("created_at").nullable()
    val updatedAt   = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object ExpenseCategories : Table("expense_categories") {
    val id          = text("id")
    val storeId     = text("store_id")
    val name        = text("name")
    val description = text("description").nullable()
    val parentId    = text("parent_id").nullable()
    val sortOrder   = integer("sort_order")
    val isActive    = bool("is_active")
    val syncVersion = long("sync_version")
    val createdAt   = timestampWithTimeZone("created_at").nullable()
    val updatedAt   = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object CouponUsages : Table("coupon_usages") {
    val id              = text("id")
    val storeId         = text("store_id")
    val couponId        = text("coupon_id")
    val orderId         = text("order_id")
    val customerId      = text("customer_id").nullable()
    val discountAmount  = decimal("discount_amount", 12, 4)
    val redeemedBy      = text("redeemed_by").nullable()
    val isActive        = bool("is_active")
    val syncVersion     = long("sync_version")
    val createdAt       = timestampWithTimeZone("created_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object Promotions : Table("promotions") {
    val id              = text("id")
    val storeId         = text("store_id")
    val name            = text("name")
    val description     = text("description").nullable()
    val type            = text("type")
    val value           = decimal("value", 12, 4)
    val minimumPurchase = decimal("minimum_purchase", 12, 4)
    val scope           = text("scope")
    val scopeIds        = text("scope_ids").nullable()
    val validFrom       = long("valid_from").nullable()
    val validTo         = long("valid_to").nullable()
    val priority        = integer("priority")
    val isStackable     = bool("is_stackable")
    val isActive        = bool("is_active")
    val syncVersion     = long("sync_version")
    // V37: typed config JSON + store_ids for KMM-compatible GET /v1/promotions
    val config          = text("config")
    val storeIds        = text("store_ids")
    val createdAt       = timestampWithTimeZone("created_at").nullable()
    val updatedAt       = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object CustomerGroups : Table("customer_groups") {
    val id           = text("id")
    val storeId      = text("store_id")
    val name         = text("name")
    val description  = text("description").nullable()
    val discountRate = decimal("discount_rate", 6, 3)
    val isActive     = bool("is_active")
    val syncVersion  = long("sync_version")
    val createdAt    = timestampWithTimeZone("created_at").nullable()
    val updatedAt    = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

/**
 * Applies accepted sync operations to the normalized entity tables
 * so that server-side queries see up-to-date data.
 *
 * Entity snapshots are maintained by the PostgreSQL trigger defined in V4
 * migration (trg_sync_op_snapshot), so this class handles normalized tables
 * that the API uses for direct queries (products, categories, customers,
 * suppliers, orders, stock adjustments, registers, etc.).
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
        // Normalize entity type to uppercase — KMM client sends lowercase
        val type = op.entityType.uppercase()
        try {
            when (type) {
                "PRODUCT"           -> applyProduct(storeId, op)
                "CATEGORY"          -> applyCategory(storeId, op)
                "CUSTOMER"          -> applyCustomer(storeId, op)
                "SUPPLIER"          -> applySupplier(storeId, op)
                "ORDER"             -> applyOrder(storeId, op)
                "ORDER_ITEM"        -> applyOrderItem(op)
                "AUDIT_ENTRY"       -> applyAuditEntry(storeId, op)
                "STOCK_ADJUSTMENT"  -> applyStockAdjustment(storeId, op)
                "CASH_REGISTER"     -> applyCashRegister(storeId, op)
                "REGISTER_SESSION"  -> applyRegisterSession(storeId, op)
                "CASH_MOVEMENT"     -> applyCashMovement(storeId, op)
                "TAX_GROUP"         -> applyTaxGroup(storeId, op)
                "UNIT_OF_MEASURE"   -> applyUnitOfMeasure(storeId, op)
                "PAYMENT_SPLIT"     -> applyPaymentSplit(storeId, op)
                "COUPON"            -> applyCoupon(storeId, op)
                "EXPENSE"           -> applyExpense(storeId, op)
                "SETTINGS"          -> applySettings(storeId, op)
                "EMPLOYEE"          -> applyEmployee(storeId, op)
                "EXPENSE_CATEGORY"  -> applyExpenseCategory(storeId, op)
                "COUPON_USAGE"      -> applyCouponUsage(storeId, op)
                "PROMOTION"         -> applyPromotion(storeId, op)
                "CUSTOMER_GROUP"    -> applyCustomerGroup(storeId, op)
                "MASTER_PRODUCT"    -> applyMasterProduct(op)
                "STORE_PRODUCT"     -> applyStoreProduct(storeId, op)
                "WAREHOUSE_STOCK"   -> applyWarehouseStock(storeId, op)
                "REPLENISHMENT_RULE" -> applyReplenishmentRule(op)
                "STOCK_TRANSFER"    -> applyStockTransfer(storeId, op)
                "PURCHASE_ORDER"    -> applyPurchaseOrder(storeId, op)
                "PRICING_RULE"      -> applyPricingRule(storeId, op)
                "REGIONAL_TAX_OVERRIDE" -> applyRegionalTaxOverride(storeId, op)
                "USER_STORE_ACCESS" -> applyUserStoreAccess(op)
                "EMPLOYEE_STORE_ASSIGNMENT" -> applyEmployeeStoreAssignment(op)
                "TRANSIT_EVENT"     -> { /* append-only — stored via entity_snapshots; no normalized table */ }
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

    // ── Master Product (global catalog — no storeId) ──────────────────────

    private fun applyMasterProduct(op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                val productName = payload.str("name") ?: return
                MasterProducts.upsert(MasterProducts.id) {
                    it[MasterProducts.id]          = op.entityId
                    it[MasterProducts.name]        = productName
                    it[MasterProducts.sku]         = payload.str("sku")
                    it[MasterProducts.barcode]     = payload.str("barcode")
                    it[MasterProducts.basePrice]   = payload.dbl("base_price").toBigDecimal()
                    it[MasterProducts.costPrice]   = payload.dbl("cost_price").toBigDecimal()
                    it[MasterProducts.categoryId]  = payload.str("category_id")
                    it[MasterProducts.unitId]      = payload.str("unit_id")
                    it[MasterProducts.taxGroupId]  = payload.str("tax_group_id")
                    it[MasterProducts.imageUrl]    = payload.str("image_url")
                    it[MasterProducts.description] = payload.str("description")
                    it[MasterProducts.isActive]    = payload.bool("is_active")
                    it[MasterProducts.syncVersion] = op.createdAt
                    it[MasterProducts.updatedAt]   = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> softDelete(MasterProducts, MasterProducts.id, MasterProducts.isActive, MasterProducts.syncVersion, MasterProducts.updatedAt, op)
        }
    }

    // ── Store Product (per-store overrides) ─────────────────────────────────

    private fun applyStoreProduct(storeId: String, op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                val masterProductId = payload.str("master_product_id") ?: return
                StoreProducts.upsert(StoreProducts.masterProductId, StoreProducts.storeId) {
                    it[StoreProducts.id]              = op.entityId
                    it[StoreProducts.masterProductId] = masterProductId
                    it[StoreProducts.storeId]         = storeId
                    it[StoreProducts.localPrice]      = payload.str("local_price")?.toDoubleOrNull()?.toBigDecimal()
                    it[StoreProducts.localCostPrice]  = payload.str("local_cost_price")?.toDoubleOrNull()?.toBigDecimal()
                    it[StoreProducts.localStockQty]   = payload.dbl("local_stock_qty").toInt()
                    it[StoreProducts.minStockQty]     = payload.dbl("min_stock_qty").toInt()
                    it[StoreProducts.isActive]        = payload.bool("is_active")
                    it[StoreProducts.syncVersion]     = op.createdAt
                    it[StoreProducts.updatedAt]       = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> {
                StoreProducts.deleteWhere {
                    (StoreProducts.id eq op.entityId)
                }
            }
        }
    }

    // ── Category ──────────────────────────────────────────────────────────

    private fun applyCategory(storeId: String, op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                val name = payload.str("name") ?: return
                val parentId = payload.str("parent_id")

                // Circular parent reference detection — walk the ancestor chain
                if (parentId != null) {
                    if (parentId == op.entityId) {
                        logger.warn("Category ${op.entityId}: self-referencing parentId rejected")
                        return
                    }
                    if (detectCircularParent(storeId, op.entityId, parentId)) {
                        logger.warn("Category ${op.entityId}: circular parent reference detected via $parentId — rejected")
                        return
                    }
                }

                Categories.upsert(Categories.id) {
                    it[Categories.id]          = op.entityId
                    it[Categories.storeId]     = storeId
                    it[Categories.name]        = name
                    it[Categories.parentId]    = parentId
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

    /**
     * Walks the parent chain from [parentId] up to [MAX_CATEGORY_DEPTH] levels.
     * Returns true if [categoryId] is found in the ancestor chain (circular reference).
     */
    private fun detectCircularParent(storeId: String, categoryId: String, parentId: String): Boolean {
        var currentId: String? = parentId
        var depth = 0
        while (currentId != null && depth < MAX_CATEGORY_DEPTH) {
            if (currentId == categoryId) return true
            val parent = Categories.selectAll().where {
                (Categories.id eq currentId!!) and (Categories.storeId eq storeId)
            }.singleOrNull() ?: break
            currentId = parent[Categories.parentId]
            depth++
        }
        return false
    }

    companion object {
        /** Maximum category hierarchy depth to prevent infinite loops on corrupted data. */
        private const val MAX_CATEGORY_DEPTH = 10
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

    // ── Audit Entry ───────────────────────────────────────────────────────

    private fun applyAuditEntry(storeId: String, op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        // Audit entries are append-only — INSERT only, never UPDATE or DELETE
        when (op.operation) {
            "INSERT", "CREATE" -> {
                AuditEntries.insertIgnore {
                    it[AuditEntries.id]            = op.entityId
                    it[AuditEntries.storeId]       = storeId
                    it[AuditEntries.deviceId]      = payload.str("device_id") ?: ""
                    it[AuditEntries.eventType]     = payload.str("event_type") ?: "UNKNOWN"
                    it[AuditEntries.userId]        = payload.str("user_id") ?: ""
                    it[AuditEntries.userName]      = payload.str("user_name")
                    it[AuditEntries.userRole]      = payload.str("user_role")
                    it[AuditEntries.entityType]    = payload.str("entity_type")
                    it[AuditEntries.entityId]      = payload.str("entity_id")
                    it[AuditEntries.details]       = payload.str("details") ?: "{}"
                    it[AuditEntries.previousValue] = payload.str("previous_value")
                    it[AuditEntries.newValue]      = payload.str("new_value")
                    it[AuditEntries.success]       = payload.bool("success")
                    it[AuditEntries.ipAddress]     = payload.str("ip_address")
                    it[AuditEntries.hash]          = payload.str("hash") ?: ""
                    it[AuditEntries.previousHash]  = payload.str("previous_hash") ?: ""
                    it[AuditEntries.timestamp]     = payload.str("timestamp")?.toLongOrNull() ?: op.createdAt
                    it[AuditEntries.syncVersion]   = op.createdAt
                }
            }
            else -> {
                logger.debug("Ignoring ${op.operation} on AUDIT_ENTRY — audit entries are append-only")
            }
        }
    }

    // ── Stock Adjustment (with stock_qty side-effect on products) ─────────

    private fun applyStockAdjustment(storeId: String, op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE" -> {
                val productId = payload.str("product_id") ?: return
                val type = payload.str("type") ?: return
                val qty = payload.dbl("quantity").toBigDecimal()

                StockAdjustments.insertIgnore {
                    it[StockAdjustments.id]          = op.entityId
                    it[StockAdjustments.storeId]     = storeId
                    it[StockAdjustments.productId]   = productId
                    it[StockAdjustments.type]        = type
                    it[StockAdjustments.quantity]    = qty
                    it[StockAdjustments.reason]      = payload.str("reason")
                    it[StockAdjustments.adjustedBy]  = payload.str("adjusted_by")
                    it[StockAdjustments.isActive]    = payload.bool("is_active")
                    it[StockAdjustments.syncVersion] = op.createdAt
                    it[StockAdjustments.updatedAt]   = OffsetDateTime.now(ZoneOffset.UTC)
                }

                // Side-effect: update product stock quantity
                val currentQty = Products.selectAll()
                    .where { Products.id eq productId }
                    .firstOrNull()
                    ?.get(Products.stockQty)

                if (currentQty != null) {
                    val newQty = when (type) {
                        "INCREASE" -> currentQty + qty
                        "DECREASE", "TRANSFER" -> currentQty - qty
                        else -> currentQty
                    }
                    Products.update({ Products.id eq productId }) {
                        it[Products.stockQty] = newQty
                        it[Products.updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
                    }
                }
            }
            "UPDATE" -> {
                // Stock adjustments are generally immutable; allow metadata updates only
                StockAdjustments.upsert(StockAdjustments.id) {
                    it[StockAdjustments.id]          = op.entityId
                    it[StockAdjustments.storeId]     = storeId
                    it[StockAdjustments.productId]   = payload.str("product_id") ?: ""
                    it[StockAdjustments.type]        = payload.str("type") ?: "INCREASE"
                    it[StockAdjustments.quantity]    = payload.dbl("quantity").toBigDecimal()
                    it[StockAdjustments.reason]      = payload.str("reason")
                    it[StockAdjustments.adjustedBy]  = payload.str("adjusted_by")
                    it[StockAdjustments.isActive]    = payload.bool("is_active")
                    it[StockAdjustments.syncVersion] = op.createdAt
                    it[StockAdjustments.updatedAt]   = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> softDelete(StockAdjustments, StockAdjustments.id, StockAdjustments.isActive, StockAdjustments.syncVersion, StockAdjustments.updatedAt, op)
        }
    }

    // ── Cash Register ────────────────────────────────────────────────────

    private fun applyCashRegister(storeId: String, op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                val name = payload.str("name") ?: return
                CashRegisters.upsert(CashRegisters.id) {
                    it[CashRegisters.id]               = op.entityId
                    it[CashRegisters.storeId]          = storeId
                    it[CashRegisters.name]             = name
                    it[CashRegisters.currentSessionId] = payload.str("current_session_id")
                    it[CashRegisters.isActive]         = payload.bool("is_active")
                    it[CashRegisters.syncVersion]      = op.createdAt
                    it[CashRegisters.updatedAt]        = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> softDelete(CashRegisters, CashRegisters.id, CashRegisters.isActive, CashRegisters.syncVersion, CashRegisters.updatedAt, op)
        }
    }

    // ── Register Session ─────────────────────────────────────────────────

    private fun applyRegisterSession(storeId: String, op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                val registerId = payload.str("register_id") ?: return
                val openedBy = payload.str("opened_by") ?: return
                RegisterSessions.upsert(RegisterSessions.id) {
                    it[RegisterSessions.id]              = op.entityId
                    it[RegisterSessions.storeId]         = storeId
                    it[RegisterSessions.registerId]      = registerId
                    it[RegisterSessions.openedBy]        = openedBy
                    it[RegisterSessions.closedBy]        = payload.str("closed_by")
                    it[RegisterSessions.openingBalance]  = payload.dbl("opening_balance").toBigDecimal()
                    it[RegisterSessions.closingBalance]  = payload.str("closing_balance")?.toBigDecimalOrNull()
                    it[RegisterSessions.expectedBalance] = payload.dbl("expected_balance").toBigDecimal()
                    it[RegisterSessions.actualBalance]   = payload.str("actual_balance")?.toBigDecimalOrNull()
                    it[RegisterSessions.status]          = payload.str("status") ?: "OPEN"
                    it[RegisterSessions.isActive]        = payload.bool("is_active")
                    it[RegisterSessions.syncVersion]     = op.createdAt
                    it[RegisterSessions.updatedAt]       = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> softDelete(RegisterSessions, RegisterSessions.id, RegisterSessions.isActive, RegisterSessions.syncVersion, RegisterSessions.updatedAt, op)
        }
    }

    // ── Cash Movement ────────────────────────────────────────────────────

    private fun applyCashMovement(storeId: String, op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                val sessionId = payload.str("session_id") ?: return
                CashMovements.upsert(CashMovements.id) {
                    it[CashMovements.id]          = op.entityId
                    it[CashMovements.storeId]     = storeId
                    it[CashMovements.sessionId]   = sessionId
                    it[CashMovements.type]        = payload.str("type") ?: "IN"
                    it[CashMovements.amount]      = payload.dbl("amount").toBigDecimal()
                    it[CashMovements.reason]      = payload.str("reason")
                    it[CashMovements.recordedBy]  = payload.str("recorded_by")
                    it[CashMovements.isActive]    = payload.bool("is_active")
                    it[CashMovements.syncVersion] = op.createdAt
                    it[CashMovements.updatedAt]   = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> softDelete(CashMovements, CashMovements.id, CashMovements.isActive, CashMovements.syncVersion, CashMovements.updatedAt, op)
        }
    }

    // ── Tax Group ────────────────────────────────────────────────────────

    private fun applyTaxGroup(storeId: String, op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                val name = payload.str("name") ?: return
                TaxGroups.upsert(TaxGroups.id) {
                    it[TaxGroups.id]          = op.entityId
                    it[TaxGroups.storeId]     = storeId
                    it[TaxGroups.name]        = name
                    it[TaxGroups.rate]        = payload.dbl("rate").toBigDecimal()
                    it[TaxGroups.isInclusive] = payload.bool("is_inclusive", default = false)
                    it[TaxGroups.isActive]    = payload.bool("is_active")
                    it[TaxGroups.syncVersion] = op.createdAt
                    it[TaxGroups.updatedAt]   = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> softDelete(TaxGroups, TaxGroups.id, TaxGroups.isActive, TaxGroups.syncVersion, TaxGroups.updatedAt, op)
        }
    }

    // ── Unit of Measure ──────────────────────────────────────────────────

    private fun applyUnitOfMeasure(storeId: String, op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                val name = payload.str("name") ?: return
                UnitsOfMeasure.upsert(UnitsOfMeasure.id) {
                    it[UnitsOfMeasure.id]             = op.entityId
                    it[UnitsOfMeasure.storeId]        = storeId
                    it[UnitsOfMeasure.name]           = name
                    it[UnitsOfMeasure.abbreviation]   = payload.str("abbreviation")
                    it[UnitsOfMeasure.isBaseUnit]     = payload.bool("is_base_unit", default = false)
                    it[UnitsOfMeasure.conversionRate] = payload.dbl("conversion_rate").let { if (it == 0.0) 1.0 else it }.toBigDecimal()
                    it[UnitsOfMeasure.isActive]       = payload.bool("is_active")
                    it[UnitsOfMeasure.syncVersion]    = op.createdAt
                    it[UnitsOfMeasure.updatedAt]      = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> softDelete(UnitsOfMeasure, UnitsOfMeasure.id, UnitsOfMeasure.isActive, UnitsOfMeasure.syncVersion, UnitsOfMeasure.updatedAt, op)
        }
    }

    // ── Payment Split ────────────────────────────────────────────────────

    private fun applyPaymentSplit(storeId: String, op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                val orderId = payload.str("order_id") ?: return
                PaymentSplits.upsert(PaymentSplits.id) {
                    it[PaymentSplits.id]          = op.entityId
                    it[PaymentSplits.storeId]     = storeId
                    it[PaymentSplits.orderId]     = orderId
                    it[PaymentSplits.method]      = payload.str("method") ?: "CASH"
                    it[PaymentSplits.amount]      = payload.dbl("amount").toBigDecimal()
                    it[PaymentSplits.reference]   = payload.str("reference")
                    it[PaymentSplits.isActive]    = payload.bool("is_active")
                    it[PaymentSplits.syncVersion] = op.createdAt
                }
            }
            "DELETE" -> {
                PaymentSplits.deleteWhere { PaymentSplits.id eq op.entityId }
            }
        }
    }

    // ── Coupon ───────────────────────────────────────────────────────────

    private fun applyCoupon(storeId: String, op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                val code = payload.str("code") ?: return
                val name = payload.str("name") ?: return
                Coupons.upsert(Coupons.id) {
                    it[Coupons.id]               = op.entityId
                    it[Coupons.storeId]          = storeId
                    it[Coupons.code]             = code
                    it[Coupons.name]             = name
                    it[Coupons.discountType]     = payload.str("discount_type") ?: "PERCENTAGE"
                    it[Coupons.discountValue]    = payload.dbl("discount_value").toBigDecimal()
                    it[Coupons.minimumPurchase]  = payload.dbl("minimum_purchase").toBigDecimal()
                    it[Coupons.maximumDiscount]  = payload.str("maximum_discount")?.toBigDecimalOrNull()
                    it[Coupons.usageLimit]       = payload.str("usage_limit")?.toIntOrNull()
                    it[Coupons.usageCount]       = payload.int("usage_count")
                    it[Coupons.perCustomerLimit] = payload.str("per_customer_limit")?.toIntOrNull()
                    it[Coupons.scope]            = payload.str("scope") ?: "CART"
                    it[Coupons.scopeIds]         = payload.str("scope_ids")
                    it[Coupons.validFrom]        = payload.str("valid_from")?.toLongOrNull()
                    it[Coupons.validTo]          = payload.str("valid_to")?.toLongOrNull()
                    it[Coupons.isActive]         = payload.bool("is_active")
                    it[Coupons.syncVersion]      = op.createdAt
                    it[Coupons.updatedAt]        = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> softDelete(Coupons, Coupons.id, Coupons.isActive, Coupons.syncVersion, Coupons.updatedAt, op)
        }
    }

    // ── Expense ──────────────────────────────────────────────────────────

    private fun applyExpense(storeId: String, op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                Expenses.upsert(Expenses.id) {
                    it[Expenses.id]          = op.entityId
                    it[Expenses.storeId]     = storeId
                    it[Expenses.category]    = payload.str("category")
                    it[Expenses.amount]      = payload.dbl("amount").toBigDecimal()
                    it[Expenses.description] = payload.str("description")
                    it[Expenses.recordedBy]  = payload.str("recorded_by")
                    it[Expenses.isActive]    = payload.bool("is_active")
                    it[Expenses.syncVersion] = op.createdAt
                    it[Expenses.updatedAt]   = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> softDelete(Expenses, Expenses.id, Expenses.isActive, Expenses.syncVersion, Expenses.updatedAt, op)
        }
    }

    // ── Settings (key-value per store) ───────────────────────────────────

    private fun applySettings(storeId: String, op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                val key = payload.str("key") ?: return
                Settings.upsert(Settings.id) {
                    it[Settings.id]          = op.entityId
                    it[Settings.storeId]     = storeId
                    it[Settings.key]         = key
                    it[Settings.value]       = payload.str("value")
                    it[Settings.isActive]    = payload.bool("is_active")
                    it[Settings.syncVersion] = op.createdAt
                    it[Settings.updatedAt]   = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> softDelete(Settings, Settings.id, Settings.isActive, Settings.syncVersion, Settings.updatedAt, op)
        }
    }

    // ── Employee ──────────────────────────────────────────────────────────

    private fun applyEmployee(storeId: String, op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                val name = payload.str("name") ?: return
                Employees.upsert(Employees.id) {
                    it[Employees.id]          = op.entityId
                    it[Employees.storeId]     = storeId
                    it[Employees.name]        = name
                    it[Employees.email]       = payload.str("email")
                    it[Employees.phone]       = payload.str("phone")
                    it[Employees.role]        = payload.str("role") ?: "STAFF"
                    it[Employees.department]  = payload.str("department")
                    it[Employees.hireDate]    = payload.str("hire_date")?.toLongOrNull()
                    it[Employees.hourlyRate]  = payload.dbl("hourly_rate").toBigDecimal()
                    it[Employees.notes]       = payload.str("notes")
                    it[Employees.isActive]    = payload.bool("is_active")
                    it[Employees.syncVersion] = op.createdAt
                    it[Employees.updatedAt]   = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> softDelete(Employees, Employees.id, Employees.isActive, Employees.syncVersion, Employees.updatedAt, op)
        }
    }

    // ── Expense Category ──────────────────────────────────────────────────

    private fun applyExpenseCategory(storeId: String, op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                val name = payload.str("name") ?: return
                ExpenseCategories.upsert(ExpenseCategories.id) {
                    it[ExpenseCategories.id]          = op.entityId
                    it[ExpenseCategories.storeId]     = storeId
                    it[ExpenseCategories.name]        = name
                    it[ExpenseCategories.description] = payload.str("description")
                    it[ExpenseCategories.parentId]    = payload.str("parent_id")
                    it[ExpenseCategories.sortOrder]   = payload.int("sort_order")
                    it[ExpenseCategories.isActive]    = payload.bool("is_active")
                    it[ExpenseCategories.syncVersion] = op.createdAt
                    it[ExpenseCategories.updatedAt]   = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> softDelete(ExpenseCategories, ExpenseCategories.id, ExpenseCategories.isActive, ExpenseCategories.syncVersion, ExpenseCategories.updatedAt, op)
        }
    }

    // ── Coupon Usage ──────────────────────────────────────────────────────

    private fun applyCouponUsage(storeId: String, op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE" -> {
                val couponId = payload.str("coupon_id") ?: return
                val orderId = payload.str("order_id") ?: return
                CouponUsages.insertIgnore {
                    it[CouponUsages.id]             = op.entityId
                    it[CouponUsages.storeId]        = storeId
                    it[CouponUsages.couponId]       = couponId
                    it[CouponUsages.orderId]        = orderId
                    it[CouponUsages.customerId]     = payload.str("customer_id")
                    it[CouponUsages.discountAmount] = payload.dbl("discount_amount").toBigDecimal()
                    it[CouponUsages.redeemedBy]     = payload.str("redeemed_by")
                    it[CouponUsages.isActive]       = payload.bool("is_active")
                    it[CouponUsages.syncVersion]    = op.createdAt
                }
            }
            "UPDATE" -> {
                val couponId = payload.str("coupon_id") ?: return
                val orderId = payload.str("order_id") ?: return
                CouponUsages.upsert(CouponUsages.id) {
                    it[CouponUsages.id]             = op.entityId
                    it[CouponUsages.storeId]        = storeId
                    it[CouponUsages.couponId]       = couponId
                    it[CouponUsages.orderId]        = orderId
                    it[CouponUsages.customerId]     = payload.str("customer_id")
                    it[CouponUsages.discountAmount] = payload.dbl("discount_amount").toBigDecimal()
                    it[CouponUsages.redeemedBy]     = payload.str("redeemed_by")
                    it[CouponUsages.isActive]       = payload.bool("is_active")
                    it[CouponUsages.syncVersion]    = op.createdAt
                }
            }
            "DELETE" -> {
                // Coupon usages are transaction records — hard delete
                CouponUsages.deleteWhere { CouponUsages.id eq op.entityId }
            }
        }
    }

    // ── Promotion ─────────────────────────────────────────────────────────

    private fun applyPromotion(storeId: String, op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                val name = payload.str("name") ?: return
                Promotions.upsert(Promotions.id) {
                    it[Promotions.id]              = op.entityId
                    it[Promotions.storeId]         = storeId
                    it[Promotions.name]            = name
                    it[Promotions.description]     = payload.str("description")
                    it[Promotions.type]            = payload.str("type") ?: "PERCENTAGE"
                    it[Promotions.value]           = payload.dbl("value").toBigDecimal()
                    it[Promotions.minimumPurchase] = payload.dbl("minimum_purchase").toBigDecimal()
                    it[Promotions.scope]           = payload.str("scope") ?: "CART"
                    it[Promotions.scopeIds]        = payload.str("scope_ids")
                    it[Promotions.validFrom]       = payload.str("valid_from")?.toLongOrNull()
                    it[Promotions.validTo]         = payload.str("valid_to")?.toLongOrNull()
                    it[Promotions.priority]        = payload.int("priority")
                    it[Promotions.isStackable]     = payload.bool("is_stackable", default = false)
                    it[Promotions.isActive]        = payload.bool("is_active")
                    it[Promotions.syncVersion]     = op.createdAt
                    // V37: store typed config and store_ids for GET /v1/promotions
                    it[Promotions.config]          = payload.str("config") ?: "{}"
                    it[Promotions.storeIds]        = payload.str("store_ids") ?: "[]"
                    it[Promotions.updatedAt]       = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> softDelete(Promotions, Promotions.id, Promotions.isActive, Promotions.syncVersion, Promotions.updatedAt, op)
        }
    }

    // ── Customer Group ────────────────────────────────────────────────────

    private fun applyCustomerGroup(storeId: String, op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                val name = payload.str("name") ?: return
                CustomerGroups.upsert(CustomerGroups.id) {
                    it[CustomerGroups.id]           = op.entityId
                    it[CustomerGroups.storeId]      = storeId
                    it[CustomerGroups.name]         = name
                    it[CustomerGroups.description]  = payload.str("description")
                    it[CustomerGroups.discountRate] = payload.dbl("discount_rate").toBigDecimal()
                    it[CustomerGroups.isActive]     = payload.bool("is_active")
                    it[CustomerGroups.syncVersion]  = op.createdAt
                    it[CustomerGroups.updatedAt]    = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> softDelete(CustomerGroups, CustomerGroups.id, CustomerGroups.isActive, CustomerGroups.syncVersion, CustomerGroups.updatedAt, op)
        }
    }

    // ── Warehouse Stock (C1.2) ────────────────────────────────────────────

    private fun applyWarehouseStock(storeId: String, op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                val warehouseId = payload.str("warehouse_id") ?: return
                val productId   = payload.str("product_id") ?: return
                WarehouseStock.upsert(WarehouseStock.id) {
                    it[WarehouseStock.id]          = op.entityId
                    it[WarehouseStock.warehouseId] = warehouseId
                    it[WarehouseStock.productId]   = productId
                    it[WarehouseStock.storeId]     = storeId
                    it[WarehouseStock.quantity]    = payload.dbl("quantity").toBigDecimal()
                    it[WarehouseStock.minQuantity] = payload.dbl("min_quantity").toBigDecimal()
                    it[WarehouseStock.syncVersion] = op.createdAt
                    it[WarehouseStock.updatedAt]   = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> WarehouseStock.deleteWhere { WarehouseStock.id eq op.entityId }
        }
    }

    // ── Replenishment Rule (C1.5) ──────────────────────────────────────────

    private fun applyReplenishmentRule(op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                val productId   = payload.str("product_id") ?: return
                val warehouseId = payload.str("warehouse_id") ?: return
                val supplierId  = payload.str("supplier_id") ?: return
                ReplenishmentRules.upsert(ReplenishmentRules.id) {
                    it[ReplenishmentRules.id]           = op.entityId
                    it[ReplenishmentRules.productId]    = productId
                    it[ReplenishmentRules.warehouseId]  = warehouseId
                    it[ReplenishmentRules.supplierId]   = supplierId
                    it[ReplenishmentRules.reorderPoint] = payload.dbl("reorder_point").toBigDecimal()
                    it[ReplenishmentRules.reorderQty]   = payload.dbl("reorder_qty").toBigDecimal()
                    it[ReplenishmentRules.autoApprove]  = payload.bool("auto_approve")
                    it[ReplenishmentRules.isActive]     = payload.bool("is_active")
                    it[ReplenishmentRules.updatedAt]    = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> ReplenishmentRules.deleteWhere { ReplenishmentRules.id eq op.entityId }
        }
    }

    // ── Pricing Rule (C2.1) ──────────────────────────────────────────────

    private fun applyPricingRule(storeId: String, op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                val productId = payload.str("product_id") ?: return
                PricingRules.upsert(PricingRules.id) {
                    it[PricingRules.id]          = op.entityId
                    it[PricingRules.productId]   = productId
                    it[PricingRules.storeId]     = payload.str("store_id")
                    it[PricingRules.price]       = payload.dbl("price").toBigDecimal()
                    it[PricingRules.costPrice]   = payload.str("cost_price")?.toDoubleOrNull()?.toBigDecimal()
                    it[PricingRules.priority]    = payload.int("priority")
                    it[PricingRules.validFrom]   = payload.lng("valid_from")?.let { ms -> TimestampUtils.fromEpochMs(ms) }
                    it[PricingRules.validTo]     = payload.lng("valid_to")?.let { ms -> TimestampUtils.fromEpochMs(ms) }
                    it[PricingRules.isActive]    = payload.bool("is_active")
                    it[PricingRules.description] = payload.str("description") ?: ""
                    it[PricingRules.updatedAt]   = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> PricingRules.deleteWhere { PricingRules.id eq op.entityId }
        }
    }

    // ── Regional Tax Override (C2.3) ──────────────────────────────────────

    private fun applyRegionalTaxOverride(storeId: String, op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                val taxGroupId = payload.str("tax_group_id") ?: return
                RegionalTaxOverrides.upsert(RegionalTaxOverrides.id) {
                    it[RegionalTaxOverrides.id]                    = op.entityId
                    it[RegionalTaxOverrides.taxGroupId]            = taxGroupId
                    it[RegionalTaxOverrides.storeId]               = storeId
                    it[RegionalTaxOverrides.effectiveRate]          = payload.dbl("effective_rate").toBigDecimal()
                    it[RegionalTaxOverrides.jurisdictionCode]      = payload.str("jurisdiction_code") ?: ""
                    it[RegionalTaxOverrides.taxRegistrationNumber]  = payload.str("tax_registration_number") ?: ""
                    it[RegionalTaxOverrides.validFrom]              = payload.lng("valid_from")?.let { ms -> TimestampUtils.fromEpochMs(ms) }
                    it[RegionalTaxOverrides.validTo]                = payload.lng("valid_to")?.let { ms -> TimestampUtils.fromEpochMs(ms) }
                    it[RegionalTaxOverrides.isActive]               = payload.bool("is_active")
                    it[RegionalTaxOverrides.updatedAt]              = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> RegionalTaxOverrides.deleteWhere { RegionalTaxOverrides.id eq op.entityId }
        }
    }

    // ── User Store Access (C3.2) ──────────────────────────────────────────

    private fun applyUserStoreAccess(op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                val userId  = payload.str("user_id") ?: return
                val storeId = payload.str("store_id") ?: return
                UserStoreAccessTable.upsert(UserStoreAccessTable.id) {
                    it[UserStoreAccessTable.id]          = java.util.UUID.fromString(op.entityId)
                    it[UserStoreAccessTable.userId]      = userId
                    it[UserStoreAccessTable.storeId]     = storeId
                    it[UserStoreAccessTable.roleAtStore]  = payload.str("role_at_store")
                    it[UserStoreAccessTable.isActive]     = payload.bool("is_active")
                    it[UserStoreAccessTable.grantedBy]    = payload.str("granted_by")
                    it[UserStoreAccessTable.updatedAt]    = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> UserStoreAccessTable.deleteWhere { UserStoreAccessTable.id eq java.util.UUID.fromString(op.entityId) }
        }
    }

    // ── Employee Store Assignment (C3.4) ─────────────────────────────────

    private fun applyEmployeeStoreAssignment(op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                val employeeId = payload.str("employee_id") ?: return
                val storeId    = payload.str("store_id") ?: return
                EmployeeStoreAssignments.upsert(EmployeeStoreAssignments.id) {
                    it[EmployeeStoreAssignments.id]          = java.util.UUID.fromString(op.entityId)
                    it[EmployeeStoreAssignments.employeeId]  = employeeId
                    it[EmployeeStoreAssignments.storeId]     = storeId
                    it[EmployeeStoreAssignments.isTemporary] = payload.bool("is_temporary")
                    it[EmployeeStoreAssignments.updatedAt]   = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> EmployeeStoreAssignments.deleteWhere { EmployeeStoreAssignments.id eq java.util.UUID.fromString(op.entityId) }
        }
    }

    // ── Stock Transfer (C1.3) ────────────────────────────────────────────

    private fun applyStockTransfer(storeId: String, op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                val srcWarehouse = payload.str("source_warehouse_id") ?: return
                val dstWarehouse = payload.str("dest_warehouse_id") ?: return
                val productId    = payload.str("product_id") ?: return
                StockTransfers.upsert(StockTransfers.id) {
                    it[StockTransfers.id]                = op.entityId
                    it[StockTransfers.sourceWarehouseId]  = srcWarehouse
                    it[StockTransfers.destWarehouseId]    = dstWarehouse
                    it[StockTransfers.sourceStoreId]      = payload.str("source_store_id")
                    it[StockTransfers.destStoreId]        = payload.str("dest_store_id")
                    it[StockTransfers.productId]          = productId
                    it[StockTransfers.quantity]           = payload.dbl("quantity").toBigDecimal()
                    it[StockTransfers.status]             = payload.str("status") ?: "PENDING"
                    it[StockTransfers.notes]              = payload.str("notes")
                    it[StockTransfers.createdBy]          = payload.str("created_by")
                    it[StockTransfers.updatedAt]          = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> StockTransfers.deleteWhere { StockTransfers.id eq op.entityId }
        }
    }

    // ── Purchase Order (C1.3/C1.5) ───────────────────────────────────────

    private fun applyPurchaseOrder(storeId: String, op: SyncOperation) {
        val payload = parsePayload(op) ?: return
        when (op.operation) {
            "INSERT", "CREATE", "UPDATE" -> {
                val supplierId  = payload.str("supplier_id") ?: return
                val orderNumber = payload.str("order_number") ?: "PO-${op.entityId.take(8)}"
                PurchaseOrders.upsert(PurchaseOrders.id) {
                    it[PurchaseOrders.id]          = op.entityId
                    it[PurchaseOrders.storeId]     = storeId
                    it[PurchaseOrders.supplierId]  = supplierId
                    it[PurchaseOrders.orderNumber] = orderNumber
                    it[PurchaseOrders.status]      = payload.str("status") ?: "PENDING"
                    it[PurchaseOrders.orderDate]   = OffsetDateTime.now(ZoneOffset.UTC)
                    it[PurchaseOrders.totalAmount]  = payload.dbl("total_amount").toBigDecimal()
                    it[PurchaseOrders.currency]    = payload.str("currency") ?: "LKR"
                    it[PurchaseOrders.notes]       = payload.str("notes")
                    it[PurchaseOrders.createdBy]   = payload.str("created_by") ?: ""
                    it[PurchaseOrders.syncVersion] = op.createdAt
                    it[PurchaseOrders.updatedAt]   = OffsetDateTime.now(ZoneOffset.UTC)
                }
            }
            "DELETE" -> PurchaseOrders.deleteWhere { PurchaseOrders.id eq op.entityId }
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

}
