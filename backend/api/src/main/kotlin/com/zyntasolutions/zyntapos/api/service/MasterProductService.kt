package com.zyntasolutions.zyntapos.api.service

import com.zyntasolutions.zyntapos.api.models.PagedResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

// ── Exposed table definitions ────────────────────────────────────────────────

object MasterProducts : Table("master_products") {
    val id          = text("id")
    val sku         = text("sku").nullable()
    val barcode     = text("barcode").nullable()
    val name        = text("name")
    val description = text("description").nullable()
    val basePrice   = decimal("base_price", 12, 4)
    val costPrice   = decimal("cost_price", 12, 4)
    val categoryId  = text("category_id").nullable()
    val unitId      = text("unit_id").nullable()
    val taxGroupId  = text("tax_group_id").nullable()
    val imageUrl    = text("image_url").nullable()
    val isActive    = bool("is_active")
    val syncVersion = long("sync_version")
    val createdAt   = timestampWithTimeZone("created_at")
    val updatedAt   = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object StoreProducts : Table("store_products") {
    val id              = text("id")
    val masterProductId = text("master_product_id")
    val storeId         = text("store_id")
    val localPrice      = decimal("local_price", 12, 4).nullable()
    val localCostPrice  = decimal("local_cost_price", 12, 4).nullable()
    val localStockQty   = integer("local_stock_qty")
    val minStockQty     = integer("min_stock_qty")
    val isActive        = bool("is_active")
    val syncVersion     = long("sync_version")
    val createdAt       = timestampWithTimeZone("created_at")
    val updatedAt       = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

// ── DTOs ─────────────────────────────────────────────────────────────────────

@Serializable
data class MasterProductDto(
    val id: String,
    val sku: String? = null,
    val barcode: String? = null,
    val name: String,
    val description: String? = null,
    @SerialName("base_price") val basePrice: Double,
    @SerialName("cost_price") val costPrice: Double = 0.0,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("unit_id") val unitId: String? = null,
    @SerialName("tax_group_id") val taxGroupId: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
    @SerialName("store_count") val storeCount: Int = 0,
)

@Serializable
data class CreateMasterProductRequest(
    val sku: String? = null,
    val barcode: String? = null,
    val name: String,
    val description: String? = null,
    @SerialName("base_price") val basePrice: Double,
    @SerialName("cost_price") val costPrice: Double = 0.0,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("unit_id") val unitId: String? = null,
    @SerialName("tax_group_id") val taxGroupId: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
)

@Serializable
data class UpdateMasterProductRequest(
    val sku: String? = null,
    val barcode: String? = null,
    val name: String? = null,
    val description: String? = null,
    @SerialName("base_price") val basePrice: Double? = null,
    @SerialName("cost_price") val costPrice: Double? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("unit_id") val unitId: String? = null,
    @SerialName("tax_group_id") val taxGroupId: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
)

@Serializable
data class StoreProductAssignmentDto(
    @SerialName("store_id") val storeId: String,
    @SerialName("store_name") val storeName: String,
    @SerialName("local_price") val localPrice: Double? = null,
    @SerialName("local_cost_price") val localCostPrice: Double? = null,
    @SerialName("local_stock_qty") val localStockQty: Int = 0,
    @SerialName("min_stock_qty") val minStockQty: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
data class AssignToStoreRequest(
    @SerialName("local_price") val localPrice: Double? = null,
    @SerialName("local_cost_price") val localCostPrice: Double? = null,
    @SerialName("local_stock_qty") val localStockQty: Int = 0,
    @SerialName("min_stock_qty") val minStockQty: Int = 0,
)

@Serializable
data class BulkAssignRequest(
    @SerialName("store_ids") val storeIds: List<String>,
    @SerialName("local_price") val localPrice: Double? = null,
    @SerialName("local_cost_price") val localCostPrice: Double? = null,
)

// ── Service ──────────────────────────────────────────────────────────────────

class MasterProductService {
    private val logger = LoggerFactory.getLogger(MasterProductService::class.java)

    suspend fun create(request: CreateMasterProductRequest): MasterProductDto = newSuspendedTransaction {
        val id = UUID.randomUUID().toString()
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        MasterProducts.insert {
            it[MasterProducts.id]          = id
            it[MasterProducts.sku]         = request.sku
            it[MasterProducts.barcode]     = request.barcode
            it[MasterProducts.name]        = request.name
            it[MasterProducts.description] = request.description
            it[MasterProducts.basePrice]   = request.basePrice.toBigDecimal()
            it[MasterProducts.costPrice]   = request.costPrice.toBigDecimal()
            it[MasterProducts.categoryId]  = request.categoryId
            it[MasterProducts.unitId]      = request.unitId
            it[MasterProducts.taxGroupId]  = request.taxGroupId
            it[MasterProducts.imageUrl]    = request.imageUrl
            it[MasterProducts.isActive]    = true
            it[MasterProducts.syncVersion] = 1
            it[MasterProducts.createdAt]   = now
            it[MasterProducts.updatedAt]   = now
        }
        logger.info("Created master product: id=$id name=${request.name}")
        getByIdInternal(id)!!
    }

    suspend fun update(id: String, request: UpdateMasterProductRequest): MasterProductDto? = newSuspendedTransaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val updated = MasterProducts.update({ MasterProducts.id eq id }) {
            request.sku?.let { v -> it[sku] = v }
            request.barcode?.let { v -> it[barcode] = v }
            request.name?.let { v -> it[name] = v }
            request.description?.let { v -> it[description] = v }
            request.basePrice?.let { v -> it[basePrice] = v.toBigDecimal() }
            request.costPrice?.let { v -> it[costPrice] = v.toBigDecimal() }
            request.categoryId?.let { v -> it[categoryId] = v }
            request.unitId?.let { v -> it[unitId] = v }
            request.taxGroupId?.let { v -> it[taxGroupId] = v }
            request.imageUrl?.let { v -> it[imageUrl] = v }
            request.isActive?.let { v -> it[isActive] = v }
            it[syncVersion] = MasterProducts.syncVersion + 1
            it[updatedAt] = now
        }
        if (updated > 0) {
            logger.info("Updated master product: id=$id")
            getByIdInternal(id)
        } else null
    }

    suspend fun delete(id: String): Boolean = newSuspendedTransaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val updated = MasterProducts.update({ MasterProducts.id eq id }) {
            it[isActive] = false
            it[syncVersion] = MasterProducts.syncVersion + 1
            it[updatedAt] = now
        }
        if (updated > 0) logger.info("Soft-deleted master product: id=$id")
        updated > 0
    }

    suspend fun list(page: Int, size: Int, search: String?): PagedResponse<MasterProductDto> = newSuspendedTransaction {
        val baseQuery = MasterProducts.selectAll().andWhere { MasterProducts.isActive eq true }
        if (!search.isNullOrBlank()) {
            val pattern = "%${search.lowercase()}%"
            baseQuery.andWhere {
                MasterProducts.name.lowerCase() like pattern or
                    (MasterProducts.sku.lowerCase() like pattern) or
                    (MasterProducts.barcode.lowerCase() like pattern)
            }
        }
        val total = baseQuery.count()
        val items = baseQuery
            .orderBy(MasterProducts.name to SortOrder.ASC)
            .limit(size).offset((page.toLong() * size))
            .map { it.toMasterProductDto() }
        PagedResponse(
            data    = items,
            page    = page,
            size    = size,
            total   = total,
            hasMore = (page.toLong() * size + size) < total,
        )
    }

    suspend fun getById(id: String): MasterProductDto? = newSuspendedTransaction {
        getByIdInternal(id)
    }

    suspend fun assignToStore(masterProductId: String, storeId: String, request: AssignToStoreRequest): Boolean = newSuspendedTransaction {
        val exists = MasterProducts.selectAll().where { MasterProducts.id eq masterProductId }.count() > 0
        if (!exists) return@newSuspendedTransaction false
        val id = UUID.randomUUID().toString()
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        StoreProducts.upsert(StoreProducts.masterProductId, StoreProducts.storeId) {
            it[StoreProducts.id]              = id
            it[StoreProducts.masterProductId] = masterProductId
            it[StoreProducts.storeId]         = storeId
            it[StoreProducts.localPrice]      = request.localPrice?.toBigDecimal()
            it[StoreProducts.localCostPrice]  = request.localCostPrice?.toBigDecimal()
            it[StoreProducts.localStockQty]   = request.localStockQty
            it[StoreProducts.minStockQty]     = request.minStockQty
            it[StoreProducts.isActive]        = true
            it[StoreProducts.syncVersion]     = 1
            it[StoreProducts.createdAt]       = now
            it[StoreProducts.updatedAt]       = now
        }
        logger.info("Assigned master product $masterProductId to store $storeId")
        true
    }

    suspend fun removeFromStore(masterProductId: String, storeId: String): Boolean = newSuspendedTransaction {
        val deleted = StoreProducts.deleteWhere {
            (StoreProducts.masterProductId eq masterProductId) and (StoreProducts.storeId eq storeId)
        }
        if (deleted > 0) logger.info("Removed master product $masterProductId from store $storeId")
        deleted > 0
    }

    suspend fun updateStoreOverride(masterProductId: String, storeId: String, request: AssignToStoreRequest): Boolean = newSuspendedTransaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val updated = StoreProducts.update({
            (StoreProducts.masterProductId eq masterProductId) and (StoreProducts.storeId eq storeId)
        }) {
            it[localPrice]     = request.localPrice?.toBigDecimal()
            it[localCostPrice] = request.localCostPrice?.toBigDecimal()
            it[localStockQty]  = request.localStockQty
            it[minStockQty]    = request.minStockQty
            it[syncVersion]    = StoreProducts.syncVersion + 1
            it[updatedAt]      = now
        }
        if (updated > 0) logger.info("Updated store override: master=$masterProductId store=$storeId")
        updated > 0
    }

