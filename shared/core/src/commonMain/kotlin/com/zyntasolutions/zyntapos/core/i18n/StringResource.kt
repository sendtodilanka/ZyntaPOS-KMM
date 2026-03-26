package com.zyntasolutions.zyntapos.core.i18n

/**
 * A typed string resource key.
 *
 * Each [StringResource] maps to a translation entry in every supported locale's
 * string table. Using an enum (rather than raw strings) catches missing keys at
 * compile time instead of runtime.
 *
 * ### Naming convention
 * `<screen>_<element>` — lowercase, underscore-separated.
 *
 * ### Categories
 * Keys are grouped by feature module for easier auditing:
 * - `common_*`     — shared labels (OK, Cancel, Save, etc.)
 * - `auth_*`       — login, PIN lock, onboarding
 * - `pos_*`        — POS checkout, cart, payment
 * - `inventory_*`  — product, category, stock
 * - `register_*`   — cash register, EOD
 * - `reports_*`    — sales, stock, customer reports
 * - `settings_*`   — store profile, users, RBAC
 * - `dashboard_*`  — KPI dashboard
 * - `customers_*`  — customer directory
 * - `staff_*`      — HR, attendance, payroll
 * - `admin_*`      — system health, backups, audit
 * - `nav_*`        — navigation items
 * - `error_*`      — error messages
 * - `validation_*` — form validation messages
 */
enum class StringResource {
    // ── Common ──────────────────────────────────────────────────────────────
    COMMON_OK,
    COMMON_CANCEL,
    COMMON_SAVE,
    COMMON_DELETE,
    COMMON_EDIT,
    COMMON_SEARCH,
    COMMON_LOADING,
    COMMON_RETRY,
    COMMON_CONFIRM,
    COMMON_YES,
    COMMON_NO,
    COMMON_BACK,
    COMMON_CLOSE,
    COMMON_DONE,
    COMMON_ERROR,
    COMMON_SUCCESS,
    COMMON_WARNING,
    COMMON_NO_DATA,
    COMMON_REQUIRED,
    COMMON_OPTIONAL,
    COMMON_TOTAL,
    COMMON_SUBTOTAL,
    COMMON_TAX,
    COMMON_DISCOUNT,
    COMMON_QUANTITY,
    COMMON_PRICE,
    COMMON_AMOUNT,
    COMMON_DATE,
    COMMON_TIME,
    COMMON_STATUS,
    COMMON_ACTIONS,
    COMMON_SELECT_ALL,
    COMMON_DESELECT_ALL,
    COMMON_EXPORT,
    COMMON_PRINT,
    COMMON_REFRESH,

    // ── Auth ────────────────────────────────────────────────────────────────
    AUTH_LOGIN_TITLE,
    AUTH_EMAIL_LABEL,
    AUTH_PASSWORD_LABEL,
    AUTH_LOGIN_BUTTON,
    AUTH_FORGOT_PASSWORD,
    AUTH_PIN_TITLE,
    AUTH_PIN_SUBTITLE,
    AUTH_ENTER_PIN,
    AUTH_WRONG_PIN,
    AUTH_LOCKED_OUT,
    AUTH_LOGOUT,
    AUTH_SESSION_EXPIRED,

    // ── Onboarding ──────────────────────────────────────────────────────────
    ONBOARDING_WELCOME_TITLE,
    ONBOARDING_BUSINESS_NAME,
    ONBOARDING_BUSINESS_ADDRESS,
    ONBOARDING_ADMIN_NAME,
    ONBOARDING_ADMIN_EMAIL,
    ONBOARDING_ADMIN_PASSWORD,
    ONBOARDING_CREATE_ACCOUNT,
    ONBOARDING_STEP_BUSINESS,
    ONBOARDING_STEP_ADMIN,
    ONBOARDING_STEP_COMPLETE,

    // ── Navigation ──────────────────────────────────────────────────────────
    NAV_DASHBOARD,
    NAV_POS,
    NAV_INVENTORY,
    NAV_REGISTER,
    NAV_REPORTS,
    NAV_CUSTOMERS,
    NAV_SETTINGS,
    NAV_STAFF,
    NAV_ADMIN,
    NAV_EXPENSES,
    NAV_COUPONS,
    NAV_MULTISTORE,
    NAV_ACCOUNTING,

    // ── Dashboard ───────────────────────────────────────────────────────────
    DASHBOARD_TITLE,
    DASHBOARD_TODAYS_SALES,
    DASHBOARD_ORDER_COUNT,
    DASHBOARD_AVG_ORDER,
    DASHBOARD_LOW_STOCK_ALERTS,
    DASHBOARD_ACTIVE_REGISTERS,
    DASHBOARD_WEEKLY_TREND,
    DASHBOARD_RECENT_ORDERS,
    DASHBOARD_NO_ORDERS_TODAY,

