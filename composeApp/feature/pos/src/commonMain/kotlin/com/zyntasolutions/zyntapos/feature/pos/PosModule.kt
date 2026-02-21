package com.zyntasolutions.zyntapos.feature.pos

import com.zyntasolutions.zyntapos.domain.formatter.ReceiptFormatter
import com.zyntasolutions.zyntapos.domain.printer.ReceiptPrinterPort
import com.zyntasolutions.zyntapos.domain.usecase.pos.AddItemToCartUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.ApplyItemDiscountUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.ApplyOrderDiscountUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.CalculateOrderTotalsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.HoldOrderUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.PrintReceiptUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.ProcessPaymentUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.RemoveItemFromCartUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.RetrieveHeldOrderUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.UpdateCartItemQuantityUseCase
import com.zyntasolutions.zyntapos.feature.pos.printer.PrinterManagerReceiptAdapter
import com.zyntasolutions.zyntapos.hal.printer.PrinterManager

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin DI module for the `:composeApp:feature:pos` feature (Sprint 14–17).
 *
 * Provides:
 * - All POS use cases (stateless — factory scope)
 * - [PrinterManagerReceiptAdapter] bound as [ReceiptPrinterPort] (single — one printer pipeline)
 * - [ReceiptFormatter] (factory — stateless, safe to share or re-create)
 * - [SecurityAuditLogger] (singleton — shared across modules)
 * - [PosViewModel] (viewModel scope — one instance per screen)
 *
 * ### Session context
 * [PosViewModel] requires [cashierId], [storeId], and [registerSessionId].
 * These are resolved from the auth session at injection time. The caller must
 * ensure the session is established before the POS screen is entered.
 *
 * ### HAL bindings
 * [PrinterManager] is expected to be provided by the platform-specific module
 * (`:androidApp` or `:desktopApp`) and retrieved here via `get()`.
 *
 * [BarcodeScanner] is consumed by [BarcodeInputHandler] composable directly
 * (not by the ViewModel), so it is not injected into [PosViewModel]. It can be
 * retrieved at the composable call site via `koinInject<BarcodeScanner>()`.
 */
val posModule = module {

    // ── Shared infrastructure ─────────────────────────────────────────────────

    /**
     * Infrastructure adapter that bridges [PrintReceiptUseCase] (domain) to the
     * HAL printer pipeline. Bound as [ReceiptPrinterPort] so the use case never
     * imports HAL or security modules directly.
     *
     * Requires [PrinterManager] from the platform HAL module and
     * [SecurityAuditLogger] from above.
     */
    single<ReceiptPrinterPort> {
        PrinterManagerReceiptAdapter(
            settingsRepository = get(),
            printerManager = get<PrinterManager>(),
            auditLogger = get(),
        )
    }

    /**
     * Plain-text receipt formatter used by [PosViewModel] to populate
     * [PosState.receiptPreviewText] after a successful payment.
     * Stateless — safe to use as a factory.
     */
    factory { ReceiptFormatter(currencyFormatter = get()) }

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

    /**
     * Receipt printing use case.
     *
     * Requires [ReceiptPrinterPort] — provided above as [PrinterManagerReceiptAdapter].
     * The use case itself has no HAL or security imports; all infrastructure is
     * encapsulated behind the port interface.
     */
    factory {
        PrintReceiptUseCase(
            printerPort = get<ReceiptPrinterPort>(),
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
            printReceiptUseCase = get(),
            receiptFormatter = get(),
            cashierId = params.get(),
            storeId = params.get(),
            registerSessionId = params.get(),
        )
    }
}
