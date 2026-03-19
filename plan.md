# C6.1 — CRDT Merge Integration into SyncEngine

## Current State Analysis

**What EXISTS (already implemented):**
- `ConflictResolver` class (346 lines) — LWW + deviceId tiebreak + PRODUCT field-level merge
- `ConflictLog`, `ConflictResolution`, `ResolutionStrategy` data types
- `conflict_log.sq` — SQLDelight schema with full CRUD queries
- `version_vectors.sq` — schema with upsert/increment queries
- `sync_queue.sq` — outbox pattern (PENDING → SYNCING → SYNCED/FAILED)
- `ConflictLogRepository` interface in `:shared:domain`
- `SyncConflict` domain model with `Resolution` enum
- `ConflictResolver` registered in Koin (`DataModule.kt` line 305-309)
- Server-side `ServerConflictResolver` + `SyncProcessor` integration (fully working + tested)

**What's MISSING (the actual C6.1 work):**
1. `ConflictResolver` is NOT injected into `SyncEngine` — no constructor param
2. Conflict detection on pull — `applyDeltaOperations()` blindly overwrites local data
3. `ConflictLogRepositoryImpl` — interface exists but NO implementation
4. Conflict log persistence — `ConflictResolver.resolve()` returns `ConflictLog` but nothing writes it to DB
5. Version vector increment on local writes — schema exists but never used
6. `SyncResult` doesn't track conflict count
7. No client-side `ConflictResolver` unit tests

---

## Implementation Plan (7 Steps)

### Step 1: Implement `ConflictLogRepositoryImpl`

**File:** `shared/data/src/commonMain/kotlin/com/zyntasolutions/zyntapos/data/repository/ConflictLogRepositoryImpl.kt` (NEW)

Create the repository implementation that maps between `SyncConflict` domain model and `conflict_log` SQLDelight table:

```kotlin
class ConflictLogRepositoryImpl(
    private val db: ZyntaDatabase,
) : ConflictLogRepository {
    // getUnresolved() → asFlow().mapToList() from conflict_log WHERE resolved_at IS NULL
    // getByEntity() → asFlow().mapToList() from conflict_log WHERE entity_type/entity_id
    // getUnresolvedCount() → SELECT COUNT
    // insert() → insertConflict query
    // resolve() → resolveConflict query
    // pruneOld() → pruneOldResolved query
}
```

**Register in Koin** (`DataModule.kt`):
```kotlin
single<ConflictLogRepository> { ConflictLogRepositoryImpl(db = get()) }
```

---

### Step 2: Wire `ConflictResolver` + `ConflictLogRepository` into `SyncEngine`

**File:** `shared/data/src/commonMain/kotlin/com/zyntasolutions/zyntapos/data/sync/SyncEngine.kt`

Add two new constructor parameters:
```kotlin
class SyncEngine(
    // ...existing params...
    private val conflictResolver: ConflictResolver,           // NEW
    private val conflictLogRepository: ConflictLogRepository,  // NEW
)
```

Update Koin binding in `DataModule.kt`:
```kotlin
single {
    SyncEngine(
        // ...existing params...
        conflictResolver      = get(),
        conflictLogRepository = get(),
    )
}
```

Update test helper `engine()` in `SyncEngineIntegrationTest.kt` to pass the new params.

Add conflict tracking to `SyncResult.Success`:
```kotlin
data class Success(
    val pushedCount: Int,
    val pulledCount: Int,
    val conflictCount: Int = 0,  // NEW
    val durationMs: Long,
)
```

---

### Step 3: Implement Conflict Detection on Pull (in `applyDeltaOperations`)

**File:** `SyncEngine.kt` — modify `applyDeltaOperations()`

For each incoming delta op (CREATE/UPDATE), before blindly upserting:
1. Check if there's a **PENDING** local operation for the same `(entityType, entityId)` in `pending_operations`
2. If yes → conflict detected:
   - Convert both the local pending op and remote delta op to `SyncOperation` domain objects
   - Call `conflictResolver.resolve(local, remote)`
   - Persist `ConflictLog` via `conflictLogRepository.insert()`
   - Apply the **winner** payload (not blindly the remote)
   - If local won → keep local pending op in queue (server will get it on next push)
   - If remote won → remove/mark the local pending op as SYNCED (server version supersedes)
3. If no pending local op → apply remote as-is (current behavior, no change)

```kotlin
private suspend fun applyDeltaOperations(ops: List<SyncOperationDto>): Int {
    var conflicts = 0
    for (op in ops) {
        // Check for local pending conflict
        val localPending = db.sync_queueQueries
            .getByEntityId(/* need a new query: getByEntityTypeAndId */)
            .executeAsOneOrNull()

        if (localPending != null && localPending.status == "PENDING") {
            // CONFLICT: resolve using ConflictResolver
            val resolution = conflictResolver.resolve(localToSyncOp(localPending), remoteToSyncOp(op))
            persistConflictLog(resolution.conflictLog)
            conflicts++

            if (resolution.winner.id == op.id) {
                // Remote won → apply remote, discard local
                applyRemoteDelta(op)
                db.sync_queueQueries.markSynced(localPending.id)
            } else {
                // Local won → skip remote, keep local in queue
                log.d("Local wins conflict for ${op.entityType}/${op.entityId} — skipping remote delta")
            }
        } else {
            // No conflict — apply as-is (existing behavior)
            applyRemoteDelta(op)
        }
    }
    return conflicts
}
```

