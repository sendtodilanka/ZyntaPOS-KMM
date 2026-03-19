# Sync Strategy — ZyntaPOS Offline-First Architecture

**Status:** Phase 1 implementation documented. Phase 2 CRDT gaps explicitly called out.
**Last updated:** 2026-03-18
**Sources:** `shared/data/src/commonMain/kotlin/.../sync/SyncEngine.kt`, `SyncRepositoryImpl.kt`, `SyncEnqueuer.kt`, SQLDelight `.sq` files

---

## 1. Offline-First Strategy Overview

ZyntaPOS is designed to operate indefinitely without a network connection. Every write operation
(sale, stock adjustment, customer update, etc.) is committed to the local encrypted SQLite
database **immediately and synchronously** before any network call is attempted.

A background sync engine then pushes locally-created operations to the cloud server and pulls
server-side changes. The device is always consistent with itself; eventual consistency with the
server is achieved asynchronously.

**Core guarantee:** A cashier can ring up sales, adjust stock, and close a register session even
with zero connectivity. Operations accumulate in the local outbox queue and drain when connectivity
is restored.

---

## 2. Sync Queue: Outbox Pattern

### Schema (`pending_operations` table in `sync_queue.sq`)

```sql
CREATE TABLE pending_operations (
    id          TEXT    NOT NULL PRIMARY KEY,
    entity_type TEXT    NOT NULL,
    entity_id   TEXT    NOT NULL,
    operation   TEXT    NOT NULL,   -- CREATE | UPDATE | DELETE
    payload     TEXT    NOT NULL DEFAULT '{}',
    status      TEXT    NOT NULL DEFAULT 'PENDING',  -- PENDING | SYNCING | SYNCED | FAILED
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at  INTEGER NOT NULL,   -- epoch millis
    last_tried  INTEGER NOT NULL DEFAULT 0
);
```

### Lifecycle State Machine

```
[Local Write] ──► PENDING
                    │
              SyncEngine polls (batch of 50)
                    │
              markSyncing ──► SYNCING
                    │
          ┌─────────┴──────────┐
      Server ACK           Server NACK / error
          │                    │
       SYNCED          retry_count += 1
                               │
                     retry_count < 5?
                        ┌──────┴──────┐
                       Yes            No
                        │             │
                     PENDING       FAILED (permanent)
```

**Crash safety:** On startup, any rows left in SYNCING state (stuck for > 10 minutes, as defined
by `STALE_SYNCING_THRESHOLD_MS = 10 * 60 * 1000`) are reset to PENDING via `resetStaleSync`.

### Enqueueing Operations (`SyncEnqueuer.kt`)

`SyncEnqueuer` is a lightweight helper injected into every `RepositoryImpl`. It writes to the
`pending_operations` table inside the same database transaction as the original write:

```kotlin
// INSERT OR IGNORE — idempotent; duplicate (entity_type, entity_id) within the same ms
// is silently dropped, preventing double-enqueue on retry.
enqueueOperation:
INSERT OR IGNORE INTO pending_operations(id, entity_type, entity_id, operation, payload, created_at)
VALUES (?, ?, ?, ?, ?, ?);
```

The `INSERT OR IGNORE` primary-key constraint ensures idempotency: if the same UUID is enqueued
twice (e.g., due to a retry loop), only the first row is kept.

### Deduplication

Before each push cycle, `deduplicatePending` removes all but the **latest** PENDING operation per
`(entity_type, entity_id)` pair. This prevents sending multiple UPDATE operations for the same
entity when only the most recent snapshot is needed.

---

## 3. SyncEngine: Push / Pull Cycle

**File:** `shared/data/src/commonMain/kotlin/com/zyntasolutions/zyntapos/data/sync/SyncEngine.kt`

### Platform Scheduling

| Platform | Mechanism |
|----------|-----------|
| Android | `SyncWorker` (WorkManager `CoroutineWorker`) calls `SyncEngine.runOnce()` |
| Desktop | `SyncEngine.startPeriodicSync(scope)` — coroutine loop with `AppConfig.SYNC_INTERVAL_MS` delay |

Re-entrancy is guarded by `_isSyncing` (`MutableStateFlow<Boolean>`) using `compareAndSet`. If
a sync cycle is already running (foreground trigger + background tick racing), the second call is
a no-op.

### Push Phase

1. Read up to `BATCH_SIZE = 50` PENDING rows ordered oldest-first (`getEligibleOperations`).
2. Mark all selected rows as SYNCING (crash-safe: `resetStaleSync` handles recovery).
3. Call `ApiService.pushOperations(dtos)` — POST to `/api/v1/sync/push`.
4. Mark `response.accepted` IDs as SYNCED.
5. For `response.rejected` IDs: increment `retry_count`; permanently fail if `>= 5`.
6. Apply `response.deltaOperations` (conflict resolutions bundled in the push ACK) via
   `applyDeltaOperations`.

