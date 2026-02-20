package com.zyntasolutions.zyntapos.feature.pos

import com.zyntasolutions.zyntapos.domain.usecase.pos.AddItemToCartUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.ApplyItemDiscountUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.ApplyOrderDiscountUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.CalculateOrderTotalsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.HoldOrderUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.ProcessPaymentUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.RemoveItemFromCartUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.RetrieveHeldOrderUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.UpdateCartItemQuantityUseCase
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin DI module for the `:composeApp:feature:pos` feature (Sprint 14).
 *
 * Provides:
 * - All POS use cases (stateless — factory scope)
 * - [PosViewModel] (viewModel scope — one instance per screen)
 *
 * ### Session context
 * [PosViewModel] requires [cashierId], [storeId], and [registerSessionId].
 * These are resolved from the auth session at injection time. The caller must
 * ensure the session is established before the POS screen is entered.
 *
 * ### HAL bindings
 * [BarcodeScanner] is expected to be provided by the platform-specific module
 * (`:androidApp` or `:desktopApp`) and retrieved here via `get()`.
 *
 * **Note:** `BarcodeScanner` is consumed by [BarcodeInputHandler] composable directly
 * (not by the ViewModel), so it is not injected into [PosViewModel]. It can be
 * retrieved at the composable call site via `koinInject<BarcodeScanner>()`.
 */
val posModule = module {

    // ── Use cases ─────────────────────────────────────────────────────────────

    factory { CalculateOrderTotalsUseCase() }
    factory { AddItemToCartUseCase(productRepository = get()) }
    factory { RemoveItemFromCartUseCase() }
    factory { UpdateCartItemQuantityUseCase(productRepository = get()) }
    factory {
        ApplyItemDiscountUseCase(
            checkPermissionUseCase = get(),
        )
    }
    factory {
        ApplyOrderDiscountUseCase(
            settingsRepository = get(),
            calculateOrderTotalsUseCase = get(),
        )
    }
    factory { HoldOrderUseCase(orderRepository = get()) }
    factory { RetrieveHeldOrderUseCase(orderRepository = get()) }
    factory {
        ProcessPaymentUseCase(
            orderRepository = get(),
            stockRepository = get(),
            calculateTotalsUseCase = get(),
        )
    }

    // ── ViewModel ─────────────────────────────────────────────────────────────

    viewModel { params ->
        PosViewModel(
            productRepository = get(),
            categoryRepository = get(),
            orderRepository = get(),
            addItemUseCase = get(),
            removeItemUseCase = get(),
            updateQtyUseCase = get(),
            applyItemDiscountUseCase = get(),
            applyOrderDiscountUseCase = get(),
            calculateTotalsUseCase = get(),
            holdOrderUseCase = get(),
            retrieveHeldUseCase = get(),
            processPaymentUseCase = get(),
            cashierId = params.get(),
            storeId = params.get(),
            registerSessionId = params.get(),
        )
    }
}
