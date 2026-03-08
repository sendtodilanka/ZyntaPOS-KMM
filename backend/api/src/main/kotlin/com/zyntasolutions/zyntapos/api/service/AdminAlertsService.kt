package com.zyntasolutions.zyntapos.api.service

import com.zyntasolutions.zyntapos.api.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.ceil

// ── Exposed tables ────────────────────────────────────────────────────────────

object AlertRules : Table("alert_rules") {
    val id             = uuid("id")
    val name           = text("name")
    val description    = text("description")
    val category       = text("category")
    val severity       = text("severity")
    val enabled        = bool("enabled")
    val conditions     = text("conditions")   // JSONB stored as text
    val notifyChannels = text("notify_channels").array<String>()
    val createdAt      = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object AlertInstances : Table("alerts") {
    val id              = uuid("id")
    val ruleId          = uuid("rule_id").nullable()
    val title           = text("title")
    val message         = text("message")
    val severity        = text("severity")
    val status          = text("status")
    val category        = text("category")
    val storeId         = text("store_id").nullable()
    val storeName       = text("store_name").nullable()
    val metadata        = text("metadata")   // JSONB
    val firedAt         = timestampWithTimeZone("fired_at")
    val updatedAt       = timestampWithTimeZone("updated_at")
    val acknowledgedBy  = uuid("acknowledged_by").nullable()
    val acknowledgedAt  = timestampWithTimeZone("acknowledged_at").nullable()
    val resolvedAt      = timestampWithTimeZone("resolved_at").nullable()
    val silencedUntil   = timestampWithTimeZone("silenced_until").nullable()
    override val primaryKey = PrimaryKey(id)
}

// ── Service ──────────────────────────────────────────────────────────────────

class AdminAlertsService {

    suspend fun listAlerts(
        page: Int,
        pageSize: Int,
        status: String?,
        severity: String?,
        category: String?,
        storeId: String?
    ): AlertsPage = newSuspendedTransaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        var query = AlertInstances.selectAll()

        // Auto-expire silenced alerts where silenced_until has passed
        AlertInstances.update({
            (AlertInstances.status eq "silenced") and
            (AlertInstances.silencedUntil lessEq now)
        }) { it[AlertInstances.status] = "active"; it[AlertInstances.updatedAt] = now }

        if (!status.isNullOrBlank()) {
            query = query.adjustWhere { AlertInstances.status eq status.lowercase() }
        }
        if (!severity.isNullOrBlank()) {
            query = query.adjustWhere { AlertInstances.severity eq severity.lowercase() }
        }
        if (!category.isNullOrBlank()) {
            query = query.adjustWhere { AlertInstances.category eq category.lowercase() }
        }
        if (!storeId.isNullOrBlank()) {
            query = query.adjustWhere { AlertInstances.storeId eq storeId }
        }

        val total = query.count().toInt()
        val items = query
            .orderBy(AlertInstances.firedAt, SortOrder.DESC)
            .limit(pageSize, offset = (page * pageSize).toLong())
            .map { it.toAdminAlert() }

        AlertsPage(items = items, total = total, page = page, pageSize = pageSize)
    }

    suspend fun getCounts(): Map<String, Int> = newSuspendedTransaction {
        val all = AlertInstances.selectAll().where {
            AlertInstances.status inList listOf("active", "acknowledged")
        }.toList()

        mapOf(
            "active"       to all.count { it[AlertInstances.status] == "active" },
            "acknowledged" to all.count { it[AlertInstances.status] == "acknowledged" },
            "critical"     to all.count { it[AlertInstances.severity] == "critical" && it[AlertInstances.status] == "active" },
            "high"         to all.count { it[AlertInstances.severity] == "high" && it[AlertInstances.status] == "active" },
            "medium"       to all.count { it[AlertInstances.severity] == "medium" && it[AlertInstances.status] == "active" },
            "low"          to all.count { it[AlertInstances.severity] == "low" && it[AlertInstances.status] == "active" }
        )
    }

    suspend fun listRules(): List<AdminAlertRule> = newSuspendedTransaction {
        AlertRules.selectAll().map { it.toAlertRule() }
    }

