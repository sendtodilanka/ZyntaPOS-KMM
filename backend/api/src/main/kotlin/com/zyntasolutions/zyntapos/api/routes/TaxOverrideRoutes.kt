package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.db.RegionalTaxOverrides
import com.zyntasolutions.zyntapos.api.sync.TaxGroups
import com.zyntasolutions.zyntapos.common.ErrorResponse
import io.ktor.http.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.upsert
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

fun Route.taxOverrideRoutes() {
    route("/taxes") {

        /**
         * GET /v1/taxes/overrides
         *
         * Returns the active regional tax overrides for the current store.
         * Overrides let a store apply a jurisdiction-specific effective rate
         * that differs from the master tax group rate.
         *
         * Response: list of TaxOverrideDto, ordered by tax_group_id.
         */
        get("/overrides") {
            val principal = call.principal<JWTPrincipal>()!!
            val storeId = principal.payload.getClaim("storeId").asString()

            val overrides = newSuspendedTransaction {
                RegionalTaxOverrides.selectAll().where {
                    (RegionalTaxOverrides.storeId eq storeId) and
                    (RegionalTaxOverrides.isActive eq true)
                }.orderBy(RegionalTaxOverrides.taxGroupId)
                .map { row ->
                    TaxOverrideDto(
                        id                    = row[RegionalTaxOverrides.id],
                        taxGroupId            = row[RegionalTaxOverrides.taxGroupId],
                        storeId               = row[RegionalTaxOverrides.storeId],
                        effectiveRate         = row[RegionalTaxOverrides.effectiveRate].toDouble(),
                        jurisdictionCode      = row[RegionalTaxOverrides.jurisdictionCode],
                        taxRegistrationNumber = row[RegionalTaxOverrides.taxRegistrationNumber],
                        validFrom             = row[RegionalTaxOverrides.validFrom]?.toString(),
                        validTo               = row[RegionalTaxOverrides.validTo]?.toString(),
                        isActive              = row[RegionalTaxOverrides.isActive],
                        createdAt             = row[RegionalTaxOverrides.createdAt].toString(),
                        updatedAt             = row[RegionalTaxOverrides.updatedAt].toString(),
                    )
                }
            }

            call.respond(HttpStatusCode.OK, overrides)
        }

        /**
         * POST /v1/taxes/overrides
         *
         * Creates or updates a regional tax override for the current store.
         * If an active override already exists for the given taxGroupId, it is
         * replaced (upsert on id — client must supply a stable UUID per group).
         *
         * Body: TaxOverrideRequest
         * Response 200: TaxOverrideDto (the saved record)
         * Response 400: validation error
         * Response 404: taxGroupId not found in this store's tax_groups
         */
        post("/overrides") {
            val principal = call.principal<JWTPrincipal>()!!
            val storeId = principal.payload.getClaim("storeId").asString()

            val body = runCatching { call.receive<TaxOverrideRequest>() }.getOrElse {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("BAD_REQUEST", "Invalid request body")
                )
            }

            if (body.effectiveRate < 0.0 || body.effectiveRate > 100.0) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("BAD_REQUEST", "effectiveRate must be between 0 and 100")
                )
            }

            val saved = newSuspendedTransaction {
                // Verify the tax group belongs to this store
                val taxGroupExists = TaxGroups.selectAll().where {
                    (TaxGroups.id eq body.taxGroupId) and (TaxGroups.storeId eq storeId)
                }.count() > 0

                if (!taxGroupExists) return@newSuspendedTransaction null

                val now = OffsetDateTime.now(ZoneOffset.UTC)
                val recordId = body.id ?: UUID.randomUUID().toString()

                RegionalTaxOverrides.upsert(RegionalTaxOverrides.id) {
                    it[id]                    = recordId
                    it[RegionalTaxOverrides.taxGroupId]            = body.taxGroupId
                    it[RegionalTaxOverrides.storeId]               = storeId
                    it[effectiveRate]         = body.effectiveRate.toBigDecimal()
                    it[jurisdictionCode]      = body.jurisdictionCode ?: ""
                    it[taxRegistrationNumber] = body.taxRegistrationNumber ?: ""
                    it[validFrom]             = body.validFrom?.let { ts ->
                        runCatching { OffsetDateTime.parse(ts) }.getOrNull()
                    }
                    it[validTo]               = body.validTo?.let { ts ->
                        runCatching { OffsetDateTime.parse(ts) }.getOrNull()
                    }
                    it[isActive]              = true
                    it[createdAt]             = now
                    it[updatedAt]             = now
                }

                RegionalTaxOverrides.selectAll().where {
                    RegionalTaxOverrides.id eq recordId
                }.single().let { row ->
                    TaxOverrideDto(
                        id                    = row[RegionalTaxOverrides.id],
                        taxGroupId            = row[RegionalTaxOverrides.taxGroupId],
                        storeId               = row[RegionalTaxOverrides.storeId],
                        effectiveRate         = row[RegionalTaxOverrides.effectiveRate].toDouble(),
                        jurisdictionCode      = row[RegionalTaxOverrides.jurisdictionCode],
                        taxRegistrationNumber = row[RegionalTaxOverrides.taxRegistrationNumber],
                        validFrom             = row[RegionalTaxOverrides.validFrom]?.toString(),
                        validTo               = row[RegionalTaxOverrides.validTo]?.toString(),
                        isActive              = row[RegionalTaxOverrides.isActive],
                        createdAt             = row[RegionalTaxOverrides.createdAt].toString(),
                        updatedAt             = row[RegionalTaxOverrides.updatedAt].toString(),
                    )
                }
            }

            if (saved == null) {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse("NOT_FOUND", "Tax group '${body.taxGroupId}' not found for this store")
                )
            } else {
                call.respond(HttpStatusCode.OK, saved)
            }
        }
    }
}

// ── Request / Response DTOs ──────────────────────────────────────────────────

@Serializable
data class TaxOverrideRequest(
    val id: String? = null,
    @SerialName("tax_group_id")            val taxGroupId: String,
    @SerialName("effective_rate")          val effectiveRate: Double,
    @SerialName("jurisdiction_code")       val jurisdictionCode: String? = null,
    @SerialName("tax_registration_number") val taxRegistrationNumber: String? = null,
    @SerialName("valid_from")              val validFrom: String? = null,
    @SerialName("valid_to")               val validTo: String? = null,
)

@Serializable
data class TaxOverrideDto(
    val id: String,
    @SerialName("tax_group_id")            val taxGroupId: String,
    @SerialName("store_id")                val storeId: String,
    @SerialName("effective_rate")          val effectiveRate: Double,
    @SerialName("jurisdiction_code")       val jurisdictionCode: String,
    @SerialName("tax_registration_number") val taxRegistrationNumber: String,
    @SerialName("valid_from")              val validFrom: String?,
    @SerialName("valid_to")               val validTo: String?,
    @SerialName("is_active")              val isActive: Boolean,
    @SerialName("created_at")             val createdAt: String,
    @SerialName("updated_at")             val updatedAt: String,
)
