package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.db.Master_products
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.MasterProduct
import com.zyntasolutions.zyntapos.domain.repository.MasterProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Concrete implementation of [MasterProductRepository] delegating to SQLDelight queries.
 *
 * Master products are read-only on POS devices — synced from backend via pull.
 * No [SyncEnqueuer] is used because master products never originate from devices.
 */
class MasterProductRepositoryImpl(
    private val db: ZyntaDatabase,
) : MasterProductRepository {

    private val q get() = db.master_productsQueries

    override fun getAll(): Flow<List<MasterProduct>> =
        q.getAllMasterProducts()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override suspend fun getById(id: String): Result<MasterProduct> = withContext(Dispatchers.IO) {
        runCatching {
            q.getMasterProductById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(
                    DatabaseException("Master product not found: $id", operation = "getMasterProductById")
                )
        }.fold(
            onSuccess = { row -> Result.Success(toDomain(row)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun getByBarcode(barcode: String): Result<MasterProduct> = withContext(Dispatchers.IO) {
        runCatching {
            q.getMasterProductByBarcode(barcode).executeAsOneOrNull()
                ?: return@withContext Result.Error(
                    DatabaseException("No master product for barcode: $barcode", operation = "getMasterProductByBarcode")
                )
        }.fold(
            onSuccess = { row -> Result.Success(toDomain(row)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override fun search(query: String): Flow<List<MasterProduct>> {
        if (query.isBlank()) return getAll()
        val ftsQuery = toFtsQuery(query)
        return q.searchMasterProducts(ftsQuery)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }
    }

    override suspend fun upsertFromSync(masterProduct: MasterProduct): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            q.upsertMasterProduct(
                id          = masterProduct.id,
                sku         = masterProduct.sku,
                barcode     = masterProduct.barcode,
                name        = masterProduct.name,
                description = masterProduct.description,
                base_price  = masterProduct.basePrice,
                cost_price  = masterProduct.costPrice,
                category_id = masterProduct.categoryId,
                unit_id     = masterProduct.unitId,
                tax_group_id = masterProduct.taxGroupId,
                image_url   = masterProduct.imageUrl,
                is_active   = if (masterProduct.isActive) 1L else 0L,
                created_at  = masterProduct.createdAt.toEpochMilliseconds(),
                updated_at  = masterProduct.updatedAt.toEpochMilliseconds(),
                sync_status = "SYNCED",
            )
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Upsert failed", cause = t)) },
        )
    }

    override suspend fun getCount(): Int = withContext(Dispatchers.IO) {
        q.countMasterProducts().executeAsOne().toInt()
    }

    companion object {
        private val syncJson = Json { ignoreUnknownKeys = true; isLenient = true }
    }

    /** Applies a server-authoritative master product from a JSON sync payload. */
    suspend fun upsertFromSyncPayload(payload: String) = withContext(Dispatchers.IO) {
        val dto = syncJson.decodeFromString<MasterProductSyncDto>(payload)
        val isActive = if (dto.isActive) 1L else 0L
        q.upsertMasterProduct(
            id          = dto.id,
            sku         = dto.sku,
            barcode     = dto.barcode,
            name        = dto.name,
            description = dto.description,
            base_price  = dto.basePrice,
            cost_price  = dto.costPrice,
            category_id = dto.categoryId,
            unit_id     = dto.unitId,
            tax_group_id = dto.taxGroupId,
            image_url   = dto.imageUrl,
            is_active   = isActive,
            created_at  = dto.createdAt,
            updated_at  = dto.updatedAt,
            sync_status = "SYNCED",
        )
    }

    private fun toDomain(row: Master_products): MasterProduct = MasterProduct(
        id          = row.id,
        sku         = row.sku,
        barcode     = row.barcode,
        name        = row.name,
        description = row.description,
        basePrice   = row.base_price,
        costPrice   = row.cost_price,
        categoryId  = row.category_id,
        unitId      = row.unit_id,
        taxGroupId  = row.tax_group_id,
        imageUrl    = row.image_url,
        isActive    = row.is_active == 1L,
        createdAt   = Instant.fromEpochMilliseconds(row.created_at),
        updatedAt   = Instant.fromEpochMilliseconds(row.updated_at),
    )

    private fun toFtsQuery(raw: String): String =
        raw.trim()
            .replace("\"", "")
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .joinToString(" ") { "$it*" }
}

/** Internal DTO for deserializing master product sync payloads. */
@Serializable
internal data class MasterProductSyncDto(
    val id: String,
    val sku: String? = null,
    val barcode: String? = null,
    val name: String,
    val description: String? = null,
    @SerialName("base_price") val basePrice: Double = 0.0,
    @SerialName("cost_price") val costPrice: Double = 0.0,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("unit_id") val unitId: String? = null,
    @SerialName("tax_group_id") val taxGroupId: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
)
