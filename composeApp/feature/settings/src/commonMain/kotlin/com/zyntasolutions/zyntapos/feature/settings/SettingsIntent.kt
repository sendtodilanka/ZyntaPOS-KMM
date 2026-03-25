package com.zyntasolutions.zyntapos.feature.settings

import com.zyntasolutions.zyntapos.domain.model.CustomRole
import com.zyntasolutions.zyntapos.domain.model.OrderType
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.PrinterJobType
import com.zyntasolutions.zyntapos.domain.model.PrinterProfile
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.TaxGroup

/**
 * MVI — all user intents that [SettingsViewModel] can process.
 */
sealed interface SettingsIntent {

    // ── General Settings ──────────────────────────────────────────────────────
    data object LoadGeneral : SettingsIntent
    data class UpdateStoreName(val value: String) : SettingsIntent
    data class UpdateStoreAddress(val value: String) : SettingsIntent
    data class UpdateStorePhone(val value: String) : SettingsIntent
    data class UpdateLogoUri(val uri: String) : SettingsIntent
    data class UpdateCurrency(val currency: Currency) : SettingsIntent
    data class UpdateTimezone(val tz: String) : SettingsIntent
    data class UpdateDateFormat(val format: String) : SettingsIntent
    data object SaveGeneral : SettingsIntent

    // ── POS Settings ──────────────────────────────────────────────────────────
    data object LoadPos : SettingsIntent
    data class UpdateDefaultOrderType(val orderType: OrderType) : SettingsIntent
    data class UpdateAutoPrintReceipt(val enabled: Boolean) : SettingsIntent
    data class UpdateTaxDisplayMode(val mode: TaxDisplayMode) : SettingsIntent
    data class UpdateReceiptTemplate(val template: ReceiptTemplate) : SettingsIntent
    data class UpdateMaxDiscount(val percent: Double) : SettingsIntent
    data class UpdateDailySalesTarget(val amount: Double) : SettingsIntent
    data object SavePos : SettingsIntent

    // ── Tax Settings ──────────────────────────────────────────────────────────
    data object LoadTaxGroups : SettingsIntent
    data object OpenCreateTaxGroup : SettingsIntent
    data class OpenEditTaxGroup(val taxGroup: TaxGroup) : SettingsIntent
    data class SaveTaxGroup(val taxGroup: TaxGroup, val isUpdate: Boolean) : SettingsIntent
    data object DismissTaxForm : SettingsIntent
    data class RequestDeleteTaxGroup(val taxGroup: TaxGroup) : SettingsIntent
    data object ConfirmDeleteTaxGroup : SettingsIntent
    data object CancelDeleteTaxGroup : SettingsIntent

    // ── Printer Settings ──────────────────────────────────────────────────────
    data object LoadPrinter : SettingsIntent
    data class UpdatePrinterType(val type: PrinterType) : SettingsIntent
    data class UpdateTcpHost(val host: String) : SettingsIntent
    data class UpdateTcpPort(val port: String) : SettingsIntent
    data class UpdateSerialPort(val port: String) : SettingsIntent
    data class UpdateBaudRate(val rate: String) : SettingsIntent
    data class UpdateBtAddress(val address: String) : SettingsIntent
    data class UpdatePaperWidth(val option: PaperWidthOption) : SettingsIntent
    data class UpdateHeaderLine(val index: Int, val value: String) : SettingsIntent
    data class UpdateFooterLine(val index: Int, val value: String) : SettingsIntent
    data class UpdateShowQrCode(val show: Boolean) : SettingsIntent
    data class UpdateShowLogo(val show: Boolean) : SettingsIntent
    data object SavePrinter : SettingsIntent
    data object TestPrint : SettingsIntent

    // ── User Management ───────────────────────────────────────────────────────
    data object LoadUsers : SettingsIntent
    data object OpenCreateUser : SettingsIntent
    data class OpenEditUser(val user: com.zyntasolutions.zyntapos.domain.model.User) : SettingsIntent
    data object DismissUserForm : SettingsIntent
    data class UpdateUserFormName(val value: String) : SettingsIntent
    data class UpdateUserFormEmail(val value: String) : SettingsIntent
    data class UpdateUserFormPassword(val value: String) : SettingsIntent
    data class UpdateUserFormRole(val role: Role) : SettingsIntent
    /** Sets [SettingsState.UserState.UserForm.roleKey] to an arbitrary key (used for custom roles). */
    data class UpdateUserFormRoleKey(val key: String) : SettingsIntent
    data class UpdateUserFormActive(val isActive: Boolean) : SettingsIntent
    data object SaveUser : SettingsIntent

