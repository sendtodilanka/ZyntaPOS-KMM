package com.zyntasolutions.zyntapos.feature.settings

// ─────────────────────────────────────────────────────────────────────────────
// SettingsKeys — Canonical string key constants for [SettingsRepository].
//
// All feature code MUST use these constants instead of raw string literals to
// prevent typo-driven regressions and enable easy global search/replace.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Single source of truth for all settings keys stored via [SettingsRepository].
 */
object SettingsKeys {

    // ── General ───────────────────────────────────────────────────────────────
    const val STORE_NAME      = "general.store_name"
    const val STORE_ADDRESS   = "general.store_address"
    const val STORE_PHONE     = "general.store_phone"
    const val STORE_LOGO_URI  = "general.store_logo_uri"
    const val CURRENCY        = "general.currency"
    const val TIMEZONE        = "general.timezone"
    const val DATE_FORMAT     = "general.date_format"
    const val LANGUAGE        = "general.language"

    // ── POS ───────────────────────────────────────────────────────────────────
    const val DEFAULT_ORDER_TYPE      = "pos.default_order_type"
    const val AUTO_PRINT_RECEIPT      = "pos.auto_print_receipt"
    const val TAX_DISPLAY_MODE        = "pos.tax_display_mode"
    const val RECEIPT_TEMPLATE        = "pos.receipt_template"
    const val MAX_DISCOUNT_PERCENT    = "pos.max_discount_percent"

    // ── Printer ───────────────────────────────────────────────────────────────
    const val PRINTER_TYPE            = "printer.type"
    const val PRINTER_TCP_HOST        = "printer.tcp_host"
    const val PRINTER_TCP_PORT        = "printer.tcp_port"
    const val PRINTER_SERIAL_PORT     = "printer.serial_port"
    const val PRINTER_BAUD_RATE       = "printer.baud_rate"
    const val PRINTER_BT_ADDRESS      = "printer.bt_address"
    const val PRINTER_PAPER_WIDTH     = "printer.paper_width"
    const val PRINTER_HEADER_LINE_1   = "printer.header_1"
    const val PRINTER_HEADER_LINE_2   = "printer.header_2"
    const val PRINTER_HEADER_LINE_3   = "printer.header_3"
    const val PRINTER_HEADER_LINE_4   = "printer.header_4"
    const val PRINTER_HEADER_LINE_5   = "printer.header_5"
    const val PRINTER_FOOTER_LINE_1   = "printer.footer_1"
    const val PRINTER_FOOTER_LINE_2   = "printer.footer_2"
    const val PRINTER_SHOW_QR         = "printer.show_qr"
    const val PRINTER_SHOW_LOGO       = "printer.show_logo"

    // ── Appearance ────────────────────────────────────────────────────────────
    const val THEME_MODE              = "appearance.theme_mode"

    // ── Security ──────────────────────────────────────────────────────────────
    const val SECURITY_AUTOLOCK_MINUTES = "security.autolock_minutes"

    // ── Backup ────────────────────────────────────────────────────────────────
    const val LAST_BACKUP_TIMESTAMP   = "backup.last_backup_ts"
}
