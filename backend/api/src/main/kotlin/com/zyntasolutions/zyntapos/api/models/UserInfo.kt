package com.zyntasolutions.zyntapos.api.models

data class UserInfo(
    val id: String,
    val username: String,
    val role: String,
    val storeId: String
)
