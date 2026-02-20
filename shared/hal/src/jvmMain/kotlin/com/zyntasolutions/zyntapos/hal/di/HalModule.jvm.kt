package com.zyntasolutions.zyntapos.hal.di

import com.zyntasolutions.zyntapos.hal.printer.DesktopTcpPrinterPort
import com.zyntasolutions.zyntapos.hal.printer.EscPosReceiptBuilder
import com.zyntasolutions.zyntapos.hal.printer.PrinterConfig
import com.zyntasolutions.zyntapos.hal.printer.PrinterPort
import com.zyntasolutions.zyntapos.hal.printer.ReceiptBuilder
import com.zyntasolutions.zyntapos.hal.scanner.BarcodeScanner
import com.zyntasolutions.zyntapos.hal.scanner.DesktopHidScanner
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * ZentaPOS — Hardware Abstraction Layer · Desktop (JVM) Koin DI
 *
 * Desktop actual for [halModule].
 *
 * Provides:
 * - [PrinterPort] → [DesktopTcpPrinterPort] (TCP/IP ESC/POS, default 192.168.1.100:9100)
 * - [BarcodeScanner] → [DesktopHidScanner] (AWT keyboard-wedge HID)
 * - [ReceiptBuilder] → [EscPosReceiptBuilder] with default [PrinterConfig]
 *
 * Also includes [halCommonModule] which provides [PrinterManager] using the
 * above bindings.
 *
 * ### Printer host / port
 * The default TCP host/port comes from [PrinterConfig.DEFAULT]. In Phase 1
 * these values are hardcoded; the :feature:settings module will expose a
 * UI to reconfigure them and re-bind [PrinterPort] at runtime.
 */
actual fun halModule(): Module = module {

    // ── Printer port (TCP/IP default) ─────────────────────────────────────
    single<PrinterPort> {
        // Phase 1 defaults; settings module will override in a later sprint.
        DesktopTcpPrinterPort(host = "192.168.1.100", port = 9100)
    }

    // ── Barcode scanner (HID keyboard wedge) ─────────────────────────────
    single<BarcodeScanner> {
        DesktopHidScanner()
    }

    // ── Receipt builder ────────────────────────────────────────────────────
    single<ReceiptBuilder> {
        EscPosReceiptBuilder(config = PrinterConfig.DEFAULT)
    }

    // ── Common bindings (PrinterManager) ──────────────────────────────────
    includes(halCommonModule)
}
