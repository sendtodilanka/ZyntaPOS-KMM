package com.zyntasolutions.zyntapos.data.sync

import com.zyntasolutions.zyntapos.core.result.NetworkException
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.data.repository.CategoryRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.ConflictLogRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.CustomerRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.OrderRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.ProductRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.PricingRuleRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.RegionalTaxOverrideRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.UserStoreAccessRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.StockRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.MasterProductRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.StoreProductOverrideRepositoryImpl
import com.zyntasolutions.zyntapos.data.repository.SupplierRepositoryImpl
import com.zyntasolutions.zyntapos.domain.port.SecureStorageKeys
import com.zyntasolutions.zyntapos.domain.port.SecureStoragePort
import com.zyntasolutions.zyntapos.data.remote.api.ApiService
import com.zyntasolutions.zyntapos.data.remote.dto.AuthRefreshResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.AuthRequestDto
import com.zyntasolutions.zyntapos.data.remote.dto.AuthResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.ProductDto
import com.zyntasolutions.zyntapos.data.remote.dto.LicenseActivateRequestDto
import com.zyntasolutions.zyntapos.data.remote.dto.LicenseActivateResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.LicenseHeartbeatRequestDto
import com.zyntasolutions.zyntapos.data.remote.dto.LicenseHeartbeatResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.PublicKeyResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.SyncOperationDto
import com.zyntasolutions.zyntapos.data.remote.dto.SyncPullResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.SyncResponseDto
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ZyntaPOS — SyncEngine Integration Tests (jvmTest)
 *
 * Step 3.4.7 | :shared:data | jvmTest
 *
 * Tests the SyncEngine push/pull cycle against a real in-memory SQLDelight database.
 * The [ApiService] is replaced by a configurable fake; [NetworkMonitor] is the real
 * Desktop (JVM) `actual` implementation instantiated without calling [start], so no
 * background polling occurs.
 *
 * NOTE: [SyncEngine.runOnce] does NOT gate on [NetworkMonitor.isConnected] — that check
 * lives in [SyncEngine.startPeriodicSync]. Tests call [runOnce] directly, bypassing it.
 *
 * Coverage:
 *  A. Empty queue → no API calls; SyncResult.Success(pushed=0, pulled=0)
 *  B. PENDING ops pushed → accepted marked SYNCED
 *  C. Rejected ops → retry_count incremented; permanently FAILED at MAX_RETRIES
 *  D. Network failure → SyncResult.Failure; queue preserved
 *  E. LAST_SYNC_TS persisted in prefs after successful cycle
 *  F. Pull delta → SyncResult.Success.pulledCount reflects server ops
 */

// ─────────────────────────────────────────────────────────────────────────────
// Fake ApiService implementations
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Configurable [ApiService] fake — all push/pull responses are controlled by lambdas.
 */
private class FakeApiService(
    private val pushResponse: () -> SyncResponseDto,
    private val pullResponse: () -> SyncPullResponseDto = {
        SyncPullResponseDto(
            operations      = emptyList(),
            serverTimestamp = Clock.System.now().toEpochMilliseconds(),
        )
    },
) : ApiService {

    var pushCallCount  = 0
    var pullCallCount  = 0
    var lastPushedOps: List<SyncOperationDto> = emptyList()

    override suspend fun login(request: AuthRequestDto): AuthResponseDto =
        error("Not used in sync tests")

    override suspend fun refreshToken(refreshToken: String): AuthRefreshResponseDto =
        error("Not used in sync tests")

    override suspend fun getProducts(): List<ProductDto> =
        error("Not used in sync tests")

    override suspend fun pushOperations(operations: List<SyncOperationDto>): SyncResponseDto {
        pushCallCount++
        lastPushedOps = operations
        return pushResponse()
    }

    override suspend fun pullOperations(lastSyncTimestamp: Long): SyncPullResponseDto {
        pullCallCount++
        return pullResponse()
    }

    override suspend fun fetchPublicKey(): PublicKeyResponseDto = error("Not used in sync tests")
    override suspend fun activateLicense(request: LicenseActivateRequestDto): LicenseActivateResponseDto = error("Not used in sync tests")
    override suspend fun licenseHeartbeat(request: LicenseHeartbeatRequestDto): LicenseHeartbeatResponseDto = error("Not used in sync tests")
    override suspend fun grantDiagnosticConsent(sessionId: String, grantedAtMs: Long) = error("Not used in sync tests")
    override suspend fun revokeDiagnosticConsent(sessionId: String) = error("Not used in sync tests")
}

