package com.zyntasolutions.zyntapos.navigation

import org.koin.dsl.module

/**
 * Koin DI module for `:composeApp:navigation`.
 *
 * Provides application-level navigation singletons that are injected into
 * feature ViewModels and screen composables.
 *
 * **What is NOT provided here:**
 * - [NavigationController] — it is created via [rememberNavigationController] at the
 *   Compose root level (it wraps a [NavHostController] which requires Compose composition)
 *   and passed explicitly as a parameter to screens that need it. ViewModel-level
 *   navigation events are emitted as [NavigationEvent] via `SharedFlow` and observed
 *   by the host composable, avoiding ViewModel → controller coupling.
 *
 * **What IS provided here:**
 * - [RbacNavFilter] — exposed as a singleton for feature modules that need to
 *   query route permissions outside of the nav graph (e.g. hiding FABs).
 */
val navigationModule = module {

    /**
     * [RbacNavFilter] singleton.
     *
     * Resolves RBAC-filtered navigation items from the domain [Permission] model.
     * Consumed by feature ViewModels that gate UI elements by role.
     */
    single { RbacNavFilter }
}