    // ── PIN management ────────────────────────────────────────────────────────
    data class UpdateUserFormPin(val pin: String) : SettingsIntent
    data class UpdateUserFormConfirmPin(val pin: String) : SettingsIntent
    data object ClearUserFormPin : SettingsIntent

    // ── RBAC management ───────────────────────────────────────────────────────
    data object LoadRbac : SettingsIntent
    data object OpenCreateCustomRole : SettingsIntent
    data class OpenEditCustomRole(val role: CustomRole) : SettingsIntent
    data object DismissCustomRoleForm : SettingsIntent
    data class UpdateCustomRoleFormName(val name: String) : SettingsIntent
    data class UpdateCustomRoleFormDescription(val desc: String) : SettingsIntent
    data class ToggleCustomRolePermission(val permission: Permission) : SettingsIntent
    data object SaveCustomRole : SettingsIntent
    data class DeleteCustomRole(val id: String) : SettingsIntent
    data class ToggleBuiltInRolePermission(val role: Role, val permission: Permission) : SettingsIntent
    data class ResetBuiltInRolePermissions(val role: Role) : SettingsIntent

    // ── Backup ────────────────────────────────────────────────────────────────
    data object LoadBackupInfo : SettingsIntent
    data object TriggerBackup : SettingsIntent
    data class RestoreSelected(val filePath: String) : SettingsIntent
    data object ConfirmRestore : SettingsIntent
    data object CancelRestore : SettingsIntent

    // ── Appearance ────────────────────────────────────────────────────────────
    data object LoadAppearance : SettingsIntent
    data class UpdateThemeMode(val mode: ThemeMode) : SettingsIntent

    // ── Security Settings ─────────────────────────────────────────────────────
    data object LoadSecuritySettings : SettingsIntent
    data object OpenAutoLockDialog : SettingsIntent
    data object DismissAutoLockDialog : SettingsIntent
    data class SetAutoLockTimeout(val minutes: Int) : SettingsIntent

    // ── Label Printer Settings ────────────────────────────────────────────────
    data object LoadLabelPrinter : SettingsIntent
    data class UpdateLabelPrinterType(val type: LabelPrinterTypeOption) : SettingsIntent
    data class UpdateLabelPrinterTcpHost(val host: String) : SettingsIntent
    data class UpdateLabelPrinterTcpPort(val port: String) : SettingsIntent
    data class UpdateLabelPrinterSerialPort(val port: String) : SettingsIntent
    data class UpdateLabelPrinterBaudRate(val rate: String) : SettingsIntent
    data class UpdateLabelPrinterBtAddress(val address: String) : SettingsIntent
    data class UpdateLabelPrinterDarkness(val level: Int) : SettingsIntent
    data class UpdateLabelPrinterSpeed(val level: Int) : SettingsIntent
    data object SaveLabelPrinter : SettingsIntent

    // ── Scanner Settings ──────────────────────────────────────────────────────
    data object LoadScannerSettings : SettingsIntent
    data class UpdateScannerMinLength(val length: Int) : SettingsIntent
    data class UpdateScannerPrefix(val prefix: String) : SettingsIntent
    data class UpdateScannerSuffix(val suffix: String) : SettingsIntent
    data class UpdateScannerSoundFeedback(val enabled: Boolean) : SettingsIntent
    data class SimulateScan(val barcode: String) : SettingsIntent
    data object SaveScannerSettings : SettingsIntent

    // ── Printer Profiles ──────────────────────────────────────────────────────
    data object LoadPrinterProfiles : SettingsIntent
    data object OpenCreatePrinterProfile : SettingsIntent
    data class OpenEditPrinterProfile(val profile: PrinterProfile) : SettingsIntent
    data object DismissPrinterProfileForm : SettingsIntent
    data class UpdateProfileName(val name: String) : SettingsIntent
    data class UpdateProfileJobType(val jobType: PrinterJobType) : SettingsIntent
    data class UpdateProfilePrinterType(val type: String) : SettingsIntent
    data class UpdateProfileTcpHost(val host: String) : SettingsIntent
    data class UpdateProfileTcpPort(val port: String) : SettingsIntent
    data class UpdateProfileSerialPort(val port: String) : SettingsIntent
    data class UpdateProfileBaudRate(val rate: String) : SettingsIntent
    data class UpdateProfileBtAddress(val address: String) : SettingsIntent
    data class UpdateProfilePaperWidth(val mm: Int) : SettingsIntent
    data class UpdateProfileIsDefault(val isDefault: Boolean) : SettingsIntent
    data object SavePrinterProfile : SettingsIntent
    data class DeletePrinterProfile(val id: String) : SettingsIntent
}
