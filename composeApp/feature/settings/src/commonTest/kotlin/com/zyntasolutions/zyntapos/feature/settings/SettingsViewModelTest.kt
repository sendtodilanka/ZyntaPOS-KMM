package com.zyntasolutions.zyntapos.feature.settings

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.domain.model.CustomRole
import com.zyntasolutions.zyntapos.domain.model.OrderType
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.TaxGroup
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.AuditRepository
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.RoleRepository
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository
import com.zyntasolutions.zyntapos.domain.repository.TaxGroupRepository
import com.zyntasolutions.zyntapos.domain.repository.UserRepository
import com.zyntasolutions.zyntapos.security.audit.SecurityAuditLogger
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
import com.zyntasolutions.zyntapos.domain.model.RegionalTaxOverride
import com.zyntasolutions.zyntapos.domain.repository.RegionalTaxOverrideRepository
import com.zyntasolutions.zyntapos.domain.usecase.settings.DeleteTaxOverrideUseCase
import com.zyntasolutions.zyntapos.domain.usecase.settings.GetTaxOverridesUseCase
import com.zyntasolutions.zyntapos.domain.usecase.settings.SaveTaxOverrideUseCase
import com.zyntasolutions.zyntapos.domain.model.LabelPrinterConfig
import com.zyntasolutions.zyntapos.domain.model.PrinterJobType
import com.zyntasolutions.zyntapos.domain.model.PrinterPaperWidth
import com.zyntasolutions.zyntapos.domain.model.PrinterProfile
import com.zyntasolutions.zyntapos.domain.repository.LabelPrinterConfigRepository
import com.zyntasolutions.zyntapos.domain.repository.PrinterProfileRepository
import com.zyntasolutions.zyntapos.feature.settings.backup.BackupResult
import com.zyntasolutions.zyntapos.feature.settings.backup.BackupService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker
import kotlin.time.Clock