    // ── POS ─────────────────────────────────────────────────────────────────
    POS_TITLE,
    POS_SEARCH_PRODUCTS,
    POS_ALL_CATEGORIES,
    POS_CART,
    POS_CART_EMPTY,
    POS_ADD_TO_CART,
    POS_REMOVE_FROM_CART,
    POS_HOLD_ORDER,
    POS_RECALL_ORDER,
    POS_VOID_ORDER,
    POS_PAY,
    POS_PAYMENT_CASH,
    POS_PAYMENT_CARD,
    POS_PAYMENT_SPLIT,
    POS_CHANGE_DUE,
    POS_RECEIPT,
    POS_PRINT_RECEIPT,
    POS_NEW_SALE,
    POS_CUSTOMER,
    POS_ADD_DISCOUNT,
    POS_ITEM_DISCOUNT,
    POS_ORDER_DISCOUNT,
    POS_REFUND,
    POS_REFUND_REASON,

    // ── Inventory ───────────────────────────────────────────────────────────
    INVENTORY_TITLE,
    INVENTORY_PRODUCTS,
    INVENTORY_CATEGORIES,
    INVENTORY_ADD_PRODUCT,
    INVENTORY_EDIT_PRODUCT,
    INVENTORY_PRODUCT_NAME,
    INVENTORY_SKU,
    INVENTORY_BARCODE,
    INVENTORY_COST_PRICE,
    INVENTORY_SELL_PRICE,
    INVENTORY_STOCK_LEVEL,
    INVENTORY_LOW_STOCK_THRESHOLD,
    INVENTORY_CATEGORY,
    INVENTORY_SUPPLIER,
    INVENTORY_ADJUST_STOCK,
    INVENTORY_ADJUSTMENT_REASON,
    INVENTORY_IN_STOCK,
    INVENTORY_LOW_STOCK,
    INVENTORY_OUT_OF_STOCK,

    // ── Register ────────────────────────────────────────────────────────────
    REGISTER_TITLE,
    REGISTER_OPEN,
    REGISTER_CLOSE,
    REGISTER_OPENING_BALANCE,
    REGISTER_CLOSING_BALANCE,
    REGISTER_CASH_IN,
    REGISTER_CASH_OUT,
    REGISTER_EXPECTED,
    REGISTER_ACTUAL,
    REGISTER_DIFFERENCE,
    REGISTER_Z_REPORT,
    REGISTER_SESSION_ACTIVE,
    REGISTER_SESSION_CLOSED,
    REGISTER_NOTES,

    // ── Reports ─────────────────────────────────────────────────────────────
    REPORTS_TITLE,
    REPORTS_SALES_SUMMARY,
    REPORTS_PRODUCT_PERFORMANCE,
    REPORTS_STOCK_REPORT,
    REPORTS_CUSTOMER_REPORT,
    REPORTS_EXPENSE_REPORT,
    REPORTS_DATE_RANGE,
    REPORTS_TODAY,
    REPORTS_THIS_WEEK,
    REPORTS_THIS_MONTH,
    REPORTS_CUSTOM_RANGE,
    REPORTS_EXPORT_CSV,
    REPORTS_EXPORT_PDF,
    REPORTS_TOTAL_REVENUE,
    REPORTS_TOTAL_ORDERS,
    REPORTS_AVG_ORDER_VALUE,
    REPORTS_TOP_PRODUCTS,
    REPORTS_PAYMENT_BREAKDOWN,

    // ── Settings ────────────────────────────────────────────────────────────
    SETTINGS_TITLE,
    SETTINGS_STORE_PROFILE,
    SETTINGS_TAX_CONFIG,
    SETTINGS_PRINTER_SETUP,
    SETTINGS_USER_MANAGEMENT,
    SETTINGS_SECURITY,
    SETTINGS_BACKUP_RESTORE,
    SETTINGS_ABOUT,
    SETTINGS_ROLES_PERMISSIONS,
    SETTINGS_ADD_USER,
    SETTINGS_EDIT_USER,
    SETTINGS_DEACTIVATE_USER,
    SETTINGS_RESET_PIN,

    // ── Customers ───────────────────────────────────────────────────────────
    CUSTOMERS_TITLE,
    CUSTOMERS_ADD,
    CUSTOMERS_NAME,
    CUSTOMERS_PHONE,
    CUSTOMERS_EMAIL,
    CUSTOMERS_LOYALTY_POINTS,
    CUSTOMERS_TOTAL_SPENT,
    CUSTOMERS_LAST_VISIT,

    // ── Staff ───────────────────────────────────────────────────────────────
    STAFF_TITLE,
    STAFF_EMPLOYEES,
    STAFF_ATTENDANCE,
    STAFF_SHIFTS,
    STAFF_PAYROLL,
    STAFF_CLOCK_IN,
    STAFF_CLOCK_OUT,
    STAFF_ADD_EMPLOYEE,
    STAFF_EDIT_EMPLOYEE,
    STAFF_DEPARTMENT,
    STAFF_POSITION,

    // ── Admin ───────────────────────────────────────────────────────────────
    ADMIN_TITLE,
    ADMIN_SYSTEM_HEALTH,
    ADMIN_DATABASE,
    ADMIN_VACUUM,
    ADMIN_PURGE,
    ADMIN_BACKUPS,
    ADMIN_CREATE_BACKUP,
    ADMIN_RESTORE_BACKUP,
    ADMIN_AUDIT_LOG,
    ADMIN_CONFLICTS,

