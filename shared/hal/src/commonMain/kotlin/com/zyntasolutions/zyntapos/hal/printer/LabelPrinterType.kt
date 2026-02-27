package com.zyntasolutions.zyntapos.hal.printer

/**
 * ZyntaPOS — Hardware Abstraction Layer
 *
 * Identifies the command language and transport combination used to communicate
 * with a label printer. Stored in [LabelPrinterConfig.type].
 */
enum class LabelPrinterType {

    /** No label printer configured — all label printing uses the PDF/OS fallback. */
    NONE,

    /** Zebra ZPL (Zebra Programming Language) over TCP/IP port 9100. */
    ZPL_TCP,

    /** Zebra ZPL over USB direct (raw write to USB device). */
    ZPL_USB,

    /** Zebra ZPL over Bluetooth SPP (Serial Port Profile). */
    ZPL_BT,

    /** TSC/TSPL (TSC Label Programming Language) over TCP/IP port 9100. */
    TSPL_TCP,

    /** TSC/TSPL over USB direct. */
    TSPL_USB,

    /** TSC/TSPL over Bluetooth SPP. */
    TSPL_BT,

    /**
     * PDF generated and printed via the OS print dialog.
     * Supports any printer the OS driver can reach (DYMO, Brother, generic A4).
     * This is the fallback used when no direct ZPL/TSPL connection is configured.
     */
    PDF_SYSTEM,
}
