package com.zyntasolutions.zyntapos.api.models

import kotlinx.serialization.Serializable

// ── Request bodies ──────────────────────────────────────────────────

@Serializable
data class AdminLoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class AdminRefreshRequest(
    val refreshToken: String
)

// ── Response bodies ─────────────────────────────────────────────────

@Serializable
data class AdminUserResponse(
    val id: String,
    val email: String,
    val name: String,
    val role: String,
    val mfaEnabled: Boolean,
    val isActive: Boolean,
    val lastLoginAt: Long?,
    val createdAt: Long
)

@Serializable
data class AdminLoginResponse(
    val user: AdminUserResponse,
    val expiresIn: Long,          // access token TTL in seconds
    val mfaRequired: Boolean = false
)

@Serializable
data class AdminCreateUserRequest(
    val email: String,
    val name: String,
    val role: String,
    val password: String
)

@Serializable
data class AdminUpdateUserRequest(
    val name: String? = null,
    val role: String? = null,
    val isActive: Boolean? = null
)
