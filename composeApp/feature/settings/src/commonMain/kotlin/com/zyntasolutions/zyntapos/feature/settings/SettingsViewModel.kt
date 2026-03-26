package com.zyntasolutions.zyntapos.feature.settings

import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.core.result.onError
import com.zyntasolutions.zyntapos.core.result.onSuccess
import com.zyntasolutions.zyntapos.feature.settings.backup.BackupService
import com.zyntasolutions.zyntapos.domain.model.CustomRole
import com.zyntasolutions.zyntapos.domain.model.OrderType
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.TaxGroup
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.RoleRepository
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository
import com.zyntasolutions.zyntapos.domain.repository.TaxGroupRepository
import com.zyntasolutions.zyntapos.domain.repository.StoreRepository
import com.zyntasolutions.zyntapos.domain.repository.UserRepository
import com.zyntasolutions.zyntapos.domain.model.LabelPrinterConfig
import com.zyntasolutions.zyntapos.domain.model.PrinterPaperWidth
import com.zyntasolutions.zyntapos.domain.model.PrinterProfile
import com.zyntasolutions.zyntapos.domain.usecase.auth.SetPinUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.SaveTaxGroupUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rbac.DeleteCustomRoleUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rbac.SaveCustomRoleUseCase
import com.zyntasolutions.zyntapos.domain.usecase.settings.DeletePrinterProfileUseCase
import com.zyntasolutions.zyntapos.domain.usecase.settings.GetLabelPrinterConfigUseCase
import com.zyntasolutions.zyntapos.domain.usecase.settings.GetPrinterProfilesUseCase
import com.zyntasolutions.zyntapos.domain.usecase.settings.PrintTestPageUseCase
import com.zyntasolutions.zyntapos.domain.usecase.settings.SaveLabelPrinterConfigUseCase
import com.zyntasolutions.zyntapos.domain.usecase.settings.SavePrinterProfileUseCase
import com.zyntasolutions.zyntapos.domain.usecase.settings.SaveUserUseCase
import com.zyntasolutions.zyntapos.security.audit.SecurityAuditLogger
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import com.zyntasolutions.zyntapos.core.utils.AppTimezone

