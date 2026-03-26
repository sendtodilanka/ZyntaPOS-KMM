package com.zyntasolutions.zyntapos.domain.model

data class PermissionGroup(
    val module: String,
    val displayName: String,
    val permissions: List<PermissionItem>,
)

data class PermissionItem(
    val permission: Permission,
    val displayName: String,
    val description: String,
)
