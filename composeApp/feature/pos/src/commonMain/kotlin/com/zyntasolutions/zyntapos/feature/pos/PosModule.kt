package com.zyntasolutions.zyntapos.feature.pos

import com.zyntasolutions.zyntapos.domain.formatter.ReceiptFormatter
import com.zyntasolutions.zyntapos.domain.printer.A4InvoicePrinterPort
import com.zyntasolutions.zyntapos.domain.printer.ReceiptPrinterPort
import com.zyntasolutions.zyntapos.domain.usecase.accounting.PostSaleJournalEntryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.PrintA4TaxInvoiceUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.ReprintLastReceiptUseCase
import com.zyntasolutions.zyntapos.feature.pos.printer.A4InvoicePrinterAdapter
import com.zyntasolutions.zyntapos.domain.usecase.coupons.CalculateCouponDiscountUseCase
import com.zyntasolutions.zyntapos.domain.usecase.coupons.ValidateCouponUseCase
import com.zyntasolutions.zyntapos.domain.usecase.crm.CalculateLoyaltyDiscountUseCase
import com.zyntasolutions.zyntapos.domain.usecase.crm.EarnRewardPointsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.crm.RedeemRewardPointsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.GetEffectiveProductPriceUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.AddItemToCartUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.GetEffectiveTaxRateUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.ApplyItemDiscountUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.ApplyOrderDiscountUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.CalculateOrderTotalsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.HoldOrderUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.OpenCashDrawerUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.PrintReceiptUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.ProcessPaymentUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.RemoveItemFromCartUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.RetrieveHeldOrderUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.UpdateCartItemQuantityUseCase
import com.zyntasolutions.zyntapos.feature.pos.printer.PrinterManagerReceiptAdapter
import com.zyntasolutions.zyntapos.hal.printer.PrinterManager
import com.zyntasolutions.zyntapos.security.audit.SecurityAuditLogger

import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin DI module for the `:composeApp:feature:pos` feature (Sprint 14–17, extended Sprint 22).
 *
 * Provides:
 * - All POS use cases (stateless — factory scope)
 * - [PrinterManagerReceiptAdapter] bound as [ReceiptPrinterPort] (single — one printer pipeline)
 * - [ReceiptFormatter] (factory — stateless, safe to share or re-create)
 * - [PosViewModel] (viewModel scope — one instance per screen)
 *
 * ### Sprint 22 additions
 * - [ValidateCouponUseCase] — validates coupon codes at checkout
 * - [CalculateCouponDiscountUseCase] — computes the monetary coupon discount
 * - [EarnRewardPointsUseCase] — awards loyalty points after successful payment
 * - [CustomerWalletRepository] + [LoyaltyRepository] injected into [PosViewModel]
 *   for wallet balance display and post-payment wallet debit
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
     * [AuditRepository] from the data layer (MERGED-G2.1).
     */
    single<ReceiptPrinterPort> {
        PrinterManagerReceiptAdapter(
            settingsRepository = get(),
            printerManager = get<PrinterManager>(),
            auditRepository = get(),
            deviceId = get(named("deviceId")),
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
    factory {
        GetEffectiveTaxRateUseCase(
            regionalTaxOverrideRepository = get(),
        )
    }
    factory {
        GetEffectiveProductPriceUseCase(
            masterProductRepository = get(),
            storeProductOverrideRepository = get(),
            pricingRuleRepository = get(),
        )
    }
    factory {
        AddItemToCartUseCase(
            productRepository = get(),
            getEffectivePrice = get(),
            taxGroupRepository = get(),
            getEffectiveTaxRate = get(),
        )
    }
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

    /** Opens the cash drawer via the printer ESC/POS kick command. */
    factory {
        OpenCashDrawerUseCase(
            printerPort = get<ReceiptPrinterPort>(),
        )
    }

    // ── Sprint 22: coupon + loyalty use cases ─────────────────────────────────

    /** Validates coupon codes against the CouponRepository. */
    factory { ValidateCouponUseCase(couponRepo = get()) }

    /** Pure function — computes the monetary coupon discount. No repository dep. */
    factory { CalculateCouponDiscountUseCase() }

    /** Awards loyalty points to a customer after a successful sale. */
    factory { EarnRewardPointsUseCase(loyaltyRepo = get()) }

    /** Redeems loyalty points from a customer's balance. */
    factory { RedeemRewardPointsUseCase(loyaltyRepo = get()) }

    /** Converts loyalty points to a monetary discount value. */
    factory { CalculateLoyaltyDiscountUseCase() }

    /** Auto-posts a balanced double-entry journal entry for each completed sale (Wave 1A). */
    factory {
        PostSaleJournalEntryUseCase(
            journalRepository = get(),
            accountRepository = get(),
            periodRepository = get(),
        )
    }

    // ── A4 invoice adapter + use cases ────────────────────────────────────────

    /**
     * A4 PDF printer adapter — delegates to the platform [A4PrintDelegate]
     * provided via [a4PrintDelegateModule] in the platform DI setup.
     */
    single<A4InvoicePrinterPort> {
        A4InvoicePrinterAdapter(delegate = get())
    }

    /** Reprints a past order's thermal receipt. */
    factory {
        ReprintLastReceiptUseCase(
            orderRepository = get(),
            printerPort = get<ReceiptPrinterPort>(),
        )
    }

    /** Generates and delivers an A4 tax invoice PDF (RBAC gated). */
    factory {
        PrintA4TaxInvoiceUseCase(
            orderRepository = get(),
            printerPort = get<A4InvoicePrinterPort>(),
            checkPermission = get(),
        )
    }

    // ── ViewModel ─────────────────────────────────────────────────────────────

    viewModel {
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
            openCashDrawerUseCase = get(),
            receiptFormatter = get(),
            walletRepository = get(),
            loyaltyRepository = get(),
            validateCouponUseCase = get(),
            calculateCouponDiscountUseCase = get(),
            earnRewardPointsUseCase = get(),
            redeemRewardPointsUseCase = get(),
            calculateLoyaltyDiscountUseCase = get(),
            customerRepository = get(),
            registerRepository = get(),
            authRepository = get(),
            postSaleJournalEntryUseCase = get(),
            reprintLastReceiptUseCase = get(),
            printA4TaxInvoiceUseCase = get(),
            auditLogger = get(),
            analytics = get(),
        )
    }
}
