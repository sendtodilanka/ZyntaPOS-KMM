package com.zyntasolutions.zyntapos.data.sync

import com.zyntasolutions.zyntapos.core.result.NetworkException
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.domain.port.SecureStorageKeys
import com.zyntasolutions.zyntapos.domain.port.SecureStoragePort
import com.zyntasolutions.zyntapos.data.remote.api.ApiService
import com.zyntasolutions.zyntapos.data.remote.dto.AuthRefreshResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.AuthRequestDto
import com.zyntasolutions.zyntapos.data.remote.dto.AuthResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.ProductDto
import com.zyntasolutions.zyntapos.data.remote.dto.SyncOperationDto
import com.zyntasolutions.zyntapos.data.remote.dto.SyncPullResponseDto
import com.zyntasolutions.zyntapos.data.remote.dto.SyncResponseDto
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
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

    private fun engine(api: ApiService) = SyncEngine(
        db             = db,
        api            = api,
        prefs          = prefs,
        networkMonitor = networkMonitor,
    )

    private fun enqueue(id: String, entityType: String = "ORDER") {
        db.sync_queueQueries.enqueueOperation(
            id          = id,
            entity_type = entityType,
            entity_id   = "entity-$id",
            operation   = "CREATE",
            payload     = """{"id":"entity-$id"}""",
            created_at  = now,
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

        val remaining = db.sync_queueQueries.getEligibleOperations(10).executeAsList()
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
        val eligible = db.sync_queueQueries.getEligibleOperations(10).executeAsList()
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
        val totalInQueue = db.sync_queueQueries.getEligibleOperations(10).executeAsList().size +
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
}
