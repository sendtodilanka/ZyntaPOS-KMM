package com.zyntasolutions.zyntapos.feature.settings

import androidx.compose.runtime.Immutable
import com.zyntasolutions.zyntapos.domain.model.CustomRole
import com.zyntasolutions.zyntapos.domain.model.OrderType
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.PrinterJobType
import com.zyntasolutions.zyntapos.domain.model.PrinterProfile
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.TaxGroup
import com.zyntasolutions.zyntapos.domain.model.User
import kotlinx.datetime.Instant

/**
 * MVI — UI state for the entire settings feature.
 *
 * Each settings sub-screen has a dedicated nested [State] class.
 * All state is observable via [SettingsViewModel.state].
 */
@Immutable
data class SettingsState(
    val general: GeneralState               = GeneralState(),
    val pos: PosState                       = PosState(),
    val tax: TaxState                       = TaxState(),
    val printer: PrinterState               = PrinterState(),
    val users: UserState                    = UserState(),
    val backup: BackupState                 = BackupState(),
    val appearance: AppearanceState         = AppearanceState(),
    val security: SecurityState             = SecurityState(),
    val rbac: RbacState                     = RbacState(),
    val labelPrinter: LabelPrinterState     = LabelPrinterState(),
    val scannerSettings: ScannerSettingsState = ScannerSettingsState(),
    val printerProfiles: PrinterProfilesState = PrinterProfilesState(),

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
        val dailySalesTarget: Double     = 75_000.0,
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
        /** Per-store tax rate overrides for multi-region support (G8-1). */
        val taxOverrides: List<StoreTaxOverride>  = emptyList(),
        val showTaxOverrideDialog: Boolean        = false,
        val editingTaxOverride: StoreTaxOverride?  = null,
    )

    /**
     * A per-store override for a specific [TaxGroup] rate.
     *
     * Allows stores in different regions to charge a different tax rate
     * than the global default defined on the [TaxGroup].
     */
    data class StoreTaxOverride(
        val storeId: String,
        val storeName: String,
        val taxGroupId: String,
        val taxGroupName: String,
        val overrideRate: Double,
        val isEnabled: Boolean = true,
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
        val users: List<User>               = emptyList(),
        val isLoading: Boolean              = false,
        val editingUser: User?              = null,   // non-null = slide-over open
        val isCreating: Boolean             = false,
        /** Form field values for the create/edit slide-over. */
        val form: UserForm                  = UserForm(),
        val saveError: String?              = null,
        /** Custom roles available for role-assignment dropdown. Populated alongside users. */
        val availableCustomRoles: List<CustomRole> = emptyList(),
    ) {
        data class UserForm(
            val name: String        = "",
            val email: String       = "",
            val password: String    = "",
            val roleKey: String     = "CASHIER",
            val isActive: Boolean   = true,
            // ── PIN setup (optional; blank = do not change) ──
            val newPin: String      = "",
            val confirmPin: String  = "",
            val pinError: String?   = null,
        )
    }

    // ── RBAC management ───────────────────────────────────────────────────────

    data class RbacState(
        /** Effective permission sets per non-ADMIN built-in role (override or default). */
        val builtInRoles: List<Pair<Role, Set<Permission>>> = emptyList(),
        val customRoles: List<CustomRole>                   = emptyList(),
        val isLoading: Boolean                              = false,
        /** Non-null while the edit bottom sheet is open for a custom role. */
        val editingCustomRole: CustomRole?                  = null,
        val isCreatingCustomRole: Boolean                   = false,
        val roleForm: CustomRoleForm                        = CustomRoleForm(),
        val saveError: String?                              = null,
    ) {
        data class CustomRoleForm(
            val name: String                    = "",
            val description: String             = "",
            val selectedPermissions: Set<Permission> = emptySet(),
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

    // ── Security settings ─────────────────────────────────────────────────────

    data class SecurityState(
        val autoLockMinutes: Int          = 5,
        val isAutoLockDialogVisible: Boolean = false,
    )

    // ── Label printer settings ────────────────────────────────────────────────

    data class LabelPrinterState(
        val printerType: LabelPrinterTypeOption = LabelPrinterTypeOption.NONE,
        val tcpHost: String       = "",
        val tcpPort: String       = "9100",
        val serialPort: String    = "",
        val baudRate: String      = "9600",
        val btAddress: String     = "",
        val darknessLevel: Int    = 8,
        val speedLevel: Int       = 4,
        val isConnected: Boolean  = false,
        val isSaving: Boolean     = false,
        val saveError: String?    = null,
    )

    // ── Scanner settings ──────────────────────────────────────────────────────

    data class ScannerSettingsState(
        val minBarcodeLength: Int    = 4,
        val prefixToStrip: String    = "",
        val suffixToStrip: String    = "",
        val soundFeedbackEnabled: Boolean = true,
        val lastScannedBarcode: String?   = null,
        val lastScannedFormat: String?    = null,
        val lastScannedAt: Long?          = null,
        val isSaving: Boolean             = false,
    )

    // ── Printer profiles ──────────────────────────────────────────────────────

    data class PrinterProfilesState(
        val profiles: List<PrinterProfile>  = emptyList(),
        val isLoading: Boolean              = false,
        val editingProfile: PrinterProfile? = null,
        val isCreating: Boolean             = false,
        val form: PrinterProfileForm        = PrinterProfileForm(),
        val saveError: String?              = null,
    ) {
        data class PrinterProfileForm(
            val name: String            = "",
            val jobType: PrinterJobType = PrinterJobType.RECEIPT,
            val printerType: String     = "TCP",
            val tcpHost: String         = "",
            val tcpPort: String         = "9100",
            val serialPort: String      = "",
            val baudRate: String        = "115200",
            val btAddress: String       = "",
            val paperWidthMm: Int       = 80,
            val isDefault: Boolean      = false,
        )
    }
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

/** Label printer hardware type / language. */
enum class LabelPrinterTypeOption(val displayName: String, val domainKey: String) {
    NONE("None (PDF only)",      "NONE"),
    ZPL_TCP("Zebra — TCP/IP",    "ZPL_TCP"),
    ZPL_USB("Zebra — USB",       "ZPL_USB"),
    ZPL_BT("Zebra — Bluetooth",  "ZPL_BT"),
    TSPL_TCP("TSC — TCP/IP",     "TSPL_TCP"),
    TSPL_USB("TSC — USB",        "TSPL_USB"),
    TSPL_BT("TSC — Bluetooth",   "TSPL_BT"),
    PDF_SYSTEM("PDF (OS dialog)", "PDF_SYSTEM"),
}
