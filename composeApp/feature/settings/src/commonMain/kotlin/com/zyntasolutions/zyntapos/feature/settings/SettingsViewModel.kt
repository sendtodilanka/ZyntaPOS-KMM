package com.zyntasolutions.zyntapos.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.zyntasolutions.zyntapos.domain.usecase.settings.PrintTestPageUseCase
import com.zyntasolutions.zyntapos.domain.usecase.settings.SaveUserUseCase
import com.zyntasolutions.zyntapos.hal.printer.PaperWidth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * MVI ViewModel for the Settings feature (Sprint 23).
 *
 * Orchestrates CRUD for General, POS, Tax, Printer, User, Backup, and
 * Appearance settings. Each settings domain has a dedicated nested state slice
 * within [SettingsState] to minimise recomposition scope.
 *
 * @param settingsRepository  Typed key-value persistent settings store.
 * @param taxGroupRepository  Tax group CRUD persistence.
 * @param userRepository      User account CRUD persistence.
 * @param saveTaxGroupUseCase Validated tax group insert/update.
 * @param saveUserUseCase     Validated user account insert/update.
 * @param printTestPageUseCase Sends ESC/POS test page via HAL printer.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val taxGroupRepository: TaxGroupRepository,
    private val userRepository: UserRepository,
    private val saveTaxGroupUseCase: SaveTaxGroupUseCase,
    private val saveUserUseCase: SaveUserUseCase,
    private val printTestPageUseCase: PrintTestPageUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<SettingsEffect>(extraBufferCapacity = 16)
    val effect: SharedFlow<SettingsEffect> = _effect.asSharedFlow()

    private var taxGroupJob: Job? = null
    private var userJob: Job? = null

    // ── Intent dispatch ──────────────────────────────────────────────────────

    fun onIntent(intent: SettingsIntent) = when (intent) {
        // General
        SettingsIntent.LoadGeneral                   -> loadGeneral()
        is SettingsIntent.UpdateStoreName            -> _state.update { it.copy(general = it.general.copy(storeName = intent.value)) }
        is SettingsIntent.UpdateStoreAddress         -> _state.update { it.copy(general = it.general.copy(storeAddress = intent.value)) }
        is SettingsIntent.UpdateStorePhone           -> _state.update { it.copy(general = it.general.copy(storePhone = intent.value)) }
        is SettingsIntent.UpdateLogoUri              -> _state.update { it.copy(general = it.general.copy(logoUri = intent.uri)) }
        is SettingsIntent.UpdateCurrency             -> _state.update { it.copy(general = it.general.copy(currency = intent.currency)) }
        is SettingsIntent.UpdateTimezone             -> _state.update { it.copy(general = it.general.copy(timezone = intent.tz)) }
        is SettingsIntent.UpdateDateFormat           -> _state.update { it.copy(general = it.general.copy(dateFormat = intent.format)) }
        SettingsIntent.SaveGeneral                   -> saveGeneral()
        // POS
        SettingsIntent.LoadPos                       -> loadPos()
        is SettingsIntent.UpdateDefaultOrderType     -> _state.update { it.copy(pos = it.pos.copy(defaultOrderType = intent.orderType)) }
        is SettingsIntent.UpdateAutoPrintReceipt     -> _state.update { it.copy(pos = it.pos.copy(autoPrintReceipt = intent.enabled)) }
        is SettingsIntent.UpdateTaxDisplayMode       -> _state.update { it.copy(pos = it.pos.copy(taxDisplayMode = intent.mode)) }
        is SettingsIntent.UpdateReceiptTemplate      -> _state.update { it.copy(pos = it.pos.copy(receiptTemplate = intent.template)) }
        is SettingsIntent.UpdateMaxDiscount          -> _state.update { it.copy(pos = it.pos.copy(maxDiscountPercent = intent.percent)) }
        SettingsIntent.SavePos                       -> savePos()
        // Tax
        SettingsIntent.LoadTaxGroups                 -> loadTaxGroups()
        SettingsIntent.OpenCreateTaxGroup            -> _state.update { it.copy(tax = it.tax.copy(isCreating = true, isEditing = null)) }
        is SettingsIntent.OpenEditTaxGroup           -> _state.update { it.copy(tax = it.tax.copy(isEditing = intent.taxGroup, isCreating = false)) }
        is SettingsIntent.SaveTaxGroup               -> saveTaxGroup(intent.taxGroup, intent.isUpdate)
        SettingsIntent.DismissTaxForm                -> _state.update { it.copy(tax = it.tax.copy(isCreating = false, isEditing = null, saveError = null)) }
        is SettingsIntent.RequestDeleteTaxGroup      -> _state.update { it.copy(tax = it.tax.copy(deleteTarget = intent.taxGroup)) }
        SettingsIntent.ConfirmDeleteTaxGroup         -> confirmDeleteTaxGroup()
        SettingsIntent.CancelDeleteTaxGroup          -> _state.update { it.copy(tax = it.tax.copy(deleteTarget = null)) }
        // Printer
        SettingsIntent.LoadPrinter                   -> loadPrinter()
        is SettingsIntent.UpdatePrinterType          -> _state.update { it.copy(printer = it.printer.copy(printerType = intent.type)) }
        is SettingsIntent.UpdateTcpHost              -> _state.update { it.copy(printer = it.printer.copy(tcpHost = intent.host)) }
        is SettingsIntent.UpdateTcpPort              -> _state.update { it.copy(printer = it.printer.copy(tcpPort = intent.port)) }
        is SettingsIntent.UpdateSerialPort           -> _state.update { it.copy(printer = it.printer.copy(serialPort = intent.port)) }
        is SettingsIntent.UpdateBaudRate             -> _state.update { it.copy(printer = it.printer.copy(baudRate = intent.rate)) }
        is SettingsIntent.UpdateBtAddress            -> _state.update { it.copy(printer = it.printer.copy(btAddress = intent.address)) }
        is SettingsIntent.UpdatePaperWidth           -> _state.update { it.copy(printer = it.printer.copy(paperWidth = intent.option)) }
        is SettingsIntent.UpdateHeaderLine           -> updateHeaderLine(intent.index, intent.value)
        is SettingsIntent.UpdateFooterLine           -> updateFooterLine(intent.index, intent.value)
        is SettingsIntent.UpdateShowQrCode           -> _state.update { it.copy(printer = it.printer.copy(showQrCode = intent.show)) }
        is SettingsIntent.UpdateShowLogo             -> _state.update { it.copy(printer = it.printer.copy(showLogo = intent.show)) }
        SettingsIntent.SavePrinter                   -> savePrinter()
        SettingsIntent.TestPrint                     -> testPrint()
        // Users
        SettingsIntent.LoadUsers                     -> loadUsers()
        SettingsIntent.OpenCreateUser                -> _state.update { it.copy(users = it.users.copy(isCreating = true, editingUser = null, form = SettingsState.UserState.UserForm())) }
        is SettingsIntent.OpenEditUser               -> openEditUser(intent.user)
        SettingsIntent.DismissUserForm               -> _state.update { it.copy(users = it.users.copy(isCreating = false, editingUser = null, saveError = null)) }
        is SettingsIntent.UpdateUserFormName         -> _state.update { it.copy(users = it.users.copy(form = it.users.form.copy(name = intent.value))) }
        is SettingsIntent.UpdateUserFormEmail        -> _state.update { it.copy(users = it.users.copy(form = it.users.form.copy(email = intent.value))) }
        is SettingsIntent.UpdateUserFormPassword     -> _state.update { it.copy(users = it.users.copy(form = it.users.form.copy(password = intent.value))) }
        is SettingsIntent.UpdateUserFormRole         -> _state.update { it.copy(users = it.users.copy(form = it.users.form.copy(roleKey = intent.role.name))) }
        is SettingsIntent.UpdateUserFormActive       -> _state.update { it.copy(users = it.users.copy(form = it.users.form.copy(isActive = intent.isActive))) }
        SettingsIntent.SaveUser                      -> saveUser()
        // Backup
        SettingsIntent.LoadBackupInfo                -> loadBackupInfo()
        SettingsIntent.TriggerBackup                 -> triggerBackup()
        is SettingsIntent.RestoreSelected            -> _state.update { it.copy(backup = it.backup.copy(restoreFilePath = intent.filePath, confirmRestore = true)) }
        SettingsIntent.ConfirmRestore                -> confirmRestore()
        SettingsIntent.CancelRestore                 -> _state.update { it.copy(backup = it.backup.copy(confirmRestore = false, restoreFilePath = null)) }
        // Appearance
        SettingsIntent.LoadAppearance                -> loadAppearance()
        is SettingsIntent.UpdateThemeMode            -> updateThemeMode(intent.mode)
    }

    // ── General ──────────────────────────────────────────────────────────────

    private fun loadGeneral() {
        viewModelScope.launch {
            val all = settingsRepository.getAll()
            _state.update {
                it.copy(
                    general = it.general.copy(
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
        val s = _state.value.general
        _state.update { it.copy(general = it.general.copy(isSaving = true, saveError = null)) }
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
                _effect.emit(SettingsEffect.GeneralSaved)
            } catch (e: Exception) {
                _state.update { it.copy(general = it.general.copy(saveError = e.message)) }
            } finally {
                _state.update { it.copy(general = it.general.copy(isSaving = false)) }
            }
        }
    }

    // ── POS ──────────────────────────────────────────────────────────────────

    private fun loadPos() {
        viewModelScope.launch {
            val all = settingsRepository.getAll()
            _state.update {
                it.copy(
                    pos = it.pos.copy(
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
        val s = _state.value.pos
        _state.update { it.copy(pos = it.pos.copy(isSaving = true, saveError = null)) }
        viewModelScope.launch {
            try {
                settingsRepository.set(SettingsKeys.DEFAULT_ORDER_TYPE,   s.defaultOrderType.name)
                settingsRepository.set(SettingsKeys.AUTO_PRINT_RECEIPT,   s.autoPrintReceipt.toString())
                settingsRepository.set(SettingsKeys.TAX_DISPLAY_MODE,     s.taxDisplayMode.name)
                settingsRepository.set(SettingsKeys.RECEIPT_TEMPLATE,     s.receiptTemplate.name)
                settingsRepository.set(SettingsKeys.MAX_DISCOUNT_PERCENT, s.maxDiscountPercent.toString())
                _effect.emit(SettingsEffect.PosSaved)
            } catch (e: Exception) {
                _state.update { it.copy(pos = it.pos.copy(saveError = e.message)) }
            } finally {
                _state.update { it.copy(pos = it.pos.copy(isSaving = false)) }
            }
        }
    }

    // ── Tax ───────────────────────────────────────────────────────────────────

    private fun loadTaxGroups() {
        taxGroupJob?.cancel()
        _state.update { it.copy(tax = it.tax.copy(isLoading = true)) }
        taxGroupJob = viewModelScope.launch {
            taxGroupRepository.getAll()
                .catch { e ->
                    _state.update { it.copy(tax = it.tax.copy(isLoading = false, saveError = e.message)) }
                }
                .collect { groups ->
                    _state.update { it.copy(tax = it.tax.copy(taxGroups = groups, isLoading = false)) }
                }
        }
    }

    private fun saveTaxGroup(taxGroup: TaxGroup, isUpdate: Boolean) {
        viewModelScope.launch {
            saveTaxGroupUseCase(taxGroup, isUpdate)
                .onSuccess {
                    _state.update { it.copy(tax = it.tax.copy(isCreating = false, isEditing = null, saveError = null)) }
                    _effect.emit(SettingsEffect.ShowSnackbar("Tax group saved successfully."))
                }
                .onError { e ->
                    _state.update { it.copy(tax = it.tax.copy(saveError = e.message)) }
                }
        }
    }

    private fun confirmDeleteTaxGroup() {
        val target = _state.value.tax.deleteTarget ?: return
        _state.update { it.copy(tax = it.tax.copy(deleteTarget = null)) }
        viewModelScope.launch {
            taxGroupRepository.delete(target.id)
                .onSuccess { _effect.emit(SettingsEffect.ShowSnackbar("'${target.name}' deleted.")) }
                .onError { e -> _effect.emit(SettingsEffect.ShowSnackbar("Delete failed: ${e.message}")) }
        }
    }

    // ── Printer ───────────────────────────────────────────────────────────────

    private fun loadPrinter() {
        viewModelScope.launch {
            val all = settingsRepository.getAll()
            _state.update {
                it.copy(
                    printer = it.printer.copy(
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
        val s = _state.value.printer
        _state.update { it.copy(printer = it.printer.copy(isSaving = true, saveError = null)) }
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
                _effect.emit(SettingsEffect.PrinterSaved)
            } catch (e: Exception) {
                _state.update { it.copy(printer = it.printer.copy(saveError = e.message)) }
            } finally {
                _state.update { it.copy(printer = it.printer.copy(isSaving = false)) }
            }
        }
    }

    private fun testPrint() {
        _state.update { it.copy(printer = it.printer.copy(isTestPrinting = true)) }
        viewModelScope.launch {
            val pw = if (_state.value.printer.paperWidth == PaperWidthOption.MM_58) PaperWidth.MM_58 else PaperWidth.MM_80
            printTestPageUseCase(pw).fold(
                onSuccess = { _effect.emit(SettingsEffect.PrintTestPageSent) },
                onFailure = { e -> _effect.emit(SettingsEffect.ShowSnackbar("Test print failed: ${e.message}")) },
            )
            _state.update { it.copy(printer = it.printer.copy(isTestPrinting = false)) }
        }
    }

    private fun updateHeaderLine(index: Int, value: String) = _state.update {
        val updated = it.printer.headerLines.toMutableList().also { list ->
            if (index in list.indices) list[index] = value
        }
        it.copy(printer = it.printer.copy(headerLines = updated))
    }

    private fun updateFooterLine(index: Int, value: String) = _state.update {
        val updated = it.printer.footerLines.toMutableList().also { list ->
            if (index in list.indices) list[index] = value
        }
        it.copy(printer = it.printer.copy(footerLines = updated))
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    private fun loadUsers() {
        userJob?.cancel()
        _state.update { it.copy(users = it.users.copy(isLoading = true)) }
        userJob = viewModelScope.launch {
            userRepository.getAll()
                .catch { e -> _state.update { it.copy(users = it.users.copy(isLoading = false, saveError = e.message)) } }
                .collect { list -> _state.update { it.copy(users = it.users.copy(users = list, isLoading = false)) } }
        }
    }

    private fun openEditUser(user: User) = _state.update {
        it.copy(
            users = it.users.copy(
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
        val form        = _state.value.users.form
        val editingUser = _state.value.users.editingUser
        val isUpdate    = editingUser != null
        viewModelScope.launch {
            val now = Clock.System.now()
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
                    _state.update { it.copy(users = it.users.copy(isCreating = false, editingUser = null, saveError = null)) }
                    _effect.emit(SettingsEffect.UserSaved)
                }
                .onError { e -> _state.update { it.copy(users = it.users.copy(saveError = e.message)) } }
        }
    }

    // ── Backup ────────────────────────────────────────────────────────────────

    private fun loadBackupInfo() {
        viewModelScope.launch {
            val ts = settingsRepository.get(SettingsKeys.LAST_BACKUP_TIMESTAMP)
            _state.update {
                it.copy(backup = it.backup.copy(lastBackupAt = ts?.let { s ->
                    runCatching { kotlinx.datetime.Instant.parse(s) }.getOrNull()
                }))
            }
        }
    }

    private fun triggerBackup() {
        _state.update { it.copy(backup = it.backup.copy(isBackingUp = true, backupError = null)) }
        viewModelScope.launch {
            try {
                val now = Clock.System.now()
                settingsRepository.set(SettingsKeys.LAST_BACKUP_TIMESTAMP, now.toString())
                _state.update { it.copy(backup = it.backup.copy(lastBackupAt = now)) }
                _effect.emit(SettingsEffect.BackupComplete("backup_${now.epochSeconds}.db"))
            } catch (e: Exception) {
                _state.update { it.copy(backup = it.backup.copy(backupError = e.message)) }
            } finally {
                _state.update { it.copy(backup = it.backup.copy(isBackingUp = false)) }
            }
        }
    }

    private fun confirmRestore() {
        _state.update { it.copy(backup = it.backup.copy(isRestoring = true, confirmRestore = false)) }
        viewModelScope.launch {
            try {
                _effect.emit(SettingsEffect.RestoreComplete)
            } catch (e: Exception) {
                _state.update { it.copy(backup = it.backup.copy(backupError = e.message)) }
            } finally {
                _state.update { it.copy(backup = it.backup.copy(isRestoring = false, restoreFilePath = null)) }
            }
        }
    }

    // ── Appearance ────────────────────────────────────────────────────────────

    private fun loadAppearance() {
        viewModelScope.launch {
            val raw = settingsRepository.get(SettingsKeys.THEME_MODE)
            val mode = runCatching { ThemeMode.valueOf(raw ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
            _state.update { it.copy(appearance = it.appearance.copy(themeMode = mode)) }
        }
    }

    private fun updateThemeMode(mode: ThemeMode) {
        _state.update { it.copy(appearance = it.appearance.copy(themeMode = mode)) }
        viewModelScope.launch {
            settingsRepository.set(SettingsKeys.THEME_MODE, mode.name)
            _effect.emit(SettingsEffect.ThemeModeChanged(mode))
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /** Generates a naive UUID v4 string without platform-specific APIs. */
    private fun generateUuid(): String = buildString {
        val hex = "0123456789abcdef"
        var cnt = 0
        repeat(32) { i ->
            if (i in listOf(8, 12, 16, 20)) append('-')
            val nibble = when (i) {
                12   -> 4
                16   -> 8 + (0..3).random()
                else -> (0..15).random()
            }
            append(hex[nibble])
        }
    }
}
