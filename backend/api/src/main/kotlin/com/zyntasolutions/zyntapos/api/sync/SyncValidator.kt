package com.zyntasolutions.zyntapos.api.sync

import com.zyntasolutions.zyntapos.api.models.SyncOperation
import com.zyntasolutions.zyntapos.common.TimestampUtils
import com.zyntasolutions.zyntapos.common.dbl
import com.zyntasolutions.zyntapos.common.lng
import com.zyntasolutions.zyntapos.common.str
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Validates incoming push batches from POS terminals.
 *
 * Performs schema checks, entity type allow-listing, payload size limits,
 * JSON validity, and timestamp sanity checks.
 */
class SyncValidator {

    companion object {
        const val MAX_BATCH_SIZE = 50
        const val MAX_PAYLOAD_BYTES = 1_048_576 // 1 MB per operation
        val VALID_OPERATIONS = setOf("INSERT", "CREATE", "UPDATE", "DELETE")
        val VALID_ENTITY_TYPES = setOf(
            "PRODUCT", "CATEGORY", "CUSTOMER", "ORDER", "ORDER_ITEM",
            "SUPPLIER", "TAX_GROUP", "STOCK", "STOCK_ADJUSTMENT",
            "SETTINGS", "CASH_REGISTER", "REGISTER_SESSION", "CASH_MOVEMENT",
            "PAYMENT_SPLIT", "COUPON", "EXPENSE", "EMPLOYEE",
            "SHIFT", "ATTENDANCE", "MEDIA_FILE", "E_INVOICE",
            "ACCOUNTING_ENTRY", "CUSTOMER_GROUP", "UNIT_OF_MEASURE",
            "WAREHOUSE", "INSTALLMENT", "LEAVE_RECORD", "PAYROLL",
            // Phase 2: Multi-store (C1.1–C1.5)
            "MASTER_PRODUCT", "STORE_PRODUCT", "WAREHOUSE_STOCK",
            "STOCK_TRANSFER", "PURCHASE_ORDER", "TRANSIT_EVENT",
            "REPLENISHMENT_RULE",
            // Phase 2: Pricing & Tax (C2.1, C2.3)
            "PRICING_RULE", "REGIONAL_TAX_OVERRIDE",
            // Phase 2: User Access (C3.2)
            "USER_STORE_ACCESS",
        )
        private val json = Json { ignoreUnknownKeys = true }
    }

    data class ValidationResult(
        val valid: List<SyncOperation>,
        val invalid: List<InvalidOperation>,
    )

    data class InvalidOperation(
        val id: String,
        val reason: String,
    )

    fun validateBatch(operations: List<SyncOperation>): ValidationResult {
        if (operations.size > MAX_BATCH_SIZE) {
            return ValidationResult(
                valid = emptyList(),
                invalid = operations.map {
                    InvalidOperation(it.id, "Batch exceeds max size of $MAX_BATCH_SIZE")
                }
            )
        }

        val valid = mutableListOf<SyncOperation>()
        val invalid = mutableListOf<InvalidOperation>()

        for (op in operations) {
            val errors = mutableListOf<String>()
            // Normalize entity type to uppercase — KMM client sends lowercase
            val normalizedType = op.entityType.uppercase()

            if (op.id.isBlank()) errors.add("Operation ID must not be blank")

            if (op.operation !in VALID_OPERATIONS) {
                errors.add("Invalid operation '${op.operation}'; must be one of $VALID_OPERATIONS")
            }

            if (normalizedType !in VALID_ENTITY_TYPES) {
                errors.add("Unknown entity type '${op.entityType}'")
            }

            if (op.entityId.isBlank()) errors.add("Entity ID must not be blank")

            if (op.payload.length > MAX_PAYLOAD_BYTES) {
                errors.add("Payload exceeds 1 MB limit")
            }

            // Quick structural check before full parse: sync payloads must be JSON objects or arrays
            val firstChar = op.payload.trimStart().firstOrNull()
            if (firstChar != '{' && firstChar != '[') {
                errors.add("Payload is not valid JSON")
            } else {
                try {
                    json.parseToJsonElement(op.payload)
                } catch (e: Exception) {
                    errors.add("Payload is not valid JSON")
                }
            }

            TimestampUtils.validateEpochMs(op.createdAt, "created_at")?.let { errors.add(it) }

            // S2-7: Field-level validation for known entity types (CREATE/UPDATE only)
            if (errors.isEmpty() && op.operation in setOf("CREATE", "INSERT", "UPDATE")) {
                validatePayloadFields(normalizedType, op.payload, errors)
            }

            if (errors.isEmpty()) valid.add(op)
            else invalid.add(InvalidOperation(op.id, errors.joinToString("; ")))
        }

        return ValidationResult(valid, invalid)
    }