    suspend fun getStoreAssignments(masterProductId: String): List<StoreProductAssignmentDto> = newSuspendedTransaction {
        (StoreProducts innerJoin Stores)
            .selectAll()
            .where { StoreProducts.masterProductId eq masterProductId }
            .map { row ->
                StoreProductAssignmentDto(
                    storeId       = row[StoreProducts.storeId],
                    storeName     = row[Stores.name],
                    localPrice    = row[StoreProducts.localPrice]?.toDouble(),
                    localCostPrice = row[StoreProducts.localCostPrice]?.toDouble(),
                    localStockQty = row[StoreProducts.localStockQty],
                    minStockQty   = row[StoreProducts.minStockQty],
                    isActive      = row[StoreProducts.isActive],
                )
            }
    }

    suspend fun bulkAssign(masterProductId: String, request: BulkAssignRequest): Int = newSuspendedTransaction {
        val exists = MasterProducts.selectAll().where { MasterProducts.id eq masterProductId }.count() > 0
        if (!exists) return@newSuspendedTransaction 0
        var count = 0
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        for (storeId in request.storeIds) {
            val id = UUID.randomUUID().toString()
            StoreProducts.upsert(StoreProducts.masterProductId, StoreProducts.storeId) {
                it[StoreProducts.id]              = id
                it[StoreProducts.masterProductId] = masterProductId
                it[StoreProducts.storeId]         = storeId
                it[StoreProducts.localPrice]      = request.localPrice?.toBigDecimal()
                it[StoreProducts.localCostPrice]  = request.localCostPrice?.toBigDecimal()
                it[StoreProducts.localStockQty]   = 0
                it[StoreProducts.minStockQty]     = 0
                it[StoreProducts.isActive]        = true
                it[StoreProducts.syncVersion]     = 1
                it[StoreProducts.createdAt]       = now
                it[StoreProducts.updatedAt]       = now
            }
            count++
        }
        logger.info("Bulk assigned master product $masterProductId to ${count} stores")
        count
    }

