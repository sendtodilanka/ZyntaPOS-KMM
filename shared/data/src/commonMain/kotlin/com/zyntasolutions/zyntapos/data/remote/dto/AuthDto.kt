package com.zyntasolutions.zyntapos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────────────────────
// Auth DTOs
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class AuthRequestDto(
    @SerialName("email")    val email: String,
    @SerialName("password") val password: String,
    @SerialName("store_id") val storeId: String? = null,
)

@Serializable
data class AuthResponseDto(
    @SerialName("access_token")  val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in")    val expiresIn: Long,          // seconds
    @SerialName("user")          val user: UserDto,
)

@Serializable
data class AuthRefreshRequestDto(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
data class AuthRefreshResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in")   val expiresIn: Long,
)

// ─────────────────────────────────────────────────────────────────────────────
// User DTO
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class UserDto(
    @SerialName("id")         val id: String,
    @SerialName("name")       val name: String,
    @SerialName("email")      val email: String,
    @SerialName("role")       val role: String,
    @SerialName("store_id")   val storeId: String,
    @SerialName("is_active")  val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
)
