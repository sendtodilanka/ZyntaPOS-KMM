package com.zyntasolutions.zyntapos.domain.usecase.rbac

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.CustomRole

fun interface CloneRoleUseCase {
    suspend operator fun invoke(sourceRoleId: String, newName: String): Result<CustomRole>
}