### Pull Phase

1. Read the stored `KEY_LAST_SYNC_TS` from `SecurePreferences`.
2. Call `ApiService.pullOperations(lastSyncTimestamp)` — GET `/api/v1/sync/pull`.
3. Apply returned delta operations via `applyDeltaOperations`.
4. Persist the new server timestamp.

---

## 4. Delta Application — Implemented

**File:** `SyncEngine.kt`, method `applyDeltaOperations`

`applyDeltaOperations` is fully implemented. It dispatches each delta operation by `entityType`
to the appropriate `RepositoryImpl.upsertFromSync()` or `.delete()`:

```kotlin
private suspend fun applyDeltaOperations(ops: List<SyncOperationDto>) {
    for (op in ops) {
        when (op.operation) {
            "DELETE" -> when (op.entityType) {
                PRODUCT  -> productRepository.delete(op.entityId)
                ORDER    -> orderRepository.void(op.entityId, "server-sync")
                CUSTOMER -> customerRepository.delete(op.entityId)
                CATEGORY -> categoryRepository.delete(op.entityId)
                else     -> log.w("Unhandled DELETE for entityType: ${op.entityType}")
            }
            else -> when (op.entityType) { // "CREATE" | "UPDATE" — server snapshot wins
                PRODUCT          -> productRepository.upsertFromSync(op.payload)
                ORDER            -> orderRepository.upsertFromSync(op.payload)
                CUSTOMER         -> customerRepository.upsertFromSync(op.payload)
                CATEGORY         -> categoryRepository.upsertFromSync(op.payload)
                SUPPLIER         -> supplierRepository.upsertFromSync(op.payload)
                STOCK_ADJUSTMENT -> stockRepository.upsertFromSync(op.payload)
                USER             -> { /* read-only from device — managed via auth */ }
                else             -> log.w("Unknown entityType for delta op: ${op.entityType}")
            }
        }
    }
}
```

**Note:** Unknown entity types are logged as warnings and skipped; they do not cause the sync cycle to fail.

---

## 5. Network Layer — SyncRepositoryImpl

**File:** `SyncRepositoryImpl.kt`

`SyncRepositoryImpl` is fully implemented. `pushToServer` calls `ApiService.pushOperations(dtos)`
(POST `/api/v1/sync/push`) and handles accepted/rejected acknowledgements. `pullFromServer` calls
`ApiService.pullOperations(lastSyncTimestamp)` (GET `/api/v1/sync/pull`) and maps returned delta
operations to local `SyncOperation` domain objects. Errors are caught and wrapped in `Result.Error`
with structured logging.

`SyncEngine` calls `ApiService` directly for performance; `SyncRepositoryImpl` implements the
`SyncRepository` interface and is used in integration/contract tests.

---

## 6. CRDT Conflict Resolution — Implemented (C6.1, 2026-03-19)

### Algorithm: Last-Write-Wins (LWW) with Deterministic Tiebreaking

When two `SyncOperation`s target the same entity from different devices, `ConflictResolver`
determines the canonical winner:

1. **Primary rule — LWW timestamp:** The operation with the later `createdAt` wins.
2. **Tiebreaker — `deviceId` lexicographic order:** If timestamps are equal, the device ID
   that sorts later alphabetically wins. Deterministic across all nodes.
3. **PRODUCT entity merge:** For PRODUCT conflicts, non-null fields from the *losing*
   operation's JSON payload are carried forward into the winner's payload if the winner's
   field is null/blank. Reduces data loss from partial-update scenarios.

### Key Components

- **`ConflictResolver`** (`shared/data/.../sync/ConflictResolver.kt`, 346 lines) — LWW resolver
  with field-level merge for PRODUCT entities. Injectable via Koin with `localDeviceId`.
- **`ConflictLogRepositoryImpl`** (`shared/data/.../repository/ConflictLogRepositoryImpl.kt`) —
  SQLDelight-backed implementation of `ConflictLogRepository` interface. Persists audit trail.
- **`SyncEngine` integration** — `applyDeltaOperations()` checks for PENDING local operations
  before applying server deltas. If conflict detected: resolves via `ConflictResolver`, persists
  `SyncConflict` audit record, applies winner payload, marks loser accordingly.

### Version Vectors (`version_vectors.sq`)

The schema for CRDT vector clocks is fully defined:

```sql
CREATE TABLE IF NOT EXISTS version_vectors (
    entity_type TEXT    NOT NULL,
    entity_id   TEXT    NOT NULL,
    device_id   TEXT    NOT NULL,
    version     INTEGER NOT NULL DEFAULT 0,
    updated_at  INTEGER NOT NULL,
    PRIMARY KEY (entity_type, entity_id, device_id)
);
```

