package com.zyntasolutions.zyntapos.license.models

import kotlinx.serialization.Serializable

// S2-2: ErrorResponse moved to common module — re-export for backward compatibility
typealias ErrorResponse = com.zyntasolutions.zyntapos.common.ErrorResponse

// ── Admin panel response models ──────────────────────────────────────

@Serializable
data class AdminPagedResponse<T>(
    val data: List<T>,
    val page: Int,
    val size: Int,
    val total: Int,
    val totalPages: Int
)

@Serializable
data class AdminLicense(
    val id: String,
    val key: String,
    val customerId: String,
    val customerName: String,
    val edition: String,
    val status: String,
    val maxDevices: Int,
    val activeDevices: Int,
    val activatedAt: String,
    val expiresAt: String?,
    val lastHeartbeatAt: String?,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class AdminLicenseWithDevices(
    val license: AdminLicense,
    val devices: List<LicenseDevice>
)

@Serializable
data class LicenseDevice(
    val id: String,
    val licenseKey: String,
    val deviceId: String,
    val deviceName: String,
    val appVersion: String,
    val os: String,
    val osVersion: String,
    val firstSeenAt: String,
    val lastSeenAt: String
)

@Serializable
data class AdminLicenseStats(
    val total: Int,
    val active: Int,
    val expired: Int,
    val revoked: Int,
    val suspended: Int,
    val expiringSoon: Int,
    val byEdition: Map<String, Int>
)

@Serializable
data class AdminCreateLicenseRequest(
    val customerId: String,
    val customerName: String? = null,
    val edition: String,
    val maxDevices: Int,
    val expiresAt: String? = null
)

@Serializable
data class AdminUpdateLicenseRequest(
    val edition: String? = null,
    val maxDevices: Int? = null,
    val expiresAt: String? = null,
    val clearExpiry: Boolean? = null,
    val status: String? = null,
    val forceSync: Boolean? = null
)

@Serializable
data class ActivateRequest(
    val licenseKey: String,
    val deviceId: String,
    val deviceName: String? = null,
    val appVersion: String,
    val osVersion: String? = null
)

@Serializable
data class ActivateResponse(
    val isValid: Boolean,
    val licenseKey: String,
    val edition: String,
    val maxDevices: Int,
    val activeDevices: Int,
    val expiresAt: Long?,           // Unix epoch millis, null = perpetual
    val gracePeriodDays: Int = 7,
    val message: String? = null,
    val errorCode: String? = null
)

@Serializable
data class HeartbeatRequest(
    val licenseKey: String,
    val deviceId: String,
    val appVersion: String,
    val osVersion: String? = null,
    val dbSizeBytes: Long = 0L,
    val syncQueueDepth: Int = 0,
    val lastErrorCount: Int = 0,
    val uptimeHours: Double = 0.0,
    val nonce: String? = null,       // unique per-heartbeat, replay protection
    val clientTimestamp: Long? = null // epoch ms, reject if too stale
)

@Serializable
data class HeartbeatResponse(
    val status: String,             // "ACTIVE", "EXPIRING_SOON", "GRACE_PERIOD", "EXPIRED"
    val licenseKey: String,
    val expiresAt: Long?,
    val daysUntilExpiry: Int?,      // null = perpetual
    val serverTimestamp: Long,
    val forceSync: Boolean = false  // Server can request immediate sync
)

@Serializable
data class LicenseStatusResponse(
    val key: String,
    val edition: String,
    val status: String,
    val maxDevices: Int,
    val activeDevices: Int,
    val issuedAt: Long,
    val expiresAt: Long?,
    val lastHeartbeatAt: Long?
)
