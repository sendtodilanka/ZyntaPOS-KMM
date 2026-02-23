package com.zyntasolutions.zyntapos.hal.di

import com.zyntasolutions.zyntapos.hal.printer.EscPosReceiptBuilder
import com.zyntasolutions.zyntapos.hal.printer.NullPrinterPort
import com.zyntasolutions.zyntapos.hal.printer.PrinterConfig
import com.zyntasolutions.zyntapos.hal.printer.PrinterPort
import com.zyntasolutions.zyntapos.hal.printer.ReceiptBuilder
import com.zyntasolutions.zyntapos.hal.scanner.AndroidUsbScanner
import com.zyntasolutions.zyntapos.hal.scanner.BarcodeScanner
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * ZyntaPOS — Hardware Abstraction Layer · Android Koin DI
 *
 * Android actual for [halModule].
 *
 * ### Phase 1 Printer Binding Strategy
 * Both [AndroidUsbPrinterPort] and [AndroidBluetoothPrinterPort] require a
 * runtime-discovered device reference (`UsbDevice` / `BluetoothDevice`) only
 * available after the operator configures a printer in Settings. The default
 * binding is therefore [NullPrinterPort] — a safe stub returning descriptive
 * errors until configuration completes.
 *
 * **Phase 2 override** (`:feature:settings`, Sprint 23):
 * ```kotlin
 * koin.loadModules(listOf(module {
 *     single<PrinterPort>(override = true) { AndroidUsbPrinterPort(ctx, usbDevice) }
 * }))
 * ```
 *
 * ### Phase 1 Scanner Binding
 * Default: [AndroidUsbScanner] — USB HID keyboard-wedge mode. Works without a
 * lifecycle owner; the POS screen later rebinds to [AndroidCameraScanner] when
 * the operator enables camera scanning in Settings.
 *
 * ### Provides
 * - [PrinterPort] → [NullPrinterPort] (Phase 1 safe default)
 * - [BarcodeScanner] → [AndroidUsbScanner] (USB HID keyboard wedge)
 * - [ReceiptBuilder] → [EscPosReceiptBuilder]
 * - [PrinterManager] via [halCommonModule]
 */
actual fun halModule(): Module = module {

    // ── Printer port — safe stub until Settings configures hardware ─────────
    single<PrinterPort> { NullPrinterPort() }

    // ── Barcode scanner — USB HID keyboard wedge (no LifecycleOwner needed) ─
    single<BarcodeScanner> {
        AndroidUsbScanner(context = androidContext())
    }

    // ── Receipt builder (concrete + interface binding for DI resolution) ───
    single { EscPosReceiptBuilder(config = PrinterConfig.DEFAULT) } bind ReceiptBuilder::class

    // ── Common bindings (PrinterManager) ──────────────────────────────────
    includes(halCommonModule)
}
