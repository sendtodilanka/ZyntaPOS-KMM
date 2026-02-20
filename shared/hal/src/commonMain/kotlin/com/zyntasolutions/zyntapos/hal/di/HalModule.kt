package com.zyntasolutions.zyntapos.hal.di

/**
 * ZentaPOS — :shared:hal Koin DI Module
 *
 * Placeholder for Sprint 7 (Step 4.2.11) where platform-specific bindings are added
 * via `expect fun halModule(): Module`:
 *
 * Android provides:
 * - AndroidUsbPrinterPort (Step 4.2.1)
 * - AndroidCameraScanner (Step 4.2.3)
 *
 * Desktop (JVM) provides:
 * - DesktopTcpPrinterPort (Step 4.2.6)
 * - DesktopHidScanner (Step 4.2.8)
 *
 * Common provides:
 * - PrinterManager wrapper (Step 4.1.6)
 *
 * Registered in the root Koin graph via `startKoin { modules(halModule()) }`.
 */
internal object HalModule
