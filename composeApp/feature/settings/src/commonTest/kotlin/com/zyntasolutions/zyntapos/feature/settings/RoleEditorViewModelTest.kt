package com.zyntasolutions.zyntapos.feature.settings

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.CustomRole
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.PermissionGroup
import com.zyntasolutions.zyntapos.domain.model.PermissionItem
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.repository.RoleRepository
import com.zyntasolutions.zyntapos.domain.usecase.rbac.GetPermissionsTreeUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rbac.SaveCustomRoleUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RoleEditorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(testDispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    // ── Inline RoleRepository fake (kept local — :composeApp:feature:settings
    // doesn't depend on :shared:domain's commonTest source set).
    private class FakeRoleRepo : RoleRepository {
        private val _customRoles = MutableStateFlow<List<CustomRole>>(emptyList())
        var shouldFail = false
        val createdRoles = mutableListOf<CustomRole>()
        val updatedRoles = mutableListOf<CustomRole>()

        fun seed(vararg roles: CustomRole) { _customRoles.value = roles.toList() }

        override fun getAllCustomRoles(): Flow<List<CustomRole>> = _customRoles
        override suspend fun getCustomRoleById(id: String): Result<CustomRole> =
            _customRoles.value.firstOrNull { it.id == id }
                ?.let { Result.Success(it) }
                ?: Result.Error(DatabaseException("Custom role '$id' not found."))
        override suspend fun createCustomRole(role: CustomRole): Result<Unit> {
            if (shouldFail) return Result.Error(DatabaseException("Forced failure"))
            createdRoles.add(role)
            _customRoles.value = _customRoles.value + role
            return Result.Success(Unit)
        }
        override suspend fun updateCustomRole(role: CustomRole): Result<Unit> {
            if (shouldFail) return Result.Error(DatabaseException("Forced failure"))
            updatedRoles.add(role)
            _customRoles.value = _customRoles.value.map { if (it.id == role.id) role else it }
            return Result.Success(Unit)
        }
        override suspend fun deleteCustomRole(id: String): Result<Unit> {
            _customRoles.value = _customRoles.value.filter { it.id != id }
            return Result.Success(Unit)
        }
        override suspend fun getBuiltInRolePermissions(role: Role): Set<Permission>? = null
        override suspend fun setBuiltInRolePermissions(role: Role, permissions: Set<Permission>): Result<Unit> = Result.Success(Unit)
        override suspend fun resetBuiltInRolePermissions(role: Role): Result<Unit> = Result.Success(Unit)
    }

    // ── Test fixtures ─────────────────────────────────────────────────────────

    private val twoGroupTree: List<PermissionGroup> = listOf(
        PermissionGroup(
            module = "POS",
            displayName = "POS",
            permissions = listOf(
                PermissionItem(Permission.PROCESS_SALE, "Process Sale", "Ring up sales."),
                PermissionItem(Permission.VOID_ORDER, "Void Order", "Cancel a completed order."),
            ),
        ),
        PermissionGroup(
            module = "REGISTER",
            displayName = "Register",
            permissions = listOf(
                PermissionItem(Permission.OPEN_REGISTER, "Open Register", "Open session."),
                PermissionItem(Permission.CLOSE_REGISTER, "Close Register", "Close session."),
            ),
        ),
    )

    private val stubTreeUseCase = GetPermissionsTreeUseCase { twoGroupTree }

    private fun newViewModel(
        repo: RoleRepository = FakeRoleRepo(),
    ): RoleEditorViewModel = RoleEditorViewModel(
        roleRepository = repo,
        getPermissionsTreeUseCase = stubTreeUseCase,
        saveCustomRoleUseCase = SaveCustomRoleUseCase(repo),
    )

    private fun seedRole(
        repo: FakeRoleRepo,
        id: String = "role-01",
        name: String = "Cashier",
        permissions: Set<Permission> = setOf(Permission.PROCESS_SALE),
    ) = repo.also {
        it.seed(
            CustomRole(
                id = id,
                name = name,
                description = "",
                permissions = permissions,
                createdAt = Instant.fromEpochSeconds(0),
                updatedAt = Instant.fromEpochSeconds(0),
            ),
        )
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    @Test
    fun `Load with null roleId resets form to empty create state`() = runTest(testDispatcher) {
        val vm = newViewModel()
        vm.dispatch(RoleEditorIntent.Load(null))
        advanceUntilIdle()

        val s = vm.state.value
        assertNull(s.roleId)
        assertEquals("", s.name)
        assertEquals(emptySet(), s.selected)
        assertEquals(twoGroupTree, s.tree)
        assertFalse(s.isLoading)
    }

    @Test
    fun `Load with existing roleId seeds name and selected from repository`() = runTest(testDispatcher) {
        val repo = seedRole(FakeRoleRepo(), permissions = setOf(Permission.PROCESS_SALE, Permission.VOID_ORDER))
        val vm = newViewModel(repo)
        vm.dispatch(RoleEditorIntent.Load("role-01"))
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("role-01", s.roleId)
        assertEquals("Cashier", s.name)
        assertEquals(setOf(Permission.PROCESS_SALE, Permission.VOID_ORDER), s.selected)
        assertFalse(s.isLoading)
    }

    @Test
    fun `Load with unknown roleId surfaces saveError but still loads tree`() = runTest(testDispatcher) {
        val vm = newViewModel(FakeRoleRepo())
        vm.dispatch(RoleEditorIntent.Load("missing"))
        advanceUntilIdle()

        val s = vm.state.value
        assertNotNull(s.saveError)
        assertEquals(twoGroupTree, s.tree)
    }

    // ── Permission toggling ──────────────────────────────────────────────────

    @Test
    fun `TogglePermission flips a single permission`() = runTest(testDispatcher) {
        val vm = newViewModel()
        vm.dispatch(RoleEditorIntent.Load(null))
        advanceUntilIdle()

        vm.dispatch(RoleEditorIntent.TogglePermission(Permission.PROCESS_SALE))
        advanceUntilIdle()
        assertEquals(setOf(Permission.PROCESS_SALE), vm.state.value.selected)

        vm.dispatch(RoleEditorIntent.TogglePermission(Permission.PROCESS_SALE))
        advanceUntilIdle()
        assertEquals(emptySet(), vm.state.value.selected)
    }

    @Test
    fun `ToggleGroup adds all permissions when none are selected`() = runTest(testDispatcher) {
        val vm = newViewModel()
        vm.dispatch(RoleEditorIntent.Load(null))
        advanceUntilIdle()

        vm.dispatch(RoleEditorIntent.ToggleGroup(twoGroupTree.first()))
        advanceUntilIdle()
        assertEquals(setOf(Permission.PROCESS_SALE, Permission.VOID_ORDER), vm.state.value.selected)
    }

    @Test
    fun `ToggleGroup removes all permissions when all are selected`() = runTest(testDispatcher) {
        val vm = newViewModel()
        vm.dispatch(RoleEditorIntent.Load(null))
        advanceUntilIdle()
        vm.dispatch(RoleEditorIntent.TogglePermission(Permission.PROCESS_SALE))
        vm.dispatch(RoleEditorIntent.TogglePermission(Permission.VOID_ORDER))
        advanceUntilIdle()
        assertEquals(setOf(Permission.PROCESS_SALE, Permission.VOID_ORDER), vm.state.value.selected)

        vm.dispatch(RoleEditorIntent.ToggleGroup(twoGroupTree.first()))
        advanceUntilIdle()
        assertEquals(emptySet(), vm.state.value.selected)
    }

    @Test
    fun `ToggleGroup promotes partial selection to fully selected`() = runTest(testDispatcher) {
        val vm = newViewModel()
        vm.dispatch(RoleEditorIntent.Load(null))
        advanceUntilIdle()
        vm.dispatch(RoleEditorIntent.TogglePermission(Permission.PROCESS_SALE)) // 1 of 2
        advanceUntilIdle()

        vm.dispatch(RoleEditorIntent.ToggleGroup(twoGroupTree.first()))
        advanceUntilIdle()
        assertEquals(setOf(Permission.PROCESS_SALE, Permission.VOID_ORDER), vm.state.value.selected)
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    @Test
    fun `Save with blank name sets nameError and does not persist`() = runTest(testDispatcher) {
        val repo = FakeRoleRepo()
        val vm = newViewModel(repo)
        vm.dispatch(RoleEditorIntent.Load(null))
        advanceUntilIdle()

        vm.dispatch(RoleEditorIntent.Save)
        advanceUntilIdle()

        assertEquals("Role name is required.", vm.state.value.nameError)
        assertTrue(repo.createdRoles.isEmpty())
    }

    @Test
    fun `Save creates a new custom role and emits Saved effect`() = runTest(UnconfinedTestDispatcher()) {
        val repo = FakeRoleRepo()
        val vm = newViewModel(repo)
        vm.dispatch(RoleEditorIntent.Load(null))
        vm.dispatch(RoleEditorIntent.UpdateName("New Cashier"))
        vm.dispatch(RoleEditorIntent.TogglePermission(Permission.PROCESS_SALE))

        vm.effects.test {
            vm.dispatch(RoleEditorIntent.Save)
            assertEquals(RoleEditorEffect.Saved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, repo.createdRoles.size)
        val created = repo.createdRoles.single()
        assertEquals("New Cashier", created.name)
        assertEquals(setOf(Permission.PROCESS_SALE), created.permissions)
        assertTrue(created.id.isNotBlank())
    }

    @Test
    fun `Save updates an existing role when roleId is set`() = runTest(UnconfinedTestDispatcher()) {
        val repo = seedRole(FakeRoleRepo())
        val vm = newViewModel(repo)
        vm.dispatch(RoleEditorIntent.Load("role-01"))

        vm.dispatch(RoleEditorIntent.UpdateName("Cashier (renamed)"))
        vm.effects.test {
            vm.dispatch(RoleEditorIntent.Save)
            assertEquals(RoleEditorEffect.Saved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, repo.updatedRoles.size)
        val updated = repo.updatedRoles.single()
        assertEquals("role-01", updated.id)
        assertEquals("Cashier (renamed)", updated.name)
    }

    @Test
    fun `Save persistence failure surfaces as saveError`() = runTest(testDispatcher) {
        val repo = FakeRoleRepo().apply { shouldFail = true }
        val vm = newViewModel(repo)
        vm.dispatch(RoleEditorIntent.Load(null))
        vm.dispatch(RoleEditorIntent.UpdateName("Anything"))
        advanceUntilIdle()

        vm.dispatch(RoleEditorIntent.Save)
        advanceUntilIdle()

        assertNotNull(vm.state.value.saveError)
        assertFalse(vm.state.value.isSaving)
    }
}
