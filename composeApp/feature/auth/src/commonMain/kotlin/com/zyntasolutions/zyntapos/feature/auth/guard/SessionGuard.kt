package com.zyntasolutions.zyntapos.feature.auth.guard

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow

/**
 * Authentication guard composable.
 *
 * Wraps protected content and redirects unauthenticated users to the login screen.
 * This composable collects the live session [Flow] from [AuthRepository] and
 * responds to real-time session changes (login, logout, token expiry).
 *
 * ### Usage
 * ```kotlin
 * SessionGuard(
 *     sessionFlow = authRepository.getSession(),
 *     onNavigateToLogin = { navController.navigateAndClear(ZentaRoute.Login) },
 * ) { user ->
 *     DashboardScreen(currentUser = user)
 * }
 * ```
 *
 * @param sessionFlow       Live session flow from [AuthRepository.getSession].
 * @param onNavigateToLogin Invoked when session is null — caller should clear back stack.
 * @param content           Protected composable; receives the non-null [User].
 */
@Composable
fun SessionGuard(
    sessionFlow: Flow<User?>,
    onNavigateToLogin: () -> Unit,
    content: @Composable (user: User) -> Unit,
) {
    val session by sessionFlow.collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(session) {
        if (session == null) {
            onNavigateToLogin()
        }
    }

    val user = session
    if (user != null) {
        content(user)
    }
    // If session is null, render nothing — navigation effect is in-flight.
}
