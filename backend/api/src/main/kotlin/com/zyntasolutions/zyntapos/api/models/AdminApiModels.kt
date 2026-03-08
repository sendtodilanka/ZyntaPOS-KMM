package com.zyntasolutions.zyntapos.api.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ── Generic paged response (matches admin panel PagedResponse<T>) ─────────────

@Serializable
data class AdminPagedResponse<T>(
    val data: List<T>,
    val page: Int,
    val size: Int,
    val total: Int,
    val totalPages: Int
)

// ── Stores ────────────────────────────────────────────────────────────────────

@Serializable
data class AdminStore(
    val id: String,
    val name: String,
    val location: String,
    val licenseKey: String,
    val edition: String,
    val status: String,
    val activeUsers: Int,
    val lastSyncAt: String?,
    val lastHeartbeatAt: String?,
    val appVersion: String,
    val createdAt: String
)

@Serializable
data class AdminStoreHealth(
    val storeId: String,
    val status: String,
    val healthScore: Int,
    val dbSizeBytes: Long,
    val syncQueueDepth: Int,
    val errorCount24h: Int,
    val uptimeHours: Double,
    val lastHeartbeatAt: String?,
    val responseTimeMs: Long,
    val appVersion: String,
    val osInfo: String
)

@Serializable
data class AdminStoreConfig(
    val storeId: String,
    val timezone: String,
    val currency: String,
    val updatedAt: String
)

@Serializable
data class AdminUpdateStoreConfigRequest(
    val timezone: String? = null,
    val currency: String? = null
)

// ── System Health ─────────────────────────────────────────────────────────────

@Serializable
data class ServiceHealth(
    val name: String,
    val status: String,
    val latencyMs: Long,
    val uptime: Double,
    val lastChecked: String,
    val version: String? = null,
    val details: Map<String, String> = emptyMap()
)

@Serializable
data class SystemHealth(
    val overall: String,
    val services: List<ServiceHealth>,
    val checkedAt: String
)

@Serializable
data class StoreHealthSummary(
    val storeId: String,
    val storeName: String,
    val status: String,
    val lastSync: String?,
    val pendingOperations: Int,
    val appVersion: String,
    val androidVersion: String,
    val uptimePercent: Double
)

@Serializable
data class StoreHealthDetail(
    val storeId: String,
    val storeName: String,
    val status: String,
    val lastSync: String?,
    val pendingOperations: Int,
    val appVersion: String,
    val androidVersion: String,
    val uptimePercent: Double,
    val recentActivity: List<HealthTimeSeries> = emptyList(),
    val errorLog: List<ErrorLogEntry> = emptyList()
)

@Serializable
data class HealthTimeSeries(val timestamp: String, val latencyMs: Long, val status: String)

@Serializable
data class ErrorLogEntry(val timestamp: String, val message: String, val severity: String)

// ── Audit ─────────────────────────────────────────────────────────────────────

@Serializable
data class AdminAuditEntry(
    val id: String,
    val eventType: String,
    val category: String,
    val userId: String?,
    val userName: String?,
    val storeId: String?,
    val storeName: String?,
    val entityType: String?,
    val entityId: String?,
    val previousValues: JsonElement?,
    val newValues: JsonElement?,
    val ipAddress: String?,
    val userAgent: String?,
    val success: Boolean,
    val errorMessage: String?,
    val hashChain: String,
    val createdAt: String
)

// ── Metrics ───────────────────────────────────────────────────────────────────

@Serializable
data class DashboardKPIs(
    val totalStores: Int,
    val totalStoresTrend: Double,
    val activeLicenses: Int,
    val activeLicensesTrend: Double,
    val revenueToday: Double,
    val revenueTodayTrend: Double,
    val syncHealthPercent: Double,
    val syncHealthTrend: Double,
    val currency: String
)

@Serializable
data class SalesChartData(
    val period: String,
    val revenue: Double,
    val orders: Int,
    val averageOrderValue: Double
)

@Serializable
data class StoreComparisonData(
    val storeId: String,
    val storeName: String,
    val revenue: Double,
    val orders: Int,
    val growth: Double
)

