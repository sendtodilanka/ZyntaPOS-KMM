package com.zyntasolutions.zyntapos.api.sync

import com.zyntasolutions.zyntapos.api.models.SyncOperation
import kotlinx.serialization.json.Json

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
            "SETTINGS", "REGISTER_SESSION", "CASH_MOVEMENT",
            "PAYMENT_SPLIT", "COUPON", "EXPENSE", "EMPLOYEE",
            "SHIFT", "ATTENDANCE", "MEDIA_FILE", "E_INVOICE",
            "ACCOUNTING_ENTRY", "CUSTOMER_GROUP", "UNIT_OF_MEASURE",
            "WAREHOUSE", "INSTALLMENT", "LEAVE_RECORD", "PAYROLL",
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

            if (op.id.isBlank()) errors.add("Operation ID must not be blank")

            if (op.operation !in VALID_OPERATIONS) {
                errors.add("Invalid operation '${op.operation}'; must be one of $VALID_OPERATIONS")
            }

            if (op.entityType !in VALID_ENTITY_TYPES) {
                errors.add("Unknown entity type '${op.entityType}'")
            }

            if (op.entityId.isBlank()) errors.add("Entity ID must not be blank")

            if (op.payload.length > MAX_PAYLOAD_BYTES) {
                errors.add("Payload exceeds 1 MB limit")
            }

            try {
                json.parseToJsonElement(op.payload)
            } catch (_: Exception) {
                errors.add("Payload is not valid JSON")
            }

            val now = System.currentTimeMillis()
            if (op.createdAt > now + 60_000) {
                errors.add("created_at is in the future (clock skew > 60s)")
            }

            if (errors.isEmpty()) valid.add(op)
            else invalid.add(InvalidOperation(op.id, errors.joinToString("; ")))
        }

        return ValidationResult(valid, invalid)
    }
}
