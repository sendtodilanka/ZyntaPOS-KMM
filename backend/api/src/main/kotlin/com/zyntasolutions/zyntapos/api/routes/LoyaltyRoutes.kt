package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.sync.Customers
import io.ktor.http.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

fun Route.loyaltyRoutes() {
    route("/loyalty") {

        /**
         * GET /v1/loyalty/summary
         *
         * Returns store-level loyalty programme summary statistics for the
         * current store (derived from JWT storeId claim).
         *
         * Response:
         *   totalCustomers    — number of active customers with any loyalty points
         *   totalPoints       — sum of all loyalty points in circulation
         *   averagePoints     — mean points per participating customer (0 if none)
         *   topTierCustomers  — customers with ≥ 1000 points (configurable in future)
         */
        get("/summary") {
            val principal = call.principal<JWTPrincipal>()!!
            val storeId = principal.payload.getClaim("storeId").asString()

            val summary = newSuspendedTransaction {
                val activeCustomers = Customers.selectAll().where {
                    (Customers.storeId eq storeId) and (Customers.isActive eq true)
                }.toList()

                val totalCustomers = activeCustomers.size
                val totalPoints = activeCustomers.sumOf { it[Customers.loyaltyPoints].toLong() }
                val participatingCustomers = activeCustomers.count { it[Customers.loyaltyPoints] > 0 }
                val averagePoints = if (participatingCustomers > 0)
                    totalPoints.toDouble() / participatingCustomers else 0.0
                val topTierCustomers = activeCustomers.count { it[Customers.loyaltyPoints] >= 1000 }

                LoyaltySummaryResponse(
                    totalCustomers       = totalCustomers,
                    totalPoints          = totalPoints,
                    participatingCustomers = participatingCustomers,
                    averagePoints        = averagePoints,
                    topTierCustomers     = topTierCustomers,
                )
            }

            call.respond(HttpStatusCode.OK, summary)
        }
    }
}

@Serializable
data class LoyaltySummaryResponse(
    val totalCustomers: Int,
    val totalPoints: Long,
    val participatingCustomers: Int,
    val averagePoints: Double,
    val topTierCustomers: Int,
)
