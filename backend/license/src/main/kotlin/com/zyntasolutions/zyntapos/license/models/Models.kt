package com.zyntasolutions.zyntapos.license.models

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val code: String, val message: String)

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
    val uptimeHours: Double = 0.0
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
