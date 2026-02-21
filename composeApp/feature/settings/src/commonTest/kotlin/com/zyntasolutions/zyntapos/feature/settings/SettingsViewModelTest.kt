package com.zyntasolutions.zyntapos.feature.settings

import com.zyntasolutions.zyntapos.core.result.Result
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// SettingsViewModelTest — unit tests for SettingsViewModel MVI logic.
//
// Uses hand-rolled fakes (no Mockative annotation needed for pure Kotlin stubs).
// All tests run under UnconfinedTestDispatcher for deterministic coroutine execution.
// Sprint 23 — Step 13.1.TEST
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    // ── Fake collaborators ────────────────────────────────────────────────────

    private val store = mutableMapOf<String, String>()

    private val fakeSettingsRepository = object : SettingsRepository {
        override suspend fun get(key: String): String? = store[key]
        override suspend fun set(key: String, value: String) { store[key] = value }
        override suspend fun getAll(): Map<String, String> = store.toMap()
        override suspend fun remove(key: String) { store.remove(key) }
    }

    private val taxGroupsFlow = MutableStateFlow<List<TaxGroup>>(emptyList())
    private val savedTaxGroups = mutableListOf<TaxGroup>()
    private val deletedTaxGroupIds = mutableListOf<String>()

    private val fakeTaxGroupRepository = object : TaxGroupRepository {
        override fun getAll() = taxGroupsFlow
        override suspend fun delete(id: String): Result<Unit> {
            deletedTaxGroupIds.add(id)
            taxGroupsFlow.value = taxGroupsFlow.value.filter { it.id != id }
            return Result.Success(Unit)
        }
    }

    private val usersFlow = MutableStateFlow<List<User>>(emptyList())

    private val fakeUserRepository = object : UserRepository {
        override fun getAll() = usersFlow
    }

    private val fakeSaveTaxGroupUseCase = object : SaveTaxGroupUseCase {
        override suspend operator fun invoke(taxGroup: TaxGroup, isUpdate: Boolean): Result<Unit> {
            savedTaxGroups.add(taxGroup)
            val updated = taxGroupsFlow.value.toMutableList()
            val idx = updated.indexOfFirst { it.id == taxGroup.id }
            if (idx >= 0) updated[idx] = taxGroup else updated.add(taxGroup)
            taxGroupsFlow.value = updated
            return Result.Success(Unit)
        }
    }

    private val savedUsers = mutableListOf<User>()

    private val fakeSaveUserUseCase = object : SaveUserUseCase {
        override suspend operator fun invoke(user: User, isUpdate: Boolean, rawPassword: String): Result<Unit> {
            savedUsers.add(user)
            return Result.Success(Unit)
        }
    }

    private var testPrintCalled = false
    private var testPrintPaperWidth: PaperWidth? = null

    private val fakePrintTestPageUseCase = object : PrintTestPageUseCase {
        override suspend operator fun invoke(paperWidth: PaperWidth): kotlin.Result<Unit> {
            testPrintCalled = true
            testPrintPaperWidth = paperWidth
            return kotlin.Result.success(Unit)
        }
    }

    private lateinit var viewModel: SettingsViewModel

    @BeforeTest
    fun setUp() {
        store.clear()
        savedTaxGroups.clear()
        deletedTaxGroupIds.clear()
        savedUsers.clear()
        taxGroupsFlow.value = emptyList()
        usersFlow.value = emptyList()
        testPrintCalled = false

        viewModel = SettingsViewModel(
            settingsRepository   = fakeSettingsRepository,
            taxGroupRepository   = fakeTaxGroupRepository,
            userRepository       = fakeUserRepository,
            saveTaxGroupUseCase  = fakeSaveTaxGroupUseCase,
            saveUserUseCase      = fakeSaveUserUseCase,
            printTestPageUseCase = fakePrintTestPageUseCase,
        )
    }

    // ── General settings tests ────────────────────────────────────────────────

    @Test
    fun `LoadGeneral updates state from repository`() = runTest(UnconfinedTestDispatcher()) {
        store[SettingsKeys.STORE_NAME]    = "My Shop"
        store[SettingsKeys.STORE_ADDRESS] = "123 Main St"
        store[SettingsKeys.CURRENCY]      = "USD"

        viewModel.onIntent(SettingsIntent.LoadGeneral)

        val state = viewModel.state.value.general
        assertEquals("My Shop",   state.storeName)
        assertEquals("123 Main St", state.storeAddress)
        assertEquals(Currency.USD,  state.currency)
    }

    @Test
    fun `SaveGeneral persists all general settings keys`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.onIntent(SettingsIntent.UpdateStoreName("ZyntaShop"))
        viewModel.onIntent(SettingsIntent.UpdateCurrency(Currency.EUR))
        viewModel.onIntent(SettingsIntent.SaveGeneral)

        assertEquals("ZyntaShop", store[SettingsKeys.STORE_NAME])
        assertEquals("EUR",       store[SettingsKeys.CURRENCY])
    }

    // ── POS settings tests ────────────────────────────────────────────────────

    @Test
    fun `LoadPos defaults to SALE orderType when no stored value`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.onIntent(SettingsIntent.LoadPos)
        assertEquals(OrderType.SALE, viewModel.state.value.pos.defaultOrderType)
    }

    @Test
    fun `UpdateMaxDiscount reflects in state`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.onIntent(SettingsIntent.UpdateMaxDiscount(15.0))
        assertEquals(15.0, viewModel.state.value.pos.maxDiscountPercent)
    }

    @Test
    fun `SavePos persists auto-print and tax display mode`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.onIntent(SettingsIntent.UpdateAutoPrintReceipt(false))
        viewModel.onIntent(SettingsIntent.UpdateTaxDisplayMode(TaxDisplayMode.INCLUSIVE))
        viewModel.onIntent(SettingsIntent.SavePos)

        assertEquals("false",     store[SettingsKeys.AUTO_PRINT_RECEIPT])
        assertEquals("INCLUSIVE", store[SettingsKeys.TAX_DISPLAY_MODE])
    }

    // ── Tax settings tests ────────────────────────────────────────────────────

    @Test
    fun `LoadTaxGroups collects from TaxGroupRepository`() = runTest(UnconfinedTestDispatcher()) {
        val now = Clock.System.now()
        taxGroupsFlow.value = listOf(
            TaxGroup(id = "1", storeId = "s", name = "VAT", rate = 15.0, isInclusive = false, createdAt = now, updatedAt = now),
        )
        viewModel.onIntent(SettingsIntent.LoadTaxGroups)

        assertEquals(1, viewModel.state.value.tax.taxGroups.size)
        assertEquals("VAT", viewModel.state.value.tax.taxGroups.first().name)
    }

    @Test
    fun `OpenCreateTaxGroup sets isCreating=true and clears isEditing`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.onIntent(SettingsIntent.OpenCreateTaxGroup)
        val tax = viewModel.state.value.tax
        assertTrue(tax.isCreating)
        assertNull(tax.isEditing)
    }

    @Test
    fun `ConfirmDeleteTaxGroup removes group from repository`() = runTest(UnconfinedTestDispatcher()) {
        val now = Clock.System.now()
        val tg = TaxGroup("t1", "s", "GST", 10.0, false, now, now)
        taxGroupsFlow.value = listOf(tg)
        viewModel.onIntent(SettingsIntent.LoadTaxGroups)
        viewModel.onIntent(SettingsIntent.RequestDeleteTaxGroup(tg))
        viewModel.onIntent(SettingsIntent.ConfirmDeleteTaxGroup)

        assertTrue(deletedTaxGroupIds.contains("t1"))
    }

    // ── Printer settings tests ────────────────────────────────────────────────

    @Test
    fun `UpdatePrinterType reflects in state`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.onIntent(SettingsIntent.UpdatePrinterType(PrinterType.TCP))
        assertEquals(PrinterType.TCP, viewModel.state.value.printer.printerType)
    }

    @Test
    fun `UpdateHeaderLine mutates correct index`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.onIntent(SettingsIntent.UpdateHeaderLine(2, "Thank you!"))
        assertEquals("Thank you!", viewModel.state.value.printer.headerLines[2])
    }

    @Test
    fun `TestPrint invokes PrintTestPageUseCase`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.onIntent(SettingsIntent.TestPrint)
        assertTrue(testPrintCalled)
    }

    // ── Appearance settings tests ─────────────────────────────────────────────

    @Test
    fun `UpdateThemeMode persists to repository and updates state`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.onIntent(SettingsIntent.UpdateThemeMode(ThemeMode.DARK))

        assertEquals(ThemeMode.DARK, viewModel.state.value.appearance.themeMode)
        assertEquals("DARK", store[SettingsKeys.THEME_MODE])
    }

    @Test
    fun `LoadAppearance restores persisted theme mode`() = runTest(UnconfinedTestDispatcher()) {
        store[SettingsKeys.THEME_MODE] = "LIGHT"
        viewModel.onIntent(SettingsIntent.LoadAppearance)
        assertEquals(ThemeMode.LIGHT, viewModel.state.value.appearance.themeMode)
    }

    // ── Backup tests ──────────────────────────────────────────────────────────

    @Test
    fun `TriggerBackup updates lastBackupAt timestamp`() = runTest(UnconfinedTestDispatcher()) {
        assertNull(viewModel.state.value.backup.lastBackupAt)
        viewModel.onIntent(SettingsIntent.TriggerBackup)
        assertTrue(viewModel.state.value.backup.lastBackupAt != null)
        assertTrue(store.containsKey(SettingsKeys.LAST_BACKUP_TIMESTAMP))
    }

    @Test
    fun `CancelRestore clears confirmRestore flag`() = runTest(UnconfinedTestDispatcher()) {
        viewModel.onIntent(SettingsIntent.RestoreSelected("/some/path.db"))
        assertTrue(viewModel.state.value.backup.confirmRestore)
        viewModel.onIntent(SettingsIntent.CancelRestore)
        assertFalse(viewModel.state.value.backup.confirmRestore)
    }
}
