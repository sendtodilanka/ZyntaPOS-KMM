package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.auth.AdminPermissions
import com.zyntasolutions.zyntapos.api.repository.ExchangeRateRepository
import com.zyntasolutions.zyntapos.api.service.AdminAuthService
import com.zyntasolutions.zyntapos.common.ErrorResponse
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

/**
 * Admin panel routes for exchange rate management (C2.2 Multi-Currency).
 *
 * Per ADR-009: Exchange rates are a PLATFORM-LEVEL operation (not store-level).
 * Zynta Solutions staff manage exchange rates centrally. Stores consume rates
 * for multi-currency display and conversion.
 *
 * Endpoints:
 *   GET  /admin/exchange-rates  — list all exchange rates
 *   PUT  /admin/exchange-rates  — upsert an exchange rate
 */
fun Route.adminExchangeRateRoutes() {
    val repo: ExchangeRateRepository by inject()
    val authService: AdminAuthService by inject()

    route("/admin/exchange-rates") {

        /**
         * GET /admin/exchange-rates
         *
         * Returns all exchange rates.
         * Roles: ADMIN, OPERATOR, FINANCE (inventory:read)
         */
        get {
            val user = resolveAdminUser(call, authService) ?: return@get
            if (!AdminPermissions.check(user.role, "inventory:read")) {
                return@get call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("FORBIDDEN", "inventory:read permission required"),
                )
            }
            val rates = repo.getRates()
            call.respond(HttpStatusCode.OK, ExchangeRatesResponse(
                total = rates.size,
                rates = rates,
            ))
        }

        /**
         * PUT /admin/exchange-rates
         *
         * Upserts an exchange rate for a currency pair.
         * Roles: ADMIN, OPERATOR (inventory:write)
         */
        put {
            val user = resolveAdminUser(call, authService) ?: return@put
            if (!AdminPermissions.check(user.role, "inventory:write")) {
                return@put call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("FORBIDDEN", "inventory:write permission required"),
                )
            }
            val body = call.receive<UpsertExchangeRateRequest>()
            if (body.sourceCurrency.length != 3 || body.targetCurrency.length != 3) {
                return@put call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("VALIDATION_ERROR", "Currency codes must be 3-letter ISO 4217"),
                )
            }
            if (body.sourceCurrency == body.targetCurrency) {
                return@put call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("VALIDATION_ERROR", "Source and target currencies must be different"),
                )
            }
            if (body.rate <= 0) {
                return@put call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("VALIDATION_ERROR", "Rate must be positive"),
                )
            }
            val rate = repo.upsertRate(
                sourceCurrency = body.sourceCurrency.uppercase(),
                targetCurrency = body.targetCurrency.uppercase(),
                rate = body.rate,
                source = body.source ?: "MANUAL",
            )
            call.respond(HttpStatusCode.OK, rate)
        }
    }
}

@Serializable
data class ExchangeRatesResponse(
    val total: Int,
    val rates: List<com.zyntasolutions.zyntapos.api.repository.ExchangeRateRow>,
)

@Serializable
data class UpsertExchangeRateRequest(
    val sourceCurrency: String,
    val targetCurrency: String,
    val rate: Double,
    val source: String? = null,
)
