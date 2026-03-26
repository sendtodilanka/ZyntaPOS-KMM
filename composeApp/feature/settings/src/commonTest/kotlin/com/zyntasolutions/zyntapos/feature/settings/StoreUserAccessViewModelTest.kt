package com.zyntasolutions.zyntapos.feature.settings

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.model.UserStoreAccess
import com.zyntasolutions.zyntapos.domain.repository.UserRepository
import com.zyntasolutions.zyntapos.domain.repository.UserStoreAccessRepository
import com.zyntasolutions.zyntapos.domain.usecase.auth.GrantStoreAccessUseCase
import com.zyntasolutions.zyntapos.domain.usecase.auth.RevokeStoreAccessUseCase
import com.zyntasolutions.zyntapos.feature.settings.screen.StoreUserAccessIntent
import com.zyntasolutions.zyntapos.feature.settings.screen.StoreUserAccessViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// StoreUserAccessViewModelTest — C3.2 user-store assignment management.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class StoreUserAccessViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // ── Fake data ─────────────────────────────────────────────────────────────

    private val now = Instant.fromEpochMilliseconds(0L)

    private val userAlice = User(
        id = "user-alice",
        name = "Alice",
        email = "alice@store.com",
        role = Role.CASHIER,
        storeId = "store-1",
        isActive = true,
        createdAt = now,
        updatedAt = now,
    )

    private val userBob = User(
        id = "user-bob",
        name = "Bob",
        email = "bob@store.com",
        role = Role.STORE_MANAGER,
        storeId = "store-2",
        isActive = true,
        createdAt = now,
        updatedAt = now,
    )

    private val accessEntry = UserStoreAccess(
        id = "access-1",
        userId = "user-alice",
        storeId = "store-1",
        roleAtStore = null,
        isActive = true,
        grantedBy = null,
        createdAt = now,
        updatedAt = now,
    )

    // ── Mutable state flows ────────────────────────────────────────────────────

    private val usersFlow = MutableStateFlow<List<User>>(listOf(userAlice, userBob))
    private val accessFlow = MutableStateFlow<List<UserStoreAccess>>(emptyList())

    private var grantResult: Result<UserStoreAccess> = Result.Success(accessEntry)
    private var revokeResult: Result<Unit> = Result.Success(Unit)
    private var hasAccessResult: Boolean = false

    // ── Fake repositories ─────────────────────────────────────────────────────

    private val fakeUserRepo = object : UserRepository {
        override fun getAll(storeId: String?): Flow<List<User>> = usersFlow
        override suspend fun getById(id: String): Result<User> =
            usersFlow.value.find { it.id == id }?.let { Result.Success(it) }
                ?: Result.Error(ValidationException("User not found"))
        override suspend fun create(user: User, plainPassword: String): Result<Unit> = Result.Success(Unit)
        override suspend fun update(user: User): Result<Unit> = Result.Success(Unit)
        override suspend fun updatePassword(userId: String, newPlainPassword: String): Result<Unit> = Result.Success(Unit)
        override suspend fun deactivate(userId: String): Result<Unit> = Result.Success(Unit)
        override suspend fun getSystemAdmin(): Result<User?> = Result.Success(null)
        override suspend fun adminExists(): Result<Boolean> = Result.Success(false)
        override suspend fun transferSystemAdmin(fromUserId: String, toUserId: String): Result<Unit> = Result.Success(Unit)
        override suspend fun getQuickSwitchCandidates(storeId: String): Result<List<com.zyntasolutions.zyntapos.domain.model.QuickSwitchCandidate>> = Result.Success(emptyList())
    }

    private val fakeAccessRepo = object : UserStoreAccessRepository {
        override fun getAccessibleStores(userId: String): Flow<List<UserStoreAccess>> = accessFlow
        override fun getUsersForStore(storeId: String): Flow<List<UserStoreAccess>> = accessFlow
        override suspend fun getById(id: String): Result<UserStoreAccess> = Result.Success(accessEntry)
        override suspend fun getByUserAndStore(userId: String, storeId: String): Result<UserStoreAccess?> = Result.Success(null)
        override suspend fun grantAccess(access: UserStoreAccess): Result<Unit> =
            if (grantResult is Result.Success) Result.Success(Unit) else Result.Error((grantResult as Result.Error).exception)
        override suspend fun revokeAccess(userId: String, storeId: String): Result<Unit> = revokeResult
        override suspend fun hasAccess(userId: String, storeId: String): Boolean = hasAccessResult
        override suspend fun upsertFromSync(access: UserStoreAccess): Result<Unit> = Result.Success(Unit)
    }

    private val grantUseCase = GrantStoreAccessUseCase(fakeUserRepo, fakeAccessRepo)
    private val revokeUseCase = RevokeStoreAccessUseCase(fakeAccessRepo)

    private fun makeViewModel() = StoreUserAccessViewModel(
        userRepository = fakeUserRepo,
        accessRepository = fakeAccessRepo,
        grantStoreAccessUseCase = grantUseCase,
        revokeStoreAccessUseCase = revokeUseCase,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `LoadForStore sets storeId and shows loading then populates users and access`() = runTest {
        accessFlow.value = listOf(accessEntry)
        val vm = makeViewModel()
        vm.state.test {
            val initial = awaitItem()
            assertEquals("", initial.storeId)

            vm.dispatch(StoreUserAccessIntent.LoadForStore("store-1"))
            advanceUntilIdle()

            val loaded = awaitItem()
            assertEquals("store-1", loaded.storeId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `OpenGrantForm sets showGrantForm true and clears form fields`() = runTest {
        val vm = makeViewModel()
        vm.dispatch(StoreUserAccessIntent.LoadForStore("store-1"))
        advanceUntilIdle()
        vm.dispatch(StoreUserAccessIntent.OpenGrantForm)
        advanceUntilIdle()

        assertTrue(vm.state.value.showGrantForm)
        assertEquals("", vm.state.value.formUserId)
        assertNull(vm.state.value.formRole)
    }

    @Test
    fun `DismissGrantForm clears form and hides it`() = runTest {
        val vm = makeViewModel()
        vm.dispatch(StoreUserAccessIntent.OpenGrantForm)
        advanceUntilIdle()
        vm.dispatch(StoreUserAccessIntent.UpdateFormUserId("user-alice"))
        advanceUntilIdle()
        vm.dispatch(StoreUserAccessIntent.DismissGrantForm)
        advanceUntilIdle()

        assertFalse(vm.state.value.showGrantForm)
        assertEquals("", vm.state.value.formUserId)
    }

    @Test
    fun `SubmitGrant with valid userId succeeds and hides form`() = runTest {
        hasAccessResult = false
        val vm = makeViewModel()
        vm.dispatch(StoreUserAccessIntent.LoadForStore("store-1"))
        advanceUntilIdle()
        vm.dispatch(StoreUserAccessIntent.OpenGrantForm)
        vm.dispatch(StoreUserAccessIntent.UpdateFormUserId("user-bob"))
        advanceUntilIdle()
        vm.dispatch(StoreUserAccessIntent.SubmitGrant)
        advanceUntilIdle()

        assertFalse(vm.state.value.showGrantForm)
    }

    @Test
    fun `SubmitGrant with blank userId emits ShowError effect`() = runTest {
        val vm = makeViewModel()
        vm.dispatch(StoreUserAccessIntent.LoadForStore("store-1"))
        vm.dispatch(StoreUserAccessIntent.OpenGrantForm)
        advanceUntilIdle()

        vm.effects.test {
            vm.dispatch(StoreUserAccessIntent.SubmitGrant)
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is com.zyntasolutions.zyntapos.feature.settings.screen.StoreUserAccessEffect.ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `RequestRevoke sets pendingRevokeEntry`() = runTest {
        val vm = makeViewModel()
        vm.dispatch(StoreUserAccessIntent.RequestRevoke(accessEntry))
        advanceUntilIdle()

        assertEquals(accessEntry, vm.state.value.pendingRevokeEntry)
    }

    @Test
    fun `CancelRevoke clears pendingRevokeEntry`() = runTest {
        val vm = makeViewModel()
        vm.dispatch(StoreUserAccessIntent.RequestRevoke(accessEntry))
        advanceUntilIdle()
        vm.dispatch(StoreUserAccessIntent.CancelRevoke)
        advanceUntilIdle()

        assertNull(vm.state.value.pendingRevokeEntry)
    }

    @Test
    fun `ConfirmRevoke with active access succeeds and clears pendingRevokeEntry`() = runTest {
        hasAccessResult = true
        revokeResult = Result.Success(Unit)
        val vm = makeViewModel()
        vm.dispatch(StoreUserAccessIntent.LoadForStore("store-1"))
        advanceUntilIdle()
        vm.dispatch(StoreUserAccessIntent.RequestRevoke(accessEntry))
        advanceUntilIdle()
        vm.dispatch(StoreUserAccessIntent.ConfirmRevoke)
        advanceUntilIdle()

        assertNull(vm.state.value.pendingRevokeEntry)
    }

    @Test
    fun `UpdateFormRole updates formRole in state`() = runTest {
        val vm = makeViewModel()
        vm.dispatch(StoreUserAccessIntent.UpdateFormRole(Role.STORE_MANAGER))
        advanceUntilIdle()

        assertEquals(Role.STORE_MANAGER, vm.state.value.formRole)
    }
}