    suspend fun acknowledgeAlert(alertId: UUID, adminId: UUID): AdminAlert? = newSuspendedTransaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        AlertInstances.update({ AlertInstances.id eq alertId }) {
            it[status] = "acknowledged"
            it[acknowledgedBy] = adminId
            it[acknowledgedAt] = now
            it[updatedAt] = now
        }
        AlertInstances.selectAll().where { AlertInstances.id eq alertId }.singleOrNull()?.toAdminAlert()
    }

    suspend fun resolveAlert(alertId: UUID): AdminAlert? = newSuspendedTransaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        AlertInstances.update({ AlertInstances.id eq alertId }) {
            it[status] = "resolved"
            it[resolvedAt] = now
            it[updatedAt] = now
        }
        AlertInstances.selectAll().where { AlertInstances.id eq alertId }.singleOrNull()?.toAdminAlert()
    }

    suspend fun silenceAlert(alertId: UUID, durationMinutes: Int): AdminAlert? = newSuspendedTransaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val silencedUntil = now.plusMinutes(durationMinutes.toLong())
        AlertInstances.update({ AlertInstances.id eq alertId }) {
            it[status] = "silenced"
            it[AlertInstances.silencedUntil] = silencedUntil
            it[updatedAt] = now
        }
        AlertInstances.selectAll().where { AlertInstances.id eq alertId }.singleOrNull()?.toAdminAlert()
    }

    suspend fun toggleRule(ruleId: UUID, enabled: Boolean): AdminAlertRule? = newSuspendedTransaction {
        AlertRules.update({ AlertRules.id eq ruleId }) { it[AlertRules.enabled] = enabled }
        AlertRules.selectAll().where { AlertRules.id eq ruleId }.singleOrNull()?.toAlertRule()
    }

    /** Evaluate all enabled alert rules and insert new alert instances as needed. */
    suspend fun evaluateRules(pendingOpsByStore: Map<String, Int>, storeNames: Map<String, String>) =
        newSuspendedTransaction {
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            val rules = AlertRules.selectAll().where { AlertRules.enabled eq true }.toList()

            for (rule in rules) {
                val category = rule[AlertRules.category]
                val severity = rule[AlertRules.severity]
                val ruleId = rule[AlertRules.id]

                when (category) {
                    "sync" -> {
                        for ((storeId, depth) in pendingOpsByStore) {
                            if (depth > 50) {
                                val existingActive = AlertInstances.selectAll().where {
                                    (AlertInstances.ruleId eq ruleId) and
                                    (AlertInstances.storeId eq storeId) and
                                    (AlertInstances.status inList listOf("active", "acknowledged", "silenced"))
                                }.none()

                                if (existingActive) {
                                    AlertInstances.insert {
                                        it[id]           = UUID.randomUUID()
                                        it[AlertInstances.ruleId]    = ruleId
                                        it[title]        = rule[AlertRules.name]
                                        it[message]      = "Store ${storeNames[storeId] ?: storeId}: sync queue depth is $depth (threshold: 50)"
                                        it[AlertInstances.severity]  = severity
                                        it[status]       = "active"
                                        it[AlertInstances.category]  = category
                                        it[AlertInstances.storeId]   = storeId
                                        it[storeName]    = storeNames[storeId]
                                        it[metadata]     = "{\"queueDepth\":$depth}"
                                        it[firedAt]      = now
                                        it[updatedAt]    = now
                                    }
                                }
                            }
                        }
                    }
                    // Additional rule types can be evaluated here
                }
            }
        }

    private fun ResultRow.toAdminAlert() = AdminAlert(
        id             = this[AlertInstances.id].toString(),
        title          = this[AlertInstances.title],
        message        = this[AlertInstances.message],
        severity       = this[AlertInstances.severity],
        status         = this[AlertInstances.status],
        category       = this[AlertInstances.category],
        storeId        = this[AlertInstances.storeId],
        storeName      = this[AlertInstances.storeName],
        createdAt      = this[AlertInstances.firedAt].toInstant().toString(),
        updatedAt      = this[AlertInstances.updatedAt].toInstant().toString(),
        acknowledgedBy = this[AlertInstances.acknowledgedBy]?.toString(),
        resolvedAt     = this[AlertInstances.resolvedAt]?.toInstant()?.toString()
    )

    private fun ResultRow.toAlertRule() = AdminAlertRule(
        id             = this[AlertRules.id].toString(),
        name           = this[AlertRules.name],
        description    = this[AlertRules.description],
        category       = this[AlertRules.category],
        severity       = this[AlertRules.severity],
        enabled        = this[AlertRules.enabled],
        conditions     = mapOf("raw" to this[AlertRules.conditions]),
        notifyChannels = this[AlertRules.notifyChannels].toList()
    )
}
