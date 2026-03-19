package com.zyntasolutions.zyntapos.api.service

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

// ── Exposed table definition ─────────────────────────────────────────────────

object StockTransfers : Table("stock_transfers") {
    val id                = text("id")
    val sourceWarehouseId = text("source_warehouse_id")
    val destWarehouseId   = text("dest_warehouse_id")
    val sourceStoreId     = text("source_store_id").nullable()
    val destStoreId       = text("dest_store_id").nullable()
    val productId         = text("product_id")
    val quantity          = decimal("quantity", 12, 4)
    val status            = text("status")
    val notes             = text("notes").nullable()
    val createdBy         = text("created_by").nullable()
    val approvedBy        = text("approved_by").nullable()
    val approvedAt        = timestampWithTimeZone("approved_at").nullable()
    val dispatchedBy      = text("dispatched_by").nullable()
    val dispatchedAt      = timestampWithTimeZone("dispatched_at").nullable()
    val receivedBy        = text("received_by").nullable()
    val receivedAt        = timestampWithTimeZone("received_at").nullable()
    val transferredBy     = text("transferred_by").nullable()
    val transferredAt     = timestampWithTimeZone("transferred_at").nullable()
    val createdAt         = timestampWithTimeZone("created_at")
    val updatedAt         = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object PurchaseOrders : Table("purchase_orders") {
    val id           = text("id")
    val storeId      = text("store_id")
    val supplierId   = text("supplier_id")
    val orderNumber  = text("order_number")
    val status       = text("status")
    val orderDate    = timestampWithTimeZone("order_date")
    val expectedDate = timestampWithTimeZone("expected_date").nullable()
    val receivedDate = timestampWithTimeZone("received_date").nullable()
    val totalAmount  = decimal("total_amount", 12, 4)
    val currency     = text("currency")
    val notes        = text("notes").nullable()
    val createdBy    = text("created_by")
    val syncVersion  = long("sync_version")
    val createdAt    = timestampWithTimeZone("created_at")
    val updatedAt    = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

// ── DTOs ─────────────────────────────────────────────────────────────────────

@Serializable
data class StockTransferDto(
    val id: String,
    val sourceWarehouseId: String,
    val destWarehouseId: String,
    val sourceStoreId: String? = null,
    val destStoreId: String? = null,
    val productId: String,
    val quantity: Double,
    val status: String,
    val notes: String? = null,
    val createdBy: String? = null,
    val approvedBy: String? = null,
    val approvedAt: Long? = null,
    val dispatchedBy: String? = null,
    val dispatchedAt: Long? = null,
    val receivedBy: String? = null,
    val receivedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class CreateTransferRequest(
    val sourceWarehouseId: String,
    val destWarehouseId: String,
    val sourceStoreId: String? = null,
    val destStoreId: String? = null,
    val productId: String,
    val quantity: Double,
    val notes: String? = null,
)

@Serializable
data class ApproveTransferRequest(
    val approvedBy: String,
)

@Serializable
data class DispatchTransferRequest(
    val dispatchedBy: String,
)

@Serializable
data class ReceiveTransferRequest(
    val receivedBy: String,
)

@Serializable
data class TransferListResponse(
    val transfers: List<StockTransferDto>,
    val total: Int,
)

// ── Service ──────────────────────────────────────────────────────────────────

/**
 * Backend service for inter-store stock transfer (IST) management (C1.3).
 *
 * Provides admin-panel endpoints for listing, creating, and transitioning
 * transfers through the multi-step IST workflow:
 *   PENDING → APPROVED → IN_TRANSIT → RECEIVED
 */
class AdminTransferService {

    private val log = LoggerFactory.getLogger(AdminTransferService::class.java)

    private val validStatuses = setOf("PENDING", "APPROVED", "IN_TRANSIT", "RECEIVED", "COMMITTED", "CANCELLED")

    /** Lists all transfers, optionally filtered by [storeId] or [status]. */
    suspend fun listTransfers(
        storeId: String? = null,
        status: String? = null,
        page: Int = 0,
        size: Int = 20,
    ): TransferListResponse = newSuspendedTransaction {
        val query = StockTransfers.selectAll()
        if (storeId != null) {
            query.andWhere { (StockTransfers.sourceStoreId eq storeId) or (StockTransfers.destStoreId eq storeId) }
        }
        if (status != null && status in validStatuses) {
            query.andWhere { StockTransfers.status eq status }
        }
        val total = query.count().toInt()
        val rows = query
            .orderBy(StockTransfers.createdAt, SortOrder.DESC)
            .limit(size, offset = (page * size).toLong())
            .map(::toDto)
        TransferListResponse(transfers = rows, total = total)
    }

    /** Returns a single transfer by ID, or null if not found. */
    suspend fun getById(id: String): StockTransferDto? = newSuspendedTransaction {
        StockTransfers.select { StockTransfers.id eq id }.singleOrNull()?.let(::toDto)
    }

    /** Creates a new transfer in PENDING status. */
    suspend fun create(request: CreateTransferRequest, createdBy: String): StockTransferDto = newSuspendedTransaction {
        val id = UUID.randomUUID().toString()
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        StockTransfers.insert {
            it[StockTransfers.id]                = id
            it[StockTransfers.sourceWarehouseId] = request.sourceWarehouseId
            it[StockTransfers.destWarehouseId]   = request.destWarehouseId
            it[StockTransfers.sourceStoreId]     = request.sourceStoreId
            it[StockTransfers.destStoreId]       = request.destStoreId
            it[StockTransfers.productId]         = request.productId
            it[StockTransfers.quantity]          = request.quantity.toBigDecimal()
            it[StockTransfers.status]            = "PENDING"
            it[StockTransfers.notes]             = request.notes
            it[StockTransfers.createdBy]         = createdBy
            it[StockTransfers.createdAt]         = now
            it[StockTransfers.updatedAt]         = now
        }
        log.info("IST created: id=$id by=$createdBy src=${request.sourceWarehouseId} dst=${request.destWarehouseId}")
        StockTransfers.select { StockTransfers.id eq id }.single().let(::toDto)
    }

    /** Approves a PENDING transfer (PENDING → APPROVED). */
    suspend fun approve(id: String, approvedBy: String): StockTransferDto? = newSuspendedTransaction {
        val row = StockTransfers.select { StockTransfers.id eq id }.singleOrNull() ?: return@newSuspendedTransaction null
        if (row[StockTransfers.status] != "PENDING") return@newSuspendedTransaction null
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        StockTransfers.update({ StockTransfers.id eq id }) {
            it[status]     = "APPROVED"
            it[StockTransfers.approvedBy]  = approvedBy
            it[StockTransfers.approvedAt]  = now
            it[updatedAt]  = now
        }
        log.info("IST approved: id=$id by=$approvedBy")
        StockTransfers.select { StockTransfers.id eq id }.single().let(::toDto)
    }

    /** Dispatches an APPROVED transfer (APPROVED → IN_TRANSIT). */
    suspend fun dispatch(id: String, dispatchedBy: String): StockTransferDto? = newSuspendedTransaction {
        val row = StockTransfers.select { StockTransfers.id eq id }.singleOrNull() ?: return@newSuspendedTransaction null
        if (row[StockTransfers.status] != "APPROVED") return@newSuspendedTransaction null
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        StockTransfers.update({ StockTransfers.id eq id }) {
            it[status]                       = "IN_TRANSIT"
            it[StockTransfers.dispatchedBy]  = dispatchedBy
            it[StockTransfers.dispatchedAt]  = now
            it[updatedAt]                    = now
        }
        log.info("IST dispatched: id=$id by=$dispatchedBy")
        StockTransfers.select { StockTransfers.id eq id }.single().let(::toDto)
    }

    /** Receives an IN_TRANSIT transfer (IN_TRANSIT → RECEIVED). */
    suspend fun receive(id: String, receivedBy: String): StockTransferDto? = newSuspendedTransaction {
        val row = StockTransfers.select { StockTransfers.id eq id }.singleOrNull() ?: return@newSuspendedTransaction null
        if (row[StockTransfers.status] != "IN_TRANSIT") return@newSuspendedTransaction null
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        StockTransfers.update({ StockTransfers.id eq id }) {
            it[status]                      = "RECEIVED"
            it[StockTransfers.receivedBy]   = receivedBy
            it[StockTransfers.receivedAt]   = now
            it[updatedAt]                   = now
        }
        log.info("IST received: id=$id by=$receivedBy")
        StockTransfers.select { StockTransfers.id eq id }.single().let(::toDto)
    }

    /** Cancels a PENDING or APPROVED transfer (no stock movement). */
    suspend fun cancel(id: String, cancelledBy: String): StockTransferDto? = newSuspendedTransaction {
        val row = StockTransfers.select { StockTransfers.id eq id }.singleOrNull() ?: return@newSuspendedTransaction null
        val currentStatus = row[StockTransfers.status]
        if (currentStatus != "PENDING" && currentStatus != "APPROVED") return@newSuspendedTransaction null
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        StockTransfers.update({ StockTransfers.id eq id }) {
            it[status]    = "CANCELLED"
            it[updatedAt] = now
        }
        log.info("IST cancelled: id=$id by=$cancelledBy (was $currentStatus)")
        StockTransfers.select { StockTransfers.id eq id }.single().let(::toDto)
    }

    // ── Mapper ──────────────────────────────────────────────────────────────

    private fun toDto(row: org.jetbrains.exposed.sql.ResultRow) = StockTransferDto(
        id                = row[StockTransfers.id],
        sourceWarehouseId = row[StockTransfers.sourceWarehouseId],
        destWarehouseId   = row[StockTransfers.destWarehouseId],
        sourceStoreId     = row[StockTransfers.sourceStoreId],
        destStoreId       = row[StockTransfers.destStoreId],
        productId         = row[StockTransfers.productId],
        quantity          = row[StockTransfers.quantity].toDouble(),
        status            = row[StockTransfers.status],
        notes             = row[StockTransfers.notes],
        createdBy         = row[StockTransfers.createdBy],
        approvedBy        = row[StockTransfers.approvedBy],
        approvedAt        = row[StockTransfers.approvedAt]?.toInstant()?.toEpochMilli(),
        dispatchedBy      = row[StockTransfers.dispatchedBy],
        dispatchedAt      = row[StockTransfers.dispatchedAt]?.toInstant()?.toEpochMilli(),
        receivedBy        = row[StockTransfers.receivedBy],
        receivedAt        = row[StockTransfers.receivedAt]?.toInstant()?.toEpochMilli(),
        createdAt         = row[StockTransfers.createdAt].toInstant().toEpochMilli(),
        updatedAt         = row[StockTransfers.updatedAt].toInstant().toEpochMilli(),
    )
}