**New SQLDelight query needed in `sync_queue.sq`:**
```sql
getPendingByEntity:
SELECT * FROM pending_operations
WHERE entity_type = ? AND entity_id = ? AND status = 'PENDING'
ORDER BY created_at DESC
LIMIT 1;
```

---

### Step 4: Implement Conflict Detection on Push Response

**File:** `SyncEngine.kt` — modify `pushPendingOperations()`

When the server returns `deltaOperations` in the push response (these are server-side conflict resolutions), the current code calls `applyDeltaOperations()` which will now automatically handle conflicts via Step 3. No additional changes needed here — the Step 3 logic covers both push-response deltas and pull deltas.

However, add conflict count tracking to `executeSyncCycle()`:
```kotlin
private suspend fun executeSyncCycle() {
    val pushConflicts = pushPendingOperations()  // returns (pushedCount, conflictCount)
    val (pulled, pullConflicts) = pullServerDelta()  // returns (pulledCount, conflictCount)

    _lastSyncResult.value = SyncResult.Success(
        pushedCount   = pushed,
        pulledCount   = pulled,
        conflictCount = pushConflicts + pullConflicts,
        durationMs    = durationMs,
    )
}
```

---

### Step 5: Implement Version Vector Increment on Local Writes

**File:** `shared/data/src/commonMain/kotlin/com/zyntasolutions/zyntapos/data/local/SyncEnqueuer.kt`

After enqueuing a pending operation, increment the version vector for the affected entity:

```kotlin
fun enqueue(entityType: String, entityId: String, operation: String, payload: String) {
    // ...existing enqueue logic...

    // Increment version vector for this device + entity
    val now = Clock.System.now().toEpochMilliseconds()
    db.version_vectorsQueries.upsert(
        entity_type = entityType,
        entity_id   = entityId,
        device_id   = localDeviceId,
        version     = 1,  // initial version for upsert
        updated_at  = now,
    )
    // If row already exists, increment instead
    db.version_vectorsQueries.incrementVersion(now, entityType, entityId, localDeviceId)
}
```

**Note:** `SyncEnqueuer` currently only takes `db` — add `localDeviceId` parameter (from Koin named binding).

---

### Step 6: Add ConflictResolver Unit Tests

**File:** `shared/data/src/commonTest/kotlin/com/zyntasolutions/zyntapos/data/sync/ConflictResolverTest.kt` (NEW)

Test cases:
1. `resolve_laterTimestamp_wins` — remote has later timestamp → remote wins
2. `resolve_earlierTimestamp_loses` — local has later timestamp → local wins
3. `resolve_equalTimestamp_deviceIdTiebreak` — same timestamp → lexicographic deviceId breaks tie
4. `resolve_productEntity_mergesFields` — PRODUCT conflict → loser's non-null fields merged into winner
5. `resolve_productEntity_winnerFieldsPreserved` — winner's fields never overwritten by loser
6. `resolve_nonProductEntity_noMerge` — ORDER/CUSTOMER conflicts → no field merge
7. `resolve_mismatchedEntityType_throws` — different entityTypes → IllegalArgumentException
8. `resolve_mismatchedEntityId_throws` — different entityIds → IllegalArgumentException
9. `mergeJsonPayloads_loserFillsBlanks` — null/empty winner fields filled from loser
10. `mergeJsonPayloads_nestedObjects_preserved` — nested JSON handled as opaque strings

---

### Step 7: Add SyncEngine Conflict Integration Tests

**File:** `shared/data/src/jvmTest/kotlin/com/zyntasolutions/zyntapos/data/sync/SyncEngineIntegrationTest.kt` (MODIFY)

Add test cases:
1. `runOnce_pullConflict_remoteWins_localDiscarded` — pending local op + newer remote delta → remote applied, local marked SYNCED
2. `runOnce_pullConflict_localWins_remoteSkipped` — pending local op + older remote delta → remote skipped, local stays PENDING
3. `runOnce_pullConflict_productMerge_fieldsPreserved` — PRODUCT conflict → merged payload applied
4. `runOnce_pullNoConflict_appliedNormally` — no pending local op → remote applied as-is (regression)
5. `runOnce_conflictCount_tracked` — verify `SyncResult.Success.conflictCount` is correct

---

## Files Changed Summary

| File | Action | Description |
|------|--------|-------------|
| `shared/data/.../repository/ConflictLogRepositoryImpl.kt` | NEW | Implements `ConflictLogRepository` interface |
| `shared/data/.../sync/SyncEngine.kt` | MODIFY | Add conflict detection in `applyDeltaOperations`, wire resolver |
| `shared/data/.../di/DataModule.kt` | MODIFY | Add `ConflictLogRepository` binding, update `SyncEngine` binding |
| `shared/data/.../sqldelight/.../sync_queue.sq` | MODIFY | Add `getPendingByEntity` query |
| `shared/data/.../local/SyncEnqueuer.kt` | MODIFY | Add version vector increment |
| `shared/data/src/commonTest/.../sync/ConflictResolverTest.kt` | NEW | 10 unit tests |
| `shared/data/src/jvmTest/.../sync/SyncEngineIntegrationTest.kt` | MODIFY | 5 new integration tests |

## Dependency Direction (verified)

All changes stay within `:shared:data` → `:shared:domain` → `:shared:core`. No architecture violations.

## Risk Assessment

- **Low risk:** ConflictResolver already works (tested on server-side). Client class is identical algorithm.
- **Medium risk:** `applyDeltaOperations` change — need to ensure no infinite sync loop (resolved ops must NOT be re-enqueued).
- **Mitigation:** The existing `applyDeltaOperations` comment already states "No SyncEnqueuer.enqueue is called here" — this invariant is preserved.
