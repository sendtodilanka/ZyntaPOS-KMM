package com.zyntasolutions.zyntapos.domain.usecase.rbac

import com.zyntasolutions.zyntapos.domain.model.PermissionGroup

fun interface GetPermissionsTreeUseCase {
    suspend operator fun invoke(): List<PermissionGroup>
}