// ─────────────────────────────────────────────────────────────────────────────
// SettingsViewModelTest — unit tests for SettingsViewModel MVI logic.
//
// Uses hand-rolled fakes (no Mockative annotation needed for pure Kotlin stubs).
// All tests run under UnconfinedTestDispatcher for deterministic coroutine execution.
// Sprint 23 — Step 13.1.TEST
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val noOpAnalytics = object : AnalyticsTracker {
        override fun logEvent(name: String, params: Map<String, String>) = Unit
        override fun logScreenView(screenName: String, screenClass: String) = Unit
        override fun setUserId(userId: String?) = Unit
        override fun setUserProperty(name: String, value: String) = Unit
    }

    // ── Fake collaborators ────────────────────────────────────────────────────

    private val store = mutableMapOf<String, String>()

    private val fakeSettingsRepository = object : SettingsRepository {
        override suspend fun get(key: String): String? = store[key]
        override suspend fun set(key: String, value: String): Result<Unit> {
            store[key] = value
            return Result.Success(Unit)
        }
        override suspend fun getAll(): Map<String, String> = store.toMap()
        override fun observe(key: String): Flow<String?> = MutableStateFlow(store[key])
    }

    private val taxGroupsFlow = MutableStateFlow<List<TaxGroup>>(emptyList())
    private val savedTaxGroups = mutableListOf<TaxGroup>()
    private val deletedTaxGroupIds = mutableListOf<String>()

    private val fakeTaxGroupRepository = object : TaxGroupRepository {
        override fun getAll() = taxGroupsFlow
        override suspend fun getById(id: String): Result<TaxGroup> {
            val tg = taxGroupsFlow.value.firstOrNull { it.id == id }
                ?: return Result.Error(DatabaseException("Not found"))
            return Result.Success(tg)
        }
        override suspend fun insert(taxGroup: TaxGroup): Result<Unit> {
            taxGroupsFlow.value = taxGroupsFlow.value + taxGroup
            return Result.Success(Unit)
        }
        override suspend fun update(taxGroup: TaxGroup): Result<Unit> {
            taxGroupsFlow.value = taxGroupsFlow.value.map { if (it.id == taxGroup.id) taxGroup else it }
            return Result.Success(Unit)
        }
        override suspend fun delete(id: String): Result<Unit> {
            deletedTaxGroupIds.add(id)
            taxGroupsFlow.value = taxGroupsFlow.value.filter { it.id != id }
            return Result.Success(Unit)
        }
    }

    private val usersFlow = MutableStateFlow<List<User>>(emptyList())

    private val fakeUserRepository = object : UserRepository {
        override fun getAll(storeId: String?) = usersFlow
        override suspend fun getById(id: String): Result<User> =
            Result.Error(DatabaseException("Not implemented"))
        override suspend fun create(user: User, plainPassword: String): Result<Unit> =
            Result.Success(Unit)
        override suspend fun update(user: User): Result<Unit> =
            Result.Success(Unit)
        override suspend fun updatePassword(userId: String, newPlainPassword: String): Result<Unit> =
            Result.Success(Unit)
        override suspend fun deactivate(userId: String): Result<Unit> =
            Result.Success(Unit)
        override suspend fun getSystemAdmin(): Result<User?> = Result.Success(null)
        override suspend fun adminExists(): Result<Boolean> = Result.Success(false)
        override suspend fun transferSystemAdmin(fromUserId: String, toUserId: String): Result<Unit> =
            Result.Success(Unit)
        override suspend fun getQuickSwitchCandidates(storeId: String): Result<List<com.zyntasolutions.zyntapos.domain.model.QuickSwitchCandidate>> =
            Result.Success(emptyList())
    }

    private val fakeSaveTaxGroupUseCase = SaveTaxGroupUseCase(fakeTaxGroupRepository)

    private val savedUsers = mutableListOf<User>()

    private val fakeSaveUserUseCase = SaveUserUseCase(fakeUserRepository)

    private var testPrintCalled = false
    private var testPrintPaperWidth: PrinterPaperWidth? = null

    private val fakePrintTestPageUseCase = PrintTestPageUseCase { paperWidth ->
        testPrintCalled = true
        testPrintPaperWidth = paperWidth
        kotlin.Result.success(Unit)
    }

    private val fakeBackupService = object : BackupService {
        override suspend fun createBackup(): kotlin.Result<BackupResult> =
            kotlin.Result.success(BackupResult(filePath = "/tmp/backup.db", sizeBytes = 1024L))
        override suspend fun restoreFromBackup(sourcePath: String): kotlin.Result<Unit> =
            kotlin.Result.success(Unit)
        override fun getDefaultBackupDirectory(): String = "/tmp/backups"
    }

    // ── No-op AuditRepository + SecurityAuditLogger ───────────────────────────

    private val noOpAuditRepository = object : AuditRepository {
        override suspend fun insert(entry: AuditEntry) = Unit
        override fun observeAll(): Flow<List<AuditEntry>> = MutableStateFlow(emptyList())
        override fun observeByUserId(userId: String): Flow<List<AuditEntry>> = MutableStateFlow(emptyList())
        override suspend fun getAllChronological(): List<AuditEntry> = emptyList()
        override suspend fun getLatestHash(): String? = null
        override suspend fun countEntries(): Long = 0L
        override suspend fun getRecentLoginFailureCount(userId: String, sinceEpochMillis: Long): Long = 0L
    }
    private val testAuditLogger = SecurityAuditLogger(noOpAuditRepository, "test-device")

    // ── Auth repository stub (for SetPinUseCase) ──────────────────────────────

    private val fakeAuthRepository = object : AuthRepository {
        override suspend fun login(email: String, password: String): Result<User> = Result.Success(
            User(
                id = "u1", name = "Test", email = "test@test.com", role = Role.CASHIER,
                storeId = "store-01", isActive = true, pinHash = null,
                createdAt = Instant.fromEpochSeconds(0), updatedAt = Instant.fromEpochSeconds(0),
            )
        )
        override suspend fun logout() {}
        override fun getSession(): Flow<User?> = MutableStateFlow(null)
        override suspend fun refreshToken(): Result<Unit> = Result.Success(Unit)
        override suspend fun updatePin(userId: String, pin: String): Result<Unit> = Result.Success(Unit)
        override suspend fun validatePin(userId: String, pin: String): Result<Boolean> = Result.Success(true)
        override suspend fun quickSwitch(userId: String, pin: String): Result<User> = Result.Success(
            User(
                id = "u1", name = "Test", email = "test@test.com", role = Role.CASHIER,
                storeId = "store-01", isActive = true, pinHash = null,
                createdAt = Instant.fromEpochSeconds(0), updatedAt = Instant.fromEpochSeconds(0),
            )
        )
        override suspend fun validateManagerPin(pin: String): Result<Boolean> = Result.Success(true)
    }

    // ── Role repository fake ──────────────────────────────────────────────────

    private val fakeCustomRolesFlow = MutableStateFlow<List<CustomRole>>(emptyList())
    private val createdRoles        = mutableListOf<CustomRole>()
    private val deletedRoleIds      = mutableListOf<String>()
    private val builtInOverrides    = mutableMapOf<Role, Set<Permission>>()

    private val fakeRoleRepository = object : RoleRepository {
        override fun getAllCustomRoles(): Flow<List<CustomRole>> = fakeCustomRolesFlow
        override suspend fun getCustomRoleById(id: String): Result<CustomRole> =
            fakeCustomRolesFlow.value.firstOrNull { it.id == id }?.let { Result.Success(it) }
                ?: Result.Error(DatabaseException("Not found"))
        override suspend fun createCustomRole(role: CustomRole): Result<Unit> {
            createdRoles.add(role)
            fakeCustomRolesFlow.value = fakeCustomRolesFlow.value + role
            return Result.Success(Unit)
        }
        override suspend fun updateCustomRole(role: CustomRole): Result<Unit> {
            fakeCustomRolesFlow.value = fakeCustomRolesFlow.value.map {
                if (it.id == role.id) role else it
            }
            return Result.Success(Unit)
        }
        override suspend fun deleteCustomRole(id: String): Result<Unit> {
            deletedRoleIds.add(id)
            fakeCustomRolesFlow.value = fakeCustomRolesFlow.value.filter { it.id != id }
            return Result.Success(Unit)
        }
        override suspend fun getBuiltInRolePermissions(role: Role): Set<Permission>? =
            builtInOverrides[role]
        override suspend fun setBuiltInRolePermissions(
            role: Role,
            permissions: Set<Permission>,
        ): Result<Unit> {
            builtInOverrides[role] = permissions
            return Result.Success(Unit)
        }
        override suspend fun resetBuiltInRolePermissions(role: Role): Result<Unit> {
            builtInOverrides.remove(role)
            return Result.Success(Unit)
        }
    }

    // ── Fake label-printer config repository ─────────────────────────────────

    private val fakeLabelPrinterConfigRepository = object : LabelPrinterConfigRepository {
        override suspend fun get(): Result<LabelPrinterConfig?> = Result.Success(null)
        override suspend fun save(config: LabelPrinterConfig): Result<Unit> = Result.Success(Unit)
    }

    // ── Fake printer profile repository ──────────────────────────────────────

    private val printerProfilesFlow = MutableStateFlow<List<PrinterProfile>>(emptyList())

    private val fakePrinterProfileRepository = object : PrinterProfileRepository {
        override fun getAll(): Flow<List<PrinterProfile>> = printerProfilesFlow
        override suspend fun getById(id: String): Result<PrinterProfile> =
            Result.Error(DatabaseException("Not found"))
        override suspend fun getDefault(jobType: PrinterJobType): Result<PrinterProfile?> = Result.Success(null)
        override suspend fun save(profile: PrinterProfile): Result<Unit> = Result.Success(Unit)
        override suspend fun delete(id: String): Result<Unit> = Result.Success(Unit)
    }

    private val fakeRegionalTaxOverrideRepository = object : RegionalTaxOverrideRepository {
        private val store = MutableStateFlow<List<RegionalTaxOverride>>(emptyList())
        override fun getOverridesForStore(storeId: String) = store.map { it.filter { o -> o.storeId == storeId && o.isActive } }
        override suspend fun getEffectiveOverride(taxGroupId: String, storeId: String, nowEpochMs: Long) = Result.Success<RegionalTaxOverride?>(null)
        override fun getOverridesForTaxGroup(taxGroupId: String) = store.map { it.filter { o -> o.taxGroupId == taxGroupId } }
        override suspend fun upsert(override: RegionalTaxOverride): Result<Unit> { store.value = store.value.filter { it.id != override.id } + override; return Result.Success(Unit) }
        override suspend fun delete(id: String): Result<Unit> { store.value = store.value.filter { it.id != id }; return Result.Success(Unit) }
    }

    private val fakeStoreRepository = object : com.zyntasolutions.zyntapos.domain.repository.StoreRepository {
        override fun getAllStores(): Flow<List<com.zyntasolutions.zyntapos.domain.model.Store>> = MutableStateFlow(emptyList())
        override suspend fun getById(storeId: String): com.zyntasolutions.zyntapos.domain.model.Store? = null
        override suspend fun getStoreName(storeId: String): String? = null
        override suspend fun upsertFromSync(store: com.zyntasolutions.zyntapos.domain.model.Store) = Unit
    }

    private lateinit var viewModel: SettingsViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        store.clear()
        savedTaxGroups.clear()
        deletedTaxGroupIds.clear()
        savedUsers.clear()
        createdRoles.clear()
        deletedRoleIds.clear()
        builtInOverrides.clear()
        taxGroupsFlow.value = emptyList()
        usersFlow.value = emptyList()
        fakeCustomRolesFlow.value = emptyList()
        printerProfilesFlow.value = emptyList()
        testPrintCalled = false

        viewModel = SettingsViewModel(
            settingsRepository           = fakeSettingsRepository,
            taxGroupRepository           = fakeTaxGroupRepository,
            userRepository               = fakeUserRepository,
            roleRepository               = fakeRoleRepository,
            saveTaxGroupUseCase          = fakeSaveTaxGroupUseCase,
            saveUserUseCase              = fakeSaveUserUseCase,
            setPinUseCase                = SetPinUseCase(fakeAuthRepository),
            saveCustomRoleUseCase        = SaveCustomRoleUseCase(fakeRoleRepository),
            deleteCustomRoleUseCase      = DeleteCustomRoleUseCase(fakeRoleRepository),
            printTestPageUseCase         = fakePrintTestPageUseCase,
            backupService                = fakeBackupService,
            getLabelPrinterConfigUseCase = GetLabelPrinterConfigUseCase(fakeLabelPrinterConfigRepository),
            saveLabelPrinterConfigUseCase = SaveLabelPrinterConfigUseCase(fakeLabelPrinterConfigRepository),
            getPrinterProfilesUseCase    = GetPrinterProfilesUseCase(fakePrinterProfileRepository),
            savePrinterProfileUseCase    = SavePrinterProfileUseCase(fakePrinterProfileRepository),
            deletePrinterProfileUseCase  = DeletePrinterProfileUseCase(fakePrinterProfileRepository),
            storeRepository              = fakeStoreRepository,
            auditLogger                  = testAuditLogger,
            authRepository               = fakeAuthRepository,
            analytics                    = noOpAnalytics,
            getTaxOverridesUseCase       = GetTaxOverridesUseCase(fakeRegionalTaxOverrideRepository),
            saveTaxOverrideUseCase       = SaveTaxOverrideUseCase(fakeRegionalTaxOverrideRepository),
            deleteTaxOverrideUseCase     = DeleteTaxOverrideUseCase(fakeRegionalTaxOverrideRepository),
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── General settings tests ────────────────────────────────────────────────

    @Test
    fun `LoadGeneral updates state from repository`() = runTest(UnconfinedTestDispatcher()) {
        store[SettingsKeys.STORE_NAME]    = "My Shop"
        store[SettingsKeys.STORE_ADDRESS] = "123 Main St"
        store[SettingsKeys.CURRENCY]      = "USD"

        viewModel.dispatch(SettingsIntent.LoadGeneral)

        val state = viewModel.state.value.general
        assertEquals("My Shop",   state.storeName)
        assertEquals("123 Main St", state.storeAddress)
        assertEquals(Currency.USD,  state.currency)
    }

    @Test
    fun `SaveGeneral persists all general settings keys`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.dispatch(SettingsIntent.UpdateStoreName("ZyntaShop"))
        viewModel.dispatch(SettingsIntent.UpdateCurrency(Currency.EUR))
        viewModel.dispatch(SettingsIntent.SaveGeneral)

        assertEquals("ZyntaShop", store[SettingsKeys.STORE_NAME])
        assertEquals("EUR",       store[SettingsKeys.CURRENCY])
    }

    // ── POS settings tests ────────────────────────────────────────────────────

    @Test
    fun `LoadPos defaults to SALE orderType when no stored value`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.dispatch(SettingsIntent.LoadPos)
        assertEquals(OrderType.SALE, viewModel.state.value.pos.defaultOrderType)
    }

    @Test
    fun `UpdateMaxDiscount reflects in state`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.dispatch(SettingsIntent.UpdateMaxDiscount(15.0))
        assertEquals(15.0, viewModel.state.value.pos.maxDiscountPercent)
    }

    @Test
    fun `SavePos persists auto-print and tax display mode`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.dispatch(SettingsIntent.UpdateAutoPrintReceipt(false))
        viewModel.dispatch(SettingsIntent.UpdateTaxDisplayMode(TaxDisplayMode.INCLUSIVE))
        viewModel.dispatch(SettingsIntent.SavePos)

        assertEquals("false",     store[SettingsKeys.AUTO_PRINT_RECEIPT])
        assertEquals("INCLUSIVE", store[SettingsKeys.TAX_DISPLAY_MODE])
    }

    // ── Tax settings tests ────────────────────────────────────────────────────

    @Test
    fun `LoadTaxGroups collects from TaxGroupRepository`() = runTest(UnconfinedTestDispatcher()) {
        taxGroupsFlow.value = listOf(
            TaxGroup(id = "1", name = "VAT", rate = 15.0, isInclusive = false),
        )
        viewModel.dispatch(SettingsIntent.LoadTaxGroups)

        assertEquals(1, viewModel.state.value.tax.taxGroups.size)
        assertEquals("VAT", viewModel.state.value.tax.taxGroups.first().name)
    }

    @Test
    fun `OpenCreateTaxGroup sets isCreating=true and clears isEditing`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.dispatch(SettingsIntent.OpenCreateTaxGroup)
        val tax = viewModel.state.value.tax
        assertTrue(tax.isCreating)
        assertNull(tax.isEditing)
    }

    @Test
    fun `ConfirmDeleteTaxGroup removes group from repository`() = runTest(UnconfinedTestDispatcher()) {
        val tg = TaxGroup("t1", "GST", 10.0, false)
        taxGroupsFlow.value = listOf(tg)
        viewModel.dispatch(SettingsIntent.LoadTaxGroups)
        viewModel.dispatch(SettingsIntent.RequestDeleteTaxGroup(tg))
        viewModel.dispatch(SettingsIntent.ConfirmDeleteTaxGroup)

        assertTrue(deletedTaxGroupIds.contains("t1"))
    }

    // ── Printer settings tests ────────────────────────────────────────────────

    @Test
    fun `UpdatePrinterType reflects in state`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.dispatch(SettingsIntent.UpdatePrinterType(PrinterType.TCP))
        assertEquals(PrinterType.TCP, viewModel.state.value.printer.printerType)
    }

    @Test
    fun `UpdateHeaderLine mutates correct index`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.dispatch(SettingsIntent.UpdateHeaderLine(2, "Thank you!"))
        assertEquals("Thank you!", viewModel.state.value.printer.headerLines[2])
    }

    @Test
    fun `TestPrint invokes PrintTestPageUseCase`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.dispatch(SettingsIntent.TestPrint)
        assertTrue(testPrintCalled)
    }

    // ── Appearance settings tests ─────────────────────────────────────────────

    @Test
    fun `UpdateThemeMode persists to repository and updates state`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.dispatch(SettingsIntent.UpdateThemeMode(ThemeMode.DARK))

        assertEquals(ThemeMode.DARK, viewModel.state.value.appearance.themeMode)
        assertEquals("DARK", store[SettingsKeys.THEME_MODE])
    }

    @Test
    fun `LoadAppearance restores persisted theme mode`() = runTest(UnconfinedTestDispatcher()) {
        store[SettingsKeys.THEME_MODE] = "LIGHT"
        viewModel.dispatch(SettingsIntent.LoadAppearance)
        assertEquals(ThemeMode.LIGHT, viewModel.state.value.appearance.themeMode)
    }

    // ── Backup tests ──────────────────────────────────────────────────────────

    @Test
    fun `TriggerBackup updates lastBackupAt timestamp`() = runTest(UnconfinedTestDispatcher()) {
        assertNull(viewModel.state.value.backup.lastBackupAt)
        viewModel.dispatch(SettingsIntent.TriggerBackup)
        assertTrue(viewModel.state.value.backup.lastBackupAt != null)
        assertTrue(store.containsKey(SettingsKeys.LAST_BACKUP_TIMESTAMP))
    }

    @Test
    fun `CancelRestore clears confirmRestore flag`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.dispatch(SettingsIntent.RestoreSelected("/some/path.db"))
        assertTrue(viewModel.state.value.backup.confirmRestore)
        viewModel.dispatch(SettingsIntent.CancelRestore)
        assertFalse(viewModel.state.value.backup.confirmRestore)
    }

    // ── PIN management tests ──────────────────────────────────────────────────

    @Test
    fun `UpdateUserFormPin updates newPin field and clears pinError`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.dispatch(SettingsIntent.UpdateUserFormPin("1234"))

        assertEquals("1234", viewModel.state.value.users.form.newPin)
        assertNull(viewModel.state.value.users.form.pinError)
    }

    @Test
    fun `UpdateUserFormConfirmPin updates confirmPin field and clears pinError`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.dispatch(SettingsIntent.UpdateUserFormConfirmPin("1234"))

        assertEquals("1234", viewModel.state.value.users.form.confirmPin)
        assertNull(viewModel.state.value.users.form.pinError)
    }

    @Test
    fun `ClearUserFormPin resets both pin fields to empty`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.dispatch(SettingsIntent.UpdateUserFormPin("1234"))
        viewModel.dispatch(SettingsIntent.UpdateUserFormConfirmPin("1234"))
        viewModel.dispatch(SettingsIntent.ClearUserFormPin)

        assertEquals("", viewModel.state.value.users.form.newPin)
        assertEquals("", viewModel.state.value.users.form.confirmPin)
        assertNull(viewModel.state.value.users.form.pinError)
    }

    // ── RBAC management tests ─────────────────────────────────────────────────

    @Test
    fun `LoadRbac populates customRoles and builtInRoles from repository`() = runTest(UnconfinedTestDispatcher()) {
        val kitchenRole = CustomRole(
            id          = "role-01",
            name        = "Kitchen",
            permissions = setOf(Permission.MANAGE_PRODUCTS),
            createdAt   = Instant.fromEpochSeconds(0),
            updatedAt   = Instant.fromEpochSeconds(0),
        )
        fakeCustomRolesFlow.value = listOf(kitchenRole)

        viewModel.dispatch(SettingsIntent.LoadRbac)

        assertEquals(listOf(kitchenRole), viewModel.state.value.rbac.customRoles)
        // Built-in roles loaded for all non-ADMIN roles (4 entries: STORE_MANAGER, CASHIER, ACCOUNTANT, STOCK_MANAGER)
        assertTrue(viewModel.state.value.rbac.builtInRoles.isNotEmpty())
    }

    @Test
    fun `SaveCustomRole creates a new role in the repository`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.dispatch(SettingsIntent.OpenCreateCustomRole)
        viewModel.dispatch(SettingsIntent.UpdateCustomRoleFormName("Kitchen Staff"))
        viewModel.dispatch(SettingsIntent.SaveCustomRole)

        assertEquals(1, createdRoles.size)
        assertEquals("Kitchen Staff", createdRoles.first().name)
    }

    @Test
    fun `DeleteCustomRole removes the role from the repository`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.dispatch(SettingsIntent.DeleteCustomRole("role-to-delete"))

        assertTrue(deletedRoleIds.contains("role-to-delete"))
    }

    // ── User management — admin guard (TODO-001) ──────────────────────────────

    @Test
    fun `SaveUser with ADMIN role sets saveError and does not call repository`() =
        runTest(UnconfinedTestDispatcher()) {
            // Open the create-user form and select the ADMIN role
            viewModel.dispatch(SettingsIntent.OpenCreateUser)
            viewModel.dispatch(SettingsIntent.UpdateUserFormName("Alice"))
            viewModel.dispatch(SettingsIntent.UpdateUserFormEmail("alice@example.com"))
            viewModel.dispatch(SettingsIntent.UpdateUserFormPassword("Secure123!"))
            viewModel.dispatch(SettingsIntent.UpdateUserFormRole(Role.ADMIN))

            viewModel.dispatch(SettingsIntent.SaveUser)

            val state = viewModel.state.value
            assertEquals(
                "Cannot create additional admin accounts",
                state.users.saveError,
                "ViewModel must block ADMIN role with a saveError message",
            )
        }
}
