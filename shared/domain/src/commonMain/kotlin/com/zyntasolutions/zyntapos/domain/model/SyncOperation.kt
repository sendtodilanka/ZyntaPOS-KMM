package com.zyntasolutions.zyntapos.domain.model

import kotlinx.datetime.Instant

/**
 * Represents a single pending write operation waiting to be pushed to the remote server.
 *
 * The sync queue is append-only from the domain perspective; the data layer manages
 * status transitions (PENDING → SYNCING → SYNCED / FAILED).
 *
 * @property id          Unique identifier (UUID v4).
 * @property entityType  Logical type of the affected entity (e.g., "product", "order").
 *                       Use [EntityType] constants to avoid magic strings.
 * @property entityId    UUID of the affected domain record.
 * @property operation   The write action performed. See [Operation].
 * @property payload     JSON-serialised snapshot of the entity at the time of the operation.
 *                       Used by the server to reconstruct the delta on conflict resolution.
 * @property createdAt   UTC timestamp when the operation was enqueued.
 * @property retryCount  Number of failed push attempts. Capped at 5 by [SyncEngine].
 * @property status      Current lifecycle state of this sync operation.
 */
data class SyncOperation(
    val id: String,
    val entityType: String,
    val entityId: String,
    val operation: Operation,
    val payload: String,
    val createdAt: Instant,
    val retryCount: Int = 0,
    val status: Status = Status.PENDING,
) {
    /** The write action that triggered this sync operation. */
    enum class Operation {
        /** A new entity was inserted locally. */
        INSERT,

        /** An existing entity was updated locally. */
        UPDATE,

        /** An existing entity was soft-deleted locally. */
        DELETE,
    }

    /** Lifecycle state of the sync operation in the local queue. */
    enum class Status {
        /** Awaiting the next sync cycle. */
        PENDING,

        /** Currently being transmitted to the server. */
        IN_FLIGHT,

        /** Successfully acknowledged by the server. Safe to prune from the queue. */
        SYNCED,

        /**
         * All [SyncEngine.MAX_RETRIES] attempts have been exhausted.
         * Requires manual operator intervention or a future re-trigger.
         */
        FAILED,
    }

    /** Well-known [entityType] string constants. */
    object EntityType {
        const val PRODUCT = "product"
        const val CATEGORY = "category"
        const val ORDER = "order"
        const val CUSTOMER = "customer"
        const val STOCK_ADJUSTMENT = "stock_adjustment"
        const val SUPPLIER = "supplier"
        const val USER = "user"
        const val REGISTER_SESSION = "register_session"
        const val CASH_MOVEMENT = "cash_movement"
        const val SETTINGS = "settings"
        // Phase 2 entity types — Global Product Catalog (C1.1)
        const val MASTER_PRODUCT = "master_product"
        const val STORE_PRODUCT = "store_product"
        const val CUSTOMER_GROUP = "customer_group"
        const val CUSTOMER_WALLET = "customer_wallet"
        const val WALLET_TRANSACTION = "wallet_transaction"
        const val REWARD_POINTS = "reward_points"
        const val LOYALTY_TIER = "loyalty_tier"
        const val INSTALLMENT_PLAN = "installment_plan"
        const val INSTALLMENT_PAYMENT = "installment_payment"
        const val COUPON = "coupon"
        const val COUPON_USAGE = "coupon_usage"
        const val PROMOTION = "promotion"
        const val EXPENSE = "expense"
        const val EXPENSE_CATEGORY = "expense_category"
        const val RECURRING_EXPENSE = "recurring_expense"
        const val WAREHOUSE = "warehouse"
        const val STOCK_TRANSFER = "stock_transfer"
        // Phase 3 entity types
        const val EMPLOYEE = "employee"
        const val ATTENDANCE_RECORD = "attendance_record"
        const val LEAVE_RECORD = "leave_record"
        const val PAYROLL_RECORD = "payroll_record"
        const val SHIFT_SCHEDULE = "shift_schedule"
        const val MEDIA_FILE = "media_file"
        const val WAREHOUSE_RACK = "warehouse_rack"
        const val ACCOUNTING_ENTRY = "accounting_entry"
        const val E_INVOICE = "e_invoice"
    }
}
