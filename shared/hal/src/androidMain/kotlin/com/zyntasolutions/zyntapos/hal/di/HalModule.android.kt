package com.zyntasolutions.zyntapos.hal.di

import com.zyntasolutions.zyntapos.hal.image.ImageProcessor
import com.zyntasolutions.zyntapos.hal.image.ImageProcessorImpl
import com.zyntasolutions.zyntapos.hal.printer.EscPosReceiptBuilder
import com.zyntasolutions.zyntapos.hal.printer.LabelPrinterPort
import com.zyntasolutions.zyntapos.hal.printer.NullLabelPrinterPort
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
 * **Runtime override** (`:feature:settings`):
 * ```kotlin
 * koin.loadModules(listOf(module {
 *     single<PrinterPort>(override = true) { AndroidUsbPrinterPort(ctx, usbDevice) }
 *     single<LabelPrinterPort>(override = true) { AndroidUsbLabelPrinterPort(ctx, usbDevice) }
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
 * - [LabelPrinterPort] → [NullLabelPrinterPort] (Phase 1 safe default)
 * - [BarcodeScanner] → [AndroidUsbScanner] (USB HID keyboard wedge)
 * - [ReceiptBuilder] → [EscPosReceiptBuilder]
 * - [PrinterManager], [PrinterStatusMonitor], [LabelPrinterManager] via [halCommonModule]
 */
actual fun halModule(): Module = module {

    // ── Receipt printer port — safe stub until Settings configures hardware ──
    single<PrinterPort> { NullPrinterPort() }

    // ── Label printer port — safe stub until Settings configures hardware ────
    // Override with AndroidUsbLabelPrinterPort or AndroidBluetoothLabelPrinterPort
    // once the operator selects hardware in Label Printer Settings.
    single<LabelPrinterPort> { NullLabelPrinterPort() }

    // ── Barcode scanner — USB HID keyboard wedge (no LifecycleOwner needed) ─
    single<BarcodeScanner> {
        AndroidUsbScanner(context = androidContext())
    }

    // ── Receipt builder ───────────────────────────────────────────────────────
    single { EscPosReceiptBuilder(config = PrinterConfig.DEFAULT) } bind ReceiptBuilder::class

    // ── Image processor — Android Bitmap compress / crop / thumbnail ──────────
    single<ImageProcessor> { ImageProcessorImpl() }

    // ── Common bindings (PrinterManager, PrinterStatusMonitor, LabelPrinterManager) ─
    includes(halCommonModule)
}
