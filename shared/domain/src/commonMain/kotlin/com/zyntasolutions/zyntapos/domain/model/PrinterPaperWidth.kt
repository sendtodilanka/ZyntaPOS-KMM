package com.zyntasolutions.zyntapos.domain.model

/**
 * Domain representation of a thermal receipt printer's paper roll width.
 *
 * This enum belongs to the domain layer so that use cases and repositories
 * can reference paper-width settings without importing HAL types.
 * HAL implementations map this to their own [com.zyntasolutions.zyntapos.hal.printer.PaperWidth]
 * internally, keeping the domain layer HAL-free.
 *
 * @property mm Physical width of the paper roll in millimetres.
 */
enum class PrinterPaperWidth(val mm: Int) {
    /** 58 mm roll — common in compact, mobile, and countertop printers. */
    MM_58(58),
    /** 80 mm roll — standard width for most full-size POS receipt printers. */
    MM_80(80),
}
