package com.zyntasolutions.zyntapos.hal.printer

/**
 * ZyntaPOS — Hardware Abstraction Layer
 *
 * Real-time status events emitted by [PrinterPort.statusEvents].
 *
 * Implementations poll the printer's DLE EOT / GS a status register and map the
 * result bytes to these sealed values. [PrinterStatusMonitor] aggregates these
 * events into a [PrinterStatus] snapshot.
 */
sealed interface PrinterStatusEvent {

    /** Printer is online and ready. */
    data object Online : PrinterStatusEvent

    /** Printer is offline or unreachable. */
    data object Offline : PrinterStatusEvent

    /** Paper roll is empty — print head raised, no paper. */
    data object PaperOut : PrinterStatusEvent

    /** Paper roll is running low — near-end sensor triggered. */
    data object PaperLow : PrinterStatusEvent

    /** Printer cover/lid is open. */
    data object CoverOpen : PrinterStatusEvent

    /** Printer cover/lid has been closed. */
    data object CoverClosed : PrinterStatusEvent

    /** Cash drawer is open (drawer kick port detects open circuit). */
    data object DrawerOpen : PrinterStatusEvent

    /** Cash drawer is closed. */
    data object DrawerClosed : PrinterStatusEvent
}
