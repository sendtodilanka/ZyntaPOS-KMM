package com.zyntasolutions.zyntapos.hal.di

import com.zyntasolutions.zyntapos.hal.printer.LabelPrinterManager
import com.zyntasolutions.zyntapos.hal.printer.NullLabelPrinterPort
import com.zyntasolutions.zyntapos.hal.printer.PrinterManager
import com.zyntasolutions.zyntapos.hal.printer.PrinterStatusMonitor
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * ZyntaPOS — Hardware Abstraction Layer · Koin DI
 *
 * Platform-specific Koin module factory.
 *
 * Each platform's actual declaration provides concrete bindings for:
 * - **Android**: [com.zyntasolutions.zyntapos.hal.printer.AndroidUsbPrinterPort] /
 *               [com.zyntasolutions.zyntapos.hal.scanner.AndroidCameraScanner]
 * - **Desktop**: [com.zyntasolutions.zyntapos.hal.printer.DesktopTcpPrinterPort] /
 *               [com.zyntasolutions.zyntapos.hal.scanner.DesktopHidScanner]
 *
 * The common [halCommonModule] then wraps whichever platform bindings were
 * registered and provides [PrinterManager] — the single gateway used by all
 * shared ViewModels and use cases.
 *
 * ### Usage
 * ```kotlin
 * // In your KoinApplication initialisation (androidMain / jvmMain app entry)
 * startKoin {
 *     modules(halModule())
 * }
 * ```
 */
expect fun halModule(): Module

/**
 * Common bindings that delegate to platform-provided [PrinterPort] and
 * [ReceiptBuilder] singletons.
 *
 * This module is included by every platform's [halModule] actual.
 */
val halCommonModule: Module = module {
    /**
     * [PrinterManager] — single Koin singleton wrapping the active [PrinterPort].
     * Platform actuals register the concrete port; this binding picks it up via `get()`.
     */
    single { PrinterManager(port = get()) }

    /**
     * [PrinterStatusMonitor] — aggregates real-time [PrinterStatusEvent]s from the active
     * [PrinterPort] into a [PrinterStatus] StateFlow consumable by the UI layer.
     */
    single { PrinterStatusMonitor(port = get()) }

    /**
     * [NullLabelPrinterPort] — safe default stub returned until the operator configures
     * a real label printer via Settings → Label Printer.
     */
    single<com.zyntasolutions.zyntapos.hal.printer.LabelPrinterPort> { NullLabelPrinterPort() }

    /**
     * [LabelPrinterManager] — single gateway for ZPL/TSPL label printer communication.
     * Uses the [LabelPrinterPort] binding above (overrideable from Settings at runtime).
     */
    single { LabelPrinterManager(port = get()) }
}
