package com.zyntasolutions.zyntapos.api.service

import com.zyntasolutions.zyntapos.api.db.Stores
import com.zyntasolutions.zyntapos.api.db.SyncQueue
import com.zyntasolutions.zyntapos.api.models.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

class AdminMetricsService {

    suspend fun getDashboardKPIs(period: String): DashboardKPIs = newSuspendedTransaction {
        val totalStores = Stores.selectAll().count().toInt()
        val activeStores = Stores.selectAll().where { Stores.isActive eq true }.count().toInt()

        val now = OffsetDateTime.now(ZoneOffset.UTC)
        // Current window
        val (since, prevSince, prevUntil) = when (period) {
            "today" -> {
                val todayStart = now.toLocalDate().atStartOfDay(ZoneOffset.UTC).toOffsetDateTime()
                Triple(todayStart, todayStart.minusDays(1), todayStart)
            }
            "week"  -> Triple(now.minusDays(7), now.minusDays(14), now.minusDays(7))
            else    -> Triple(now.minusDays(30), now.minusDays(60), now.minusDays(30))
        }

        val totalPending = SyncQueue.selectAll()
            .where { SyncQueue.isProcessed eq false }
            .count().toInt()

        val syncHealthPct = if (totalStores == 0) 100.0 else {
            val storesWithPendingIssues = SyncQueue.selectAll()
                .where { SyncQueue.isProcessed eq false }
                .groupBy { it[SyncQueue.storeId] }
                .count { (_, ops) -> ops.size > 10 }
            ((totalStores - storesWithPendingIssues) * 100.0) / totalStores
        }

        // Revenue trend: compare current window to previous window
        val currentRevenue = estimateRevenue(since, now)
        val prevRevenue    = estimateRevenue(prevSince, prevUntil)
        val revenueTrend   = if (prevRevenue > 0.0) ((currentRevenue - prevRevenue) / prevRevenue) * 100.0 else 0.0

        // Stores trend: new stores added in current vs previous window
        val storesInCurrent = Stores.selectAll()
            .where { Stores.createdAt greaterEq since }.count().toInt()
        val storesInPrev    = Stores.selectAll()
            .where { (Stores.createdAt greaterEq prevSince) and (Stores.createdAt less prevUntil) }.count().toInt()
        val storesTrend = if (storesInPrev > 0) ((storesInCurrent - storesInPrev) * 100.0) / storesInPrev else 0.0

        val activeAlerts = AlertInstances.selectAll()
            .where { AlertInstances.status eq "active" }
            .count().toInt()

        DashboardKPIs(
            totalStores          = totalStores,
            totalStoresTrend     = storesTrend,
            activeLicenses       = activeStores,
            activeLicensesTrend  = 0.0,   // license trend requires license-service query (cross-DB — omit for now)
            revenueToday         = currentRevenue,
            revenueTodayTrend    = revenueTrend,
            syncHealthPercent    = syncHealthPct,
            syncHealthTrend      = 0.0,
            currency             = "LKR"
        )
    }

    suspend fun getSalesChart(period: String, storeId: String?): List<SalesChartData> =
        newSuspendedTransaction {
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            val since = when (period) {
                "7d"   -> now.minusDays(7)
                "30d"  -> now.minusDays(30)
                "90d"  -> now.minusDays(90)
                "12m"  -> now.minusMonths(12)
                "today"-> now.toLocalDate().atStartOfDay(ZoneOffset.UTC).toOffsetDateTime()
                "week" -> now.minusDays(7)
                else   -> now.minusDays(30)
            }

            var query = SyncQueue.selectAll().where {
                (SyncQueue.entityType eq "ORDER") and
                (SyncQueue.isProcessed eq true) and
                (SyncQueue.serverTs greaterEq since)
            }
            if (!storeId.isNullOrBlank()) {
                query = query.adjustWhere { SyncQueue.storeId eq storeId }
            }

            // Group by day
            val byDay = query.groupBy { row ->
                row[SyncQueue.serverTs].toLocalDate().toString()
            }

            byDay.entries.sortedBy { it.key }.map { (date, rows) ->
                val revenue = rows.sumOf { extractTotal(it[SyncQueue.payload]) }
                SalesChartData(
                    period             = date,
                    revenue            = revenue,
                    orders             = rows.size,
                    averageOrderValue  = if (rows.isEmpty()) 0.0 else revenue / rows.size
                )
            }
        }

    suspend fun getStoreComparison(period: String): List<StoreComparisonData> =
        newSuspendedTransaction {
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            val (since, prevSince, prevUntil) = when (period) {
                "today" -> {
                    val todayStart = now.toLocalDate().atStartOfDay(ZoneOffset.UTC).toOffsetDateTime()
                    val yesterdayStart = todayStart.minusDays(1)
                    Triple(todayStart, yesterdayStart, todayStart)
                }
                "week" -> Triple(now.minusDays(7), now.minusDays(14), now.minusDays(7))
                else   -> Triple(now.minusDays(30), now.minusDays(60), now.minusDays(30))
            }

            val storeMap = Stores.selectAll().associate { it[Stores.id] to it[Stores.name] }

            val currentRows = SyncQueue.selectAll().where {
                (SyncQueue.entityType eq "ORDER") and
                (SyncQueue.isProcessed eq true) and
                (SyncQueue.serverTs greaterEq since)
            }.groupBy { it[SyncQueue.storeId] }

            val prevRows = SyncQueue.selectAll().where {
                (SyncQueue.entityType eq "ORDER") and
                (SyncQueue.isProcessed eq true) and
                (SyncQueue.serverTs greaterEq prevSince) and
                (SyncQueue.serverTs less prevUntil)
            }.groupBy { it[SyncQueue.storeId] }

            currentRows.map { (storeId, rows) ->
                val revenue = rows.sumOf { extractTotal(it[SyncQueue.payload]) }
                val prevRevenue = prevRows[storeId]?.sumOf { extractTotal(it[SyncQueue.payload]) } ?: 0.0
                val growth = if (prevRevenue > 0.0) ((revenue - prevRevenue) / prevRevenue) * 100.0 else 0.0
                StoreComparisonData(
                    storeId   = storeId,
                    storeName = storeMap[storeId] ?: storeId,
                    revenue   = revenue,
                    orders    = rows.size,
                    growth    = growth
                )
            }.sortedByDescending { it.revenue }
        }

