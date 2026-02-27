package com.zyntasolutions.zyntapos.hal.printer

/**
 * ZyntaPOS — Hardware Abstraction Layer
 *
 * Controls when the cash drawer kick pulse is sent after a completed payment.
 *
 * Configured in [PrinterConfig.cashDrawerTrigger] and evaluated by
 * [EscPosReceiptBuilder.buildReceipt] before emitting the `ESC p` command.
 */
enum class CashDrawerTrigger {

    /** Send the kick pulse after every completed payment (default behaviour). */
    ALL_PAYMENTS,

    /**
     * Send the kick pulse only for cash payments.
     * Card, mobile-pay, bank-transfer, and split payments that do not include
     * cash will not trigger the drawer.
     */
    CASH_ONLY,

    /**
     * Never emit the cash drawer kick command.
     * Use this when the drawer is opened manually (e.g., foot pedal) or when
     * no drawer is connected to the printer.
     */
    NEVER,
}
