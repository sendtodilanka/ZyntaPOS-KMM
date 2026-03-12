package com.zyntasolutions.zyntapos.common

import kotlinx.serialization.Serializable

/**
 * S2-2: Centralized error response model shared across all backend services.
 * Provides a consistent JSON error shape for all API endpoints.
 */
@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
    val details: List<String>? = null,
)
