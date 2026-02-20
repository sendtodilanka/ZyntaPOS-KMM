package com.zyntasolutions.zyntapos.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation

// ─────────────────────────────────────────────────────────────────────────────
// AUTH NAV GRAPH
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Nested navigation graph for unauthenticated / lock-screen flows.
 *
 * Destinations:
 * - [ZentaRoute.Login]   — credential entry; start destination of the auth graph.
 * - [ZentaRoute.PinLock] — quick PIN re-authentication after idle timeout.
 *
 * Flow:
 * ```
 * App start ──► Login ──► (success) ──► Main graph (via navigateAndClear)
 *                  ▲
 *                  │  idle timeout
 * Main area ──► PinLock ──► (success) ──► pop back to previous screen
 * ```
 *
 * Screen composables are passed as lambdas to keep this graph decoupled from
 * feature module implementations (screens live in `:composeApp:feature:auth`).
 *
 * @param navigationController Controller used to trigger navigateAndClear on success.
 * @param loginScreen Composable for the Login screen. Receives a `onLoginSuccess`
 *   callback that the screen should invoke after a successful authentication.
 * @param pinLockScreen Composable for the PinLock screen. Receives `onUnlocked`
 *   callback invoked when the user enters the correct PIN.
 */
fun NavGraphBuilder.authNavGraph(
    navigationController: NavigationController,
    loginScreen: @Composable (onLoginSuccess: () -> Unit) -> Unit,
    pinLockScreen: @Composable (onUnlocked: () -> Unit) -> Unit,
) {
    navigation<ZentaRoute.Login>(startDestination = ZentaRoute.Login) {
        composable<ZentaRoute.Login> {
            loginScreen(
                onLoginSuccess = {
                    navigationController.navigateAndClear(ZentaRoute.Dashboard)
                },
            )
        }

        composable<ZentaRoute.PinLock> {
            pinLockScreen(
                onUnlocked = {
                    // Pop back to whatever screen triggered the lock
                    navigationController.popBackStack()
                },
            )
        }
    }
}
