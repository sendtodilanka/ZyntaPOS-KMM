package com.zyntasolutions.zyntapos.domain.model

import kotlinx.datetime.Instant

data class Heartbeat(
    val terminalId: String,
    val storeId: String,
    val appVersion: String,
    val sentAt: Instant,
)