    // ── Internal helpers ──

    private fun getByIdInternal(id: String): MasterProductDto? {
        val row = MasterProducts.selectAll().where { MasterProducts.id eq id }.singleOrNull()
            ?: return null
        val storeCount = StoreProducts.selectAll()
            .where { StoreProducts.masterProductId eq id }
            .count()
            .toInt()
        return row.toMasterProductDto(storeCount)
    }

    private fun ResultRow.toMasterProductDto(storeCount: Int = 0) = MasterProductDto(
        id          = this[MasterProducts.id],
        sku         = this[MasterProducts.sku],
        barcode     = this[MasterProducts.barcode],
        name        = this[MasterProducts.name],
        description = this[MasterProducts.description],
        basePrice   = this[MasterProducts.basePrice].toDouble(),
        costPrice   = this[MasterProducts.costPrice].toDouble(),
        categoryId  = this[MasterProducts.categoryId],
        unitId      = this[MasterProducts.unitId],
        taxGroupId  = this[MasterProducts.taxGroupId],
        imageUrl    = this[MasterProducts.imageUrl],
        isActive    = this[MasterProducts.isActive],
        createdAt   = this[MasterProducts.createdAt].toInstant().toEpochMilli(),
        updatedAt   = this[MasterProducts.updatedAt].toInstant().toEpochMilli(),
        storeCount  = storeCount,
    )

}

// Stores table reference for JOIN (mirrors V1 stores table)
private object Stores : Table("stores") {
    val id   = text("id")
    val name = text("name")
    override val primaryKey = PrimaryKey(id)
}
