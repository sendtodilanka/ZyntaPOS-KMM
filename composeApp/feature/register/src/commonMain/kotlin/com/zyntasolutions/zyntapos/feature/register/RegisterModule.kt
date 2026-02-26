package com.zyntasolutions.zyntapos.feature.register

import com.zyntasolutions.zyntapos.domain.printer.ZReportPrinterPort
import com.zyntasolutions.zyntapos.domain.usecase.register.CloseRegisterSessionUseCase
import com.zyntasolutions.zyntapos.domain.usecase.register.OpenRegisterSessionUseCase
import com.zyntasolutions.zyntapos.domain.usecase.register.PrintZReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.register.RecordCashMovementUseCase
import com.zyntasolutions.zyntapos.feature.register.printer.ZReportPrinterAdapter
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin DI module for the :composeApp:feature:register feature (Sprint 20–21).
 *
 * Provides:
 * - [OpenRegisterSessionUseCase]     — domain use case for opening a register session.
 * - [CloseRegisterSessionUseCase]    — domain use case for closing a session with balance reconciliation.
 * - [RecordCashMovementUseCase]      — domain use case for recording cash in/out movements.
 * - [ZReportPrinterAdapter]          — infrastructure adapter bound to [ZReportPrinterPort].
 * - [PrintZReportUseCase]            — domain use case for printing Z-report via the port.
 * - [RegisterViewModel]              — MVI ViewModel shared by all register screens.
 *
 * ### Dependency chain
 * ```
 * RegisterViewModel
 *   ├─ RegisterRepository            (bound in :shared:data module)
 *   ├─ OpenRegisterSessionUseCase
 *   ├─ CloseRegisterSessionUseCase
 *   ├─ RecordCashMovementUseCase
 *   └─ PrintZReportUseCase
 *       └─ ZReportPrinterPort (→ ZReportPrinterAdapter)
 *           ├─ PrinterManager        (bound in :shared:hal module)
 *           └─ EscPosReceiptBuilder  (bound in :shared:hal module)
 * ```
 *
 * Include this module in the root application Koin graph:
 * ```kotlin
 * startKoin {
 *     modules(registerModule, …)
 * }
 * ```
 */
val registerModule = module {
    // ── Port adapter (HAL orchestration lives here, NOT in the use case) ─────
    single<ZReportPrinterPort> { ZReportPrinterAdapter(get(), get()) }

    // ── Domain use cases ─────────────────────────────────────────────────────
    factory { OpenRegisterSessionUseCase(get()) }
    factory { CloseRegisterSessionUseCase(get()) }
    factory { RecordCashMovementUseCase(get()) }
    factory { PrintZReportUseCase(get()) }

    // ── ViewModel ────────────────────────────────────────────────────────────
    viewModel {
        RegisterViewModel(
            registerRepository          = get(),
            orderRepository             = get(),
            openRegisterSessionUseCase  = get(),
            closeRegisterSessionUseCase = get(),
            recordCashMovementUseCase   = get(),
            printZReportUseCase         = get(),
            authRepository              = get(),
        )
    }
}
