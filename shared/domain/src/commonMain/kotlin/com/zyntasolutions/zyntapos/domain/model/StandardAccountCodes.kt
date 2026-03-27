package com.zyntasolutions.zyntapos.domain.model

/**
 * Canonical account codes for the default Chart of Accounts seeded into every new store.
 *
 * Use these constants wherever code looks up accounts by code string (e.g., journal-entry
 * use cases) instead of embedding magic strings. If a store customises their chart of
 * accounts, the seed codes remain the well-known defaults.
 */
object StandardAccountCodes {
    // ── Assets ────────────────────────────────────────────────────────────────
    const val CASH                    = "1010"
    const val ACCOUNTS_RECEIVABLE     = "1100"
    const val INVENTORY               = "1200"

    // ── Liabilities ───────────────────────────────────────────────────────────
    const val SALES_TAX_PAYABLE       = "2100"
    const val ACCOUNTS_PAYABLE        = "2200"

    // ── Revenue ───────────────────────────────────────────────────────────────
    const val SALES_REVENUE           = "4010"
    const val DISCOUNT_GIVEN          = "4020"

    // ── Cost of Goods Sold ────────────────────────────────────────────────────
    const val COGS                    = "5010"

    // ── Expenses ──────────────────────────────────────────────────────────────
    const val GENERAL_EXPENSES        = "6010"
}