    /**
     * S2-7: Validates payload fields for known entity types.
     * Prevents invalid data (negative prices, blank names) from entering the sync pipeline.
     */
    private fun validatePayloadFields(entityType: String, payload: String, errors: MutableList<String>) {
        try {
            val obj = json.parseToJsonElement(payload).jsonObject
            when (entityType) {
                "PRODUCT" -> {
                    val name = obj.str("name")
                    if (name.isNullOrBlank()) errors.add("PRODUCT.name must not be blank")
                    val price = obj.dbl("price")
                    if (price < 0) errors.add("PRODUCT.price must be non-negative")
                    val costPrice = obj.dbl("cost_price")
                    if (costPrice < 0) errors.add("PRODUCT.cost_price must be non-negative")
                    val stockQty = obj.dbl("stock_qty")
                    if (stockQty < 0) errors.add("PRODUCT.stock_qty must be non-negative")
                }
                "CUSTOMER" -> {
                    val name = obj.str("name")
                    if (name.isNullOrBlank()) errors.add("CUSTOMER.name must not be blank")
                }
                "CATEGORY" -> {
                    val name = obj.str("name")
                    if (name.isNullOrBlank()) errors.add("CATEGORY.name must not be blank")
                }
                "ORDER" -> {
                    val grandTotal = obj.dbl("grand_total")
                    if (grandTotal < 0) errors.add("ORDER.grand_total must be non-negative")
                }
                "SUPPLIER" -> {
                    val name = obj.str("name")
                    if (name.isNullOrBlank()) errors.add("SUPPLIER.name must not be blank")
                }
                "TAX_GROUP" -> {
                    val name = obj.str("name")
                    if (name.isNullOrBlank()) errors.add("TAX_GROUP.name must not be blank")
                    val rate = obj.dbl("rate")
                    if (rate < 0 || rate > 100) errors.add("TAX_GROUP.rate must be 0-100")
                }
                "UNIT_OF_MEASURE" -> {
                    val name = obj.str("name")
                    if (name.isNullOrBlank()) errors.add("UNIT_OF_MEASURE.name must not be blank")
                    val conversionRate = obj.dbl("conversion_rate")
                    if (conversionRate < 0) errors.add("UNIT_OF_MEASURE.conversion_rate must be non-negative")
                }
                "STOCK_ADJUSTMENT" -> {
                    val productId = obj.str("product_id")
                    if (productId.isNullOrBlank()) errors.add("STOCK_ADJUSTMENT.product_id must not be blank")
                    val type = obj.str("type")
                    if (type != null && type !in setOf("INCREASE", "DECREASE", "TRANSFER")) {
                        errors.add("STOCK_ADJUSTMENT.type must be INCREASE, DECREASE, or TRANSFER")
                    }
                    val qty = obj.dbl("quantity")
                    if (qty < 0) errors.add("STOCK_ADJUSTMENT.quantity must be non-negative")
                }
                "CASH_REGISTER" -> {
                    val name = obj.str("name")
                    if (name.isNullOrBlank()) errors.add("CASH_REGISTER.name must not be blank")
                }
                "REGISTER_SESSION" -> {
                    val registerId = obj.str("register_id")
                    if (registerId.isNullOrBlank()) errors.add("REGISTER_SESSION.register_id must not be blank")
                    val openedBy = obj.str("opened_by")
                    if (openedBy.isNullOrBlank()) errors.add("REGISTER_SESSION.opened_by must not be blank")
                }
                "CASH_MOVEMENT" -> {
                    val sessionId = obj.str("session_id")
                    if (sessionId.isNullOrBlank()) errors.add("CASH_MOVEMENT.session_id must not be blank")
                    val amount = obj.dbl("amount")
                    if (amount < 0) errors.add("CASH_MOVEMENT.amount must be non-negative")
                    val type = obj.str("type")
                    if (type != null && type !in setOf("IN", "OUT")) {
                        errors.add("CASH_MOVEMENT.type must be IN or OUT")
                    }
                }
                "COUPON" -> {
                    val code = obj.str("code")
                    if (code.isNullOrBlank()) errors.add("COUPON.code must not be blank")
                    val name = obj.str("name")
                    if (name.isNullOrBlank()) errors.add("COUPON.name must not be blank")
                    val discountValue = obj.dbl("discount_value")
                    if (discountValue < 0) errors.add("COUPON.discount_value must be non-negative")
                }
                "EXPENSE" -> {
                    val amount = obj.dbl("amount")
                    if (amount < 0) errors.add("EXPENSE.amount must be non-negative")
                }
                "PAYMENT_SPLIT" -> {
                    val orderId = obj.str("order_id")
                    if (orderId.isNullOrBlank()) errors.add("PAYMENT_SPLIT.order_id must not be blank")
                    val amount = obj.dbl("amount")
                    if (amount < 0) errors.add("PAYMENT_SPLIT.amount must be non-negative")
                }
                "SETTINGS" -> {
                    val key = obj.str("key")
                    if (key.isNullOrBlank()) errors.add("SETTINGS.key must not be blank")
                }
                "REPLENISHMENT_RULE" -> {
                    val productId = obj.str("product_id")
                    if (productId.isNullOrBlank()) errors.add("REPLENISHMENT_RULE.product_id must not be blank")
                    val warehouseId = obj.str("warehouse_id")
                    if (warehouseId.isNullOrBlank()) errors.add("REPLENISHMENT_RULE.warehouse_id must not be blank")
                    val reorderPoint = obj.dbl("reorder_point")
                    if (reorderPoint < 0) errors.add("REPLENISHMENT_RULE.reorder_point must be non-negative")
                    val reorderQty = obj.dbl("reorder_qty")
                    if (reorderQty < 0) errors.add("REPLENISHMENT_RULE.reorder_qty must be non-negative")
                }
                "PURCHASE_ORDER" -> {
                    val supplierId = obj.str("supplier_id")
                    if (supplierId.isNullOrBlank()) errors.add("PURCHASE_ORDER.supplier_id must not be blank")
                }
                "TRANSIT_EVENT" -> {
                    val transferId = obj.str("transfer_id")
                    if (transferId.isNullOrBlank()) errors.add("TRANSIT_EVENT.transfer_id must not be blank")
                    val eventType = obj.str("event_type")
                    if (eventType.isNullOrBlank()) errors.add("TRANSIT_EVENT.event_type must not be blank")
                }
                "WAREHOUSE_STOCK" -> {
                    val warehouseId = obj.str("warehouse_id")
                    if (warehouseId.isNullOrBlank()) errors.add("WAREHOUSE_STOCK.warehouse_id must not be blank")
                    val productId = obj.str("product_id")
                    if (productId.isNullOrBlank()) errors.add("WAREHOUSE_STOCK.product_id must not be blank")
                }
                "PRICING_RULE" -> {
                    val productId = obj.str("product_id")
                    if (productId.isNullOrBlank()) errors.add("PRICING_RULE.product_id must not be blank")
                    val price = obj.dbl("price")
                    if (price < 0) errors.add("PRICING_RULE.price must be non-negative")
                }
                "USER_STORE_ACCESS" -> {
                    val userId = obj.str("user_id")
                    if (userId.isNullOrBlank()) errors.add("USER_STORE_ACCESS.user_id must not be blank")
                    val storeId = obj.str("store_id")
                    if (storeId.isNullOrBlank()) errors.add("USER_STORE_ACCESS.store_id must not be blank")
                }
                "REGIONAL_TAX_OVERRIDE" -> {
                    val taxGroupId = obj.str("tax_group_id")
                    if (taxGroupId.isNullOrBlank()) errors.add("REGIONAL_TAX_OVERRIDE.tax_group_id must not be blank")
                    val effectiveRate = obj.dbl("effective_rate")
                    if (effectiveRate < 0 || effectiveRate > 100) errors.add("REGIONAL_TAX_OVERRIDE.effective_rate must be 0-100")
                }
                // Other entity types: structural JSON validation only (already done above)
            }

            // Validate common timestamp fields in all entity payloads (if present)
            validatePayloadTimestamps(obj, entityType, errors)
        } catch (_: Exception) {
            // JSON parsing already validated above — skip field validation on parse error
        }
    }

    /**
     * Validates timestamp fields embedded in entity payloads.
     * All timestamp fields in payloads must be epoch milliseconds (Long).
     * Rejects timestamps before 2020-01-01 and more than 60s in the future.
     */
    private fun validatePayloadTimestamps(
        obj: kotlinx.serialization.json.JsonObject,
        entityType: String,
        errors: MutableList<String>,
    ) {
        val timestampFields = listOf("created_at", "updated_at", "completed_at", "closed_at")
        for (field in timestampFields) {
            val value = obj.lng(field) ?: continue
            TimestampUtils.validateEpochMs(value, "$entityType.$field", strict = true)?.let { errors.add(it) }
        }
    }
}
