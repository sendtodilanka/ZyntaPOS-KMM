package com.zyntasolutions.zyntapos.feature.settings

import com.zyntasolutions.zyntapos.domain.model.OrderType
import com.zyntasolutions.zyntapos.domain.model.TaxGroup
import com.zyntasolutions.zyntapos.domain.model.User
import kotlinx.datetime.Instant

/**
 * MVI — UI state for the entire settings feature.
 *
 * Each settings sub-screen has a dedicated nested [State] class.
 * All state is observable via [SettingsViewModel.state].
 */
data class SettingsState(
    val general: GeneralState       = GeneralState(),
    val pos: PosState               = PosState(),
    val tax: TaxState               = TaxState(),
    val printer: PrinterState       = PrinterState(),
    val users: UserState            = UserState(),
    val backup: BackupState         = BackupState(),
    val appearance: AppearanceState = AppearanceState(),

    /** True while any cross-screen async operation is pending. */
    val isLoading: Boolean = false,
    val error: String? = null,
) {

    // ── General settings ──────────────────────────────────────────────────────

    data class GeneralState(
        val storeName: String    = "",
        val storeAddress: String = "",
        val storePhone: String   = "",
        val logoUri: String      = "",
        val currency: Currency   = Currency.LKR,
        val timezone: String     = "Asia/Colombo",
        val dateFormat: String   = "dd/MM/yyyy",
        val language: String     = "en",
        val isSaving: Boolean    = false,
        val saveError: String?   = null,
    )

    // ── POS settings ──────────────────────────────────────────────────────────

    data class PosState(
        val defaultOrderType: OrderType   = OrderType.SALE,
        val autoPrintReceipt: Boolean     = true,
        val taxDisplayMode: TaxDisplayMode = TaxDisplayMode.EXCLUSIVE,
        val receiptTemplate: ReceiptTemplate = ReceiptTemplate.STANDARD,
        val maxDiscountPercent: Double    = 20.0,
        val isSaving: Boolean            = false,
        val saveError: String?           = null,
    )

    // ── Tax settings ──────────────────────────────────────────────────────────

    data class TaxState(
        val taxGroups: List<TaxGroup>   = emptyList(),
        val isLoading: Boolean          = false,
        val isEditing: TaxGroup?        = null,   // non-null = edit sheet open
        val isCreating: Boolean         = false,  // FAB → create sheet
        val deleteTarget: TaxGroup?     = null,   // non-null = confirm dialog shown
        val saveError: String?          = null,
    )

    // ── Printer settings ──────────────────────────────────────────────────────

    data class PrinterState(
        val printerType: PrinterType         = PrinterType.USB,
        val tcpHost: String                  = "",
        val tcpPort: String                  = "9100",
        val serialPort: String               = "",
        val baudRate: String                 = "115200",
        val btAddress: String                = "",
        val paperWidth: PaperWidthOption     = PaperWidthOption.MM_80,
        val headerLines: List<String>        = List(5) { "" },
        val footerLines: List<String>        = List(2) { "" },
        val showQrCode: Boolean              = true,
        val showLogo: Boolean                = false,
        val isTestPrinting: Boolean          = false,
        val isSaving: Boolean                = false,
        val saveError: String?               = null,
    )

    // ── User management ───────────────────────────────────────────────────────

    data class UserState(
        val users: List<User>          = emptyList(),
        val isLoading: Boolean         = false,
        val editingUser: User?         = null,   // non-null = slide-over open
        val isCreating: Boolean        = false,
        /** Form field values for the create/edit slide-over. */
        val form: UserForm             = UserForm(),
        val saveError: String?         = null,
    ) {
        data class UserForm(
            val name: String       = "",
            val email: String      = "",
            val password: String   = "",
            val roleKey: String    = "CASHIER",
            val isActive: Boolean  = true,
        )
    }

    // ── Backup settings ───────────────────────────────────────────────────────

    data class BackupState(
        val lastBackupAt: Instant?       = null,
        val isBackingUp: Boolean         = false,
        val isRestoring: Boolean         = false,
        val confirmRestore: Boolean      = false,
        val restoreFilePath: String?     = null,
        val backupError: String?         = null,
    )

    // ── Appearance settings ───────────────────────────────────────────────────

    data class AppearanceState(
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
    )
}

// ─── Supporting enums ─────────────────────────────────────────────────────────

/** Currencies supported in Phase 1. */
enum class Currency(val symbol: String, val code: String) {
    LKR("₨", "LKR"),
    USD("$", "USD"),
    EUR("€", "EUR"),
}

/** How tax amounts are communicated to the customer. */
enum class TaxDisplayMode { INCLUSIVE, EXCLUSIVE }

/** Receipt layout templates. */
enum class ReceiptTemplate { STANDARD, MINIMAL }

/** Physical printer connection type. */
enum class PrinterType { USB, BLUETOOTH, SERIAL, TCP }

/** Paper roll width options shown in the settings UI. */
enum class PaperWidthOption(val mm: Int, val halValue: String) {
    MM_58(58, "MM_58"),
    MM_80(80, "MM_80"),
}

/** Application theme mode. */
enum class ThemeMode { LIGHT, DARK, SYSTEM }
