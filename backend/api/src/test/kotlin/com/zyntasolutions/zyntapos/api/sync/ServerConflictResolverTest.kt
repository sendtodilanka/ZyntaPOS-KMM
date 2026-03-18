package com.zyntasolutions.zyntapos.api.sync

import com.zyntasolutions.zyntapos.api.models.SyncOperation
import com.zyntasolutions.zyntapos.api.repository.ConflictLogRepository
import com.zyntasolutions.zyntapos.api.repository.SyncOperationSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for the ServerConflictResolver.
 *
 * Tests both the pure mergeProductFields() function and the full resolve() method
 * with a mocked ConflictLogRepository.
 */
class ServerConflictResolverTest {

    private val conflictLogRepo = mockk<ConflictLogRepository>(relaxed = true)
    private val resolver = ServerConflictResolver(conflictLogRepo)

    // ── mergeProductFields tests (pure function) ────────────────────────

    @Test
    fun `winner non-null field is preserved over loser non-null field`() {
        val winner = """{"name":"Winner Product","price":100.0}"""
        val loser  = """{"name":"Loser Product","price":50.0}"""
        val merged = resolver.mergeProductFields(winner, loser)
        assertContains(merged, "Winner Product")
    }

    @Test
    fun `null field in winner is filled from loser`() {
        val winner = """{"name":"Winner","imageUrl":null}"""
        val loser  = """{"name":"Loser","imageUrl":"http://example.com/img.png"}"""
        val merged = resolver.mergeProductFields(winner, loser)
        assertContains(merged, "http://example.com/img.png")
    }

    @Test
    fun `missing field in winner is filled from loser`() {
        val winner = """{"name":"Winner"}"""
        val loser  = """{"name":"Loser","sku":"SKU-001"}"""
        val merged = resolver.mergeProductFields(winner, loser)
        assertContains(merged, "SKU-001")
    }

    @Test
    fun `loser missing fields do not affect winner`() {
        val winner = """{"name":"Winner","price":100.0,"sku":"WIN-SKU"}"""
        val loser  = """{"name":"Loser"}"""
        val merged = resolver.mergeProductFields(winner, loser)
        assertContains(merged, "WIN-SKU")
        assertContains(merged, "100.0")
    }

    @Test
    fun `invalid JSON in winner falls back to winner unchanged`() {
        val winner = "valid-json-fallback"
        val loser  = """{"name":"Loser"}"""
        val result = resolver.mergeProductFields(winner, loser)
        assertEquals(winner, result)
    }

    // ── New mergeProductFields tests ────────────────────────────────────

    @Test
    fun `invalid JSON in loser falls back to winner unchanged`() {
        val winner = """{"name":"Winner","price":10.0}"""
        val loser = "not-json"
        val result = resolver.mergeProductFields(winner, loser)
        assertEquals(winner, result)
    }

    @Test
    fun `both JSONs invalid falls back to winner unchanged`() {
        val result = resolver.mergeProductFields("bad1", "bad2")
        assertEquals("bad1", result)
    }

    @Test
    fun `all winner fields null - filled from loser`() {
        val winner = """{"name":null,"price":null,"sku":null}"""
        val loser  = """{"name":"Product","price":25.0,"sku":"SKU-1"}"""
        val merged = resolver.mergeProductFields(winner, loser)
        assertContains(merged, "Product")
        assertContains(merged, "25.0")
        assertContains(merged, "SKU-1")
    }

    @Test
    fun `winner and loser have disjoint fields - all preserved`() {
        val winner = """{"name":"Widget","price":10.0}"""
        val loser  = """{"sku":"S-100","barcode":"123456"}"""
        val merged = resolver.mergeProductFields(winner, loser)
        assertContains(merged, "Widget")
        assertContains(merged, "10.0")
        assertContains(merged, "S-100")
        assertContains(merged, "123456")
    }

    @Test
    fun `empty winner JSON is filled entirely from loser`() {
        val winner = """{}"""
        val loser  = """{"name":"Loser","price":5.0}"""
        val merged = resolver.mergeProductFields(winner, loser)
        assertContains(merged, "Loser")
        assertContains(merged, "5.0")
    }

