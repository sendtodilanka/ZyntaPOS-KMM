package com.zyntasolutions.zyntapos.feature.register

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/**
 * Navigation guard that enforces the register-open invariant.
 *
 * ## Business Rule
 * A cashier **must** have an open register session before accessing the POS or any
 * sales screen. [RegisterGuard] is placed immediately after the login success in the
 * navigation graph and checks [RegisterViewModel.state] for an active session.
 *
 * ## Behaviour
 * | Active session | Route         |
 * |----------------|---------------|
 * | `null`         | [onNoSession] is invoked → navigate to [OpenRegisterScreen] |
 * | non-null       | [content] is rendered (POS / Dashboard flows) |
 *
 * The guard observes [RegisterRepository.getActive] reactively via [RegisterViewModel].
 * If the session is closed mid-shift (e.g., manager closes remotely), the guard
 * automatically redirects back to [OpenRegisterScreen].
 *
 * ## RBAC
 * Roles without register access (Accountant, Stock Manager) skip this guard entirely —
 * their navigation graph does not include this composable. Filtering is enforced at the
 * navigation-graph level by [RbacEngine].
 *
 * @param viewModel       Shared [RegisterViewModel] (Koin-injected).
 * @param onNoSession     Callback invoked when no active session is found; should trigger
 *                        navigation to [OpenRegisterScreen].
 * @param content         Composable content rendered when a session is active.
 *
 * @sample RegisterGuardPreview
 */
@Composable
fun RegisterGuard(
    viewModel: RegisterViewModel = koinViewModel(),
    onNoSession: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Start observing the active session exactly once.
    LaunchedEffect(Unit) {
        viewModel.dispatch(RegisterIntent.ObserveActiveSession)
    }

    // React to session changes: redirect when no session is active.
    LaunchedEffect(state.activeSession) {
        if (state.activeSession == null) {
            onNoSession()
        }
    }

    // Only render child content when a valid session exists.
    if (state.activeSession != null) {
        content()
    }
}
