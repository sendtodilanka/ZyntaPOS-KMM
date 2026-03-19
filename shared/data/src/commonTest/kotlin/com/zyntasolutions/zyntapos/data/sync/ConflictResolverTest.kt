package com.zyntasolutions.zyntapos.data.sync

import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [ConflictResolver] — LWW CRDT with deviceId tiebreaker
 * and PRODUCT field-level merge.
 */
class ConflictResolverTest {

    private val resolver = ConflictResolver(localDeviceId = "device-AAA")

    // ── Helper ─────────────────────────────────────────────────────────

    private fun op(
        id: String = "op-1",
        entityType: String = SyncOperation.EntityType.PRODUCT,
        entityId: String = "entity-1",
        operation: SyncOperation.Operation = SyncOperation.Operation.UPDATE,
        payload: String = "{}",
        createdAt: Long = 1000L,
    ) = SyncOperation(
        id = id,
        entityType = entityType,
        entityId = entityId,
        operation = operation,
        payload = payload,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
    )

    // ── 1. Later timestamp wins (remote newer) ────────────────────────

    @Test
    fun resolve_remoteNewerTimestamp_remoteWins() {
        val local = op(id = "local-1", createdAt = 1000L)
        val remote = op(id = "remote-1", createdAt = 2000L)
        val result = resolver.resolve(local, remote)
        assertEquals(remote.id, result.winner.id)
        assertEquals(ResolutionStrategy.LWW_TIMESTAMP, result.conflictLog.strategy)
    }

    // ── 2. Later timestamp wins (local newer) ─────────────────────────

    @Test
    fun resolve_localNewerTimestamp_localWins() {
        val local = op(id = "local-1", createdAt = 3000L)
        val remote = op(id = "remote-1", createdAt = 1000L)
        val result = resolver.resolve(local, remote)
        assertEquals(local.id, result.winner.id)
        assertEquals(ResolutionStrategy.LWW_TIMESTAMP, result.conflictLog.strategy)
    }

    // ── 3. Equal timestamp → deviceId tiebreak ────────────────────────

    @Test
    fun resolve_equalTimestamp_deviceIdTiebreak() {
        val local = op(id = "local-1", createdAt = 1000L)
        val remote = op(id = "remote-1", createdAt = 1000L)
        val result = resolver.resolve(local, remote)
        // Tiebreaker: localDeviceId="device-AAA" vs remoteProxy="remote-1"
        // "remote-1" > "device-AAA" → remote wins
        assertEquals(remote.id, result.winner.id)
        assertEquals(ResolutionStrategy.DEVICE_ID_TIEBREAK, result.conflictLog.strategy)
    }

    // ── 4. PRODUCT entity → field-level merge applied ─────────────────

    @Test
    fun resolve_productEntity_mergesFields() {
        val local = op(
            id = "local-1", createdAt = 1000L,
            payload = """{"name":"Widget","price":null}""",
        )
        val remote = op(
            id = "remote-1", createdAt = 2000L,
            payload = """{"name":null,"price":"9.99"}""",
        )
        val result = resolver.resolve(local, remote)
        // Remote wins by timestamp; loser (local) contributes name="Widget" since remote has null
        assertTrue(result.winner.payload.contains("Widget"))
        assertTrue(result.winner.payload.contains("9.99"))
    }

    // ── 5. Winner fields never overwritten by loser ───────────────────

    @Test
    fun resolve_productEntity_winnerFieldsPreserved() {
        val local = op(
            id = "local-1", createdAt = 1000L,
            payload = """{"name":"OldName","price":"5.00"}""",
        )
        val remote = op(
            id = "remote-1", createdAt = 2000L,
            payload = """{"name":"NewName","price":"9.99"}""",
        )
        val result = resolver.resolve(local, remote)
        // Remote wins; all remote fields are non-null, so loser contributes nothing
        assertTrue(result.winner.payload.contains("NewName"))
        assertTrue(result.winner.payload.contains("9.99"))
    }

    // ── 6. Non-PRODUCT entity → no merge ──────────────────────────────

    @Test
    fun resolve_nonProductEntity_noMerge() {
        val local = op(
            id = "local-1", createdAt = 1000L,
            entityType = SyncOperation.EntityType.ORDER,
            payload = """{"total":"100"}""",
        )
        val remote = op(
            id = "remote-1", createdAt = 2000L,
            entityType = SyncOperation.EntityType.ORDER,
            payload = """{"total":"200"}""",
        )
        val result = resolver.resolve(local, remote)
        assertEquals(remote.id, result.winner.id)
        // Payload unchanged — no merge for ORDER
        assertEquals("""{"total":"200"}""", result.winner.payload)
    }

    // ── 7. Mismatched entityType → throws ─────────────────────────────

    @Test
    fun resolve_mismatchedEntityType_throws() {
        val local = op(entityType = SyncOperation.EntityType.PRODUCT)
        val remote = op(entityType = SyncOperation.EntityType.ORDER)
        assertFailsWith<IllegalArgumentException> {
            resolver.resolve(local, remote)
        }
    }

    // ── 8. Mismatched entityId → throws ───────────────────────────────

    @Test
    fun resolve_mismatchedEntityId_throws() {
        val local = op(entityId = "entity-1")
        val remote = op(entityId = "entity-2")
        assertFailsWith<IllegalArgumentException> {
            resolver.resolve(local, remote)
        }
    }

    // ── 9. mergeJsonPayloads — loser fills blanks ─────────────────────

    @Test
    fun mergeJsonPayloads_loserFillsBlanks() {
        val winner = """{"name":"Widget","desc":null}"""
        val loser = """{"name":"Other","desc":"A great widget","color":"red"}"""
        val merged = resolver.mergeJsonPayloads(winner, loser)
        // winner.name preserved, loser.desc fills null, loser.color added
        assertTrue(merged.contains("Widget"))
        assertTrue(merged.contains("A great widget"))
        assertTrue(merged.contains("red"))
    }

    // ── 10. mergeJsonPayloads — nested objects preserved ──────────────

    @Test
    fun mergeJsonPayloads_nestedObjectsPreserved() {
        val winner = """{"name":"Widget","meta":{"version":1}}"""
        val loser = """{"name":"Other","meta":{"version":2},"extra":"val"}"""
        val merged = resolver.mergeJsonPayloads(winner, loser)
        // winner.meta preserved (not overwritten by loser.meta)
        assertTrue(merged.contains(""""meta":{"version":1}"""))
        // loser.extra added since not in winner
        assertTrue(merged.contains("extra"))
    }
}