    @Test
    fun `empty loser JSON does not modify winner`() {
        val winner = """{"name":"Winner","price":42.0}"""
        val loser  = """{}"""
        val merged = resolver.mergeProductFields(winner, loser)
        assertContains(merged, "Winner")
        assertContains(merged, "42.0")
    }

    @Test
    fun `boolean fields are merged correctly`() {
        val winner = """{"name":"Widget","is_active":null}"""
        val loser  = """{"name":"Old","is_active":true}"""
        val merged = resolver.mergeProductFields(winner, loser)
        assertContains(merged, "true")
    }

    @Test
    fun `numeric fields preserved in winner over loser`() {
        val winner = """{"price":99.99,"stock_qty":50}"""
        val loser  = """{"price":49.99,"stock_qty":100}"""
        val merged = resolver.mergeProductFields(winner, loser)
        assertContains(merged, "99.99")
        assertContains(merged, "50")
    }

    @Test
    fun `merge preserves all original winner fields`() {
        val json = Json { ignoreUnknownKeys = true }
        val winner = """{"name":"A","price":10.0,"sku":"S1","description":"test"}"""
        val loser  = """{"name":"B","barcode":"BC"}"""
        val merged = resolver.mergeProductFields(winner, loser)
        val obj = json.parseToJsonElement(merged).jsonObject
        assertEquals("A", obj["name"]?.jsonPrimitive?.content)
        assertEquals("S1", obj["sku"]?.jsonPrimitive?.content)
        assertEquals("test", obj["description"]?.jsonPrimitive?.content)
        assertEquals("BC", obj["barcode"]?.jsonPrimitive?.content)
    }

    // ── Full resolve() method tests ─────────────────────────────────────

    private fun makeOp(
        id: String = "incoming-op",
        entityType: String = "PRODUCT",
        entityId: String = "entity-1",
        createdAt: Long = System.currentTimeMillis(),
        payload: String = """{"name":"Incoming","price":10.0}""",
    ) = SyncOperation(
        id = id, entityType = entityType, entityId = entityId,
        operation = "UPDATE", payload = payload, createdAt = createdAt,
    )

    private fun makeSnapshot(
        opId: String = "existing-op",
        deviceId: String = "dev-server",
        clientTimestamp: Long = System.currentTimeMillis(),
        payload: String = """{"name":"Existing","price":20.0}""",
        serverSeq: Long = 10L,
    ) = SyncOperationSnapshot(
        opId = opId, deviceId = deviceId,
        clientTimestamp = clientTimestamp, payload = payload,
        serverSeq = serverSeq, status = "ACCEPTED",
    )

    @Test
    fun `resolve - incoming wins when newer timestamp`() = runTest {
        val now = System.currentTimeMillis()
        val incoming = makeOp(createdAt = now)
        val existing = makeSnapshot(clientTimestamp = now - 5000)

        coEvery { conflictLogRepo.insert(any()) } returns "conflict-123"

        val result = resolver.resolve("store-1", incoming, existing, "dev-client")

        assertTrue(result.incomingWins)
        assertEquals(ServerConflictResolver.ResolutionStrategy.LWW_TIMESTAMP, result.strategy)
        assertEquals("conflict-123", result.conflictId)
        // Winner payload is the incoming payload
        assertContains(result.winnerPayload, "Incoming")
    }

    @Test
    fun `resolve - existing wins when newer timestamp`() = runTest {
        val now = System.currentTimeMillis()
        val incoming = makeOp(createdAt = now - 5000)
        val existing = makeSnapshot(clientTimestamp = now)

        coEvery { conflictLogRepo.insert(any()) } returns "conflict-456"

        val result = resolver.resolve("store-1", incoming, existing, "dev-client")

        assertFalse(result.incomingWins)
        // For PRODUCT entity, existing wins but field merge applies
        assertEquals(ServerConflictResolver.ResolutionStrategy.FIELD_MERGE, result.strategy)
    }

    @Test
    fun `resolve - device ID tiebreak when timestamps equal`() = runTest {
        val now = System.currentTimeMillis()
        val incoming = makeOp(createdAt = now)
        val existing = makeSnapshot(clientTimestamp = now, deviceId = "aaa-device")

        coEvery { conflictLogRepo.insert(any()) } returns "conflict-789"

        // "dev-client" > "aaa-device" lexicographically → incoming wins
        val result = resolver.resolve("store-1", incoming, existing, "dev-client")

        assertTrue(result.incomingWins)
        assertEquals(ServerConflictResolver.ResolutionStrategy.DEVICE_ID_TIEBREAK, result.strategy)
    }