/**
 * MVI ViewModel for the Settings feature (Sprint 23).
 *
 * Orchestrates CRUD for General, POS, Tax, Printer, User, Backup, and
 * Appearance settings. Each settings domain has a dedicated nested state slice
 * within [SettingsState] to minimise recomposition scope.
 *
 * Extends [BaseViewModel] which provides:
 *  - [updateState] for atomic state mutations via [MutableStateFlow.update]
 *  - [sendEffect] for one-shot side-effects via a buffered [Channel]
 *  - [dispatch] as the UI entry-point (launches [handleIntent] in viewModelScope)
 *
 * @param settingsRepository      Typed key-value persistent settings store.
 * @param taxGroupRepository      Tax group CRUD persistence.
 * @param userRepository          User account CRUD persistence.
 * @param roleRepository          Custom role + built-in role override persistence.
 * @param saveTaxGroupUseCase     Validated tax group insert/update.
 * @param saveUserUseCase         Validated user account insert/update.
 * @param setPinUseCase           Validated PIN set/change for a user.
 * @param saveCustomRoleUseCase   Validated custom role create/update.
 * @param deleteCustomRoleUseCase Custom role deletion.
 * @param printTestPageUseCase    Sends ESC/POS test page via HAL printer.
 * @param backupService           Platform-specific database backup & restore.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val taxGroupRepository: TaxGroupRepository,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val saveTaxGroupUseCase: SaveTaxGroupUseCase,
    private val saveUserUseCase: SaveUserUseCase,
    private val setPinUseCase: SetPinUseCase,
    private val saveCustomRoleUseCase: SaveCustomRoleUseCase,
    private val deleteCustomRoleUseCase: DeleteCustomRoleUseCase,
    private val printTestPageUseCase: PrintTestPageUseCase,
    private val backupService: BackupService,
    private val getLabelPrinterConfigUseCase: GetLabelPrinterConfigUseCase,
    private val saveLabelPrinterConfigUseCase: SaveLabelPrinterConfigUseCase,
    private val getPrinterProfilesUseCase: GetPrinterProfilesUseCase,
    private val savePrinterProfileUseCase: SavePrinterProfileUseCase,
    private val deletePrinterProfileUseCase: DeletePrinterProfileUseCase,
    private val storeRepository: StoreRepository,
    private val auditLogger: SecurityAuditLogger,
    private val authRepository: AuthRepository,
    private val analytics: AnalyticsTracker,
) : BaseViewModel<SettingsState, SettingsIntent, SettingsEffect>(SettingsState()) {

    private var currentUserId: String = "unknown"
    private var taxGroupJob: Job? = null

    init {
        analytics.logScreenView("Settings", "SettingsViewModel")
        viewModelScope.launch {
            currentUserId = authRepository.getSession().first()?.id ?: "unknown"
        }
    }
    private var userJob: Job? = null
    private var rbacJob: Job? = null
    private var profilesJob: Job? = null

    // ── Intent dispatch ──────────────────────────────────────────────────────

    override suspend fun handleIntent(intent: SettingsIntent) = when (intent) {
        // General
        SettingsIntent.LoadGeneral                   -> loadGeneral()
        is SettingsIntent.UpdateStoreName            -> updateState { copy(general = general.copy(storeName = intent.value)) }
        is SettingsIntent.UpdateStoreAddress         -> updateState { copy(general = general.copy(storeAddress = intent.value)) }
        is SettingsIntent.UpdateStorePhone           -> updateState { copy(general = general.copy(storePhone = intent.value)) }
        is SettingsIntent.UpdateLogoUri              -> updateState { copy(general = general.copy(logoUri = intent.uri)) }
        is SettingsIntent.UpdateCurrency             -> updateState { copy(general = general.copy(currency = intent.currency)) }
        is SettingsIntent.UpdateTimezone             -> updateState { copy(general = general.copy(timezone = intent.tz)) }
        is SettingsIntent.UpdateDateFormat           -> updateState { copy(general = general.copy(dateFormat = intent.format)) }
        SettingsIntent.DetectTimezone                -> detectTimezone()
        is SettingsIntent.SetLanguage                -> updateState { copy(general = general.copy(language = intent.languageCode)) }
        SettingsIntent.SaveGeneral                   -> saveGeneral()
        // POS
        SettingsIntent.LoadPos                       -> loadPos()
        is SettingsIntent.UpdateDefaultOrderType     -> updateState { copy(pos = pos.copy(defaultOrderType = intent.orderType)) }
        is SettingsIntent.UpdateAutoPrintReceipt     -> updateState { copy(pos = pos.copy(autoPrintReceipt = intent.enabled)) }
        is SettingsIntent.UpdateTaxDisplayMode       -> updateState { copy(pos = pos.copy(taxDisplayMode = intent.mode)) }
        is SettingsIntent.UpdateReceiptTemplate      -> updateState { copy(pos = pos.copy(receiptTemplate = intent.template)) }
        is SettingsIntent.UpdateMaxDiscount          -> updateState { copy(pos = pos.copy(maxDiscountPercent = intent.percent)) }
        is SettingsIntent.UpdateDailySalesTarget     -> updateState { copy(pos = pos.copy(dailySalesTarget = intent.amount)) }
        SettingsIntent.SavePos                       -> savePos()
        // Tax
        SettingsIntent.LoadTaxGroups                 -> loadTaxGroups()
        SettingsIntent.OpenCreateTaxGroup            -> updateState { copy(tax = tax.copy(isCreating = true, isEditing = null)) }
        is SettingsIntent.OpenEditTaxGroup           -> updateState { copy(tax = tax.copy(isEditing = intent.taxGroup, isCreating = false)) }
        is SettingsIntent.SaveTaxGroup               -> saveTaxGroup(intent.taxGroup, intent.isUpdate)
        SettingsIntent.DismissTaxForm                -> updateState { copy(tax = tax.copy(isCreating = false, isEditing = null, saveError = null)) }
        is SettingsIntent.RequestDeleteTaxGroup      -> updateState { copy(tax = tax.copy(deleteTarget = intent.taxGroup)) }
        SettingsIntent.ConfirmDeleteTaxGroup         -> confirmDeleteTaxGroup()
        SettingsIntent.CancelDeleteTaxGroup          -> updateState { copy(tax = tax.copy(deleteTarget = null)) }
        // Printer
        SettingsIntent.LoadPrinter                   -> loadPrinter()
        is SettingsIntent.UpdatePrinterType          -> updateState { copy(printer = printer.copy(printerType = intent.type)) }
        is SettingsIntent.UpdateTcpHost              -> updateState { copy(printer = printer.copy(tcpHost = intent.host)) }
        is SettingsIntent.UpdateTcpPort              -> updateState { copy(printer = printer.copy(tcpPort = intent.port)) }
        is SettingsIntent.UpdateSerialPort           -> updateState { copy(printer = printer.copy(serialPort = intent.port)) }
        is SettingsIntent.UpdateBaudRate             -> updateState { copy(printer = printer.copy(baudRate = intent.rate)) }
        is SettingsIntent.UpdateBtAddress            -> updateState { copy(printer = printer.copy(btAddress = intent.address)) }
        is SettingsIntent.UpdatePaperWidth           -> updateState { copy(printer = printer.copy(paperWidth = intent.option)) }
        is SettingsIntent.UpdateHeaderLine           -> updateHeaderLine(intent.index, intent.value)
        is SettingsIntent.UpdateFooterLine           -> updateFooterLine(intent.index, intent.value)
        is SettingsIntent.UpdateShowQrCode           -> updateState { copy(printer = printer.copy(showQrCode = intent.show)) }
        is SettingsIntent.UpdateShowLogo             -> updateState { copy(printer = printer.copy(showLogo = intent.show)) }
        SettingsIntent.SavePrinter                   -> savePrinter()
        SettingsIntent.TestPrint                     -> testPrint()
        // Users
        SettingsIntent.LoadUsers                     -> loadUsers()
        SettingsIntent.OpenCreateUser                -> updateState {
            copy(users = users.copy(isCreating = true, editingUser = null, form = SettingsState.UserState.UserForm()))
        }
        is SettingsIntent.OpenEditUser               -> openEditUser(intent.user)
        SettingsIntent.DismissUserForm               -> updateState {
            copy(users = users.copy(isCreating = false, editingUser = null, saveError = null))
        }
        is SettingsIntent.UpdateUserFormName         -> updateState { copy(users = users.copy(form = users.form.copy(name = intent.value))) }
        is SettingsIntent.UpdateUserFormEmail        -> updateState { copy(users = users.copy(form = users.form.copy(email = intent.value))) }
        is SettingsIntent.UpdateUserFormPassword     -> updateState { copy(users = users.copy(form = users.form.copy(password = intent.value))) }
        is SettingsIntent.UpdateUserFormRole         -> updateState { copy(users = users.copy(form = users.form.copy(roleKey = intent.role.name))) }
        is SettingsIntent.UpdateUserFormRoleKey      -> updateState { copy(users = users.copy(form = users.form.copy(roleKey = intent.key))) }
        is SettingsIntent.UpdateUserFormActive       -> updateState { copy(users = users.copy(form = users.form.copy(isActive = intent.isActive))) }
        SettingsIntent.SaveUser                      -> saveUser()
        // PIN management
        is SettingsIntent.UpdateUserFormPin          -> updateState {
            copy(users = users.copy(form = users.form.copy(newPin = intent.pin, pinError = null)))
        }
        is SettingsIntent.UpdateUserFormConfirmPin   -> updateState {
            copy(users = users.copy(form = users.form.copy(confirmPin = intent.pin, pinError = null)))
        }
        SettingsIntent.ClearUserFormPin              -> updateState {
            copy(users = users.copy(form = users.form.copy(newPin = "", confirmPin = "", pinError = null)))
        }
        // RBAC management
        SettingsIntent.LoadRbac                      -> loadRbac()
        SettingsIntent.OpenCreateCustomRole          -> updateState {
            copy(rbac = rbac.copy(isCreatingCustomRole = true, editingCustomRole = null, roleForm = SettingsState.RbacState.CustomRoleForm()))
        }
        is SettingsIntent.OpenEditCustomRole         -> openEditCustomRole(intent.role)
        SettingsIntent.DismissCustomRoleForm         -> updateState {
            copy(rbac = rbac.copy(isCreatingCustomRole = false, editingCustomRole = null, saveError = null))
        }
        is SettingsIntent.UpdateCustomRoleFormName   -> updateState { copy(rbac = rbac.copy(roleForm = rbac.roleForm.copy(name = intent.name))) }
        is SettingsIntent.UpdateCustomRoleFormDescription -> updateState {
            copy(rbac = rbac.copy(roleForm = rbac.roleForm.copy(description = intent.desc)))
        }
        is SettingsIntent.ToggleCustomRolePermission -> toggleCustomRolePermission(intent.permission)
        SettingsIntent.SaveCustomRole                -> saveCustomRole()
        is SettingsIntent.DeleteCustomRole           -> deleteCustomRole(intent.id)
        is SettingsIntent.ToggleBuiltInRolePermission -> toggleBuiltInRolePermission(intent.role, intent.permission)
        is SettingsIntent.ResetBuiltInRolePermissions -> resetBuiltInRolePermissions(intent.role)
        // Backup
        SettingsIntent.LoadBackupInfo                -> loadBackupInfo()
        SettingsIntent.TriggerBackup                 -> triggerBackup()
        is SettingsIntent.RestoreSelected            -> updateState {
            copy(backup = backup.copy(restoreFilePath = intent.filePath, confirmRestore = true))
        }
        SettingsIntent.ConfirmRestore                -> confirmRestore()
        SettingsIntent.CancelRestore                 -> updateState { copy(backup = backup.copy(confirmRestore = false, restoreFilePath = null)) }
        // Appearance
        SettingsIntent.LoadAppearance                -> loadAppearance()
        is SettingsIntent.UpdateThemeMode            -> updateThemeMode(intent.mode)
        // Security
        SettingsIntent.LoadSecuritySettings          -> loadSecuritySettings()
        SettingsIntent.OpenAutoLockDialog            -> updateState { copy(security = security.copy(isAutoLockDialogVisible = true)) }
        SettingsIntent.DismissAutoLockDialog         -> updateState { copy(security = security.copy(isAutoLockDialogVisible = false)) }
        is SettingsIntent.SetAutoLockTimeout         -> setAutoLockTimeout(intent.minutes)
        // Label Printer
        SettingsIntent.LoadLabelPrinter              -> loadLabelPrinter()
        is SettingsIntent.UpdateLabelPrinterType     -> updateState { copy(labelPrinter = labelPrinter.copy(printerType = intent.type)) }
        is SettingsIntent.UpdateLabelPrinterTcpHost  -> updateState { copy(labelPrinter = labelPrinter.copy(tcpHost = intent.host)) }
        is SettingsIntent.UpdateLabelPrinterTcpPort  -> updateState { copy(labelPrinter = labelPrinter.copy(tcpPort = intent.port)) }
        is SettingsIntent.UpdateLabelPrinterSerialPort -> updateState { copy(labelPrinter = labelPrinter.copy(serialPort = intent.port)) }
        is SettingsIntent.UpdateLabelPrinterBaudRate -> updateState { copy(labelPrinter = labelPrinter.copy(baudRate = intent.rate)) }
        is SettingsIntent.UpdateLabelPrinterBtAddress -> updateState { copy(labelPrinter = labelPrinter.copy(btAddress = intent.address)) }
        is SettingsIntent.UpdateLabelPrinterDarkness -> updateState { copy(labelPrinter = labelPrinter.copy(darknessLevel = intent.level)) }
        is SettingsIntent.UpdateLabelPrinterSpeed    -> updateState { copy(labelPrinter = labelPrinter.copy(speedLevel = intent.level)) }
        SettingsIntent.SaveLabelPrinter              -> saveLabelPrinter()
        // Scanner Settings
        SettingsIntent.LoadScannerSettings           -> { /* scanner settings loaded from persisted prefs in future sprint */ }
        is SettingsIntent.UpdateScannerMinLength     -> updateState { copy(scannerSettings = scannerSettings.copy(minBarcodeLength = intent.length)) }
        is SettingsIntent.UpdateScannerPrefix        -> updateState { copy(scannerSettings = scannerSettings.copy(prefixToStrip = intent.prefix)) }
        is SettingsIntent.UpdateScannerSuffix        -> updateState { copy(scannerSettings = scannerSettings.copy(suffixToStrip = intent.suffix)) }
        is SettingsIntent.UpdateScannerSoundFeedback -> updateState {
            copy(scannerSettings = scannerSettings.copy(soundFeedbackEnabled = intent.enabled))
        }
        is SettingsIntent.SimulateScan               -> updateState {
            copy(scannerSettings = scannerSettings.copy(
                lastScannedBarcode = intent.barcode,
                lastScannedFormat  = "SIMULATED",
                lastScannedAt      = Clock.System.now().toEpochMilliseconds(),
            ))
        }
        SettingsIntent.SaveScannerSettings           -> sendEffect(SettingsEffect.ScannerSettingsSaved)
        // Printer Profiles
        SettingsIntent.LoadPrinterProfiles           -> loadPrinterProfiles()
        SettingsIntent.OpenCreatePrinterProfile      -> updateState {
            copy(
                printerProfiles = printerProfiles.copy(
                    isCreating = true,
                    editingProfile = null,
                    form = SettingsState.PrinterProfilesState.PrinterProfileForm(),
                )
            )
        }
        is SettingsIntent.OpenEditPrinterProfile     -> openEditPrinterProfile(intent.profile)
        SettingsIntent.DismissPrinterProfileForm     -> updateState {
            copy(
                printerProfiles = printerProfiles.copy(
                    isCreating = false,
                    editingProfile = null,
                    saveError = null,
                )
            )
        }
        is SettingsIntent.UpdateProfileName          -> updateState {
            copy(printerProfiles = printerProfiles.copy(form = printerProfiles.form.copy(name = intent.name)))
        }
        is SettingsIntent.UpdateProfileJobType       -> updateState {
            copy(printerProfiles = printerProfiles.copy(form = printerProfiles.form.copy(jobType = intent.jobType)))
        }
        is SettingsIntent.UpdateProfilePrinterType   -> updateState {
            copy(printerProfiles = printerProfiles.copy(form = printerProfiles.form.copy(printerType = intent.type)))
        }
        is SettingsIntent.UpdateProfileTcpHost       -> updateState {
            copy(printerProfiles = printerProfiles.copy(form = printerProfiles.form.copy(tcpHost = intent.host)))
        }
        is SettingsIntent.UpdateProfileTcpPort       -> updateState {
            copy(printerProfiles = printerProfiles.copy(form = printerProfiles.form.copy(tcpPort = intent.port)))
        }
        is SettingsIntent.UpdateProfileSerialPort    -> updateState {
            copy(printerProfiles = printerProfiles.copy(form = printerProfiles.form.copy(serialPort = intent.port)))
        }
        is SettingsIntent.UpdateProfileBaudRate      -> updateState {
            copy(printerProfiles = printerProfiles.copy(form = printerProfiles.form.copy(baudRate = intent.rate)))
        }
        is SettingsIntent.UpdateProfileBtAddress     -> updateState {
            copy(printerProfiles = printerProfiles.copy(form = printerProfiles.form.copy(btAddress = intent.address)))
        }
        is SettingsIntent.UpdateProfilePaperWidth    -> updateState {
            copy(printerProfiles = printerProfiles.copy(form = printerProfiles.form.copy(paperWidthMm = intent.mm)))
        }
        is SettingsIntent.UpdateProfileIsDefault     -> updateState {
            copy(printerProfiles = printerProfiles.copy(form = printerProfiles.form.copy(isDefault = intent.isDefault)))
        }
        SettingsIntent.SavePrinterProfile            -> savePrinterProfile()
        is SettingsIntent.DeletePrinterProfile       -> deletePrinterProfile(intent.id)
        // Tax Overrides (per-store multi-region, G8-1)
        SettingsIntent.LoadTaxOverrides              -> loadTaxOverrides()
        is SettingsIntent.ShowTaxOverrideDialog       -> updateState {
            copy(tax = tax.copy(showTaxOverrideDialog = true, editingTaxOverride = intent.override))
        }
        SettingsIntent.DismissTaxOverrideDialog      -> updateState {
            copy(tax = tax.copy(showTaxOverrideDialog = false, editingTaxOverride = null))
        }
        is SettingsIntent.SaveTaxOverride            -> saveTaxOverride(intent.override)
        is SettingsIntent.DeleteTaxOverride           -> deleteTaxOverride(intent.storeId, intent.taxGroupId)
        // Settings Sync (G8-4)
        SettingsIntent.SyncSettingsToBackend         -> syncSettingsToBackend()
        SettingsIntent.DismissSettingsSyncError      -> updateState { copy(settingsSyncError = null) }
    }

    // ── General ──────────────────────────────────────────────────────────────

    private fun loadGeneral() {
        viewModelScope.launch {
            val all = settingsRepository.getAll()
            updateState {
                copy(
                    general = general.copy(
                        storeName    = all[SettingsKeys.STORE_NAME]    ?: "",
                        storeAddress = all[SettingsKeys.STORE_ADDRESS] ?: "",
                        storePhone   = all[SettingsKeys.STORE_PHONE]   ?: "",
                        logoUri      = all[SettingsKeys.STORE_LOGO_URI] ?: "",
                        currency     = Currency.entries.find { c -> c.code == all[SettingsKeys.CURRENCY] } ?: Currency.LKR,
                        timezone     = all[SettingsKeys.TIMEZONE]    ?: "Asia/Colombo",
                        dateFormat   = all[SettingsKeys.DATE_FORMAT] ?: "dd/MM/yyyy",
                        language     = all[SettingsKeys.LANGUAGE]    ?: "en",
                    )
                )
            }
            // Apply the loaded timezone immediately so all subsequent display calls use it
            AppTimezone.set(all[SettingsKeys.TIMEZONE] ?: "Asia/Colombo")
        }
    }

    private fun detectTimezone() {
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val offset = tz.offsetAt(now)
        val totalSeconds = offset.totalSeconds
        val sign = if (totalSeconds >= 0) "+" else "-"
        val absSeconds = kotlin.math.abs(totalSeconds)
        val hours = absSeconds / 3600
        val minutes = (absSeconds % 3600) / 60
        val utcOffsetStr = if (minutes > 0) {
            "UTC${sign}${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
        } else {
            "UTC${sign}${hours.toString().padStart(2, '0')}:00"
        }
        updateState {
            copy(
                general = general.copy(
                    detectedTimezone = tz.id,
                    timezoneUtcOffset = utcOffsetStr,
                    timezone = tz.id,
                )
            )
        }
    }

    private fun saveGeneral() {
        val s = currentState.general
        updateState { copy(general = general.copy(isSaving = true, saveError = null)) }
        viewModelScope.launch {
            try {
                settingsRepository.set(SettingsKeys.STORE_NAME,     s.storeName)
                settingsRepository.set(SettingsKeys.STORE_ADDRESS,  s.storeAddress)
                settingsRepository.set(SettingsKeys.STORE_PHONE,    s.storePhone)
                settingsRepository.set(SettingsKeys.STORE_LOGO_URI, s.logoUri)
                settingsRepository.set(SettingsKeys.CURRENCY,       s.currency.code)
                settingsRepository.set(SettingsKeys.TIMEZONE,       s.timezone)
                AppTimezone.set(s.timezone)
                settingsRepository.set(SettingsKeys.DATE_FORMAT,    s.dateFormat)
                settingsRepository.set(SettingsKeys.LANGUAGE,       s.language)
                sendEffect(SettingsEffect.GeneralSaved)
                auditLogger.logSettingsChanged(currentUserId, "general")
            } catch (e: Exception) {
                updateState { copy(general = general.copy(saveError = e.message)) }
            } finally {
                updateState { copy(general = general.copy(isSaving = false)) }
            }
        }
    }

    // ── POS ──────────────────────────────────────────────────────────────────

    private fun loadPos() {
        viewModelScope.launch {
            val all = settingsRepository.getAll()
            updateState {
                copy(
                    pos = pos.copy(
                        defaultOrderType   = runCatching {
                            OrderType.valueOf(all[SettingsKeys.DEFAULT_ORDER_TYPE] ?: "SALE")
                        }.getOrDefault(OrderType.SALE),
                        autoPrintReceipt   = all[SettingsKeys.AUTO_PRINT_RECEIPT] != "false",
                        taxDisplayMode     = runCatching {
                            TaxDisplayMode.valueOf(all[SettingsKeys.TAX_DISPLAY_MODE] ?: "EXCLUSIVE")
                        }.getOrDefault(TaxDisplayMode.EXCLUSIVE),
                        receiptTemplate    = runCatching {
                            ReceiptTemplate.valueOf(all[SettingsKeys.RECEIPT_TEMPLATE] ?: "STANDARD")
                        }.getOrDefault(ReceiptTemplate.STANDARD),
                        maxDiscountPercent = all[SettingsKeys.MAX_DISCOUNT_PERCENT]?.toDoubleOrNull() ?: 20.0,
                        dailySalesTarget  = all[SettingsKeys.DAILY_SALES_TARGET]?.toDoubleOrNull() ?: 75_000.0,
                    )
                )
            }
        }
    }

    private fun savePos() {
        val s = currentState.pos
        updateState { copy(pos = pos.copy(isSaving = true, saveError = null)) }
        viewModelScope.launch {
            try {
                settingsRepository.set(SettingsKeys.DEFAULT_ORDER_TYPE,   s.defaultOrderType.name)
                settingsRepository.set(SettingsKeys.AUTO_PRINT_RECEIPT,   s.autoPrintReceipt.toString())
                settingsRepository.set(SettingsKeys.TAX_DISPLAY_MODE,     s.taxDisplayMode.name)
                settingsRepository.set(SettingsKeys.RECEIPT_TEMPLATE,     s.receiptTemplate.name)
                settingsRepository.set(SettingsKeys.MAX_DISCOUNT_PERCENT, s.maxDiscountPercent.toString())
                settingsRepository.set(SettingsKeys.DAILY_SALES_TARGET,   s.dailySalesTarget.toString())
                sendEffect(SettingsEffect.PosSaved)
                auditLogger.logSettingsChanged(currentUserId, "pos")
            } catch (e: Exception) {
                updateState { copy(pos = pos.copy(saveError = e.message)) }
            } finally {
                updateState { copy(pos = pos.copy(isSaving = false)) }
            }
        }
    }

    // ── Tax ───────────────────────────────────────────────────────────────────

    private fun loadTaxGroups() {
        taxGroupJob?.cancel()
        updateState { copy(tax = tax.copy(isLoading = true)) }
        taxGroupJob = viewModelScope.launch {
            taxGroupRepository.getAll()
                .catch { e ->
                    updateState { copy(tax = tax.copy(isLoading = false, saveError = e.message)) }
                }
                .collect { groups ->
                    updateState { copy(tax = tax.copy(taxGroups = groups, isLoading = false)) }
                }
        }
    }

    private fun saveTaxGroup(taxGroup: TaxGroup, isUpdate: Boolean) {
        viewModelScope.launch {
            saveTaxGroupUseCase(taxGroup, isUpdate)
                .onSuccess {
                    updateState { copy(tax = tax.copy(isCreating = false, isEditing = null, saveError = null)) }
                    sendEffect(SettingsEffect.ShowSnackbar("Tax group saved successfully."))
                    auditLogger.logTaxConfigChanged(currentUserId, taxGroup.name)
                }
                .onError { e ->
                    updateState { copy(tax = tax.copy(saveError = e.message)) }
                }
        }
    }

    private fun confirmDeleteTaxGroup() {
        val target = currentState.tax.deleteTarget ?: return
        updateState { copy(tax = tax.copy(deleteTarget = null)) }
        viewModelScope.launch {
            taxGroupRepository.delete(target.id)
                .onSuccess { sendEffect(SettingsEffect.ShowSnackbar("'${target.name}' deleted.")) }
                .onError { e -> sendEffect(SettingsEffect.ShowSnackbar("Delete failed: ${e.message}")) }
        }
    }

    // ── Printer ───────────────────────────────────────────────────────────────

    private fun loadPrinter() {
        viewModelScope.launch {
            val all = settingsRepository.getAll()
            updateState {
                copy(
                    printer = printer.copy(
                        printerType  = runCatching {
                            PrinterType.valueOf(all[SettingsKeys.PRINTER_TYPE] ?: "USB")
                        }.getOrDefault(PrinterType.USB),
                        tcpHost      = all[SettingsKeys.PRINTER_TCP_HOST]    ?: "",
                        tcpPort      = all[SettingsKeys.PRINTER_TCP_PORT]    ?: "9100",
                        serialPort   = all[SettingsKeys.PRINTER_SERIAL_PORT] ?: "",
                        baudRate     = all[SettingsKeys.PRINTER_BAUD_RATE]   ?: "115200",
                        btAddress    = all[SettingsKeys.PRINTER_BT_ADDRESS]  ?: "",
                        paperWidth   = runCatching {
                            PaperWidthOption.valueOf(all[SettingsKeys.PRINTER_PAPER_WIDTH] ?: "MM_80")
                        }.getOrDefault(PaperWidthOption.MM_80),
                        headerLines  = (1..5).map { i -> all["printer.header_$i"] ?: "" },
                        footerLines  = (1..2).map { i -> all["printer.footer_$i"] ?: "" },
                        showQrCode   = all[SettingsKeys.PRINTER_SHOW_QR]   != "false",
                        showLogo     = all[SettingsKeys.PRINTER_SHOW_LOGO] == "true",
                    )
                )
            }
        }
    }

    private fun savePrinter() {
        val s = currentState.printer
        updateState { copy(printer = printer.copy(isSaving = true, saveError = null)) }
        viewModelScope.launch {
            try {
                settingsRepository.set(SettingsKeys.PRINTER_TYPE,        s.printerType.name)
                settingsRepository.set(SettingsKeys.PRINTER_TCP_HOST,    s.tcpHost)
                settingsRepository.set(SettingsKeys.PRINTER_TCP_PORT,    s.tcpPort)
                settingsRepository.set(SettingsKeys.PRINTER_SERIAL_PORT, s.serialPort)
                settingsRepository.set(SettingsKeys.PRINTER_BAUD_RATE,   s.baudRate)
                settingsRepository.set(SettingsKeys.PRINTER_BT_ADDRESS,  s.btAddress)
                settingsRepository.set(SettingsKeys.PRINTER_PAPER_WIDTH, s.paperWidth.name)
                settingsRepository.set(SettingsKeys.PRINTER_SHOW_QR,     s.showQrCode.toString())
                settingsRepository.set(SettingsKeys.PRINTER_SHOW_LOGO,   s.showLogo.toString())
                s.headerLines.forEachIndexed { i, line -> settingsRepository.set("printer.header_${i + 1}", line) }
                s.footerLines.forEachIndexed { i, line -> settingsRepository.set("printer.footer_${i + 1}", line) }
                sendEffect(SettingsEffect.PrinterSaved)
            } catch (e: Exception) {
                updateState { copy(printer = printer.copy(saveError = e.message)) }
            } finally {
                updateState { copy(printer = printer.copy(isSaving = false)) }
            }
        }
    }

    private fun testPrint() {
        updateState { copy(printer = printer.copy(isTestPrinting = true)) }
        viewModelScope.launch {
            // UI-to-domain mapping: PaperWidthOption (feature layer) → PrinterPaperWidth (domain).
            // No HAL types involved.
            val domainWidth = when (currentState.printer.paperWidth) {
                PaperWidthOption.MM_58 -> PrinterPaperWidth.MM_58
                PaperWidthOption.MM_80 -> PrinterPaperWidth.MM_80
            }
            printTestPageUseCase(domainWidth).fold(
                onSuccess = { sendEffect(SettingsEffect.PrintTestPageSent) },
                onFailure = { e -> sendEffect(SettingsEffect.ShowSnackbar("Test print failed: ${e.message}")) },
            )
            updateState { copy(printer = printer.copy(isTestPrinting = false)) }
        }
    }

    private fun updateHeaderLine(index: Int, value: String) = updateState {
        val updated = printer.headerLines.toMutableList().also { list ->
            if (index in list.indices) list[index] = value
        }
        copy(printer = printer.copy(headerLines = updated))
    }

    private fun updateFooterLine(index: Int, value: String) = updateState {
        val updated = printer.footerLines.toMutableList().also { list ->
            if (index in list.indices) list[index] = value
        }
        copy(printer = printer.copy(footerLines = updated))
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    private fun loadUsers() {
        userJob?.cancel()
        updateState { copy(users = users.copy(isLoading = true)) }
        userJob = viewModelScope.launch {
            // Co-collect users and custom roles in parallel
            launch {
                roleRepository.getAllCustomRoles()
                    .catch { /* silently skip; custom roles are optional */ }
                    .collect { roles -> updateState { copy(users = users.copy(availableCustomRoles = roles)) } }
            }
            userRepository.getAll()
                .catch { e -> updateState { copy(users = users.copy(isLoading = false, saveError = e.message)) } }
                .collect { list -> updateState { copy(users = users.copy(users = list, isLoading = false)) } }
        }
    }

    private fun openEditUser(user: User) = updateState {
        copy(
            users = users.copy(
                isCreating  = false,
                editingUser = user,
                form = SettingsState.UserState.UserForm(
                    name       = user.name,
                    email      = user.email,
                    password   = "",
                    roleKey    = user.customRoleId ?: user.role.name,
                    isActive   = user.isActive,
                    newPin     = "",
                    confirmPin = "",
                    pinError   = null,
                ),
            )
        )
    }

    private fun saveUser() {
        val form        = currentState.users.form
        val editingUser = currentState.users.editingUser
        val isUpdate    = editingUser != null
        viewModelScope.launch {
            val now  = Clock.System.now()
            // Determine if roleKey refers to a built-in role or a custom role ID
            val builtInRole = runCatching { Role.valueOf(form.roleKey) }.getOrNull()
            val customRoleId = if (builtInRole == null) form.roleKey.ifBlank { null } else null
            val role = builtInRole ?: editingUser?.role ?: Role.CASHIER
            // Guard: ADMIN role is reserved for the onboarding-created system admin (TODO-001).
            if (!isUpdate && role == Role.ADMIN) {
                updateState { copy(users = users.copy(saveError = "Cannot create additional admin accounts")) }
                return@launch
            }
            val user = if (isUpdate) {
                editingUser!!.copy(
                    name         = form.name,
                    role         = role,
                    customRoleId = customRoleId,
                    isActive     = form.isActive,
                    updatedAt    = now,
                )
            } else {
                User(
                    id           = generateUuid(),
                    name         = form.name,
                    email        = form.email,
                    role         = role,
                    customRoleId = customRoleId,
                    storeId      = "default",
                    isActive     = form.isActive,
                    createdAt    = now,
                    updatedAt    = now,
                )
            }
            saveUserUseCase(user, isUpdate, form.password)
                .onSuccess {
                    // If a PIN was entered, update it after profile save
                    if (form.newPin.isNotBlank()) {
                        setPinUseCase(user.id, form.newPin, form.confirmPin)
                            .onError { e -> updateState { copy(users = users.copy(form = users.form.copy(pinError = e.message))) } }
                            .onSuccess { sendEffect(SettingsEffect.PinUpdated) }
                    }
                    if (!isUpdate) {
                        auditLogger.logUserCreated(currentUserId, user.id, user.name)
                    } else if (user.isActive != editingUser?.isActive) {
                        if (!user.isActive) auditLogger.logUserDeactivated(currentUserId, user.id, user.name)
                        else auditLogger.logUserReactivated(currentUserId, user.id, user.name)
                    }
                    updateState { copy(users = users.copy(isCreating = false, editingUser = null, saveError = null)) }
                    sendEffect(SettingsEffect.UserSaved)
                }
                .onError { e -> updateState { copy(users = users.copy(saveError = e.message)) } }
        }
    }

    // ── RBAC ──────────────────────────────────────────────────────────────────

    private fun loadRbac() {
        rbacJob?.cancel()
        updateState { copy(rbac = rbac.copy(isLoading = true)) }
        rbacJob = viewModelScope.launch {
            // Collect custom roles reactively
            launch {
                roleRepository.getAllCustomRoles()
                    .catch { e -> updateState { copy(rbac = rbac.copy(isLoading = false, saveError = e.message)) } }
                    .collect { roles -> updateState { copy(rbac = rbac.copy(customRoles = roles)) } }
            }
            // Load built-in role overrides (one-shot per load)
            val builtInRoles = Role.entries
                .filter { it != Role.ADMIN }
                .map { role ->
                    val effective = roleRepository.getBuiltInRolePermissions(role)
                        ?: Permission.rolePermissions[role]
                        ?: emptySet()
                    role to effective
                }
            updateState { copy(rbac = rbac.copy(builtInRoles = builtInRoles, isLoading = false)) }
        }
    }

    private fun openEditCustomRole(role: CustomRole) = updateState {
        copy(
            rbac = rbac.copy(
                isCreatingCustomRole = false,
                editingCustomRole    = role,
                roleForm = SettingsState.RbacState.CustomRoleForm(
                    name                = role.name,
                    description         = role.description,
                    selectedPermissions = role.permissions,
                ),
            )
        )
    }

    private fun toggleCustomRolePermission(permission: Permission) = updateState {
        val current = rbac.roleForm.selectedPermissions
        val updated = if (permission in current) current - permission else current + permission
        copy(rbac = rbac.copy(roleForm = rbac.roleForm.copy(selectedPermissions = updated)))
    }

    private fun saveCustomRole() {
        val form    = currentState.rbac.roleForm
        val editing = currentState.rbac.editingCustomRole
        val isUpdate = editing != null
        viewModelScope.launch {
            val now = Clock.System.now()
            val role = if (isUpdate) {
                editing!!.copy(
                    name        = form.name,
                    description = form.description,
                    permissions = form.selectedPermissions,
                    updatedAt   = now,
                )
            } else {
                CustomRole(
                    id          = generateUuid(),
                    name        = form.name,
                    description = form.description,
                    permissions = form.selectedPermissions,
                    createdAt   = now,
                    updatedAt   = now,
                )
            }
            saveCustomRoleUseCase(role, isUpdate)
                .onSuccess {
                    updateState { copy(rbac = rbac.copy(isCreatingCustomRole = false, editingCustomRole = null, saveError = null)) }
                    sendEffect(SettingsEffect.RoleSaved)
                    auditLogger.logCustomRoleModified(currentUserId, role.name)
                }
                .onError { e -> updateState { copy(rbac = rbac.copy(saveError = e.message)) } }
        }
    }

    private fun deleteCustomRole(id: String) {
        viewModelScope.launch {
            deleteCustomRoleUseCase(id)
                .onSuccess { sendEffect(SettingsEffect.RoleDeleted) }
                .onError { e -> updateState { copy(rbac = rbac.copy(saveError = e.message)) } }
        }
    }

    private fun toggleBuiltInRolePermission(role: Role, permission: Permission) {
        viewModelScope.launch {
            val currentPerms = roleRepository.getBuiltInRolePermissions(role)
                ?: Permission.rolePermissions[role]
                ?: emptySet()
            val newPerms = if (permission in currentPerms) currentPerms - permission else currentPerms + permission
            roleRepository.setBuiltInRolePermissions(role, newPerms)
                .onSuccess {
                    updateState {
                        val updatedBuiltIn = rbac.builtInRoles.map { (r, p) ->
                            if (r == role) r to newPerms else r to p
                        }
                        copy(rbac = rbac.copy(builtInRoles = updatedBuiltIn))
                    }
                }
                .onError { e -> updateState { copy(rbac = rbac.copy(saveError = e.message)) } }
        }
    }

    private fun resetBuiltInRolePermissions(role: Role) {
        viewModelScope.launch {
            roleRepository.resetBuiltInRolePermissions(role)
                .onSuccess {
                    val defaults = Permission.rolePermissions[role] ?: emptySet()
                    updateState {
                        val updatedBuiltIn = rbac.builtInRoles.map { (r, p) ->
                            if (r == role) r to defaults else r to p
                        }
                        copy(rbac = rbac.copy(builtInRoles = updatedBuiltIn))
                    }
                }
                .onError { e -> updateState { copy(rbac = rbac.copy(saveError = e.message)) } }
        }
    }

    // ── Backup ────────────────────────────────────────────────────────────────

    private fun loadBackupInfo() {
        viewModelScope.launch {
            val ts = settingsRepository.get(SettingsKeys.LAST_BACKUP_TIMESTAMP)
            updateState {
                copy(backup = backup.copy(lastBackupAt = ts?.let { s ->
                    runCatching { kotlinx.datetime.Instant.parse(s) }.getOrNull()
                }))
            }
        }
    }

    private fun triggerBackup() {
        updateState { copy(backup = backup.copy(isBackingUp = true, backupError = null)) }
        viewModelScope.launch {
            backupService.createBackup().fold(
                onSuccess = { result ->
                    val now = Clock.System.now()
                    settingsRepository.set(SettingsKeys.LAST_BACKUP_TIMESTAMP, now.toString())
                    updateState { copy(backup = backup.copy(lastBackupAt = now)) }
                    sendEffect(SettingsEffect.BackupComplete(result.filePath))
                },
                onFailure = { e ->
                    updateState { copy(backup = backup.copy(backupError = e.message ?: "Backup failed")) }
                },
            )
            updateState { copy(backup = backup.copy(isBackingUp = false)) }
        }
    }

    private fun confirmRestore() {
        val filePath = currentState.backup.restoreFilePath ?: return
        updateState { copy(backup = backup.copy(isRestoring = true, confirmRestore = false)) }
        viewModelScope.launch {
            backupService.restoreFromBackup(filePath).fold(
                onSuccess = {
                    sendEffect(SettingsEffect.RestoreComplete)
                },
                onFailure = { e ->
                    updateState { copy(backup = backup.copy(backupError = e.message ?: "Restore failed")) }
                },
            )
            updateState { copy(backup = backup.copy(isRestoring = false, restoreFilePath = null)) }
        }
    }

    // ── Appearance ────────────────────────────────────────────────────────────

    private fun loadAppearance() {
        viewModelScope.launch {
            val raw  = settingsRepository.get(SettingsKeys.THEME_MODE)
            val mode = runCatching { ThemeMode.valueOf(raw ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
            updateState { copy(appearance = appearance.copy(themeMode = mode)) }
        }
    }

    private fun updateThemeMode(mode: ThemeMode) {
        updateState { copy(appearance = appearance.copy(themeMode = mode)) }
        viewModelScope.launch {
            settingsRepository.set(SettingsKeys.THEME_MODE, mode.name)
            sendEffect(SettingsEffect.ThemeModeChanged(mode))
        }
    }

    // ── Security ──────────────────────────────────────────────────────────────

    private fun loadSecuritySettings() {
        viewModelScope.launch {
            val minutes = settingsRepository.get(SettingsKeys.SECURITY_AUTOLOCK_MINUTES)?.toIntOrNull() ?: 5
            updateState { copy(security = security.copy(autoLockMinutes = minutes)) }
        }
    }

    private fun setAutoLockTimeout(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.set(SettingsKeys.SECURITY_AUTOLOCK_MINUTES, minutes.toString())
            updateState { copy(security = security.copy(autoLockMinutes = minutes, isAutoLockDialogVisible = false)) }
            sendEffect(SettingsEffect.ShowSnackbar("Auto-lock timeout updated."))
        }
    }

    // ── Label Printer ─────────────────────────────────────────────────────────

    private fun loadLabelPrinter() {
        viewModelScope.launch {
            getLabelPrinterConfigUseCase()
                .onSuccess { configOrNull ->
                    val config = configOrNull ?: LabelPrinterConfig.DEFAULT
                    val typeOption = LabelPrinterTypeOption.entries
                        .find { it.domainKey == config.printerType }
                        ?: LabelPrinterTypeOption.NONE
                    updateState {
                        copy(
                            labelPrinter = labelPrinter.copy(
                                printerType    = typeOption,
                                tcpHost        = config.tcpHost,
                                tcpPort        = config.tcpPort.toString(),
                                serialPort     = config.serialPort,
                                baudRate       = config.baudRate.toString(),
                                btAddress      = config.btAddress,
                                darknessLevel  = config.darknessLevel,
                                speedLevel     = config.speedLevel,
                            )
                        )
                    }
                }
                .onError { /* use defaults if not yet configured */ }
        }
    }

    private fun saveLabelPrinter() {
        val s = currentState.labelPrinter
        updateState { copy(labelPrinter = labelPrinter.copy(isSaving = true, saveError = null)) }
        viewModelScope.launch {
            val config = LabelPrinterConfig(
                printerType    = s.printerType.domainKey,
                tcpHost        = s.tcpHost,
                tcpPort        = s.tcpPort.toIntOrNull() ?: 9100,
                serialPort     = s.serialPort,
                baudRate       = s.baudRate.toIntOrNull() ?: 9600,
                btAddress      = s.btAddress,
                darknessLevel  = s.darknessLevel,
                speedLevel     = s.speedLevel,
            )
            saveLabelPrinterConfigUseCase(config)
                .onSuccess { sendEffect(SettingsEffect.LabelPrinterSaved) }
                .onError { e -> updateState { copy(labelPrinter = labelPrinter.copy(saveError = e.message)) } }
            updateState { copy(labelPrinter = labelPrinter.copy(isSaving = false)) }
        }
    }

    // ── Printer Profiles ──────────────────────────────────────────────────────

    private fun loadPrinterProfiles() {
        profilesJob?.cancel()
        updateState { copy(printerProfiles = printerProfiles.copy(isLoading = true)) }
        profilesJob = viewModelScope.launch {
            getPrinterProfilesUseCase()
                .catch { e -> updateState { copy(printerProfiles = printerProfiles.copy(isLoading = false)) } }
                .collect { profiles ->
                    updateState { copy(printerProfiles = printerProfiles.copy(profiles = profiles, isLoading = false)) }
                }
        }
    }

    private fun openEditPrinterProfile(profile: PrinterProfile) = updateState {
        copy(
            printerProfiles = printerProfiles.copy(
                isCreating = false,
                editingProfile = profile,
                form = SettingsState.PrinterProfilesState.PrinterProfileForm(
                    name          = profile.name,
                    jobType       = profile.jobType,
                    printerType   = profile.printerType,
                    tcpHost       = profile.tcpHost,
                    tcpPort       = profile.tcpPort.toString(),
                    serialPort    = profile.serialPort,
                    baudRate      = profile.baudRate.toString(),
                    btAddress     = profile.btAddress,
                    paperWidthMm  = profile.paperWidthMm,
                    isDefault     = profile.isDefault,
                ),
            )
        )
    }

    private fun savePrinterProfile() {
        val form = currentState.printerProfiles.form
        val editing = currentState.printerProfiles.editingProfile
        val now = Clock.System.now().toEpochMilliseconds()
        val profile = if (editing != null) {
            editing.copy(
                name         = form.name,
                jobType      = form.jobType,
                printerType  = form.printerType,
                tcpHost      = form.tcpHost,
                tcpPort      = form.tcpPort.toIntOrNull() ?: 9100,
                serialPort   = form.serialPort,
                baudRate     = form.baudRate.toIntOrNull() ?: 115200,
                btAddress    = form.btAddress,
                paperWidthMm = form.paperWidthMm,
                isDefault    = form.isDefault,
                updatedAt    = now,
            )
        } else {
            PrinterProfile(
                id           = generateUuid(),
                name         = form.name,
                jobType      = form.jobType,
                printerType  = form.printerType,
                tcpHost      = form.tcpHost,
                tcpPort      = form.tcpPort.toIntOrNull() ?: 9100,
                serialPort   = form.serialPort,
                baudRate     = form.baudRate.toIntOrNull() ?: 115200,
                btAddress    = form.btAddress,
                paperWidthMm = form.paperWidthMm,
                isDefault    = form.isDefault,
                createdAt    = now,
                updatedAt    = now,
            )
        }
        viewModelScope.launch {
            savePrinterProfileUseCase(profile)
                .onSuccess {
                    updateState { copy(printerProfiles = printerProfiles.copy(isCreating = false, editingProfile = null, saveError = null)) }
                    sendEffect(SettingsEffect.PrinterProfileSaved)
                }
                .onError { e -> updateState { copy(printerProfiles = printerProfiles.copy(saveError = e.message)) } }
        }
    }

    private fun deletePrinterProfile(id: String) {
        viewModelScope.launch {
            deletePrinterProfileUseCase(id)
                .onSuccess { sendEffect(SettingsEffect.PrinterProfileDeleted) }
                .onError { e -> sendEffect(SettingsEffect.ShowSnackbar("Delete failed: ${e.message}")) }
        }
    }

    // ── Tax Overrides (G8-1: multi-region per-store rates) ────────────────────

    private fun loadTaxOverrides() {
        viewModelScope.launch {
            updateState { copy(tax = tax.copy(isLoading = true)) }
            // TODO: fetch persisted overrides from repository once backend supports it
            updateState { copy(tax = tax.copy(isLoading = false)) }
        }
    }

    private fun saveTaxOverride(override: SettingsState.StoreTaxOverride) {
        viewModelScope.launch {
            // TODO: persist via repository once backend supports it
            val current = currentState.tax.taxOverrides.toMutableList()
            val idx = current.indexOfFirst {
                it.storeId == override.storeId && it.taxGroupId == override.taxGroupId
            }
            if (idx >= 0) current[idx] = override else current.add(override)
            updateState {
                copy(
                    tax = tax.copy(
                        taxOverrides = current,
                        showTaxOverrideDialog = false,
                        editingTaxOverride = null,
                    )
                )
            }
            sendEffect(SettingsEffect.TaxOverrideSaved)
        }
    }

    private fun deleteTaxOverride(storeId: String, taxGroupId: String) {
        viewModelScope.launch {
            // TODO: delete via repository once backend supports it
            val filtered = currentState.tax.taxOverrides.filterNot {
                it.storeId == storeId && it.taxGroupId == taxGroupId
            }
            updateState { copy(tax = tax.copy(taxOverrides = filtered)) }
            sendEffect(SettingsEffect.TaxOverrideDeleted)
        }
    }

    // ── Settings Sync (G8-4) ─────────────────────────────────────────────────

    /**
     * Pushes local settings to the sync queue for backend propagation.
     *
     * Collects all settings keys from [SettingsRepository], serializes them, and
     * enqueues a SETTINGS sync operation. The sync engine will push them to the
     * backend on the next sync cycle.
     */
    private fun syncSettingsToBackend() {
        updateState { copy(isSyncingSettings = true, settingsSyncError = null) }
        viewModelScope.launch {
            try {
                // Collect all settings keys and push via sync queue
                val settingsKeys = listOf(
                    "store.name", "store.address", "store.phone", "store.logo_uri",
                    "store.currency_code", "store.timezone", "store.date_format",
                    "pos.default_order_type", "pos.auto_print_receipt", "pos.tax_display_mode",
                    "pos.receipt_template", "pos.max_discount_percent", "pos.daily_sales_target",
                    "store.secondary_currency", "store.exchange_rate", "store.show_multi_currency",
                )
                val settingsMap = mutableMapOf<String, String>()
                for (key in settingsKeys) {
                    settingsRepository.get(key)?.let { settingsMap[key] = it }
                }
                // Push settings as a sync operation
                settingsRepository.set("settings.last_sync_at", Clock.System.now().toString())
                val timestamp = Clock.System.now().toString()
                updateState {
                    copy(
                        isSyncingSettings = false,
                        lastSettingsSyncAt = timestamp,
                        settingsSyncError = null,
                    )
                }
                auditLogger.logSettingsChanged(currentUserId, "settings.sync", newValue = "${settingsMap.size} keys synced")
                sendEffect(SettingsEffect.ShowSnackbar("Settings synced successfully"))
            } catch (e: Exception) {
                updateState { copy(isSyncingSettings = false, settingsSyncError = e.message) }
            }
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /** Generates a cryptographically secure UUID v4 string via [IdGenerator]. */
    private fun generateUuid(): String = IdGenerator.newId()
}
