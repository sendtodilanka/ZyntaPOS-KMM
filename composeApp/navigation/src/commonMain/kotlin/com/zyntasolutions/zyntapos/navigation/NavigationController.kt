package com.zyntasolutions.zyntapos.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.rememberNavController

/**
 * Type-safe wrapper around [NavHostController] for ZyntaPOS navigation.
 *
 * Exposes clean, route-oriented APIs that hide the underlying navigation
 * library from feature modules. Feature screens only depend on this
 * abstraction — never on [NavHostController] directly.
 *
 * **Key responsibilities:**
 * - `navigate(route)` — standard forward navigation with dedup guard.
 * - `popBackStack()` — safe back navigation with Desktop fallback.
 * - `navigateAndClear(route)` — clear entire back stack (login → dashboard, logout).
 * - `navigateUp(fallback)` — pop or navigate to fallback if no back-stack entry exists.
 * - `lockScreen()` — navigate to [ZyntaRoute.PinLock] on idle timeout.
 *
 * @param navController The underlying [NavHostController] managed by `rememberNavController`.
 */
@Stable
class NavigationController(
    val navController: NavHostController,
) {

    /**
     * Navigate to [route] using standard forward navigation.
     *
     * Duplicate navigation to the same route as the current destination is
     * silently ignored to prevent double-push on rapid taps.
     *
     * @param route Destination [ZyntaRoute].
     * @param builder Optional [NavOptionsBuilder] for animations or launch modes.
     */
    fun navigate(route: ZyntaRoute, builder: NavOptionsBuilder.() -> Unit = {}) {
        val currentRoute = navController.currentDestination?.route
        // Deduplicate: ignore if already at this destination (same class name)
        if (currentRoute == route::class.qualifiedName) return
        navController.navigate(route, builder)
    }

    /**
     * Pop the back stack by one entry.
     *
     * On Desktop (JVM) there is no physical back button. If the back stack is
     * empty, this call is a no-op to prevent crashes. For screens that always
     * need a safe fallback, use [navigateUp] instead.
     *
     * @return `true` if the pop was successful, `false` if the stack was empty.
     */
    fun popBackStack(): Boolean = navController.popBackStack()

    /**
     * Navigate to [fallback] if the back stack cannot be popped.
     *
     * Recommended over [popBackStack] in Desktop flows where physical back
     * navigation doesn't exist.
     *
     * @param fallback Destination to navigate to if back stack is exhausted.
     */
    fun navigateUp(fallback: ZyntaRoute = ZyntaRoute.Dashboard) {
        val popped = navController.popBackStack()
        if (!popped) navigate(fallback) { launchSingleTop = true }
    }

    /**
     * Navigate to [route] and clear the entire back stack.
     *
     * Use this for:
     * - Successful login → Dashboard (clears auth stack)
     * - Logout → Login (clears all authenticated routes)
     *
     * @param route Destination to navigate to after clearing.
     */
    fun navigateAndClear(route: ZyntaRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) {
                inclusive = true
            }
            launchSingleTop = true
        }
    }

    /**
     * Navigate to [ZyntaRoute.PinLock] preserving the current back stack.
     *
     * Called automatically after an idle timeout. The PIN lock screen pops
     * back to the previous destination upon successful PIN entry.
     */
    fun lockScreen() {
        navController.navigate(ZyntaRoute.PinLock) {
            launchSingleTop = true
        }
    }

    /**
     * Navigate directly to the POS screen, clearing the back stack.
     * Convenience shortcut used by the quick-sale FAB.
     */
    fun goToPos() {
        navigate(ZyntaRoute.Pos) { launchSingleTop = true }
    }

    /**
     * Navigate to the [ZyntaRoute.ProductDetail] screen, optionally via deep link.
     *
     * @param productId Product to open for editing. Pass `null` to create a new product.
     *   When triggered by a barcode scanner deep link (`zyntapos://product/{barcode}`),
     *   the barcode value is forwarded as [productId] for lookup.
     */
    fun openProductDetail(productId: String? = null) {
        navigate(ZyntaRoute.ProductDetail(productId = productId))
    }

    /**
     * Navigate to the payment flow for the given [orderId].
     *
     * @param orderId Active order to pay.
     */
    fun openPayment(orderId: String) {
        navigate(ZyntaRoute.Payment(orderId = orderId))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Composable factory
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Creates and [remember]s a [NavigationController] backed by a stable [NavHostController].
 *
 * Place this at the root of the composition tree (e.g. `App.kt`) and pass it down
 * to child composables.
 */
@Composable
fun rememberNavigationController(): NavigationController {
    val navController = rememberNavController()
    return remember(navController) { NavigationController(navController) }
}