/** [ApiService] that always throws [NetworkException] on any network call. */
private class OfflineApiService : ApiService {
    override suspend fun login(request: AuthRequestDto): AuthResponseDto = error("unused")
    override suspend fun refreshToken(refreshToken: String): AuthRefreshResponseDto = error("unused")
    override suspend fun getProducts(): List<ProductDto> = error("unused")
    override suspend fun pushOperations(operations: List<SyncOperationDto>): SyncResponseDto =
        throw NetworkException(message = "Simulated network failure", statusCode = null)
    override suspend fun pullOperations(lastSyncTimestamp: Long): SyncPullResponseDto =
        throw NetworkException(message = "Simulated network failure", statusCode = null)
    override suspend fun fetchPublicKey(): PublicKeyResponseDto = error("unused")
    override suspend fun activateLicense(request: LicenseActivateRequestDto): LicenseActivateResponseDto = error("unused")
    override suspend fun licenseHeartbeat(request: LicenseHeartbeatRequestDto): LicenseHeartbeatResponseDto = error("unused")
    override suspend fun grantDiagnosticConsent(sessionId: String, grantedAtMs: Long) = error("unused")
    override suspend fun revokeDiagnosticConsent(sessionId: String) = error("unused")
}

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

class SyncEngineIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var prefs: SecureStoragePort

    /** Real Desktop NetworkMonitor — never started, so no background polling in tests. */
    private val networkMonitor = NetworkMonitor()

    private val now get() = Clock.System.now().toEpochMilliseconds()

    @BeforeTest
    fun setup() {
        db    = createTestDatabase()
        prefs = InMemorySecurePreferences()
    }

    private val testDeviceId = "test-device-001"

    private fun engine(api: ApiService): SyncEngine {
        val syncEnqueuer = SyncEnqueuer(db)
        return SyncEngine(
            db                    = db,
            api                   = api,
            prefs                 = prefs,
            networkMonitor        = networkMonitor,
            productRepository     = ProductRepositoryImpl(db, syncEnqueuer),
            orderRepository       = OrderRepositoryImpl(db, syncEnqueuer),
            customerRepository    = CustomerRepositoryImpl(db, syncEnqueuer),
            categoryRepository    = CategoryRepositoryImpl(db, syncEnqueuer),
            supplierRepository    = SupplierRepositoryImpl(db, syncEnqueuer),
            stockRepository       = StockRepositoryImpl(db, syncEnqueuer),
            masterProductRepository = MasterProductRepositoryImpl(db),
            storeProductOverrideRepository = StoreProductOverrideRepositoryImpl(db, syncEnqueuer),
            pricingRuleRepository = PricingRuleRepositoryImpl(db),
            regionalTaxOverrideRepository = RegionalTaxOverrideRepositoryImpl(db, syncEnqueuer),
            userStoreAccessRepository = UserStoreAccessRepositoryImpl(db, syncEnqueuer),
            conflictResolver      = ConflictResolver(localDeviceId = testDeviceId),
            conflictLogRepository = ConflictLogRepositoryImpl(db = db),
            queueMaintenance      = SyncQueueMaintenance(db = db),
            storeId               = "",
        )
    }

    private fun enqueue(id: String, entityType: String = "ORDER") {
        db.sync_queueQueries.enqueueOperation(
            id          = id,
            entity_type = entityType,
            entity_id   = "entity-$id",
            operation   = "CREATE",
            payload     = """{"id":"entity-$id"}""",
            created_at  = now,
            store_id    = "",
        )
    }

    // ── A. Empty queue — no push call; Success(0,0) ───────────────────────────

    @Test
    fun runOnce_empty_queue_skips_push_and_returns_success_zero_counts() = runTest {
        val api = FakeApiService(pushResponse = {
            SyncResponseDto(accepted = emptyList(), serverTimestamp = now)
        })
        val eng = engine(api)

        eng.runOnce()

        assertEquals(0, api.pushCallCount, "pushOperations should NOT be called with an empty queue")
        assertTrue(eng.lastSyncResult.value is SyncResult.Success)
        val result = eng.lastSyncResult.value as SyncResult.Success
        assertEquals(0, result.pushedCount)
        assertEquals(0, result.pulledCount)
    }

    // ── B. PENDING ops pushed; accepted → SYNCED ─────────────────────────────

    @Test
    fun runOnce_pending_ops_are_pushed_and_accepted_marked_synced() = runTest {
        enqueue("op-1")
        enqueue("op-2")

        val api = FakeApiService(pushResponse = {
            SyncResponseDto(
                accepted        = listOf("op-1", "op-2"),
                rejected        = emptyList(),
                deltaOperations = emptyList(),
                serverTimestamp = now,
            )
        })
        val eng = engine(api)

        eng.runOnce()

        assertEquals(1, api.pushCallCount)
        assertEquals(2, api.lastPushedOps.size)

        val remaining = db.sync_queueQueries.getEligibleOperations(store_id = "", batch_size = 10).executeAsList()
        assertEquals(0, remaining.size, "All ops should be SYNCED after full acceptance")

        assertTrue(eng.lastSyncResult.value is SyncResult.Success)
        assertEquals(2, (eng.lastSyncResult.value as SyncResult.Success).pushedCount)
    }

    // ── C. Rejected → retry_count incremented; permanently FAILED at MAX ──────

    @Test
    fun runOnce_rejected_op_increments_retry_count_and_permanently_fails_at_max_retries() = runTest {
        enqueue("op-fail")

        // Pre-set retry_count to 4 (one below the 5-retry max)
        repeat(4) { db.sync_queueQueries.markFailed(now, "op-fail") }

        val api = FakeApiService(pushResponse = {
            SyncResponseDto(
                accepted        = emptyList(),
                rejected        = listOf("op-fail"),
                deltaOperations = emptyList(),
                serverTimestamp = now,
            )
        })
        val eng = engine(api)

        eng.runOnce()

        // After 5th rejection the engine calls markPermanentlyFailed → retry_count = 5
        val eligible = db.sync_queueQueries.getEligibleOperations(store_id = "", batch_size = 10).executeAsList()
        assertEquals(0, eligible.size, "Permanently failed op should no longer be eligible")

        val failedCount = db.sync_queueQueries.getFailedCount().executeAsOne()
        assertEquals(1L, failedCount)
    }

    // ── D. Network failure → SyncResult.Failure; queue preserved ─────────────

    @Test
    fun runOnce_network_failure_captures_failure_result_and_queue_is_preserved() = runTest {
        enqueue("op-1")
        val eng = engine(OfflineApiService())

        eng.runOnce()

        assertTrue(eng.lastSyncResult.value is SyncResult.Failure)

        // Queue must still contain the operation (either PENDING or SYNCING/FAILED)
        val totalInQueue = db.sync_queueQueries.getEligibleOperations(store_id = "", batch_size = 10).executeAsList().size +
            db.sync_queueQueries.getFailedCount().executeAsOne()
        assertTrue(totalInQueue >= 1L, "Queue should still contain op-1 after a network failure")
    }

    // ── E. LAST_SYNC_TS persisted after successful cycle ─────────────────────

    @Test
    fun runOnce_persists_last_sync_timestamp_after_successful_cycle() = runTest {
        val api = FakeApiService(
            pushResponse = { SyncResponseDto(accepted = emptyList(), serverTimestamp = now) },
            pullResponse = { SyncPullResponseDto(operations = emptyList(), serverTimestamp = now + 100) },
        )
        val eng = engine(api)

        val tsBefore = prefs.get(SecureStorageKeys.KEY_LAST_SYNC_TS)?.toLongOrNull() ?: 0L

        eng.runOnce()

        val tsAfter = prefs.get(SecureStorageKeys.KEY_LAST_SYNC_TS)?.toLongOrNull() ?: 0L
        assertTrue(tsAfter > tsBefore, "LAST_SYNC_TS should advance after a successful sync cycle")
    }

    // ── F. Pull delta → pulledCount in SyncResult.Success ────────────────────

    @Test
    fun runOnce_pull_with_server_delta_returns_correct_pulled_count() = runTest {
        val serverOps = listOf(
            SyncOperationDto(
                id = "srv-1", entityType = "PRODUCT", entityId = "p-99",
                operation = "UPDATE", payload = """{"id":"p-99","stock_qty":50}""", createdAt = now,
            ),
            SyncOperationDto(
                id = "srv-2", entityType = "ORDER", entityId = "ord-1",
                operation = "CREATE", payload = """{"id":"ord-1"}""", createdAt = now,
            ),
        )
        val api = FakeApiService(
            pushResponse = { SyncResponseDto(accepted = emptyList(), serverTimestamp = now) },
            pullResponse = { SyncPullResponseDto(operations = serverOps, serverTimestamp = now + 1) },
        )
        val eng = engine(api)

        eng.runOnce()

        val result = eng.lastSyncResult.value
        assertTrue(result is SyncResult.Success)
        assertEquals(2, (result as SyncResult.Success).pulledCount)
    }

    // ── G. Pull conflict: remote wins → local discarded ───────────────

    @Test
    fun runOnce_pullConflict_remoteWins_localDiscarded() = runTest {
        // Enqueue a local PENDING op for product p-1.
        // Push rejects it (server returns conflict delta in pull instead).
        enqueue("local-op-1", entityType = "product")

        // Server sends a newer delta for the same entity during pull
        val serverOps = listOf(
            SyncOperationDto(
                id = "remote-op-1", entityType = "product", entityId = "entity-local-op-1",
                operation = "UPDATE",
                payload = """{"id":"entity-local-op-1","name":"ServerName"}""",
                createdAt = now + 5000, // 5s newer than local
            ),
        )
        val api = FakeApiService(
            pushResponse = {
                // Reject the local op so it stays PENDING for conflict detection during pull
                SyncResponseDto(
                    accepted = emptyList(),
                    rejected = listOf("local-op-1"),
                    serverTimestamp = now,
                )
            },
            pullResponse = { SyncPullResponseDto(operations = serverOps, serverTimestamp = now + 5001) },
        )
        val eng = engine(api)
        eng.runOnce()

        val result = eng.lastSyncResult.value
        assertTrue(result is SyncResult.Success)
        assertEquals(1, (result as SyncResult.Success).conflictCount)
    }

    // ── H. Pull conflict: local wins → remote skipped ─────────────────

    @Test
    fun runOnce_pullConflict_localWins_remoteSkipped() = runTest {
        // Enqueue a local PENDING op with a future timestamp.
        // Push rejects it so it stays PENDING for conflict detection during pull.
        db.sync_queueQueries.enqueueOperation(
            id = "local-newer",
            entity_type = "product",
            entity_id = "p-conflict",
            operation = "UPDATE",
            payload = """{"id":"p-conflict","name":"LocalNewer"}""",
            created_at = now + 10000, // very recent
            store_id = "",
        )

        // Server sends an older delta for the same entity
        val serverOps = listOf(
            SyncOperationDto(
                id = "remote-older", entityType = "product", entityId = "p-conflict",
                operation = "UPDATE",
                payload = """{"id":"p-conflict","name":"RemoteOlder"}""",
                createdAt = now - 5000, // older than local
            ),
        )
        val api = FakeApiService(
            pushResponse = {
                SyncResponseDto(
                    accepted = emptyList(),
                    rejected = listOf("local-newer"),
                    serverTimestamp = now,
                )
            },
            pullResponse = { SyncPullResponseDto(operations = serverOps, serverTimestamp = now + 1) },
        )
        val eng = engine(api)
        eng.runOnce()

        // Local op should still be in queue (PENDING after rejection + conflict resolution kept it)
        val pending = db.sync_queueQueries.getPendingByEntity("product", "p-conflict", "", "").executeAsOneOrNull()
        assertTrue(pending != null, "Local winning op should remain PENDING in queue")
        assertEquals("local-newer", pending.id)

        val result = eng.lastSyncResult.value
        assertTrue(result is SyncResult.Success)
        assertEquals(1, (result as SyncResult.Success).conflictCount)
    }

    // ── I. No conflict → applied normally (regression guard) ──────────

    @Test
    fun runOnce_pullNoConflict_appliedNormally() = runTest {
        // No local pending ops — server delta should be applied without conflict
        val serverOps = listOf(
            SyncOperationDto(
                id = "srv-clean", entityType = "PRODUCT", entityId = "p-clean",
                operation = "UPDATE", payload = """{"id":"p-clean","name":"Clean"}""",
                createdAt = now,
            ),
        )
        val api = FakeApiService(
            pushResponse = { SyncResponseDto(accepted = emptyList(), serverTimestamp = now) },
            pullResponse = { SyncPullResponseDto(operations = serverOps, serverTimestamp = now + 1) },
        )
        val eng = engine(api)
        eng.runOnce()

        val result = eng.lastSyncResult.value
        assertTrue(result is SyncResult.Success)
        assertEquals(0, (result as SyncResult.Success).conflictCount)
        assertEquals(1, result.pulledCount)
    }

    // ── J. conflictCount tracking across multiple conflicts ───────────

    @Test
    fun runOnce_conflictCount_tracked() = runTest {
        // Two local pending ops for different entities
        db.sync_queueQueries.enqueueOperation(
            id = "local-a", entity_type = "product", entity_id = "p-a",
            operation = "UPDATE", payload = """{"id":"p-a"}""", created_at = now, store_id = "",
        )
        db.sync_queueQueries.enqueueOperation(
            id = "local-b", entity_type = "category", entity_id = "c-b",
            operation = "UPDATE", payload = """{"id":"c-b"}""", created_at = now, store_id = "",
        )

        // Server sends conflicting deltas for both
        val serverOps = listOf(
            SyncOperationDto(
                id = "remote-a", entityType = "product", entityId = "p-a",
                operation = "UPDATE", payload = """{"id":"p-a","name":"Server"}""",
                createdAt = now + 1000,
            ),
            SyncOperationDto(
                id = "remote-b", entityType = "category", entityId = "c-b",
                operation = "UPDATE", payload = """{"id":"c-b","name":"ServerCat"}""",
                createdAt = now + 1000,
            ),
        )
        val api = FakeApiService(
            pushResponse = {
                // Reject local ops so they stay PENDING for conflict detection during pull
                SyncResponseDto(
                    accepted = emptyList(),
                    rejected = listOf("local-a", "local-b"),
                    serverTimestamp = now,
                )
            },
            pullResponse = { SyncPullResponseDto(operations = serverOps, serverTimestamp = now + 1001) },
        )
        val eng = engine(api)
        eng.runOnce()

        val result = eng.lastSyncResult.value
        assertTrue(result is SyncResult.Success)
        assertEquals(2, (result as SyncResult.Success).conflictCount)
    }
}
