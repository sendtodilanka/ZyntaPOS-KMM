# Sync Strategy — ZyntaPOS Offline-First Architecture

**Status:** Phase 1 implementation documented. Phase 2 CRDT gaps explicitly called out.
**Last updated:** 2026-03-14
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

## 4. Delta Application — Phase 1 Stub (KNOWN GAP)

**File:** `SyncEngine.kt`, method `applyDeltaOperations` (line 266)

```kotlin
private fun applyDeltaOperations(ops: List<SyncOperationDto>) {
    for (op in ops) {
        log.d("Applying delta: ${op.operation} on ${op.entityType}(${op.entityId})")
        // TODO Sprint 7: route by entityType → appropriate RepositoryImpl.upsertFromSync(op.payload)
    }
}
```

**Current behaviour:** Delta operations from the server are received and logged but are NOT
applied to the local database. The dispatcher that routes by `entityType` to the correct
`RepositoryImpl.upsertFromSync()` is a Sprint 7 TODO.

**Impact:** Pull sync is effectively a no-op in Phase 1. Devices will not receive server-side
changes (e.g., product catalog updates from a back-office system) until Sprint 7 is implemented.

---

## 5. Network Layer Stub in SyncRepositoryImpl (KNOWN GAP)

**File:** `SyncRepositoryImpl.kt`, lines 155–171

```kotlin
override suspend fun pushToServer(ops: List<SyncOperation>): Result<Unit> {
    // TODO(Sprint6-Step3.4): wire Ktor ApiService here
    return if (ops.isEmpty()) Result.Success(Unit)
    else markSynced(ops.map { it.id })
}

override suspend fun pullFromServer(lastSyncTimestamp: Long): Result<List<SyncOperation>> =
    Result.Success(emptyList())
```

`SyncRepositoryImpl` is a Phase 1 stub that marks operations as locally SYNCED without making
any HTTP call. The actual Ktor `ApiService` wiring is planned for Sprint 6, Step 3.4.

**Note:** `SyncEngine` does call `ApiService` directly (bypassing `SyncRepositoryImpl`). The
`SyncRepositoryImpl` stub is retained for the `SyncRepository` interface contract tests.

---

## 6. CRDT Conflict Resolution — Schema Exists, Logic Not Implemented

### Version Vectors (`version_vectors.sq`)

The schema for CRDT vector clocks is fully defined (Phase 3 Sprint 2):

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

Queries exist for `upsert`, `incrementVersion`, `selectByEntity`, and `selectByDevice`.

**Current status:** The table schema is generated by SQLDelight. No application code currently
reads or writes to `version_vectors`. Version vector tracking is not wired into any
`RepositoryImpl` as of Phase 1.

### Conflict Log (`conflict_log.sq`)

A `conflict_log` table is defined with fields for `local_value`, `server_value`, `resolved_by`
(`LOCAL | SERVER | MERGE | MANUAL`), and `resolved_at`. Queries exist for inserting conflicts,
resolving them, and pruning old resolved records.

**Current status:** The conflict log schema is generated but no code writes to it. The
`ConflictResolver` class does **not exist** in the codebase (Phase 2 backlog).

### Summary of CRDT Gaps

| Component | Status |
|-----------|--------|
| `version_vectors` table schema | Exists (Phase 3 Sprint 2) |
| Version vector increment on write | NOT IMPLEMENTED |
| `conflict_log` table schema | Exists |
| Conflict detection logic | NOT IMPLEMENTED |
| `ConflictResolver` class | NOT IMPLEMENTED (Phase 2 backlog) |
| `applyDeltaOperations` dispatcher | TODO Sprint 7 |

**Phase 1 conflict strategy:** Last-write-wins (server-authoritative). The server's delta
response is treated as ground truth with no local conflict checking.

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
| `applyDeltaOperations` dispatcher (route by `entityType` → `RepositoryImpl`) | Sprint 7 | Phase 1 |
| Ktor `ApiService` wiring in `SyncRepositoryImpl` | Sprint 6 Step 3.4 | Phase 1 |
| `ConflictResolver` implementation | CRDT backlog | Phase 2 |
| Version vector increment on every write | CRDT backlog | Phase 2 |
| Conflict log population | CRDT backlog | Phase 2 |
| `CashDrawerController` HAL interface | Phase 2 backlog | Phase 2 |