    @Test
    fun `resolve - device ID tiebreak existing wins when deviceId is larger`() = runTest {
        val now = System.currentTimeMillis()
        val incoming = makeOp(createdAt = now)
        val existing = makeSnapshot(clientTimestamp = now, deviceId = "zzz-device")

        coEvery { conflictLogRepo.insert(any()) } returns "conflict-abc"

        // "dev-client" < "zzz-device" lexicographically → existing wins
        val result = resolver.resolve("store-1", incoming, existing, "dev-client")

        assertFalse(result.incomingWins)
    }

    @Test
    fun `resolve - PRODUCT field merge when existing wins fills null fields`() = runTest {
        val now = System.currentTimeMillis()
        val incoming = makeOp(
            createdAt = now - 5000,
            payload = """{"name":"Incoming","imageUrl":"http://img.com/pic.jpg"}""",
        )
        val existing = makeSnapshot(
            clientTimestamp = now,
            payload = """{"name":"Existing","imageUrl":null,"price":30.0}""",
        )

        coEvery { conflictLogRepo.insert(any()) } returns "conflict-merge"

        val result = resolver.resolve("store-1", incoming, existing, "dev-client")

        assertFalse(result.incomingWins)
        assertEquals(ServerConflictResolver.ResolutionStrategy.FIELD_MERGE, result.strategy)
        // Winner (existing) had null imageUrl, should be filled from loser (incoming)
        assertContains(result.winnerPayload, "http://img.com/pic.jpg")
        // Winner's name is preserved
        assertContains(result.winnerPayload, "Existing")
    }

    @Test
    fun `resolve - non-PRODUCT entity does NOT field merge`() = runTest {
        val now = System.currentTimeMillis()
        val incoming = makeOp(
            entityType = "CUSTOMER",
            createdAt = now - 5000,
            payload = """{"name":"Incoming Customer"}""",
        )
        val existing = makeSnapshot(
            clientTimestamp = now,
            payload = """{"name":"Existing Customer"}""",
        )

        coEvery { conflictLogRepo.insert(any()) } returns "conflict-no-merge"

        val result = resolver.resolve("store-1", incoming, existing, "dev-client")

        assertFalse(result.incomingWins)
        // CUSTOMER doesn't get field merge - strategy should be LWW_TIMESTAMP, not FIELD_MERGE
        assertEquals(ServerConflictResolver.ResolutionStrategy.LWW_TIMESTAMP, result.strategy)
        assertContains(result.winnerPayload, "Existing Customer")
    }

    @Test
    fun `resolve - PRODUCT incoming wins does NOT trigger field merge`() = runTest {
        val now = System.currentTimeMillis()
        val incoming = makeOp(createdAt = now) // newer
        val existing = makeSnapshot(clientTimestamp = now - 5000) // older

        coEvery { conflictLogRepo.insert(any()) } returns "conflict-no-merge-incoming"

        val result = resolver.resolve("store-1", incoming, existing, "dev-client")

        assertTrue(result.incomingWins)
        // Field merge only applies when existing wins - not when incoming wins
        assertEquals(ServerConflictResolver.ResolutionStrategy.LWW_TIMESTAMP, result.strategy)
    }

    @Test
    fun `resolve - conflict log entry is persisted`() = runTest {
        val now = System.currentTimeMillis()
        val incoming = makeOp(createdAt = now)
        val existing = makeSnapshot(clientTimestamp = now - 1000)

        coEvery { conflictLogRepo.insert(any()) } returns "conflict-logged"

        resolver.resolve("store-1", incoming, existing, "dev-client")

        coVerify(exactly = 1) { conflictLogRepo.insert(any()) }
    }

    @Test
    fun `resolve - conflictId from log repo is returned`() = runTest {
        val now = System.currentTimeMillis()
        val incoming = makeOp(createdAt = now)
        val existing = makeSnapshot(clientTimestamp = now - 1000)

        coEvery { conflictLogRepo.insert(any()) } returns "my-unique-conflict-id"

        val result = resolver.resolve("store-1", incoming, existing, "dev-client")

        assertEquals("my-unique-conflict-id", result.conflictId)
    }
}
