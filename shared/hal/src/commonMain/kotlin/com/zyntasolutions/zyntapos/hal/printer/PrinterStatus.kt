package com.zyntasolutions.zyntapos.hal.printer

/**
 * ZyntaPOS — Hardware Abstraction Layer
 *
 * Point-in-time snapshot of a thermal printer's operational status.
 *
 * Produced and maintained by [PrinterStatusMonitor] in response to
 * [PrinterStatusEvent]s emitted by [PrinterPort.statusEvents].
 *
 * Expose [PrinterStatusMonitor.statusState] in the UI to show banners for
 * paper-out, cover-open, and drawer-state conditions.
 */
data class PrinterStatus(
    /** `true` when the printer has confirmed it is online and ready. */
    val isOnline: Boolean = false,

    /** `true` when the paper-out sensor is triggered (no paper in roll). */
    val isPaperOut: Boolean = false,

    /** `true` when the near-end (paper-low) sensor is triggered. */
    val isPaperLow: Boolean = false,

    /** `true` when the printer cover/lid is open. */
    val isCoverOpen: Boolean = false,

    /** `true` when the cash drawer (detected via the kick port) is open. */
    val isDrawerOpen: Boolean = false,
)