    suspend fun getSalesReport(
        from: String?,
        to: String?,
        storeId: String?,
        page: Int,
        size: Int
    ): AdminPagedResponse<SalesReportRow> = newSuspendedTransaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val fromTs = from?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() } ?: now.minusDays(30)
        val toTs   = to?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() } ?: now

        var query = SyncQueue.selectAll().where {
            (SyncQueue.entityType eq "ORDER") and
            (SyncQueue.serverTs greaterEq fromTs) and
            (SyncQueue.serverTs lessEq toTs)
        }
        if (!storeId.isNullOrBlank()) {
            query = query.adjustWhere { SyncQueue.storeId eq storeId }
        }

        val storeMap = Stores.selectAll().associate { it[Stores.id] to it[Stores.name] }
        val allRows = query.orderBy(SyncQueue.serverTs, SortOrder.DESC).toList()
        val total = allRows.size
        val items = allRows.drop(page * size).take(size).map { row ->
            val revenue = extractTotal(row[SyncQueue.payload])
            SalesReportRow(
                date              = row[SyncQueue.serverTs].toLocalDate().toString(),
                revenue           = revenue,
                orders            = 1,
                averageOrderValue = revenue,
                refunds           = 0.0,
                netRevenue        = revenue,
                storeId           = row[SyncQueue.storeId],
                storeName         = storeMap[row[SyncQueue.storeId]]
            )
        }

        AdminPagedResponse(
            data       = items,
            page       = page,
            size       = size,
            total      = total,
            totalPages = ceil(total.toDouble() / size).toInt()
        )
    }

    suspend fun getProductPerformance(
        from: String?,
        to: String?,
        storeId: String?,
        page: Int,
        size: Int
    ): AdminPagedResponse<ProductPerformanceRow> = newSuspendedTransaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val fromTs = from?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() } ?: now.minusDays(30)
        val toTs   = to?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() } ?: now

        var query = SyncQueue.selectAll().where {
            (SyncQueue.entityType eq "ORDER_ITEM") and
            (SyncQueue.serverTs greaterEq fromTs) and
            (SyncQueue.serverTs lessEq toTs)
        }
        if (!storeId.isNullOrBlank()) {
            query = query.adjustWhere { SyncQueue.storeId eq storeId }
        }

        val storeMap = Stores.selectAll().associate { it[Stores.id] to it[Stores.name] }
        val allRows = query.toList()

        // Build cost price map from Products table for best-effort margin calculation
        val productCostMap: Map<String, Double> = Products.selectAll()
            .associate { it[Products.id] to it[Products.costPrice].toDouble() }

        // Group by productId from payload
        val byProduct = allRows.groupBy { row ->
            extractField(row[SyncQueue.payload], "productId") ?: row[SyncQueue.entityId]
        }

        val total = byProduct.size
        val items = byProduct.entries
            .drop(page * size).take(size)
            .map { (productId, rows) ->
                val revenue = rows.sumOf { extractTotal(it[SyncQueue.payload]) }
                val unitsSold = rows.sumOf {
                    extractField(it[SyncQueue.payload], "quantity")?.toDoubleOrNull()?.toInt() ?: 1
                }
                val costPrice = productCostMap[productId] ?: 0.0
                val costTotal = costPrice * unitsSold
                val marginPercent = if (revenue > 0.0) ((revenue - costTotal) / revenue) * 100.0 else 0.0
                ProductPerformanceRow(
                    productId    = productId,
                    productName  = extractField(rows.first()[SyncQueue.payload], "productName") ?: productId,
                    category     = extractField(rows.first()[SyncQueue.payload], "category") ?: "",
                    unitsSold    = unitsSold,
                    revenue      = revenue,
                    marginPercent = marginPercent,
                    storeId      = rows.first()[SyncQueue.storeId],
                    storeName    = storeMap[rows.first()[SyncQueue.storeId]]
                )
            }

        AdminPagedResponse(
            data       = items,
            page       = page,
            size       = size,
            total      = total,
            totalPages = ceil(total.toDouble() / size).toInt()
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun estimateRevenue(from: OffsetDateTime, to: OffsetDateTime): Double {
        return try {
            SyncQueue.selectAll().where {
                (SyncQueue.entityType eq "ORDER") and
                (SyncQueue.serverTs greaterEq from) and
                (SyncQueue.serverTs lessEq to)
            }.sumOf { extractTotal(it[SyncQueue.payload]) }
        } catch (_: Exception) { 0.0 }
    }

    private fun extractTotal(payload: String): Double = try {
        val json = Json.parseToJsonElement(payload) as? JsonObject
        json?.get("total")?.jsonPrimitive?.doubleOrNull ?: 0.0
    } catch (_: Exception) { 0.0 }

    private fun extractField(payload: String, field: String): String? = try {
        val json = Json.parseToJsonElement(payload) as? JsonObject
        json?.get(field)?.jsonPrimitive?.content
    } catch (_: Exception) { null }
}