// ── Reports ───────────────────────────────────────────────────────────────────

@Serializable
data class SalesReportRow(
    val date: String,
    val revenue: Double,
    val orders: Int,
    val averageOrderValue: Double,
    val refunds: Double,
    val netRevenue: Double,
    val storeId: String? = null,
    val storeName: String? = null
)

@Serializable
data class ProductPerformanceRow(
    val productId: String,
    val productName: String,
    val category: String,
    val unitsSold: Int,
    val revenue: Double,
    val marginPercent: Double,
    val storeId: String? = null,
    val storeName: String? = null
)

// ── Alerts ────────────────────────────────────────────────────────────────────

@Serializable
data class AdminAlert(
    val id: String,
    val title: String,
    val message: String,
    val severity: String,
    val status: String,
    val category: String,
    val storeId: String?,
    val storeName: String?,
    val createdAt: String,
    val updatedAt: String,
    val acknowledgedBy: String?,
    val resolvedAt: String?,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class AlertsPage(
    val items: List<AdminAlert>,
    val total: Int,
    val page: Int,
    val pageSize: Int
)

@Serializable
data class AdminAlertRule(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val severity: String,
    val enabled: Boolean,
    val conditions: Map<String, String>,
    val notifyChannels: List<String>
)

@Serializable
data class AcknowledgeRequest(val note: String? = null)

@Serializable
data class SilenceRequest(val durationMinutes: Int)

@Serializable
data class ToggleAlertRuleRequest(val enabled: Boolean)

// ── Sync ──────────────────────────────────────────────────────────────────────

@Serializable
data class StoreSyncStatus(
    val storeId: String,
    val storeName: String,
    val status: String,
    val queueDepth: Int,
    val lastSyncAt: String?,
    val lastSyncDurationMs: Long?,
    val errorCount: Int,
    val pendingOperations: Int
)

@Serializable
data class AdminSyncQueueItem(
    val id: String,
    val storeId: String,
    val entityType: String,
    val entityId: String,
    val operationType: String,
    val payload: JsonElement,
    val clientTimestamp: String,
    val retryCount: Int,
    val lastErrorMessage: String?,
    val createdAt: String
)

@Serializable
data class ForceSyncResult(
    val storeId: String,
    val operationsQueued: Int,
    val triggeredAt: String
)

// ── Config ────────────────────────────────────────────────────────────────────

@Serializable
data class AdminFeatureFlag(
    val key: String,
    val name: String,
    val description: String,
    val enabled: Boolean,
    val category: String,
    val editionsAvailable: List<String>,
    val lastModified: String,
    val modifiedBy: String
)

@Serializable
data class ToggleFeatureFlagRequest(val enabled: Boolean)

@Serializable
data class AdminTaxRate(
    val id: String,
    val name: String,
    val rate: Double,
    val description: String,
    val applicableTo: List<String>,
    val isDefault: Boolean,
    val country: String,
    val region: String?,
    val active: Boolean
)

@Serializable
data class TaxRateCreateRequest(
    val name: String,
    val rate: Double,
    val description: String = "",
    val applicableTo: List<String> = listOf("ALL"),
    val isDefault: Boolean = false,
    val country: String = "LK",
    val region: String? = null
)

@Serializable
data class TaxRateUpdateRequest(
    val name: String? = null,
    val rate: Double? = null,
    val description: String? = null,
    val applicableTo: List<String>? = null,
    val isDefault: Boolean? = null,
    val country: String? = null,
    val region: String? = null
)

@Serializable
data class AdminSystemConfig(
    val key: String,
    val value: String,
    val type: String,
    val description: String,
    val category: String,
    val editable: Boolean,
    val sensitive: Boolean
)

@Serializable
data class UpdateSystemConfigRequest(val value: String)

// ── Auth — sessions & password change ─────────────────────────────────────

@Serializable
data class AdminSessionResponse(
    val id: String,
    val userAgent: String?,
    val ipAddress: String?,
    val createdAt: Long,
    val expiresAt: Long
)

@Serializable
data class AdminChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)
