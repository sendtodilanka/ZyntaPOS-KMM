package com.zyntasolutions.zyntapos.debug

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.AuthException
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.NetworkException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.debug.actions.AuthActionHandler
import com.zyntasolutions.zyntapos.debug.actions.DatabaseActionHandler
import com.zyntasolutions.zyntapos.debug.actions.DiagnosticsActionHandler
import com.zyntasolutions.zyntapos.debug.actions.NetworkActionHandler
import com.zyntasolutions.zyntapos.debug.actions.SeedActionHandler
import com.zyntasolutions.zyntapos.debug.model.SeedProfile
import com.zyntasolutions.zyntapos.debug.model.UserSummary
import com.zyntasolutions.zyntapos.debug.mvi.DebugEffect
import com.zyntasolutions.zyntapos.debug.mvi.DebugIntent
import com.zyntasolutions.zyntapos.debug.mvi.DebugTab
import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.domain.model.AuditEventType
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.AuditRepository
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository
import com.zyntasolutions.zyntapos.seed.SeedRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [DebugViewModel].
 *
 * Uses hand-rolled fakes for all five action handlers and [AuditRepository].
 * The [StandardTestDispatcher] is installed as Main so coroutines launched by
 * [DebugViewModel.dispatch] are fully drained via [advanceUntilIdle].
 *
 * Conventions:
 *  - Each `@Test` arranges fakes, dispatches one or more intents, calls
 *    [advanceUntilIdle], then asserts on [DebugViewModel.state].
 *  - Effect tests use Turbine's [Flow.test] collector started before dispatch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DebugViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val now = Clock.System.now()

    // ── Helpers ────────────────────────────────────────────────────────────────

    private val adminUser = User(
        id = "u-admin",
        name = "Admin",
        email = "admin@zyntapos.com",
        role = Role.ADMIN,
        storeId = "store-1",
        isActive = true,
        pinHash = "salt:hash",
        createdAt = now,
        updatedAt = now,
    )

    private val defaultSeedSummary = SeedRunner.SeedSummary(
        results = listOf(
            SeedRunner.SeedResult("Category", inserted = 8, skipped = 0, failed = 0),
            SeedRunner.SeedResult("Product", inserted = 25, skipped = 0, failed = 0),
        ),
        errors = emptyList(),
    )

    private val sampleSyncOp = SyncOperation(
        id = "op-1",
        entityType = "product",
        entityId = "e-1",
        operation = SyncOperation.Operation.INSERT,
        payload = "{}",
        createdAt = now,
    )

    private val sampleAuditEntry = AuditEntry(
        id = "a-1",
        eventType = AuditEventType.SETTINGS_CHANGED,
        userId = "u-admin",
        deviceId = "debug-console",
        payload = "{}",
        success = true,
        createdAt = now,
    )

    // ── Fake action handlers ───────────────────────────────────────────────────

    private inner class FakeSeedActionHandler(
        var result: Result<SeedRunner.SeedSummary> = Result.Success(defaultSeedSummary),
    ) : SeedActionHandler {
        var lastProfile: SeedProfile? = null
        override suspend fun runProfile(profile: SeedProfile): Result<SeedRunner.SeedSummary> {
            lastProfile = profile
            return result
        }
    }

    private inner class FakeDatabaseActionHandler(
        var tableCountsResult: Result<Map<String, Long>> = Result.Success(mapOf("products" to 10L, "users" to 2L)),
        var resetResult: Result<Unit> = Result.Success(Unit),
        var vacuumResult: Result<Unit> = Result.Success(Unit),
        var fileSizeResult: Result<Long> = Result.Success(0L),
    ) : DatabaseActionHandler {
        override suspend fun getTableRowCounts(): Result<Map<String, Long>> = tableCountsResult
        override suspend fun resetDatabase(): Result<Unit> = resetResult
        override suspend fun vacuum(): Result<Unit> = vacuumResult
        override suspend fun getDatabaseFileSizeKb(): Result<Long> = fileSizeResult
    }

    private inner class FakeAuthActionHandler(
        var currentUser: User? = null,
        var allUsersResult: Result<List<UserSummary>> = Result.Success(emptyList()),
        var createAdminResult: Result<Unit> = Result.Success(Unit),
        var deactivateResult: Result<Unit> = Result.Success(Unit),
        var clearSessionResult: Result<Unit> = Result.Success(Unit),
    ) : AuthActionHandler {
        override suspend fun getAllUsers(): Result<List<UserSummary>> = allUsersResult
        override suspend fun createAdminUser(email: String, name: String, plainPassword: String): Result<Unit> = createAdminResult
        override suspend fun deactivateUser(userId: String): Result<Unit> = deactivateResult
        override suspend fun clearSession(): Result<Unit> = clearSessionResult
        override suspend fun getCurrentUser(): User? = currentUser
    }

    private inner class FakeNetworkActionHandler(
        var pendingOpsResult: Result<List<SyncOperation>> = Result.Success(emptyList()),
        var clearQueueResult: Result<Unit> = Result.Success(Unit),
        var forceSyncResult: Result<Unit> = Result.Success(Unit),
    ) : NetworkActionHandler {
        override suspend fun getPendingOperations(): Result<List<SyncOperation>> = pendingOpsResult
        override suspend fun clearSyncQueue(): Result<Unit> = clearQueueResult
        override suspend fun forceSyncNow(): Result<Unit> = forceSyncResult
        override fun setOfflineMode(forced: Boolean) { /* no-op in tests */ }
    }

    private inner class FakeDiagnosticsActionHandler(
        var auditLogResult: Result<List<AuditEntry>> = Result.Success(emptyList()),
    ) : DiagnosticsActionHandler {
        var stubbedLogLines: List<String> = emptyList()
        override suspend fun getAuditLog(): Result<List<AuditEntry>> = auditLogResult
        override fun getLogLines(): List<String> = stubbedLogLines
    }

    private inner class FakeAuditRepository : AuditRepository {
        val insertedEntries = mutableListOf<AuditEntry>()
        val auditFlow = MutableStateFlow<List<AuditEntry>>(emptyList())

        override suspend fun insert(entry: AuditEntry) { insertedEntries += entry }
        override fun observeAll(): Flow<List<AuditEntry>> = auditFlow
        override fun observeByUserId(userId: String): Flow<List<AuditEntry>> = auditFlow
    }

    private inner class FakeSettingsRepository : SettingsRepository {
        private val store = mutableMapOf<String, String>()
        override suspend fun get(key: String): String? = store[key]
        override suspend fun set(key: String, value: String): Result<Unit> {
            store[key] = value; return Result.Success(Unit)
        }
        override suspend fun getAll(): Map<String, String> = store.toMap()
        override fun observe(key: String): Flow<String?> = MutableStateFlow(store[key])
    }

    // ── Subject under test ─────────────────────────────────────────────────────

    private lateinit var fakeSeed: FakeSeedActionHandler
    private lateinit var fakeDatabase: FakeDatabaseActionHandler
    private lateinit var fakeAuth: FakeAuthActionHandler
    private lateinit var fakeNetwork: FakeNetworkActionHandler
    private lateinit var fakeDiagnostics: FakeDiagnosticsActionHandler
    private lateinit var fakeAudit: FakeAuditRepository
    private lateinit var fakeSettings: FakeSettingsRepository
    private lateinit var viewModel: DebugViewModel

    private fun buildViewModel(): DebugViewModel = DebugViewModel(
        seedHandler         = fakeSeed,
        databaseHandler     = fakeDatabase,
        authHandler         = fakeAuth,
        networkHandler      = fakeNetwork,
        diagnosticsHandler  = fakeDiagnostics,
        auditRepository     = fakeAudit,
        settingsRepository  = fakeSettings,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSeed        = FakeSeedActionHandler()
        fakeDatabase    = FakeDatabaseActionHandler()
        fakeAuth        = FakeAuthActionHandler()
        fakeNetwork     = FakeNetworkActionHandler()
        fakeDiagnostics = FakeDiagnosticsActionHandler()
        fakeAudit       = FakeAuditRepository()
        fakeSettings    = FakeSettingsRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial state has Seeds as active tab and isLoading false`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()
        assertEquals(DebugTab.Seeds, viewModel.state.value.activeTab)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `initial state has null actionResult and null pendingDestructiveIntent`() = runTest {
        viewModel = buildViewModel()
        advanceUntilIdle()
        assertNull(viewModel.state.value.actionResult)
        assertNull(viewModel.state.value.pendingDestructiveIntent)
    }

    // ── LoadInitialData ────────────────────────────────────────────────────────

    @Test
    fun `LoadInitialData with logged-in user populates all auth fields`() = runTest {
        fakeAuth.currentUser = adminUser
        viewModel = buildViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("admin@zyntapos.com", state.currentUserEmail)
        assertEquals("ADMIN", state.currentUserRole)
        assertEquals("u-admin", state.currentUserId)
        assertTrue(state.hasPinConfigured)
    }

    @Test
    fun `LoadInitialData with no session leaves auth fields null`() = runTest {
        fakeAuth.currentUser = null
        viewModel = buildViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertNull(state.currentUserEmail)
        assertNull(state.currentUserRole)
        assertNull(state.currentUserId)
        assertFalse(state.hasPinConfigured)
    }

    @Test
    fun `LoadInitialData with user without pin sets hasPinConfigured false`() = runTest {
        fakeAuth.currentUser = adminUser.copy(pinHash = null)
        viewModel = buildViewModel()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.hasPinConfigured)
    }

    // ── SelectTab ─────────────────────────────────────────────────────────────

    @Test
    fun `SelectTab Database changes activeTab and triggers LoadTableCounts`() = runTest {
        fakeAuth.currentUser = null
        fakeDatabase.tableCountsResult = Result.Success(mapOf("orders" to 5L))
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.SelectTab(DebugTab.Database))
        advanceUntilIdle()

        assertEquals(DebugTab.Database, viewModel.state.value.activeTab)
        assertEquals(mapOf("orders" to 5L), viewModel.state.value.tableRowCounts)
    }

    @Test
    fun `SelectTab Auth changes activeTab and triggers LoadUsers`() = runTest {
        fakeAuth.currentUser = null
        val summaries = listOf(UserSummary("u1", "Alice", "alice@test.com", "CASHIER", true))
        fakeAuth.allUsersResult = Result.Success(summaries)
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.SelectTab(DebugTab.Auth))
        advanceUntilIdle()

        assertEquals(DebugTab.Auth, viewModel.state.value.activeTab)
        assertEquals(summaries, viewModel.state.value.allUsers)
    }

    @Test
    fun `SelectTab Network changes activeTab and triggers LoadSyncQueueDepth`() = runTest {
        fakeAuth.currentUser = null
        fakeNetwork.pendingOpsResult = Result.Success(listOf(sampleSyncOp, sampleSyncOp.copy(id = "op-2")))
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.SelectTab(DebugTab.Network))
        advanceUntilIdle()

        assertEquals(DebugTab.Network, viewModel.state.value.activeTab)
        assertEquals(2, viewModel.state.value.pendingOpsCount)
    }

    @Test
    fun `SelectTab Diagnostics changes activeTab and triggers LoadAuditLog and LoadSystemHealth`() = runTest {
        fakeAuth.currentUser = null
        fakeDiagnostics.auditLogResult = Result.Success(listOf(sampleAuditEntry))
        fakeDatabase.fileSizeResult = Result.Success(512L)
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.SelectTab(DebugTab.Diagnostics))
        advanceUntilIdle()

        assertEquals(DebugTab.Diagnostics, viewModel.state.value.activeTab)
        assertEquals(listOf(sampleAuditEntry), viewModel.state.value.auditEntries)
        assertEquals(512L, viewModel.state.value.dbFileSizeKb)
    }

    @Test
    fun `SelectTab Seeds does not trigger any lazy load`() = runTest {
        fakeAuth.currentUser = null
        viewModel = buildViewModel()
        advanceUntilIdle()

        // Navigate away then back to Seeds
        viewModel.dispatch(DebugIntent.SelectTab(DebugTab.Database))
        advanceUntilIdle()
        viewModel.dispatch(DebugIntent.SelectTab(DebugTab.Seeds))
        advanceUntilIdle()

        assertEquals(DebugTab.Seeds, viewModel.state.value.activeTab)
    }

    // ── DismissActionResult ────────────────────────────────────────────────────

    @Test
    fun `DismissActionResult clears actionResult from state`() = runTest {
        fakeAuth.currentUser = null
        viewModel = buildViewModel()
        advanceUntilIdle()

        // Trigger an action result first
        viewModel.dispatch(DebugIntent.ForceTokenExpiry)
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.actionResult)

        viewModel.dispatch(DebugIntent.DismissActionResult)
        advanceUntilIdle()
        assertNull(viewModel.state.value.actionResult)
    }

    // ── DismissDestructiveDialog ───────────────────────────────────────────────

    @Test
    fun `DismissDestructiveDialog clears pendingDestructiveIntent`() = runTest {
        fakeAuth.currentUser = null
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.RequestResetDatabase)
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.pendingDestructiveIntent)

        viewModel.dispatch(DebugIntent.DismissDestructiveDialog)
        advanceUntilIdle()
        assertNull(viewModel.state.value.pendingDestructiveIntent)
    }

    // ── RunSeedProfile ─────────────────────────────────────────────────────────

    @Test
    fun `RunSeedProfile success updates seedSummary and shows result message`() = runTest {
        fakeAuth.currentUser = adminUser
        fakeSeed.result = Result.Success(defaultSeedSummary)
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.RunSeedProfile)
        advanceUntilIdle()

        assertEquals(defaultSeedSummary, viewModel.state.value.seedSummary)
        assertNotNull(viewModel.state.value.actionResult)
        assertFalse(viewModel.state.value.actionResult!!.isError)
        assertTrue(viewModel.state.value.actionResult!!.message.contains("33"))  // 8+25 inserted
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `RunSeedProfile success writes audit entry`() = runTest {
        fakeAuth.currentUser = adminUser
        fakeSeed.result = Result.Success(defaultSeedSummary)
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.RunSeedProfile)
        advanceUntilIdle()

        assertTrue(fakeAudit.insertedEntries.any { it.payload.contains("seed_run") })
    }

    @Test
    fun `RunSeedProfile error shows error message and clears isLoading`() = runTest {
        fakeAuth.currentUser = null
        fakeSeed.result = Result.Error(DatabaseException("Seed run failed: constraint violation"))
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.RunSeedProfile)
        advanceUntilIdle()

        val result = viewModel.state.value.actionResult
        assertNotNull(result)
        assertTrue(result.isError)
        assertTrue(result.message.contains("Seed failed"))
        assertFalse(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.seedSummary)
    }

    @Test
    fun `RunSeedProfile uses currently selectedProfile`() = runTest {
        fakeAuth.currentUser = null
        fakeSeed.result = Result.Success(defaultSeedSummary)
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.SelectSeedProfile(SeedProfile.Restaurant))
        advanceUntilIdle()
        viewModel.dispatch(DebugIntent.RunSeedProfile)
        advanceUntilIdle()

        assertEquals(SeedProfile.Restaurant, fakeSeed.lastProfile)
    }

    @Test
    fun `RunSeedProfile with failed records shows error banner`() = runTest {
        fakeAuth.currentUser = null
        val partialFailSummary = SeedRunner.SeedSummary(
            results = listOf(SeedRunner.SeedResult("Product", inserted = 10, skipped = 0, failed = 3)),
            errors = listOf("FK constraint on product-99"),
        )
        fakeSeed.result = Result.Success(partialFailSummary)
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.RunSeedProfile)
        advanceUntilIdle()

        val actionResult = viewModel.state.value.actionResult
        assertNotNull(actionResult)
        assertTrue(actionResult.isError)
    }

    // ── SetAdminCredentials ────────────────────────────────────────────────────

    @Test
    fun `SetAdminCredentials success shows success message`() = runTest {
        fakeAuth.currentUser = adminUser
        fakeAuth.createAdminResult = Result.Success(Unit)
        fakeAuth.allUsersResult = Result.Success(emptyList())
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.SetAdminCredentials("new@test.com", "Secure123!"))
        advanceUntilIdle()

        val result = viewModel.state.value.actionResult
        assertNotNull(result)
        assertFalse(result.isError)
        assertTrue(result.message.contains("new@test.com"))
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `SetAdminCredentials never stores password in state`() = runTest {
        fakeAuth.currentUser = null
        fakeAuth.createAdminResult = Result.Success(Unit)
        fakeAuth.allUsersResult = Result.Success(emptyList())
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.SetAdminCredentials("admin@test.com", "SuperSecret99!"))
        advanceUntilIdle()

        // Inspect the entire state: no field should contain the password string
        val stateString = viewModel.state.value.toString()
        assertFalse(stateString.contains("SuperSecret99!"),
            "Password must never be retained in DebugState")
    }

    @Test
    fun `SetAdminCredentials hides admin setup dialog after call`() = runTest {
        fakeAuth.currentUser = null
        fakeAuth.createAdminResult = Result.Success(Unit)
        fakeAuth.allUsersResult = Result.Success(emptyList())
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.ShowAdminSetupDialog)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.showAdminSetupDialog)

        viewModel.dispatch(DebugIntent.SetAdminCredentials("admin@test.com", "pass"))
        advanceUntilIdle()
        assertFalse(viewModel.state.value.showAdminSetupDialog)
    }

    @Test
    fun `SetAdminCredentials error shows error message`() = runTest {
        fakeAuth.currentUser = null
        fakeAuth.createAdminResult = Result.Error(DatabaseException("Duplicate email"))
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.SetAdminCredentials("dup@test.com", "pass"))
        advanceUntilIdle()

        val result = viewModel.state.value.actionResult
        assertNotNull(result)
        assertTrue(result.isError)
        assertTrue(result.message.contains("Admin setup failed"))
    }

    // ── Database: RequestResetDatabase / ConfirmResetDatabase ─────────────────

    @Test
    fun `RequestResetDatabase sets pendingDestructiveIntent to ConfirmResetDatabase`() = runTest {
        fakeAuth.currentUser = null
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.RequestResetDatabase)
        advanceUntilIdle()

        assertEquals(DebugIntent.ConfirmResetDatabase, viewModel.state.value.pendingDestructiveIntent)
    }

    @Test
    fun `ConfirmResetDatabase success clears table counts and seedSummary`() = runTest {
        fakeAuth.currentUser = adminUser
        fakeDatabase.tableCountsResult = Result.Success(mapOf("users" to 3L))
        fakeDatabase.resetResult = Result.Success(Unit)
        viewModel = buildViewModel()
        advanceUntilIdle()

        // Load table counts first
        viewModel.dispatch(DebugIntent.SelectTab(DebugTab.Database))
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.ConfirmResetDatabase)
        advanceUntilIdle()

        assertEquals(emptyMap<String, Long>(), viewModel.state.value.tableRowCounts)
        assertNull(viewModel.state.value.seedSummary)
        assertNull(viewModel.state.value.pendingDestructiveIntent)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `ConfirmResetDatabase success sends NavigateUp effect`() = runTest {
        fakeAuth.currentUser = adminUser
        fakeDatabase.resetResult = Result.Success(Unit)
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(DebugIntent.ConfirmResetDatabase)
            advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is DebugEffect.NavigateUp,
                "Expected NavigateUp but got $effect")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ConfirmResetDatabase error shows error message`() = runTest {
        fakeAuth.currentUser = null
        fakeDatabase.resetResult = Result.Error(DatabaseException("DB locked"))
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.ConfirmResetDatabase)
        advanceUntilIdle()

        val result = viewModel.state.value.actionResult
        assertNotNull(result)
        assertTrue(result.isError)
        assertTrue(result.message.contains("Reset failed"))
        assertFalse(viewModel.state.value.isLoading)
    }

    // ── Auth: RequestClearSession / ConfirmClearSession ────────────────────────

    @Test
    fun `RequestClearSession sets pendingDestructiveIntent to ConfirmClearSession`() = runTest {
        fakeAuth.currentUser = null
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.RequestClearSession)
        advanceUntilIdle()

        assertEquals(DebugIntent.ConfirmClearSession, viewModel.state.value.pendingDestructiveIntent)
    }

    @Test
    fun `ConfirmClearSession success sends NavigateUp effect`() = runTest {
        fakeAuth.currentUser = adminUser
        fakeAuth.clearSessionResult = Result.Success(Unit)
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(DebugIntent.ConfirmClearSession)
            advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is DebugEffect.NavigateUp)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ConfirmClearSession success clears currentUserEmail and currentUserRole`() = runTest {
        fakeAuth.currentUser = adminUser
        fakeAuth.clearSessionResult = Result.Success(Unit)
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.ConfirmClearSession)
        advanceUntilIdle()

        assertNull(viewModel.state.value.currentUserEmail)
        assertNull(viewModel.state.value.currentUserRole)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `ConfirmClearSession error shows error message`() = runTest {
        fakeAuth.currentUser = null
        fakeAuth.clearSessionResult = Result.Error(AuthException("Logout failed"))
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.ConfirmClearSession)
        advanceUntilIdle()

        val result = viewModel.state.value.actionResult
        assertNotNull(result)
        assertTrue(result.isError)
        assertTrue(result.message.contains("Session clear failed"))
    }

    // ── UI/UX: SetThemeOverride / SetFontScale ─────────────────────────────────

    @Test
    fun `SetThemeOverride DARK updates themeOverride in state`() = runTest {
        fakeAuth.currentUser = null
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.SetThemeOverride("DARK"))
        advanceUntilIdle()

        assertEquals("DARK", viewModel.state.value.themeOverride)
    }

    @Test
    fun `SetThemeOverride LIGHT updates themeOverride in state`() = runTest {
        fakeAuth.currentUser = null
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.SetThemeOverride("LIGHT"))
        advanceUntilIdle()

        assertEquals("LIGHT", viewModel.state.value.themeOverride)
    }

    @Test
    fun `SetThemeOverride null clears themeOverride`() = runTest {
        fakeAuth.currentUser = null
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.SetThemeOverride("DARK"))
        advanceUntilIdle()
        viewModel.dispatch(DebugIntent.SetThemeOverride(null))
        advanceUntilIdle()

        assertNull(viewModel.state.value.themeOverride)
    }

    @Test
    fun `SetFontScale updates fontScaleOverride in state`() = runTest {
        fakeAuth.currentUser = null
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.SetFontScale(1.25f))
        advanceUntilIdle()

        assertEquals(1.25f, viewModel.state.value.fontScaleOverride)
    }

    @Test
    fun `SetFontScale with multiple values retains the last one`() = runTest {
        fakeAuth.currentUser = null
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.SetFontScale(0.85f))
        advanceUntilIdle()
        viewModel.dispatch(DebugIntent.SetFontScale(1.5f))
        advanceUntilIdle()

        assertEquals(1.5f, viewModel.state.value.fontScaleOverride)
    }

    // ── Database: LoadTableCounts / VacuumDatabase ─────────────────────────────

    @Test
    fun `LoadTableCounts success updates tableRowCounts`() = runTest {
        fakeAuth.currentUser = null
        fakeDatabase.tableCountsResult = Result.Success(mapOf("orders" to 42L, "products" to 100L))
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.LoadTableCounts)
        advanceUntilIdle()

        assertEquals(42L, viewModel.state.value.tableRowCounts["orders"])
        assertEquals(100L, viewModel.state.value.tableRowCounts["products"])
    }

    @Test
    fun `LoadTableCounts error shows error message`() = runTest {
        fakeAuth.currentUser = null
        fakeDatabase.tableCountsResult = Result.Error(DatabaseException("Query failed"))
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.LoadTableCounts)
        advanceUntilIdle()

        val result = viewModel.state.value.actionResult
        assertNotNull(result)
        assertTrue(result.isError)
        assertTrue(result.message.contains("Could not read table counts"))
    }

    @Test
    fun `VacuumDatabase success shows success message and clears isLoading`() = runTest {
        fakeAuth.currentUser = adminUser
        fakeDatabase.vacuumResult = Result.Success(Unit)
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.VacuumDatabase)
        advanceUntilIdle()

        val result = viewModel.state.value.actionResult
        assertNotNull(result)
        assertFalse(result.isError)
        assertTrue(result.message.contains("VACUUM"))
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `VacuumDatabase error shows error message`() = runTest {
        fakeAuth.currentUser = null
        fakeDatabase.vacuumResult = Result.Error(DatabaseException("VACUUM failed: locked"))
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.VacuumDatabase)
        advanceUntilIdle()

        val result = viewModel.state.value.actionResult
        assertNotNull(result)
        assertTrue(result.isError)
        assertTrue(result.message.contains("VACUUM failed"))
    }

    // ── Auth: LoadUsers / DeactivateUser ───────────────────────────────────────

    @Test
    fun `LoadUsers success updates allUsers list`() = runTest {
        fakeAuth.currentUser = null
        val users = listOf(
            UserSummary("u1", "Bob", "bob@test.com", "CASHIER", true),
            UserSummary("u2", "Carol", "carol@test.com", "ADMIN", true),
        )
        fakeAuth.allUsersResult = Result.Success(users)
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.LoadUsers)
        advanceUntilIdle()

        assertEquals(users, viewModel.state.value.allUsers)
    }

    @Test
    fun `LoadUsers error shows error message`() = runTest {
        fakeAuth.currentUser = null
        fakeAuth.allUsersResult = Result.Error(DatabaseException("No access"))
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.LoadUsers)
        advanceUntilIdle()

        val result = viewModel.state.value.actionResult
        assertNotNull(result)
        assertTrue(result.isError)
        assertTrue(result.message.contains("Failed to load users"))
    }

    @Test
    fun `DeactivateUser success shows success message and reloads users`() = runTest {
        fakeAuth.currentUser = adminUser
        fakeAuth.deactivateResult = Result.Success(Unit)
        fakeAuth.allUsersResult = Result.Success(emptyList())
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.DeactivateUser("u-cashier"))
        advanceUntilIdle()

        val result = viewModel.state.value.actionResult
        assertNotNull(result)
        assertFalse(result.isError)
        assertTrue(result.message.contains("deactivated"))
    }

    @Test
    fun `DeactivateUser error shows error message`() = runTest {
        fakeAuth.currentUser = null
        fakeAuth.deactivateResult = Result.Error(DatabaseException("User not found"))
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.DeactivateUser("u-missing"))
        advanceUntilIdle()

        val result = viewModel.state.value.actionResult
        assertNotNull(result)
        assertTrue(result.isError)
        assertTrue(result.message.contains("Deactivation failed"))
    }

    // ── Auth: ForceTokenExpiry ─────────────────────────────────────────────────

    @Test
    fun `ForceTokenExpiry shows info message without destructive confirm`() = runTest {
        fakeAuth.currentUser = null
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.ForceTokenExpiry)
        advanceUntilIdle()

        val result = viewModel.state.value.actionResult
        assertNotNull(result)
        // ForceTokenExpiry is informational — not a destructive error
        assertFalse(result.isError)
        assertNull(viewModel.state.value.pendingDestructiveIntent)
    }

    // ── Network: SetOfflineModeForced / ForceSyncNow / LoadSyncQueueDepth ──────

    @Test
    fun `SetOfflineModeForced true updates isOfflineModeForced`() = runTest {
        fakeAuth.currentUser = null
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.SetOfflineModeForced(true))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isOfflineModeForced)
    }

    @Test
    fun `SetOfflineModeForced false clears isOfflineModeForced`() = runTest {
        fakeAuth.currentUser = null
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.SetOfflineModeForced(true))
        advanceUntilIdle()
        viewModel.dispatch(DebugIntent.SetOfflineModeForced(false))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isOfflineModeForced)
    }

    @Test
    fun `ForceSyncNow success shows success message and refreshes queue depth`() = runTest {
        fakeAuth.currentUser = adminUser
        fakeNetwork.forceSyncResult = Result.Success(Unit)
        fakeNetwork.pendingOpsResult = Result.Success(emptyList())
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.ForceSyncNow)
        advanceUntilIdle()

        val result = viewModel.state.value.actionResult
        assertNotNull(result)
        assertFalse(result.isError)
        assertTrue(result.message.contains("Sync triggered"))
        assertEquals(0, viewModel.state.value.pendingOpsCount)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `ForceSyncNow error shows error message and clears isLoading`() = runTest {
        fakeAuth.currentUser = null
        fakeNetwork.forceSyncResult = Result.Error(NetworkException("Connection refused"))
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.ForceSyncNow)
        advanceUntilIdle()

        val result = viewModel.state.value.actionResult
        assertNotNull(result)
        assertTrue(result.isError)
        assertTrue(result.message.contains("Sync failed"))
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `LoadSyncQueueDepth success updates pendingOpsCount`() = runTest {
        fakeAuth.currentUser = null
        fakeNetwork.pendingOpsResult = Result.Success(
            listOf(sampleSyncOp, sampleSyncOp.copy(id = "op-2"), sampleSyncOp.copy(id = "op-3"))
        )
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.LoadSyncQueueDepth)
        advanceUntilIdle()

        assertEquals(3, viewModel.state.value.pendingOpsCount)
    }

    // ── Network: RequestClearSyncQueue / ConfirmClearSyncQueue ────────────────

    @Test
    fun `RequestClearSyncQueue sets pendingDestructiveIntent to ConfirmClearSyncQueue`() = runTest {
        fakeAuth.currentUser = null
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.RequestClearSyncQueue)
        advanceUntilIdle()

        assertEquals(DebugIntent.ConfirmClearSyncQueue, viewModel.state.value.pendingDestructiveIntent)
    }

    @Test
    fun `ConfirmClearSyncQueue success shows success message and resets pendingOpsCount`() = runTest {
        fakeAuth.currentUser = adminUser
        fakeNetwork.clearQueueResult = Result.Success(Unit)
        fakeNetwork.pendingOpsResult = Result.Success(listOf(sampleSyncOp))
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.LoadSyncQueueDepth)
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.pendingOpsCount)

        viewModel.dispatch(DebugIntent.ConfirmClearSyncQueue)
        advanceUntilIdle()

        assertEquals(0, viewModel.state.value.pendingOpsCount)
        assertNull(viewModel.state.value.pendingDestructiveIntent)
        val result = viewModel.state.value.actionResult
        assertNotNull(result)
        assertFalse(result.isError)
        assertTrue(result.message.contains("Sync queue cleared"))
    }

    @Test
    fun `ConfirmClearSyncQueue error shows error message`() = runTest {
        fakeAuth.currentUser = null
        fakeNetwork.clearQueueResult = Result.Error(NetworkException("DB error"))
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.ConfirmClearSyncQueue)
        advanceUntilIdle()

        val result = viewModel.state.value.actionResult
        assertNotNull(result)
        assertTrue(result.isError)
        assertTrue(result.message.contains("Clear sync queue failed"))
    }

    // ── Diagnostics: LoadAuditLog / LoadSystemHealth ───────────────────────────

    @Test
    fun `LoadAuditLog success updates auditEntries`() = runTest {
        fakeAuth.currentUser = null
        fakeDiagnostics.auditLogResult = Result.Success(listOf(sampleAuditEntry))
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.LoadAuditLog)
        advanceUntilIdle()

        assertEquals(listOf(sampleAuditEntry), viewModel.state.value.auditEntries)
    }

    @Test
    fun `LoadAuditLog also populates logLines from diagnosticsHandler`() = runTest {
        fakeAuth.currentUser = null
        fakeDiagnostics.auditLogResult = Result.Success(emptyList())
        fakeDiagnostics.stubbedLogLines = listOf("INFO: app started", "DEBUG: DB initialized")
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.LoadAuditLog)
        advanceUntilIdle()

        assertEquals(listOf("INFO: app started", "DEBUG: DB initialized"), viewModel.state.value.logLines)
    }

    @Test
    fun `LoadAuditLog error shows error message`() = runTest {
        fakeAuth.currentUser = null
        fakeDiagnostics.auditLogResult = Result.Error(DatabaseException("No data"))
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.LoadAuditLog)
        advanceUntilIdle()

        val result = viewModel.state.value.actionResult
        assertNotNull(result)
        assertTrue(result.isError)
        assertTrue(result.message.contains("Failed to load audit log"))
    }

    @Test
    fun `LoadSystemHealth success updates dbFileSizeKb`() = runTest {
        fakeAuth.currentUser = null
        fakeDatabase.fileSizeResult = Result.Success(1024L)
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.LoadSystemHealth)
        advanceUntilIdle()

        assertEquals(1024L, viewModel.state.value.dbFileSizeKb)
    }

    @Test
    fun `LoadSystemHealth error leaves dbFileSizeKb unchanged and shows no banner`() = runTest {
        fakeAuth.currentUser = null
        fakeDatabase.fileSizeResult = Result.Error(DatabaseException("File not accessible"))
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.LoadSystemHealth)
        advanceUntilIdle()

        // Non-critical: no banner is shown; initial default value is preserved
        assertEquals(0L, viewModel.state.value.dbFileSizeKb)
        assertNull(viewModel.state.value.actionResult)
    }

    // ── ShowAdminSetupDialog / DismissAdminSetupDialog ─────────────────────────

    @Test
    fun `ShowAdminSetupDialog sets showAdminSetupDialog true`() = runTest {
        fakeAuth.currentUser = null
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.ShowAdminSetupDialog)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showAdminSetupDialog)
    }

    @Test
    fun `DismissAdminSetupDialog sets showAdminSetupDialog false`() = runTest {
        fakeAuth.currentUser = null
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.ShowAdminSetupDialog)
        advanceUntilIdle()
        viewModel.dispatch(DebugIntent.DismissAdminSetupDialog)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.showAdminSetupDialog)
    }

    // ── SelectSeedProfile ──────────────────────────────────────────────────────

    @Test
    fun `SelectSeedProfile updates selectedProfile in state`() = runTest {
        fakeAuth.currentUser = null
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.SelectSeedProfile(SeedProfile.Retail))
        advanceUntilIdle()

        assertEquals(SeedProfile.Retail, viewModel.state.value.selectedProfile)
    }

    // ── RequestClearSeedData ───────────────────────────────────────────────────

    @Test
    fun `RequestClearSeedData sets pendingDestructiveIntent to ConfirmClearSeedData`() = runTest {
        fakeAuth.currentUser = null
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.RequestClearSeedData)
        advanceUntilIdle()

        assertEquals(DebugIntent.ConfirmClearSeedData, viewModel.state.value.pendingDestructiveIntent)
    }

    @Test
    fun `ConfirmClearSeedData shows informational message directing user to full reset`() = runTest {
        fakeAuth.currentUser = null
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.ConfirmClearSeedData)
        advanceUntilIdle()

        val result = viewModel.state.value.actionResult
        assertNotNull(result)
        assertFalse(result.isError)
        assertTrue(result.message.contains("Reset Database"))
    }

    // ── ExportDatabase / ExportLogs ────────────────────────────────────────────

    @Test
    fun `ExportDatabase shows informational message`() = runTest {
        fakeAuth.currentUser = null
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.ExportDatabase)
        advanceUntilIdle()

        val result = viewModel.state.value.actionResult
        assertNotNull(result)
        assertFalse(result.isError)
        assertTrue(result.message.contains("export"))
    }

    @Test
    fun `ExportLogs shows informational message`() = runTest {
        fakeAuth.currentUser = null
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.ExportLogs)
        advanceUntilIdle()

        val result = viewModel.state.value.actionResult
        assertNotNull(result)
        assertFalse(result.isError)
        assertTrue(result.message.contains("log"))
    }

    // ── RequestClearTable ──────────────────────────────────────────────────────

    @Test
    fun `RequestClearTable sets pendingDestructiveIntent to ConfirmClearTable`() = runTest {
        fakeAuth.currentUser = null
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.RequestClearTable("orders"))
        advanceUntilIdle()

        val pending = viewModel.state.value.pendingDestructiveIntent
        assertTrue(pending is DebugIntent.ConfirmClearTable)
        assertEquals("orders", (pending as DebugIntent.ConfirmClearTable).tableName)
    }

    @Test
    fun `ConfirmClearTable shows not-yet-supported error`() = runTest {
        fakeAuth.currentUser = null
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.ConfirmClearTable("orders"))
        advanceUntilIdle()

        val result = viewModel.state.value.actionResult
        assertNotNull(result)
        assertTrue(result.isError)
        assertTrue(result.message.contains("not yet supported"))
    }

    // ── Audit trail ───────────────────────────────────────────────────────────

    @Test
    fun `VacuumDatabase success writes audit entry`() = runTest {
        fakeAuth.currentUser = adminUser
        fakeDatabase.vacuumResult = Result.Success(Unit)
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.VacuumDatabase)
        advanceUntilIdle()

        assertTrue(fakeAudit.insertedEntries.any { it.payload.contains("db_vacuum") })
    }

    @Test
    fun `DeactivateUser success writes audit entry`() = runTest {
        fakeAuth.currentUser = adminUser
        fakeAuth.deactivateResult = Result.Success(Unit)
        fakeAuth.allUsersResult = Result.Success(emptyList())
        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.dispatch(DebugIntent.DeactivateUser("u-target"))
        advanceUntilIdle()

        assertTrue(fakeAudit.insertedEntries.any { it.payload.contains("user_deactivated") })
    }
}
