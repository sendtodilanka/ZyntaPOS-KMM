package com.zyntasolutions.zyntapos.api.models

data class UserInfo(
    val id: String,
    val email: String,
    val name: String,
    val role: String,
    val storeId: String,
    val isActive: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
