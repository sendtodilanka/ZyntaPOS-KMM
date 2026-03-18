package com.zyntasolutions.zyntapos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LicenseActivateRequestDto(
    @SerialName("licenseKey") val licenseKey: String,
    @SerialName("deviceId") val deviceId: String,
    @SerialName("deviceName") val deviceName: String? = null,
    @SerialName("appVersion") val appVersion: String,
    @SerialName("osVersion") val osVersion: String? = null,
)

@Serializable
data class LicenseActivateResponseDto(
    @SerialName("isValid") val isValid: Boolean,
    @SerialName("licenseKey") val licenseKey: String,
    @SerialName("edition") val edition: String,
    @SerialName("maxDevices") val maxDevices: Int,
    @SerialName("activeDevices") val activeDevices: Int,
    @SerialName("expiresAt") val expiresAt: Long?,           // Unix epoch millis, null = perpetual
    @SerialName("gracePeriodDays") val gracePeriodDays: Int = 7,
    @SerialName("message") val message: String? = null,
    @SerialName("errorCode") val errorCode: String? = null,
)

@Serializable
data class LicenseHeartbeatRequestDto(
    @SerialName("licenseKey") val licenseKey: String,
    @SerialName("deviceId") val deviceId: String,
    @SerialName("appVersion") val appVersion: String,
    @SerialName("osVersion") val osVersion: String? = null,
    @SerialName("dbSizeBytes") val dbSizeBytes: Long = 0L,
    @SerialName("syncQueueDepth") val syncQueueDepth: Int = 0,
    @SerialName("lastErrorCount") val lastErrorCount: Int = 0,
    @SerialName("uptimeHours") val uptimeHours: Double = 0.0,
    @SerialName("nonce") val nonce: String? = null,
    @SerialName("clientTimestamp") val clientTimestamp: Long? = null,
)

@Serializable
data class LicenseHeartbeatResponseDto(
    @SerialName("status") val status: String,           // "ACTIVE", "EXPIRING_SOON", "GRACE_PERIOD", "EXPIRED"
    @SerialName("licenseKey") val licenseKey: String,
    @SerialName("expiresAt") val expiresAt: Long?,
    @SerialName("daysUntilExpiry") val daysUntilExpiry: Int?,
    @SerialName("serverTimestamp") val serverTimestamp: Long,
    @SerialName("forceSync") val forceSync: Boolean = false,
)
