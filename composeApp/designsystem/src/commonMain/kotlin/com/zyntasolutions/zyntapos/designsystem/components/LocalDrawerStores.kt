package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal providing the list of stores available for the authenticated user.
 *
 * Set by the root `App()` composable after observing `AuthViewModel.state.availableStores`.
 * Consumed by [DrawerFooter] in [ZyntaNavigationDrawer] to render [ZyntaStoreSelector]
 * when the user has access to more than one store.
 *
 * Default is an empty list (single-store mode — selector hidden).
 */
val LocalDrawerAvailableStores = staticCompositionLocalOf<List<StoreItem>> { emptyList() }

/**
 * CompositionLocal providing the currently active store for the drawer selector.
 *
 * Matched from [LocalDrawerAvailableStores] using `currentUser.storeId` or
 * `AuthState.selectedStoreId`. Default is `null` (no store selected / single-store mode).
 */
val LocalDrawerCurrentStore = staticCompositionLocalOf<StoreItem?> { null }

/**
 * CompositionLocal providing the store-switch callback for the drawer selector.
 *
 * When the user picks a different store, this dispatches `AuthIntent.StoreSelected` via
 * the `AuthViewModel` instance in `App.kt`. Default is a no-op.
 */
val LocalDrawerOnStoreSelected = staticCompositionLocalOf<(StoreItem) -> Unit> { {} }
