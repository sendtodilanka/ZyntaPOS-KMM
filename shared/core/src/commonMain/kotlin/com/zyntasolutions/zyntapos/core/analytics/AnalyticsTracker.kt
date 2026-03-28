package com.zyntasolutions.zyntapos.core.analytics

/**
 * Analytics abstraction for feature modules (TODO-011).
 *
 * Feature modules depend on `:shared:core` and inject this interface via Koin.
 * The platform-specific [AnalyticsService] in `:shared:data` implements it.
 *
 * This separation keeps feature modules free of `:shared:data` dependencies
 * while allowing analytics event logging from any ViewModel.
 */
interface AnalyticsTracker {

    /** Log a custom event with optional parameters. */
    fun logEvent(name: String, params: Map<String, String> = emptyMap())

    /** Log a screen view event. */
    fun logScreenView(screenName: String, screenClass: String = "")

    /** Set the user ID for event attribution. Pass null on logout. */
    fun setUserId(userId: String?)

    /** Set a user property for segmentation. */
    fun setUserProperty(name: String, value: String)
}

/**
 * Standard event names for ZyntaPOS analytics.
 * Follows snake_case convention: max 40 chars, descriptive names.
 */
object AnalyticsEvents {
    // Authentication
    const val LOGIN = "login"
    const val LOGOUT = "logout"
    const val PIN_UNLOCK = "pin_unlock"

    // POS Operations
    const val SALE_COMPLETED = "sale_completed"
    const val CART_UPDATED = "cart_updated"
    const val DISCOUNT_APPLIED = "discount_applied"
    const val PAYMENT_PROCESSED = "payment_processed"
    const val ORDER_HELD = "order_held"
    const val ORDER_RESUMED = "order_resumed"
    const val REFUND_PROCESSED = "refund_processed"

    // Inventory
    const val PRODUCT_SEARCHED = "product_searched"
    const val STOCK_ADJUSTED = "stock_adjusted"
    const val BARCODE_SCANNED = "barcode_scanned"

    // Register
    const val REGISTER_OPENED = "register_opened"
    const val REGISTER_CLOSED = "register_closed"

    // Navigation
    const val SCREEN_VIEW = "screen_view"
}

/**
 * Standard parameter keys for ZyntaPOS analytics.
 */
object AnalyticsParams {
    const val SCREEN_NAME = "screen_name"
    const val SCREEN_CLASS = "screen_class"
    const val METHOD = "method"
    const val STORE_ID = "store_id"
    const val USER_ROLE = "user_role"
    const val ORDER_TOTAL = "order_total"
    const val ITEM_COUNT = "item_count"
    const val PAYMENT_METHOD = "payment_method"
    const val SEARCH_TERM = "search_term"
    const val CURRENCY = "currency"
}
