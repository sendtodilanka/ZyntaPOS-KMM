package com.zyntasolutions.zyntapos.api.service

import com.zyntasolutions.zyntapos.api.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

// ── Exposed tables ────────────────────────────────────────────────────────────

object FeatureFlags : Table("feature_flags") {
    val key                = text("key")
    val name               = text("name")
    val description        = text("description")
    val enabled            = bool("enabled")
    val category           = text("category")
    val editionsAvailable  = text("editions_available")   // TEXT[] stored as pg array literal
    val modifiedBy         = text("modified_by")
    val updatedAt          = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(key)
}

object TaxRates : Table("tax_rates") {
    val id            = uuid("id")
    val name          = text("name")
    val rate          = decimal("rate", 5, 2)
    val description   = text("description")
    val applicableTo  = text("applicable_to")   // TEXT[] stored as pg array literal
    val isDefault     = bool("is_default")
    val country       = text("country")
    val region        = text("region").nullable()
    val isActive      = bool("is_active")
    val createdAt     = timestampWithTimeZone("created_at")
    val updatedAt     = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object SystemConfig : Table("system_config") {
    val key         = text("key")
    val value       = text("value")
    val type        = text("type")
    val description = text("description")
    val category    = text("category")
    val editable    = bool("editable")
    val sensitive   = bool("sensitive")
    val updatedAt   = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(key)
}

// ── Service ──────────────────────────────────────────────────────────────────

class AdminConfigService {

    suspend fun listFeatureFlags(): List<AdminFeatureFlag> = newSuspendedTransaction {
        FeatureFlags.selectAll().orderBy(FeatureFlags.category).map { it.toFeatureFlag() }
    }

    suspend fun toggleFeatureFlag(key: String, enabled: Boolean, modifiedBy: String): AdminFeatureFlag? =
        newSuspendedTransaction {
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            FeatureFlags.upsert(FeatureFlags.key) {
                it[FeatureFlags.key]         = key
                it[FeatureFlags.enabled]     = enabled
                it[FeatureFlags.modifiedBy]  = modifiedBy
                it[FeatureFlags.updatedAt]   = now
                it[FeatureFlags.name]        = key.replace('_', ' ').replaceFirstChar { c -> c.uppercase() }
                it[FeatureFlags.description] = ""
                it[FeatureFlags.category]    = "general"
                it[FeatureFlags.editionsAvailable] = "{}"
            }
            FeatureFlags.selectAll().where { FeatureFlags.key eq key }.singleOrNull()?.toFeatureFlag()
        }

    suspend fun listTaxRates(): List<AdminTaxRate> = newSuspendedTransaction {
        TaxRates.selectAll().where { TaxRates.isActive eq true }
            .orderBy(TaxRates.createdAt)
            .map { it.toTaxRate() }
    }

    suspend fun createTaxRate(req: TaxRateCreateRequest): AdminTaxRate = newSuspendedTransaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val newId = UUID.randomUUID()
        TaxRates.insert {
            it[id]           = newId
            it[name]         = req.name
            it[rate]         = req.rate.toBigDecimal()
            it[description]  = req.description
            it[applicableTo] = toPgArray(req.applicableTo)
            it[isDefault]    = req.isDefault
            it[country]      = req.country
            it[region]       = req.region
            it[isActive]     = true
            it[createdAt]    = now
            it[updatedAt]    = now
        }
        TaxRates.selectAll().where { TaxRates.id eq newId }.single().toTaxRate()
    }

    suspend fun updateTaxRate(id: UUID, req: TaxRateUpdateRequest): AdminTaxRate? =
        newSuspendedTransaction {
            TaxRates.selectAll().where { TaxRates.id eq id }.singleOrNull()
                ?: return@newSuspendedTransaction null

            val now = OffsetDateTime.now(ZoneOffset.UTC)
            TaxRates.update({ TaxRates.id eq id }) { stmt ->
                req.name?.let { stmt[name] = it }
                req.rate?.let { stmt[rate] = it.toBigDecimal() }
                req.description?.let { stmt[description] = it }
                req.applicableTo?.let { stmt[applicableTo] = toPgArray(it) }
                req.isDefault?.let { stmt[isDefault] = it }
                req.country?.let { stmt[country] = it }
                req.region?.let { stmt[region] = it }
                stmt[updatedAt] = now
            }
            TaxRates.selectAll().where { TaxRates.id eq id }.single().toTaxRate()
        }

    suspend fun deleteTaxRate(id: UUID): Boolean = newSuspendedTransaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val updated = TaxRates.update({ TaxRates.id eq id }) {
            it[isActive] = false
            it[updatedAt] = now
        }
        updated > 0
    }

    suspend fun listSystemConfig(): List<AdminSystemConfig> = newSuspendedTransaction {
        SystemConfig.selectAll()
            .where { SystemConfig.sensitive eq false }
            .orderBy(SystemConfig.category)
            .map { it.toSystemConfig() }
    }

    suspend fun updateSystemConfig(key: String, value: String, modifiedBy: String): AdminSystemConfig? =
        newSuspendedTransaction {
            val existing = SystemConfig.selectAll().where { SystemConfig.key eq key }.singleOrNull()
                ?: return@newSuspendedTransaction null

            if (!existing[SystemConfig.editable]) return@newSuspendedTransaction existing.toSystemConfig()

            val now = OffsetDateTime.now(ZoneOffset.UTC)
            SystemConfig.update({ SystemConfig.key eq key }) {
                it[SystemConfig.value] = value
                it[updatedAt] = now
            }
            SystemConfig.selectAll().where { SystemConfig.key eq key }.single().toSystemConfig()
        }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun ResultRow.toFeatureFlag() = AdminFeatureFlag(
        key               = this[FeatureFlags.key],
        name              = this[FeatureFlags.name],
        description       = this[FeatureFlags.description],
        enabled           = this[FeatureFlags.enabled],
        category          = this[FeatureFlags.category],
        editionsAvailable = fromPgArray(this[FeatureFlags.editionsAvailable]),
        lastModified      = this[FeatureFlags.updatedAt].toInstant().toString(),
        modifiedBy        = this[FeatureFlags.modifiedBy]
    )

    private fun ResultRow.toTaxRate() = AdminTaxRate(
        id           = this[TaxRates.id].toString(),
        name         = this[TaxRates.name],
        rate         = this[TaxRates.rate].toDouble(),
        description  = this[TaxRates.description],
        applicableTo = fromPgArray(this[TaxRates.applicableTo]),
        isDefault    = this[TaxRates.isDefault],
        country      = this[TaxRates.country],
        region       = this[TaxRates.region],
        active       = this[TaxRates.isActive]
    )

    private fun ResultRow.toSystemConfig() = AdminSystemConfig(
        key         = this[SystemConfig.key],
        value       = this[SystemConfig.value],
        type        = this[SystemConfig.type],
        description = this[SystemConfig.description],
        category    = this[SystemConfig.category],
        editable    = this[SystemConfig.editable],
        sensitive   = this[SystemConfig.sensitive]
    )

    /** Converts a List<String> to PostgreSQL array literal: {val1,val2} */
    private fun toPgArray(list: List<String>): String =
        list.joinToString(",", "{", "}") { "\"${it.replace("\"", "\\\"")}\"" }

    /** Parses a PostgreSQL array literal {val1,val2} to List<String> */
    private fun fromPgArray(value: String): List<String> {
        if (value.isBlank() || value == "{}") return emptyList()
        return value.removeSurrounding("{", "}")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotEmpty() }
    }
}
