package com.zyntasolutions.zyntapos.feature.register

import com.zyntasolutions.zyntapos.domain.usecase.register.CloseRegisterSessionUseCase
import com.zyntasolutions.zyntapos.domain.usecase.register.OpenRegisterSessionUseCase
import com.zyntasolutions.zyntapos.domain.usecase.register.PrintZReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.register.RecordCashMovementUseCase
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Koin DI module for the :composeApp:feature:register feature (Sprint 20–21).
 *
 * Provides:
 * - [OpenRegisterSessionUseCase]     — domain use case for opening a register session.
 * - [CloseRegisterSessionUseCase]    — domain use case for closing a session with balance reconciliation.
 * - [RecordCashMovementUseCase]      — domain use case for recording cash in/out movements.
 * - [PrintZReportUseCase]            — domain use case for printing Z-report via thermal printer.
 * - [RegisterViewModel]              — MVI ViewModel shared by all register screens.
 *
 * ### Dependency chain
 * ```
 * RegisterViewModel
 *   ├─ RegisterRepository            (bound in :shared:data module)
 *   ├─ OpenRegisterSessionUseCase
 *   ├─ CloseRegisterSessionUseCase
 *   ├─ RecordCashMovementUseCase
 *   ├─ PrintZReportUseCase
 *   │   ├─ EscPosReceiptBuilder      (bound in :shared:hal module)
 *   │   └─ PrinterManager            (bound in :shared:hal module)
 *   └─ currentUserId : String        (provided by AuthModule / SessionManager)
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
    // ── Domain use cases ─────────────────────────────────────────────────────
    factory { OpenRegisterSessionUseCase(get()) }
    factory { CloseRegisterSessionUseCase(get()) }
    factory { RecordCashMovementUseCase(get()) }
    factory { PrintZReportUseCase(get(), get()) }

    // ── ViewModel ────────────────────────────────────────────────────────────
    // currentUserId is expected to be bound as `named("currentUserId")` or
    // resolved from SessionManager. Update the qualifier below to match the
    // session-management strategy established in :shared:security (Sprint 8).
    viewModelOf(::RegisterViewModel)
}
