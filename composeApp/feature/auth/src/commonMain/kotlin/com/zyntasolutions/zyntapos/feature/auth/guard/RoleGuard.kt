package com.zyntasolutions.zyntapos.feature.auth.guard

import androidx.compose.runtime.Composable
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.usecase.auth.CheckPermissionUseCase

/**
 * RBAC permission guard composable.
 *
 * Evaluates whether the current session user holds the required [permission].
 * Shows [content] when authorised, or [unauthorizedContent] (defaults to a
 * standard "Unauthorized" [ZyntaEmptyState]) when not.
 *
 * Evaluation is synchronous using [CheckPermissionUseCase]'s in-memory RBAC matrix.
 * No network or database call is made.
 *
 * ### Usage
 * ```kotlin
 * RoleGuard(
 *     userId = currentUser.id,
 *     permission = Permission.MANAGE_USERS,
 *     checkPermissionUseCase = checkPermissionUseCase,
 * ) {
 *     UserManagementScreen()
 * }
 * ```
 *
 * @param userId                 The currently signed-in user's ID (from active session).
 * @param permission             The [Permission] required to view [content].
 * @param checkPermissionUseCase Synchronous RBAC evaluator from `:shared:domain`.
 * @param unauthorizedContent    Fallback composable shown when access is denied.
 *                               Defaults to a ZyntaEmptyState "Unauthorized" screen.
 * @param content                Protected composable shown when permission is granted.
 */
@Composable
fun RoleGuard(
    userId: String,
    permission: Permission,
    checkPermissionUseCase: CheckPermissionUseCase,
    unauthorizedContent: @Composable () -> Unit = {
        val s = LocalStrings.current
        ZyntaEmptyState(
            title = s[StringResource.AUTH_ACCESS_DENIED_TITLE],
            subtitle = s[StringResource.AUTH_ACCESS_DENIED_SUBTITLE],
        )
    },
    content: @Composable () -> Unit,
) {
    val isAuthorized = checkPermissionUseCase(userId = userId, permission = permission)

    if (isAuthorized) {
        content()
    } else {
        unauthorizedContent()
    }
}
