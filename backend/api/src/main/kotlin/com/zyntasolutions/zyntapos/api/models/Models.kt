package com.zyntasolutions.zyntapos.api.models

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String
)

@Serializable
data class LoginRequest(
    val licenseKey: String,
    val deviceId: String,
    val username: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tokenType: String,
    val userId: String,
    val role: String,
    val storeId: String
)

@Serializable
data class RefreshRequest(
    val refreshToken: String
)

@Serializable
data class SyncOperation(
    val id: String,
    val entityType: String,    // "PRODUCT", "ORDER", "CUSTOMER", etc.
    val entityId: String,
    val operation: String,     // "INSERT", "UPDATE", "DELETE"
    val payload: String,       // JSON-encoded entity
    val vectorClock: Long,
    val clientTimestamp: Long
)

@Serializable
data class PushRequest(
    val deviceId: String,
    val operations: List<SyncOperation>
)

@Serializable
data class PushResponse(
    val accepted: Int,
    val rejected: Int,
    val conflicts: List<String>,  // IDs of conflicting operations
    val serverVectorClock: Long
)

@Serializable
data class PullResponse(
    val operations: List<SyncOperation>,
    val serverVectorClock: Long,
    val hasMore: Boolean
)

@Serializable
data class PagedResponse<T>(
    val data: List<T>,
    val page: Int,
    val size: Int,
    val total: Long,
    val hasMore: Boolean
)

@Serializable
data class ProductDto(
    val id: String,
    val name: String,
    val sku: String?,
    val barcode: String?,
    val price: Double,
    val costPrice: Double,
    val stockQuantity: Int,
    val categoryId: String?,
    val updatedAt: Long,
    val isActive: Boolean,
    val syncVersion: Long
)
