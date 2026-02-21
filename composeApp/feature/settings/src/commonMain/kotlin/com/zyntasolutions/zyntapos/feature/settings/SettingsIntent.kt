package com.zyntasolutions.zyntapos.feature.settings

import com.zyntasolutions.zyntapos.domain.model.OrderType
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
    data class UpdateUserFormActive(val isActive: Boolean) : SettingsIntent
    data object SaveUser : SettingsIntent

    // ── Backup ────────────────────────────────────────────────────────────────
    data object LoadBackupInfo : SettingsIntent
    data object TriggerBackup : SettingsIntent
    data class RestoreSelected(val filePath: String) : SettingsIntent
    data object ConfirmRestore : SettingsIntent
    data object CancelRestore : SettingsIntent

    // ── Appearance ────────────────────────────────────────────────────────────
    data object LoadAppearance : SettingsIntent
    data class UpdateThemeMode(val mode: ThemeMode) : SettingsIntent
}