**Current status:** `SyncEnqueuer` increments the version vector on every local write
(`enqueue()` calls `version_vectorsQueries.upsert()` + `incrementVersion()`).

### Conflict Log (`conflict_log.sq`)

A `conflict_log` table stores every conflict detected during sync with resolution metadata:
`local_value`, `server_value`, `resolved_by` (`LOCAL | SERVER | MERGE | MANUAL`), `resolved_at`.
`ConflictLogRepositoryImpl` provides reactive queries via `asFlow().mapToList()`.

### Implementation Status

| Component | Status |
|-----------|--------|
| `version_vectors` table schema | ✅ Implemented |
| Version vector increment on write | ✅ Implemented (`SyncEnqueuer`) |
| `conflict_log` table schema | ✅ Implemented |
| `ConflictLogRepositoryImpl` | ✅ Implemented |
| Conflict detection logic | ✅ Implemented (`SyncEngine.applyCreateOrUpdate()`) |
| `ConflictResolver` class | ✅ Implemented (LWW + tiebreak + PRODUCT merge) |
| `applyDeltaOperations` dispatcher | ✅ Implemented (routes to `applyDelete` / `applyCreateOrUpdate`) |
| `SyncResult.conflictCount` tracking | ✅ Implemented |
| Unit tests (`ConflictResolverTest`) | ✅ 10 tests |
| Integration tests (`SyncEngineIntegrationTest`) | ✅ 5 conflict tests |

**Conflict strategy:** LWW with CRDT-style conflict detection and audit logging. Server deltas
are no longer blindly applied — pending local operations are checked and resolved first.

---

## 7. Sync State Table (`sync_state.sq`)

A `sync_state` table exists for storing per-device sync cursor metadata. This supplements the
`KEY_LAST_SYNC_TS` stored in `SecurePreferences` and is intended for multi-store scenarios where
per-entity-type sync cursors are needed. Not yet wired into the sync engine.

---

## 8. Server-Side Sync Infrastructure (Backend)

The backend `api` service handles the server side of the outbox push/pull protocol.

### SyncProcessor

`backend/api/src/main/kotlin/.../sync/SyncProcessor.kt` — processes incoming `POST /api/v1/sync/push` payloads:
1. Validates store ownership of each operation
2. Persists operations to `sync_operations` table (V4) via `newSuspendedTransaction`
3. Dispatches `EntityApplier` to write normalized rows to V12 tables (products/categories/customers/orders/order_items)
4. Publishes `sync:delta:<storeId>` to Redis pub/sub so connected WebSocket clients (Sync service) are notified

**Redis connection pooling (S3-13):** `SyncProcessor` uses a `GenericObjectPool<StatefulRedisConnection<String, String>>` (Apache Commons Pool2) shared via Koin. Connections are borrowed for each `publishToRedis()` call and returned in a `finally` block. Pool size is configurable via `REDIS_POOL_SIZE` (default: 8). If Redis is unavailable, the pool binding is `null` and publish is silently skipped (sync continues; real-time notifications are best-effort).

**MDC logging (D6):** `storeId` and `deviceId` are added to SLF4J MDC at the start of each `process()` call and removed in a `finally` block. This ensures every log line emitted within a sync request carries the store/device context without cross-coroutine leakage.

**Performance index (S3-11 / V16):** `sync_operations` has two new indexes:
- `idx_sync_ops_store_entity(store_id, entity_type, entity_id)` — covers EntityApplier's full lookup predicate
- `idx_sync_ops_pending(store_id, created_at DESC) WHERE status='PENDING'` — partial index for batch-fetch queries

### ForceSyncNotifier

`backend/api/src/main/kotlin/.../api/service/ForceSyncNotifier.kt` — publishes `force_sync` commands to the `sync:commands` Redis channel. Shares the same Koin `GenericObjectPool` as `SyncProcessor` (borrow/return pattern).

### AdminAuditService

`backend/api/src/main/kotlin/.../api/service/AdminAuditService.kt` — delegates all SQL to `AdminAuditRepository` (S3-15 extraction). Adds `adminId` to SLF4J MDC around each `log()` call for structured log correlation.

---

## 9. Known Gaps Summary

| Gap | Ticket | Phase |
|-----|--------|-------|
| `ConflictResolver` implementation | CRDT backlog | Phase 2 |
| Version vector increment on every write | CRDT backlog | Phase 2 |
| Conflict log population | CRDT backlog | Phase 2 |
| Cash drawer open event not emitted after payment — `PrinterManager.openCashDrawer()` exists but POS payment flow does not call it | Phase 2 backlog | Phase 2 |
