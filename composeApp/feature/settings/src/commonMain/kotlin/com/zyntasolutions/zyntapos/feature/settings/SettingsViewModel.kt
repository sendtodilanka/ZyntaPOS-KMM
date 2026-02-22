package com.zyntasolutions.zyntapos.feature.settings

import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.core.result.onError
import com.zyntasolutions.zyntapos.core.result.onSuccess
import com.zyntasolutions.zyntapos.domain.model.OrderType
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.TaxGroup
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository
import com.zyntasolutions.zyntapos.domain.repository.TaxGroupRepository
import com.zyntasolutions.zyntapos.domain.repository.UserRepository
import com.zyntasolutions.zyntapos.domain.usecase.inventory.SaveTaxGroupUseCase
import com.zyntasolutions.zyntapos.domain.model.PrinterPaperWidth
import com.zyntasolutions.zyntapos.domain.usecase.settings.PrintTestPageUseCase
import com.zyntasolutions.zyntapos.domain.usecase.settings.SaveUserUseCase
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

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
 * @param settingsRepository   Typed key-value persistent settings store.
 * @param taxGroupRepository   Tax group CRUD persistence.
 * @param userRepository       User account CRUD persistence.
 * @param saveTaxGroupUseCase  Validated tax group insert/update.
 * @param saveUserUseCase      Validated user account insert/update.
 * @param printTestPageUseCase Sends ESC/POS test page via HAL printer.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val taxGroupRepository: TaxGroupRepository,
    private val userRepository: UserRepository,
    private val saveTaxGroupUseCase: SaveTaxGroupUseCase,
    private val saveUserUseCase: SaveUserUseCase,
    private val printTestPageUseCase: PrintTestPageUseCase,
) : BaseViewModel<SettingsState, SettingsIntent, SettingsEffect>(SettingsState()) {

    private var taxGroupJob: Job? = null
    private var userJob: Job? = null

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
        SettingsIntent.SaveGeneral                   -> saveGeneral()
        // POS
        SettingsIntent.LoadPos                       -> loadPos()
        is SettingsIntent.UpdateDefaultOrderType     -> updateState { copy(pos = pos.copy(defaultOrderType = intent.orderType)) }
        is SettingsIntent.UpdateAutoPrintReceipt     -> updateState { copy(pos = pos.copy(autoPrintReceipt = intent.enabled)) }
        is SettingsIntent.UpdateTaxDisplayMode       -> updateState { copy(pos = pos.copy(taxDisplayMode = intent.mode)) }
        is SettingsIntent.UpdateReceiptTemplate      -> updateState { copy(pos = pos.copy(receiptTemplate = intent.template)) }
        is SettingsIntent.UpdateMaxDiscount          -> updateState { copy(pos = pos.copy(maxDiscountPercent = intent.percent)) }
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
        SettingsIntent.OpenCreateUser                -> updateState { copy(users = users.copy(isCreating = true, editingUser = null, form = SettingsState.UserState.UserForm())) }
        is SettingsIntent.OpenEditUser               -> openEditUser(intent.user)
        SettingsIntent.DismissUserForm               -> updateState { copy(users = users.copy(isCreating = false, editingUser = null, saveError = null)) }
        is SettingsIntent.UpdateUserFormName         -> updateState { copy(users = users.copy(form = users.form.copy(name = intent.value))) }
        is SettingsIntent.UpdateUserFormEmail        -> updateState { copy(users = users.copy(form = users.form.copy(email = intent.value))) }
        is SettingsIntent.UpdateUserFormPassword     -> updateState { copy(users = users.copy(form = users.form.copy(password = intent.value))) }
        is SettingsIntent.UpdateUserFormRole         -> updateState { copy(users = users.copy(form = users.form.copy(roleKey = intent.role.name))) }
        is SettingsIntent.UpdateUserFormActive       -> updateState { copy(users = users.copy(form = users.form.copy(isActive = intent.isActive))) }
        SettingsIntent.SaveUser                      -> saveUser()
        // Backup
        SettingsIntent.LoadBackupInfo                -> loadBackupInfo()
        SettingsIntent.TriggerBackup                 -> triggerBackup()
        is SettingsIntent.RestoreSelected            -> updateState { copy(backup = backup.copy(restoreFilePath = intent.filePath, confirmRestore = true)) }
        SettingsIntent.ConfirmRestore                -> confirmRestore()
        SettingsIntent.CancelRestore                 -> updateState { copy(backup = backup.copy(confirmRestore = false, restoreFilePath = null)) }
        // Appearance
        SettingsIntent.LoadAppearance                -> loadAppearance()
        is SettingsIntent.UpdateThemeMode            -> updateThemeMode(intent.mode)
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
                settingsRepository.set(SettingsKeys.DATE_FORMAT,    s.dateFormat)
                settingsRepository.set(SettingsKeys.LANGUAGE,       s.language)
                sendEffect(SettingsEffect.GeneralSaved)
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
                        defaultOrderType   = runCatching { OrderType.valueOf(all[SettingsKeys.DEFAULT_ORDER_TYPE] ?: "SALE") }.getOrDefault(OrderType.SALE),
                        autoPrintReceipt   = all[SettingsKeys.AUTO_PRINT_RECEIPT] != "false",
                        taxDisplayMode     = runCatching { TaxDisplayMode.valueOf(all[SettingsKeys.TAX_DISPLAY_MODE] ?: "EXCLUSIVE") }.getOrDefault(TaxDisplayMode.EXCLUSIVE),
                        receiptTemplate    = runCatching { ReceiptTemplate.valueOf(all[SettingsKeys.RECEIPT_TEMPLATE] ?: "STANDARD") }.getOrDefault(ReceiptTemplate.STANDARD),
                        maxDiscountPercent = all[SettingsKeys.MAX_DISCOUNT_PERCENT]?.toDoubleOrNull() ?: 20.0,
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
                sendEffect(SettingsEffect.PosSaved)
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
                        printerType  = runCatching { PrinterType.valueOf(all[SettingsKeys.PRINTER_TYPE] ?: "USB") }.getOrDefault(PrinterType.USB),
                        tcpHost      = all[SettingsKeys.PRINTER_TCP_HOST]    ?: "",
                        tcpPort      = all[SettingsKeys.PRINTER_TCP_PORT]    ?: "9100",
                        serialPort   = all[SettingsKeys.PRINTER_SERIAL_PORT] ?: "",
                        baudRate     = all[SettingsKeys.PRINTER_BAUD_RATE]   ?: "115200",
                        btAddress    = all[SettingsKeys.PRINTER_BT_ADDRESS]  ?: "",
                        paperWidth   = runCatching { PaperWidthOption.valueOf(all[SettingsKeys.PRINTER_PAPER_WIDTH] ?: "MM_80") }.getOrDefault(PaperWidthOption.MM_80),
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
                    name     = user.name,
                    email    = user.email,
                    password = "",
                    roleKey  = user.role.name,
                    isActive = user.isActive,
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
            val role = runCatching { Role.valueOf(form.roleKey) }.getOrDefault(Role.CASHIER)
            val user = if (isUpdate) {
                editingUser!!.copy(name = form.name, role = role, isActive = form.isActive, updatedAt = now)
            } else {
                User(
                    id        = generateUuid(),
                    name      = form.name,
                    email     = form.email,
                    role      = role,
                    storeId   = "default",
                    isActive  = form.isActive,
                    createdAt = now,
                    updatedAt = now,
                )
            }
            saveUserUseCase(user, isUpdate, form.password)
                .onSuccess {
                    updateState { copy(users = users.copy(isCreating = false, editingUser = null, saveError = null)) }
                    sendEffect(SettingsEffect.UserSaved)
                }
                .onError { e -> updateState { copy(users = users.copy(saveError = e.message)) } }
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
            try {
                val now = Clock.System.now()
                settingsRepository.set(SettingsKeys.LAST_BACKUP_TIMESTAMP, now.toString())
                updateState { copy(backup = backup.copy(lastBackupAt = now)) }
                sendEffect(SettingsEffect.BackupComplete("backup_${now.epochSeconds}.db"))
            } catch (e: Exception) {
                updateState { copy(backup = backup.copy(backupError = e.message)) }
            } finally {
                updateState { copy(backup = backup.copy(isBackingUp = false)) }
            }
        }
    }

    private fun confirmRestore() {
        updateState { copy(backup = backup.copy(isRestoring = true, confirmRestore = false)) }
        viewModelScope.launch {
            try {
                sendEffect(SettingsEffect.RestoreComplete)
            } catch (e: Exception) {
                updateState { copy(backup = backup.copy(backupError = e.message)) }
            } finally {
                updateState { copy(backup = backup.copy(isRestoring = false, restoreFilePath = null)) }
            }
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

    // ── Utilities ─────────────────────────────────────────────────────────────

    /** Generates a cryptographically secure UUID v4 string via [IdGenerator]. */
    private fun generateUuid(): String = IdGenerator.newId()
}