    // ── Expenses ────────────────────────────────────────────────────────────
    EXPENSES_TITLE,
    EXPENSES_ADD,
    EXPENSES_CATEGORY,
    EXPENSES_AMOUNT,
    EXPENSES_APPROVE,
    EXPENSES_REJECT,

    // ── Errors ──────────────────────────────────────────────────────────────
    ERROR_GENERIC,
    ERROR_NETWORK,
    ERROR_TIMEOUT,
    ERROR_UNAUTHORIZED,
    ERROR_NOT_FOUND,
    ERROR_SERVER,
    ERROR_OFFLINE,

    // ── Validation ──────────────────────────────────────────────────────────
    VALIDATION_FIELD_REQUIRED,
    VALIDATION_EMAIL_INVALID,
    VALIDATION_PASSWORD_TOO_SHORT,
    VALIDATION_PIN_LENGTH,
    VALIDATION_AMOUNT_INVALID,
    VALIDATION_NAME_TOO_SHORT,

    // ── Media ─────────────────────────────────────────────────────────────
    MEDIA_TITLE,
    MEDIA_LIBRARY,
    MEDIA_UPLOAD,
    MEDIA_CROP,
    MEDIA_COMPRESS,
    MEDIA_SET_PRIMARY,
    MEDIA_DELETE_IMAGE,
    MEDIA_BATCH_UPLOAD,
    MEDIA_FULL_PREVIEW,

    // ── E-Invoice ─────────────────────────────────────────────────────────
    EINVOICE_TITLE,
    EINVOICE_CREATE,
    EINVOICE_SUBMIT_IRD,
    EINVOICE_CANCEL,
    EINVOICE_STATUS_DRAFT,
    EINVOICE_STATUS_SUBMITTED,
    EINVOICE_STATUS_ACCEPTED,
    EINVOICE_STATUS_REJECTED,
    EINVOICE_COMPLIANCE_REPORT,
    EINVOICE_REFERENCE_NUMBER,

    // ── Accounting ────────────────────────────────────────────────────────
    ACCOUNTING_TITLE,
    ACCOUNTING_CHART_OF_ACCOUNTS,
    ACCOUNTING_JOURNAL_ENTRIES,
    ACCOUNTING_GENERAL_LEDGER,
    ACCOUNTING_PROFIT_LOSS,
    ACCOUNTING_BALANCE_SHEET,
    ACCOUNTING_TRIAL_BALANCE,
    ACCOUNTING_CASH_FLOW,
    ACCOUNTING_RECONCILIATION,
    ACCOUNTING_EXPORT_CSV,
    ACCOUNTING_PERIOD,

    // ── Multi-Store ───────────────────────────────────────────────────────
    MULTISTORE_TITLE,
    MULTISTORE_WAREHOUSES,
    MULTISTORE_TRANSFERS,
    MULTISTORE_NEW_TRANSFER,
    MULTISTORE_TRANSFER_STATUS,
    MULTISTORE_RACKS,
    MULTISTORE_PICK_LIST,
    MULTISTORE_STORE_COMPARISON,
    MULTISTORE_SELECT_STORE,

    // ── Coupons ───────────────────────────────────────────────────────────
    COUPONS_TITLE,
    COUPONS_CREATE,
    COUPONS_CODE,
    COUPONS_DISCOUNT_TYPE,
    COUPONS_VALID_FROM,
    COUPONS_VALID_UNTIL,
    COUPONS_USAGE_LIMIT,
    COUPONS_MIN_ORDER,
    COUPONS_ACTIVE,
    COUPONS_EXPIRED,

    // ── Security Settings ─────────────────────────────────────────────────
    SETTINGS_SECURITY_POLICY,
    SETTINGS_SESSION_TIMEOUT,
    SETTINGS_PIN_COMPLEXITY,
    SETTINGS_FAILED_LOGIN_LOCKOUT,
    SETTINGS_LOCKOUT_DURATION,
    SETTINGS_BIOMETRIC_AUTH,
    SETTINGS_DATA_RETENTION,
    SETTINGS_AUDIT_RETENTION,
    SETTINGS_SYNC_RETENTION,
    SETTINGS_REPORT_RETENTION,
    SETTINGS_PURGE_NOW,
    SETTINGS_AUDIT_POLICY,
    SETTINGS_LANGUAGE,
    SETTINGS_THEME,

    // ── RBAC ──────────────────────────────────────────────────────────────
    RBAC_SYSTEM_ROLES,
    RBAC_CUSTOM_ROLES,
    RBAC_CREATE_ROLE,
    RBAC_EDIT_ROLE,
    RBAC_DELETE_ROLE,
    RBAC_CLONE_ROLE,
    RBAC_ROLE_NAME,
    RBAC_PERMISSIONS,
    RBAC_PERMISSION_COUNT,
}
