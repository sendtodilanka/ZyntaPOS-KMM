package com.zyntasolutions.zyntapos.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation

// ─────────────────────────────────────────────────────────────────────────────
// AUTH NAV GRAPH
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Nested navigation graph for unauthenticated / lock-screen / license flows.
 *
 * Destinations:
 * - [ZyntaRoute.Onboarding]        — first-run setup wizard (shown only once on first launch).
 * - [ZyntaRoute.Login]             — credential entry; normal start destination.
 * - [ZyntaRoute.PinLock]           — quick PIN re-authentication after idle timeout.
 * - [ZyntaRoute.LicenseActivation] — license key entry; shown when device is unactivated.
 * - [ZyntaRoute.LicenseExpired]    — blocker shown when license is EXPIRED or REVOKED.
 *
 * Flow:
 * ```
 * App first launch ──► Onboarding ──► (done) ──► Login
 * App normal start ──► Login ──► (success) ──► LicenseActivation (if unactivated)
 *                                           ──► LicenseExpired   (if revoked/expired)
 *                                           ──► Main graph       (if ACTIVE/GRACE)
 *                  ▲
 *                  │  idle timeout
 * Main area ──► PinLock ──► (success) ──► pop back to previous screen
 * ```
 *
 * @param navigationController  Controller used to trigger navigateAndClear on success.
 * @param isFirstRun            When `true`, [ZyntaRoute.Onboarding] is the start destination.
 * @param loginScreen           Composable for the Login screen.
 * @param onboardingScreen      Composable for the first-run Onboarding wizard.
 * @param pinLockScreen         Composable for the PinLock screen.
 * @param licenseActivationScreen Composable for the license key entry screen.
 * @param licenseExpiredScreen  Composable for the license expired/revoked blocker.
 */
fun NavGraphBuilder.authNavGraph(
    navigationController: NavigationController,
    isFirstRun: Boolean,
    loginScreen: @Composable (onLoginSuccess: () -> Unit) -> Unit,
    onboardingScreen: @Composable (onOnboardingComplete: () -> Unit) -> Unit,
    pinLockScreen: @Composable (onUnlocked: () -> Unit) -> Unit,
    licenseActivationScreen: @Composable (onActivated: () -> Unit) -> Unit = {},
    licenseExpiredScreen: @Composable () -> Unit = {},
) {
    val startDestination: ZyntaRoute = if (isFirstRun) ZyntaRoute.Onboarding else ZyntaRoute.Login

    navigation<ZyntaRoute.AuthGraph>(startDestination = startDestination) {
        // ── First-run onboarding ───────────────────────────────────────────────
        composable<ZyntaRoute.Onboarding> {
            // On wizard completion, navigate to Login and remove onboarding from the back-stack.
            onboardingScreen {
                navigationController.navigate(ZyntaRoute.Login) {
                    popUpTo<ZyntaRoute.Onboarding> { inclusive = true }
                }
            }
        }

        // ── Login ─────────────────────────────────────────────────────────────
        composable<ZyntaRoute.Login> {
            loginScreen { navigationController.navigateAndClear(ZyntaRoute.Dashboard) }
        }

        // ── PIN lock ──────────────────────────────────────────────────────────
        composable<ZyntaRoute.PinLock> {
            pinLockScreen(
                {
                    // Pop back to whatever screen triggered the lock
                    navigationController.popBackStack()
                },
            )
        }

        // ── License activation ────────────────────────────────────────────────
        composable<ZyntaRoute.LicenseActivation> {
            licenseActivationScreen {
                navigationController.navigateAndClear(ZyntaRoute.Dashboard)
            }
        }

        // ── License expired / revoked ─────────────────────────────────────────
        composable<ZyntaRoute.LicenseExpired> {
            licenseExpiredScreen()
        }
    }
}
