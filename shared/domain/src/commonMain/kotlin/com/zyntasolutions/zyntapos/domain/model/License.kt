package com.zyntasolutions.zyntapos.domain.model

import kotlinx.datetime.Instant

/**
 * Domain model for a ZyntaPOS software license.
 *
 * Field names align with the license service API contract:
 * - [key] corresponds to the server-side `licenseKey` / `key` field
 * - [deviceId] corresponds to the physical/virtual device identifier
 * - [customerId] is the customer account this license belongs to
 * - [maxDevices] is the maximum number of devices allowed (was maxTerminals)
 */
data class License(
    val key: String,
    val deviceId: String,
    val customerId: String,
    val edition: Edition,
    val status: LicenseStatus,
    val issuedAt: Instant,
    val expiresAt: Instant?,
    val gracePeriodEndsAt: Instant?,
    val lastHeartbeatAt: Instant?,
    val maxDevices: Int,
)
