package com.zyntasolutions.zyntapos.domain.model

import kotlinx.datetime.Instant

data class License(
    val id: String,
    val terminalId: String,
    val storeId: String,
    val edition: Edition,
    val status: LicenseStatus,
    val issuedAt: Instant,
    val expiresAt: Instant?,
    val gracePeriodEndsAt: Instant?,
    val lastHeartbeatAt: Instant?,
    val maxTerminals: Int,
)
