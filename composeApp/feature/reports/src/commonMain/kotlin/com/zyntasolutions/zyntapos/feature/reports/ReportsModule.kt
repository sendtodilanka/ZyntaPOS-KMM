package com.zyntasolutions.zyntapos.feature.reports

import com.zyntasolutions.zyntapos.domain.printer.ReportPrinterPort
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateSalesReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateStockReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.PrintReportUseCase
import com.zyntasolutions.zyntapos.feature.reports.printer.ReportPrinterAdapter
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Koin DI module for the :composeApp:feature:reports feature (Sprint 22).
 *
 * Provides:
 * - [GenerateSalesReportUseCase]   — aggregates sales by date range, payment method, product.
 * - [GenerateStockReportUseCase]   — current stock, low-stock, dead-stock classification.
 * - [ReportPrinterAdapter]         — infrastructure adapter bound to [ReportPrinterPort].
 * - [PrintReportUseCase]           — condensed thermal Z-report summary via the port.
 * - [ReportExporter]               — platform-specific CSV/PDF export (see platform modules).
 * - [ReportsViewModel]             — MVI ViewModel for all three report screens.
 *
 * ### Dependency chain
 * ```
 * ReportsViewModel
 *   ├─ GenerateSalesReportUseCase  → OrderRepository
 *   ├─ GenerateStockReportUseCase  → ProductRepository + StockRepository
 *   ├─ PrintReportUseCase          → ReportPrinterPort (→ ReportPrinterAdapter)
 *   │   └─ PrinterManager          (bound in :shared:hal module)
 *   └─ ReportExporter              → bound per platform (jvmMain / androidMain)
 * ```
 *
 * Platform-specific [ReportExporter] bindings are declared in platform Koin modules:
 * - JVM: `jvmReportsModule` → [JvmReportExporter]
 * - Android: `androidReportsModule` → [AndroidReportExporter]
 *
 * Include this module in the root application Koin graph alongside the platform module:
 * ```kotlin
 * startKoin {
 *     modules(reportsModule, jvmReportsModule, …)
 * }
 * ```
 */
val reportsModule = module {
    // ── Port adapter (HAL orchestration lives here, NOT in the use case) ─────
    single<ReportPrinterPort> { ReportPrinterAdapter(get()) }

    // ── Domain use cases ─────────────────────────────────────────────────────
    factory { GenerateSalesReportUseCase(get()) }
    factory { GenerateStockReportUseCase(get(), get()) }
    factory { PrintReportUseCase(get()) }

    // ── ViewModel ────────────────────────────────────────────────────────────
    viewModelOf(::ReportsViewModel)
}

/** JVM-specific Koin module — binds [JvmReportExporter] as [ReportExporter]. */
val jvmReportsModule = module {
    single<ReportExporter> { JvmReportExporter() }
}
