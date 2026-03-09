# TODO-007g — Sync Engine Server-Side (sync.zyntapos.com)

**Phase:** 2 — Growth
**Priority:** P0 (HIGH)
**Status:** ✅ DONE
**Effort:** ~5 working days (1 week, 1 developer)
**Related:** TODO-007 (infrastructure), TODO-007a (admin sync dashboard), TODO-007e (API docs — sync protocol)
**Owner:** Zynta Solutions Pvt Ltd
**Last updated:** 2026-03-09
**Completed:** 2026-03-09
**PR:** #214 — feat(sync): implement full server-side sync engine
**CI:** All checks green (Branch Validate ✅, CI Gate ✅, Detekt ✅, CodeQL ✅, JUnit ✅)

---

## ✅ Implementation Summary (2026-03-09)

All 20 implementation steps completed. 30 files changed, 2,113 insertions. CI fully green.

### What was implemented

| Component | File(s) | Status |
|-----------|---------|--------|
| **V4 migration** — 5 tables + trigger | `V4__sync_engine.sql` | ✅ |
| **Exposed table objects** | `SyncTables.kt` | ✅ |
| **SyncOperationRepository** | `repository/SyncOperationRepository.kt` | ✅ |
| **SyncCursorRepository** | `repository/SyncCursorRepository.kt` | ✅ |
| **ConflictLogRepository** | `repository/ConflictLogRepository.kt` | ✅ |
| **DeadLetterRepository** | `repository/DeadLetterRepository.kt` | ✅ |
| **EntitySnapshotRepository** | `repository/EntitySnapshotRepository.kt` | ✅ |
| **SyncValidator** | `sync/SyncValidator.kt` | ✅ |
| **ServerConflictResolver** | `sync/ServerConflictResolver.kt` | ✅ |
| **DeltaEngine** | `sync/DeltaEngine.kt` | ✅ |
| **SyncProcessor** | `sync/SyncProcessor.kt` | ✅ |
| **EntityApplier** | `sync/EntityApplier.kt` | ✅ |
| **SyncMetrics** | `sync/SyncMetrics.kt` | ✅ |
| **SyncRoutes.kt** (full push/pull) | `routes/SyncRoutes.kt` | ✅ |
| **WebSocketHub** | `sync/hub/WebSocketHub.kt` | ✅ |
| **RedisPubSubListener** | `sync/hub/RedisPubSubListener.kt` | ✅ |
| **WebSocketMessages.kt** | `sync/models/WebSocketMessages.kt` | ✅ |
| **SyncWebSocketRoutes.kt** (full WS) | `sync/routes/SyncWebSocketRoutes.kt` | ✅ |
| **HealthRoutes** — `/health/sync` | `routes/HealthRoutes.kt` | ✅ |
| **AdminSyncRoutes** — conflicts + dead-letters | `routes/AdminSyncRoutes.kt` | ✅ |
| **AppModule** — all 11 sync bindings | `di/AppModule.kt` | ✅ |
| **SyncModule** — hub + listener | `sync/di/SyncModule.kt` | ✅ |
| **SyncPullResponseDto** — cursor + hasMore | `dto/SyncDto.kt` | ✅ |
| **SyncEngine** — cursor-loop pull | `sync/SyncEngine.kt` | ✅ |
| **KtorApiService** — `?since=` param | `api/KtorApiService.kt` | ✅ |
| **Unit tests** (4 test files, ~35 tests) | `test/.../sync/` | ✅ |

---

## 1. Overview

Build the complete server-side sync engine that receives push operations from POS terminals, resolves conflicts, persists data to PostgreSQL, and broadcasts changes to connected devices via WebSocket. This is the **backbone of the offline-first architecture** — without it, POS terminals operate as isolated silos with no data consolidation.

### Current State (What Already Exists)

**Client-side (KMM app) — fully implemented:**
- `SyncEngine` — background push/pull cycle orchestrator (batches of 50)
- `SyncRepository` / `SyncRepositoryImpl` — local sync queue management
- `ConflictResolver` — LWW timestamp + device ID tiebreak + PRODUCT field merge
- `SyncEnqueuer` — auto-enqueues local writes to `pending_operations` table
- `ApiService` interface — defines `pushOperations()` and `pullOperations()` contracts
- `SyncDto` — wire format DTOs (`SyncOperationDto`, `SyncResponseDto`, `SyncPullResponseDto`)
- SQLDelight tables — `pending_operations` (sync queue), `sync_state` (per-entity cursors), `version_vectors` (Phase 2+ CRDT)
- `NetworkMonitor` — platform-aware connectivity detection (Android/JVM)

**Server-side (backend) — partially implemented:**
- `SyncRoutes.kt` — basic `POST /v1/sync/push` and `GET /v1/sync/pull` routes (skeleton)
- `SyncWebSocketRoutes.kt` — WebSocket upgrade at `/v1/sync/ws` (basic echo)
- Rate limiting — 60 req/min on sync endpoints

**What's missing (this TODO):**
- Server-side sync operation persistence (PostgreSQL tables + repository)
- Server-side conflict detection and resolution
- Delta computation for pull requests (efficient cursor-based pagination)
- WebSocket fan-out (broadcast changes to all connected devices of a store)
- Multi-store data isolation (JWT `storeId` claim enforcement)
- Sync metrics and monitoring
- Batch validation and deduplication
- Dead letter queue for permanently failed operations

### Goals

- Accept push batches from POS terminals and persist to PostgreSQL
- Detect and resolve conflicts using LWW (matching client-side algorithm)
- Serve pull requests with efficient cursor-based delta pagination
- Broadcast changes via WebSocket to all connected devices of the same store
- Enforce multi-store data isolation (store A never sees store B's data)
- Track sync metrics (operations/sec, conflict rate, queue depth per store)
- Handle partial batch failures gracefully (accept valid ops, reject invalid)
- Support 500+ concurrent POS terminals with < 200ms p95 push latency

### Non-Goals (deferred)

- CRDT-based conflict resolution with version vectors (Phase 3 — `version_vectors` table staged)
- Cross-store sync (multi-store transfers use separate API — Phase 3)
- Real-time collaborative editing (not needed for POS — single-device-per-screen)
- Sync over Bluetooth/LAN (P2P sync without internet — Phase 4)
- Binary payload compression (gzip at HTTP layer is sufficient for Phase 2)

---

## 2. Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      POS Terminal (KMM App)                     │
│                                                                 │
│  Local SQLite ──→ SyncEngine ──→ POST /v1/sync/push            │
│                                  GET  /v1/sync/pull             │
│                                  WSS  /v1/sync/ws (real-time)   │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTPS / WSS
                             │
┌────────────────────────────▼────────────────────────────────────┐
│                    Caddy (Reverse Proxy)                        │
│                    api.zyntapos.com → :8080                     │
│                    sync.zyntapos.com → :8082                    │
└────────────────────────────┬────────────────────────────────────┘
                             │
        ┌────────────────────┴────────────────────┐
        │                                         │
┌───────▼───────┐                        ┌────────▼────────┐
│  API Service  │                        │  Sync Service   │
│  (Ktor :8080) │                        │  (Ktor :8082)   │
│               │                        │                 │
│ POST /push    │                        │ WS /v1/sync/ws  │
│ GET  /pull    │                        │                 │
│               │                        │ WebSocket Hub   │
│ SyncProcessor │──── Redis Pub/Sub ────→│ (fan-out to     │
│ ConflictRes.  │                        │  connected POS) │
│ DeltaEngine   │                        │                 │
└───────┬───────┘                        └────────┬────────┘
        │                                         │
        │              ┌──────────┐               │
        └──────────────┤PostgreSQL├───────────────┘
                       │  :5432   │
                       │          │
                       │ sync_ops │
                       │ entities │
                       │ cursors  │
                       └──────────┘
```

### Data Flow: Push Operation

```
POS Terminal                    API Service                     Sync Service
     │                              │                               │
     │  POST /v1/sync/push          │                               │
     │  {deviceId, operations[]}    │                               │
     │─────────────────────────────→│                               │
     │                              │                               │
     │                    ┌─────────┴─────────┐                     │
     │                    │ 1. Validate JWT   │                     │
     │                    │ 2. Extract storeId│                     │
     │                    │ 3. Validate batch │                     │
     │                    │ 4. Dedup by opId  │                     │
     │                    │ 5. Detect conflicts│                    │
     │                    │ 6. Resolve (LWW)  │                     │
     │                    │ 7. Persist to PG  │                     │
     │                    │ 8. Update cursors │                     │
     │                    └─────────┬─────────┘                     │
     │                              │                               │
     │                              │  Redis PUBLISH                │
     │                              │  channel: sync:{storeId}      │
     │                              │──────────────────────────────→│
     │                              │                               │
     │                              │                    ┌──────────┴──────────┐
     │                              │                    │ Fan-out to all WS   │
     │                              │                    │ connections for     │
     │                              │                    │ this storeId        │
     │                              │                    │ (except sender)     │
     │                              │                    └──────────┬──────────┘
     │                              │                               │
     │  {accepted[], rejected[],    │                               │
     │   conflicts[], serverTs}     │                               │
     │←─────────────────────────────│                               │
     │                              │                               │
     │                              │         WS: {type: "delta",   │
     │                              │          operations: [...]}    │
Other│←─────────────────────────────────────────────────────────────│
POS  │                              │                               │
```

### Data Flow: Pull Operation

```
POS Terminal                    API Service
     │                              │
     │  GET /v1/sync/pull           │
     │  ?since=<cursor>&limit=50    │
     │─────────────────────────────→│
     │                              │
     │                    ┌─────────┴─────────┐
     │                    │ 1. Validate JWT   │
     │                    │ 2. Extract storeId│
     │                    │ 3. Query sync_ops │
     │                    │    WHERE store_id  │
     │                    │    AND server_seq  │
     │                    │    > cursor        │
     │                    │    ORDER BY seq    │
     │                    │    LIMIT 50        │
     │                    │ 4. Return delta   │
     │                    └─────────┬─────────┘
     │                              │
     │  {operations[], serverTs,    │
     │   cursor, hasMore}           │
     │←─────────────────────────────│
```

---

## 3. PostgreSQL Schema

### 3.1 Core Sync Tables

**File:** `backend/api/src/main/resources/db/migration/V4__sync_engine.sql`

```sql
-- ============================================================================
-- Sync Engine Tables
-- Stores all sync operations from POS terminals, with server-assigned
-- monotonic sequence numbers for efficient cursor-based pull.
-- ============================================================================

-- Main sync operations log
-- Every write from every POS terminal ends up here
CREATE TABLE sync_operations (
    id              UUID PRIMARY KEY,               -- Client-generated UUID
    store_id        UUID NOT NULL,                  -- Multi-store isolation
    device_id       TEXT NOT NULL,                  -- Source device identifier
    entity_type     TEXT NOT NULL,                  -- 'PRODUCT', 'ORDER', etc.
    entity_id       UUID NOT NULL,                  -- ID of affected entity
    operation       TEXT NOT NULL CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE')),
    payload         JSONB NOT NULL,                 -- Full entity snapshot (JSON)
    client_timestamp BIGINT NOT NULL,               -- Client-side epoch millis
    server_seq      BIGSERIAL,                      -- Server-assigned monotonic sequence
    server_timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    vector_clock    BIGINT NOT NULL DEFAULT 0,      -- For future CRDT support
    status          TEXT NOT NULL DEFAULT 'ACCEPTED'
                    CHECK (status IN ('ACCEPTED', 'CONFLICT_RESOLVED', 'REJECTED')),
    conflict_id     UUID,                           -- FK to conflict_log if resolved

    CONSTRAINT fk_store FOREIGN KEY (store_id) REFERENCES stores(id)
);

-- Critical index: pull queries use this (cursor-based pagination)
CREATE INDEX idx_sync_ops_pull
    ON sync_operations (store_id, server_seq)
    WHERE status != 'REJECTED';

-- Dedup index: prevent duplicate operations from retries
CREATE UNIQUE INDEX idx_sync_ops_dedup
    ON sync_operations (id);

-- Entity history: find all operations for a specific entity
CREATE INDEX idx_sync_ops_entity
    ON sync_operations (store_id, entity_type, entity_id, server_seq DESC);

-- Device tracking: operations by device (for diagnostics)
CREATE INDEX idx_sync_ops_device
    ON sync_operations (store_id, device_id, server_seq DESC);


-- ============================================================================
-- Sync cursors — tracks each device's pull position
-- ============================================================================
CREATE TABLE sync_cursors (
    store_id        UUID NOT NULL,
    device_id       TEXT NOT NULL,
    last_seq        BIGINT NOT NULL DEFAULT 0,      -- Last server_seq pulled
    last_pull_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (store_id, device_id),
    CONSTRAINT fk_cursor_store FOREIGN KEY (store_id) REFERENCES stores(id)
);


-- ============================================================================
-- Conflict log — audit trail of all conflict resolutions
-- ============================================================================
CREATE TABLE sync_conflict_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id        UUID NOT NULL,
    entity_type     TEXT NOT NULL,
    entity_id       UUID NOT NULL,
    local_op_id     UUID NOT NULL,                  -- The incoming (losing) operation
    server_op_id    UUID NOT NULL,                  -- The existing (winning) operation
    local_device_id TEXT NOT NULL,
    server_device_id TEXT NOT NULL,
    local_timestamp BIGINT NOT NULL,
    server_timestamp BIGINT NOT NULL,
    resolution      TEXT NOT NULL CHECK (resolution IN (
        'LWW_TIMESTAMP', 'DEVICE_ID_TIEBREAK', 'FIELD_MERGE', 'MANUAL'
    )),
    local_payload   JSONB,                          -- Snapshot of losing payload
    server_payload  JSONB,                          -- Snapshot of winning payload
    merged_payload  JSONB,                          -- Result after merge (if FIELD_MERGE)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_conflict_store FOREIGN KEY (store_id) REFERENCES stores(id)
);

CREATE INDEX idx_conflict_log_entity
    ON sync_conflict_log (store_id, entity_type, entity_id);

CREATE INDEX idx_conflict_log_time
    ON sync_conflict_log (store_id, created_at DESC);


-- ============================================================================
-- Dead letter queue — operations that permanently failed
-- ============================================================================
CREATE TABLE sync_dead_letters (
    id              UUID PRIMARY KEY,
    store_id        UUID NOT NULL,
    device_id       TEXT NOT NULL,
    entity_type     TEXT NOT NULL,
    entity_id       UUID NOT NULL,
    operation       TEXT NOT NULL,
    payload         JSONB NOT NULL,
    client_timestamp BIGINT NOT NULL,
    error_reason    TEXT NOT NULL,
    retry_count     INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at     TIMESTAMPTZ,                    -- NULL until admin reviews
    reviewed_by     TEXT,                            -- Admin user who reviewed

    CONSTRAINT fk_dead_store FOREIGN KEY (store_id) REFERENCES stores(id)
);

CREATE INDEX idx_dead_letters_pending
    ON sync_dead_letters (store_id, reviewed_at)
    WHERE reviewed_at IS NULL;


-- ============================================================================
-- Entity snapshots — latest state of each entity (materialized view)
-- Used by pull requests and admin panel queries
-- ============================================================================
CREATE TABLE entity_snapshots (
    store_id        UUID NOT NULL,
    entity_type     TEXT NOT NULL,
    entity_id       UUID NOT NULL,
    payload         JSONB NOT NULL,                 -- Latest full entity JSON
    last_op_id      UUID NOT NULL,                  -- Last sync_operation that modified this
    last_seq        BIGINT NOT NULL,                -- server_seq of last modification
    last_device_id  TEXT NOT NULL,                  -- Device that last modified
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_deleted      BOOLEAN NOT NULL DEFAULT FALSE, -- Soft-delete flag (from DELETE ops)
    PRIMARY KEY (store_id, entity_type, entity_id),
    CONSTRAINT fk_snapshot_store FOREIGN KEY (store_id) REFERENCES stores(id)
);

-- For full-entity pulls (e.g., "give me all products for store X")
CREATE INDEX idx_snapshots_type
    ON entity_snapshots (store_id, entity_type, last_seq)
    WHERE NOT is_deleted;
```

### 3.2 Entity Merge Tables

When a push operation arrives, the server must update the canonical entity tables (products, orders, customers, etc.). These tables already exist from the API service schema.

**Strategy:** Sync operations update `entity_snapshots` (denormalized JSONB) immediately. A background job then fans out changes to the normalized entity tables (products, orders, etc.) for SQL queries.

```sql
-- Trigger: auto-update entity_snapshots on new sync_operations
CREATE OR REPLACE FUNCTION update_entity_snapshot()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status = 'ACCEPTED' OR NEW.status = 'CONFLICT_RESOLVED' THEN
        INSERT INTO entity_snapshots (
            store_id, entity_type, entity_id, payload,
            last_op_id, last_seq, last_device_id, updated_at,
            is_deleted
        ) VALUES (
            NEW.store_id, NEW.entity_type, NEW.entity_id, NEW.payload,
            NEW.id, NEW.server_seq, NEW.device_id, NOW(),
            NEW.operation = 'DELETE'
        )
        ON CONFLICT (store_id, entity_type, entity_id)
        DO UPDATE SET
            payload = EXCLUDED.payload,
            last_op_id = EXCLUDED.last_op_id,
            last_seq = EXCLUDED.last_seq,
            last_device_id = EXCLUDED.last_device_id,
            updated_at = EXCLUDED.updated_at,
            is_deleted = EXCLUDED.is_deleted
        WHERE entity_snapshots.last_seq < EXCLUDED.last_seq;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sync_op_snapshot
    AFTER INSERT ON sync_operations
    FOR EACH ROW
    EXECUTE FUNCTION update_entity_snapshot();
```

---

## 4. Server-Side Components

### 4.1 Project Structure

```
backend/api/src/main/kotlin/com/zyntasolutions/zyntapos/api/
├── routes/
│   ├── SyncRoutes.kt                  # MODIFY — full push/pull implementation
│   └── HealthRoutes.kt                # MODIFY — add sync queue depth
├── sync/
│   ├── SyncProcessor.kt               # NEW — orchestrates push processing
│   ├── ServerConflictResolver.kt       # NEW — LWW conflict resolution
│   ├── DeltaEngine.kt                 # NEW — cursor-based pull delta computation
│   ├── SyncValidator.kt               # NEW — batch validation + dedup
│   ├── EntityApplier.kt               # NEW — applies ops to normalized tables
│   └── SyncMetrics.kt                 # NEW — Micrometer metrics
├── repository/
│   ├── SyncOperationRepository.kt      # NEW — CRUD for sync_operations
│   ├── SyncCursorRepository.kt         # NEW — CRUD for sync_cursors
│   ├── ConflictLogRepository.kt        # NEW — CRUD for sync_conflict_log
│   ├── DeadLetterRepository.kt         # NEW — CRUD for sync_dead_letters
│   └── EntitySnapshotRepository.kt     # NEW — CRUD for entity_snapshots
└── models/
    └── SyncModels.kt                   # MODIFY — add server-side models

backend/sync/src/main/kotlin/com/zyntasolutions/zyntapos/sync/
├── routes/
│   └── SyncWebSocketRoutes.kt          # MODIFY — full fan-out implementation
├── hub/
│   ├── WebSocketHub.kt                 # NEW — manages WS connections per store
│   ├── ConnectionRegistry.kt           # NEW — tracks active connections
│   └── RedisPubSubListener.kt          # NEW — subscribes to Redis channels
└── models/
    └── WebSocketMessages.kt            # NEW — typed WS message envelopes
```

### 4.2 SyncProcessor (Core Orchestrator)

```kotlin
// backend/api/src/main/kotlin/.../sync/SyncProcessor.kt

class SyncProcessor(
    private val syncOpRepo: SyncOperationRepository,
    private val conflictResolver: ServerConflictResolver,
    private val validator: SyncValidator,
    private val entityApplier: EntityApplier,
    private val redisClient: RedisClient,
    private val metrics: SyncMetrics,
) {
    /**
     * Process a batch of sync operations from a POS terminal.
     *
     * Pipeline:
     * 1. Validate all operations (schema, entity types, payload format)
     * 2. Deduplicate (idempotent — skip already-processed op IDs)
     * 3. For each operation:
     *    a. Check for conflict (same entity_id modified since device's last pull)
     *    b. Resolve conflict if detected (LWW)
     *    c. Persist to sync_operations (triggers entity_snapshots update)
     *    d. Apply to normalized entity tables
     * 4. Publish change notification via Redis Pub/Sub
     * 5. Return response with accepted/rejected/conflict lists
     */
    suspend fun processPush(
        storeId: UUID,
        deviceId: String,
        operations: List<SyncOperationDto>,
    ): PushResult {
        val timer = metrics.pushTimer.startTimer()
        try {
            // Step 1: Validate
            val (valid, invalid) = validator.validateBatch(operations)
            val rejected = invalid.map { it.id }

            // Step 2: Dedup
            val existingIds = syncOpRepo.findExistingIds(valid.map { UUID.fromString(it.id) })
            val newOps = valid.filter { it.id !in existingIds.map { id -> id.toString() } }
            val alreadyAccepted = valid.filter { it.id in existingIds.map { id -> id.toString() } }
                .map { it.id }

            // Step 3: Process each operation
            val accepted = mutableListOf<String>()
            val conflicts = mutableListOf<String>()

            for (op in newOps) {
                val entityId = UUID.fromString(op.entityId)
                val latestSnapshot = syncOpRepo.findLatestForEntity(
                    storeId, op.entityType, entityId
                )

                if (latestSnapshot != null && latestSnapshot.deviceId != deviceId
                    && latestSnapshot.clientTimestamp > op.createdAt
                ) {
                    // Conflict detected
                    val resolution = conflictResolver.resolve(op, latestSnapshot)
                    syncOpRepo.insertWithConflict(
                        storeId, deviceId, op, resolution
                    )
                    conflicts.add(op.id)
                    metrics.conflictsTotal.increment()
                } else {
                    // No conflict — accept
                    syncOpRepo.insert(storeId, deviceId, op)
                    accepted.add(op.id)
                }
            }

            // Step 4: Apply to normalized tables
            val acceptedOps = newOps.filter { it.id in accepted }
            entityApplier.applyBatch(storeId, acceptedOps)

            // Step 5: Publish to Redis for WebSocket fan-out
            val notification = SyncNotification(
                storeId = storeId.toString(),
                senderDeviceId = deviceId,
                operationCount = accepted.size + conflicts.size,
                latestSeq = syncOpRepo.getLatestSeq(storeId),
            )
            redisClient.publish("sync:${storeId}", Json.encodeToString(notification))

            // Step 6: Get server timestamp
            val serverTimestamp = syncOpRepo.getServerTimestamp()

            metrics.opsAccepted.increment(accepted.size.toDouble())
            metrics.opsRejected.increment(rejected.size.toDouble())

            return PushResult(
                accepted = accepted + alreadyAccepted,
                rejected = rejected,
                conflicts = conflicts,
                serverTimestamp = serverTimestamp,
            )
        } finally {
            timer.observeDuration()
        }
    }
}
```

### 4.3 ServerConflictResolver

```kotlin
// backend/api/src/main/kotlin/.../sync/ServerConflictResolver.kt

class ServerConflictResolver(
    private val conflictLogRepo: ConflictLogRepository,
) {
    /**
     * Server-side LWW conflict resolution.
     * Mirrors the client-side ConflictResolver algorithm for consistency.
     *
     * Rules:
     * 1. Later client_timestamp wins (LWW)
     * 2. If timestamps equal, lexicographically larger device_id wins
     * 3. For PRODUCT entities, field-level merge (non-null fields from loser)
     */
    fun resolve(
        incoming: SyncOperationDto,
        existing: SyncOperationSnapshot,
    ): ConflictResolution {
        val incomingWins = when {
            incoming.createdAt > existing.clientTimestamp -> true
            incoming.createdAt < existing.clientTimestamp -> false
            else -> incoming.deviceId > existing.deviceId // Tiebreak
        }

        val strategy = when {
            incoming.createdAt != existing.clientTimestamp -> ResolutionStrategy.LWW_TIMESTAMP
            else -> ResolutionStrategy.DEVICE_ID_TIEBREAK
        }

        // For PRODUCT entities, attempt field-level merge
        val (finalPayload, finalStrategy) = if (
            incoming.entityType == "PRODUCT" && !incomingWins
        ) {
            val merged = mergeProductFields(
                winner = existing.payload,
                loser = incoming.payload
            )
            merged to ResolutionStrategy.FIELD_MERGE
        } else {
            val winner = if (incomingWins) incoming.payload else existing.payload
            winner to strategy
        }

        return ConflictResolution(
            winnerPayload = finalPayload,
            incomingWins = incomingWins,
            strategy = finalStrategy,
            conflictLog = ConflictLogEntry(
                entityType = incoming.entityType,
                entityId = incoming.entityId,
                localOpId = incoming.id,
                serverOpId = existing.opId.toString(),
                resolution = finalStrategy,
                localPayload = incoming.payload,
                serverPayload = existing.payload,
                mergedPayload = if (finalStrategy == ResolutionStrategy.FIELD_MERGE) finalPayload else null,
            )
        )
    }

    /**
     * For PRODUCT entities: merge non-null fields from loser into winner.
     * e.g., if winner has null imageUrl but loser has one, keep the loser's.
     */
    private fun mergeProductFields(winner: String, loser: String): String {
        val winnerMap = Json.parseToJsonElement(winner).jsonObject.toMutableMap()
        val loserMap = Json.parseToJsonElement(loser).jsonObject

        for ((key, value) in loserMap) {
            val winnerValue = winnerMap[key]
            if (winnerValue == null || winnerValue is JsonNull) {
                winnerMap[key] = value
            }
        }

        return Json.encodeToString(JsonObject(winnerMap))
    }
}
```

### 4.4 DeltaEngine (Pull Requests)

```kotlin
// backend/api/src/main/kotlin/.../sync/DeltaEngine.kt

class DeltaEngine(
    private val syncOpRepo: SyncOperationRepository,
    private val cursorRepo: SyncCursorRepository,
) {
    /**
     * Compute delta operations for a pull request.
     * Uses server_seq (monotonic) for cursor-based pagination.
     *
     * @param storeId Store to pull from
     * @param deviceId Requesting device (excluded from results — no echo)
     * @param since Last server_seq the device received (0 for first sync)
     * @param limit Max operations to return (default 50, max 200)
     * @return Delta response with operations and new cursor
     */
    suspend fun computeDelta(
        storeId: UUID,
        deviceId: String,
        since: Long,
        limit: Int = 50,
    ): PullResult {
        val effectiveLimit = limit.coerceIn(1, 200)

        // Query sync_operations where server_seq > since
        // Exclude REJECTED operations
        // Include operations from ALL devices (including sender — they may
        // have conflict-resolved versions they don't know about)
        val operations = syncOpRepo.findAfterSeq(
            storeId = storeId,
            afterSeq = since,
            limit = effectiveLimit + 1, // Fetch one extra to detect hasMore
        )

        val hasMore = operations.size > effectiveLimit
        val page = if (hasMore) operations.dropLast(1) else operations
        val newCursor = page.lastOrNull()?.serverSeq ?: since

        // Update cursor for this device
        cursorRepo.upsert(storeId, deviceId, newCursor)

        return PullResult(
            operations = page.map { it.toDto() },
            serverTimestamp = System.currentTimeMillis(),
            cursor = newCursor,
            hasMore = hasMore,
        )
    }
}
```

### 4.5 SyncValidator

```kotlin
// backend/api/src/main/kotlin/.../sync/SyncValidator.kt

class SyncValidator {
    companion object {
        const val MAX_BATCH_SIZE = 50
        const val MAX_PAYLOAD_SIZE = 1_048_576 // 1MB per operation
        val VALID_OPERATIONS = setOf("INSERT", "UPDATE", "DELETE")
        val VALID_ENTITY_TYPES = setOf(
            "PRODUCT", "CATEGORY", "CUSTOMER", "ORDER", "ORDER_ITEM",
            "SUPPLIER", "TAX_GROUP", "STOCK", "STOCK_ADJUSTMENT",
            "SETTINGS", "REGISTER_SESSION", "CASH_MOVEMENT",
            "PAYMENT_SPLIT", "COUPON", "EXPENSE", "EMPLOYEE",
            "SHIFT", "ATTENDANCE", "MEDIA_FILE", "E_INVOICE",
            "ACCOUNTING_ENTRY", "CUSTOMER_GROUP", "UNIT_OF_MEASURE",
            "WAREHOUSE", "INSTALLMENT", "LEAVE_RECORD", "PAYROLL",
        )
    }

    data class ValidationResult(
        val valid: List<SyncOperationDto>,
        val invalid: List<InvalidOperation>,
    )

    data class InvalidOperation(
        val id: String,
        val reason: String,
    )

    fun validateBatch(operations: List<SyncOperationDto>): ValidationResult {
        if (operations.size > MAX_BATCH_SIZE) {
            return ValidationResult(
                valid = emptyList(),
                invalid = operations.map {
                    InvalidOperation(it.id, "Batch exceeds max size of $MAX_BATCH_SIZE")
                }
            )
        }

        val valid = mutableListOf<SyncOperationDto>()
        val invalid = mutableListOf<InvalidOperation>()

        for (op in operations) {
            val errors = mutableListOf<String>()

            // Validate UUID format
            try { UUID.fromString(op.id) } catch (e: Exception) {
                errors.add("Invalid operation ID format")
            }
            try { UUID.fromString(op.entityId) } catch (e: Exception) {
                errors.add("Invalid entity ID format")
            }

            // Validate operation type
            if (op.operation !in VALID_OPERATIONS) {
                errors.add("Invalid operation: ${op.operation}")
            }

            // Validate entity type
            if (op.entityType !in VALID_ENTITY_TYPES) {
                errors.add("Unknown entity type: ${op.entityType}")
            }

            // Validate payload size
            if (op.payload.length > MAX_PAYLOAD_SIZE) {
                errors.add("Payload exceeds 1MB limit")
            }

            // Validate payload is valid JSON
            try { Json.parseToJsonElement(op.payload) } catch (e: Exception) {
                errors.add("Invalid JSON payload")
            }

            // Validate timestamp is reasonable (not in future, not too old)
            val now = System.currentTimeMillis()
            if (op.createdAt > now + 60_000) { // Allow 1 min clock skew
                errors.add("Timestamp is in the future")
            }

            if (errors.isEmpty()) {
                valid.add(op)
            } else {
                invalid.add(InvalidOperation(op.id, errors.joinToString("; ")))
            }
        }

        return ValidationResult(valid, invalid)
    }
}
```

---

## 5. WebSocket Hub (Real-time Fan-out)

### 5.1 Architecture

```
                   ┌──────────────────────────────────┐
                   │        Sync Service (:8082)       │
                   │                                   │
                   │   ┌─────────────────────────┐    │
                   │   │    WebSocketHub          │    │
                   │   │                          │    │
Redis ──SUBSCRIBE──│──→│  Store A: [ws1, ws2, ws3]│    │
  sync:{storeId}   │   │  Store B: [ws4, ws5]     │    │
                   │   │  Store C: [ws6]           │    │
                   │   │                          │    │
                   │   │  On message:             │    │
                   │   │  1. Parse storeId        │    │
                   │   │  2. Find all connections  │    │
                   │   │  3. Exclude sender device │    │
                   │   │  4. Send to remaining    │    │
                   │   └─────────────────────────┘    │
                   └──────────────────────────────────┘
```

### 5.2 WebSocketHub Implementation

```kotlin
// backend/sync/src/main/kotlin/.../hub/WebSocketHub.kt

class WebSocketHub {
    // storeId -> set of connections
    private val connections = ConcurrentHashMap<String, ConcurrentHashMap<String, WebSocketSession>>()

    fun register(storeId: String, deviceId: String, session: WebSocketSession) {
        connections.getOrPut(storeId) { ConcurrentHashMap() }[deviceId] = session
        logger.info("WS registered: store=$storeId device=$deviceId (total: ${connectionCount(storeId)})")
    }

    fun unregister(storeId: String, deviceId: String) {
        connections[storeId]?.remove(deviceId)
        if (connections[storeId]?.isEmpty() == true) {
            connections.remove(storeId)
        }
        logger.info("WS unregistered: store=$storeId device=$deviceId")
    }

    suspend fun broadcast(storeId: String, excludeDeviceId: String?, message: String) {
        val storeConnections = connections[storeId] ?: return

        storeConnections.forEach { (deviceId, session) ->
            if (deviceId != excludeDeviceId && session.isActive) {
                try {
                    session.send(Frame.Text(message))
                } catch (e: Exception) {
                    logger.warn("Failed to send WS to device=$deviceId: ${e.message}")
                    unregister(storeId, deviceId)
                }
            }
        }
    }

    fun connectionCount(storeId: String): Int =
        connections[storeId]?.size ?: 0

    fun totalConnections(): Int =
        connections.values.sumOf { it.size }
}
```

### 5.3 WebSocket Message Protocol

```kotlin
// WebSocket message envelope
@Serializable
sealed class WsMessage {
    @Serializable
    @SerialName("delta")
    data class Delta(
        val storeId: String,
        val operationCount: Int,
        val latestSeq: Long,
        val operations: List<SyncOperationDto> = emptyList(), // Included for small deltas (< 10 ops)
    ) : WsMessage()

    @Serializable
    @SerialName("notify")
    data class Notify(
        val storeId: String,
        val message: String,       // "sync_available" — client should pull
        val latestSeq: Long,
    ) : WsMessage()

    @Serializable
    @SerialName("ack")
    data class Ack(
        val storeId: String,
        val deviceId: String,
        val connectedAt: Long,
    ) : WsMessage()

    @Serializable
    @SerialName("ping")
    data object Ping : WsMessage()

    @Serializable
    @SerialName("pong")
    data object Pong : WsMessage()
}
```

### 5.4 WebSocket Route (Full Implementation)

```kotlin
// backend/sync/src/main/kotlin/.../routes/SyncWebSocketRoutes.kt

fun Route.syncWebSocketRoutes(hub: WebSocketHub, jwtVerifier: JWTVerifier) {
    webSocket("/v1/sync/ws") {
        // Extract JWT from query param or header
        val token = call.request.queryParameters["token"]
            ?: call.request.headers["Authorization"]?.removePrefix("Bearer ")
            ?: return@webSocket close(CloseReason(4001, "Missing authentication"))

        val claims = try {
            jwtVerifier.verify(token)
        } catch (e: Exception) {
            return@webSocket close(CloseReason(4001, "Invalid token"))
        }

        val storeId = claims.getClaim("storeId").asString()
            ?: return@webSocket close(CloseReason(4002, "Missing storeId claim"))
        val userId = claims.subject
        val deviceId = call.request.queryParameters["deviceId"] ?: "unknown-${userId}"

        // Register connection
        hub.register(storeId, deviceId, this)

        // Send ack
        send(Frame.Text(Json.encodeToString(
            WsMessage.Ack(storeId, deviceId, System.currentTimeMillis())
        )))

        try {
            // Keep-alive ping/pong + message handling
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        if (text == """{"type":"ping"}""") {
                            send(Frame.Text("""{"type":"pong"}"""))
                        }
                        // Future: handle client-initiated messages
                    }
                    is Frame.Ping -> send(Frame.Pong(frame.data))
                    else -> {}
                }
            }
        } finally {
            hub.unregister(storeId, deviceId)
        }
    }
}
```

### 5.5 Redis Pub/Sub Listener

```kotlin
// backend/sync/src/main/kotlin/.../hub/RedisPubSubListener.kt

class RedisPubSubListener(
    private val redisClient: RedisClient,
    private val hub: WebSocketHub,
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job = scope.launch {
            // Subscribe to all sync channels using pattern
            redisClient.psubscribe("sync:*") { channel, message ->
                val storeId = channel.removePrefix("sync:")
                val notification = Json.decodeFromString<SyncNotification>(message)

                // If small delta (< 10 ops), include operations in WS message
                // Otherwise, just notify client to pull
                val wsMessage = if (notification.operationCount <= 10) {
                    WsMessage.Delta(
                        storeId = storeId,
                        operationCount = notification.operationCount,
                        latestSeq = notification.latestSeq,
                    )
                } else {
                    WsMessage.Notify(
                        storeId = storeId,
                        message = "sync_available",
                        latestSeq = notification.latestSeq,
                    )
                }

                hub.broadcast(storeId, notification.senderDeviceId, Json.encodeToString(wsMessage))
            }
        }
    }

    fun stop() {
        job?.cancel()
    }
}
```

---

## 6. API Route Implementation

### 6.1 Push Route

```kotlin
// backend/api/src/main/kotlin/.../routes/SyncRoutes.kt

fun Route.syncRoutes(syncProcessor: SyncProcessor, deltaEngine: DeltaEngine) {
    authenticate("jwt") {
        route("/v1/sync") {

            // POST /v1/sync/push
            post("/push") {
                val principal = call.principal<JWTPrincipal>()!!
                val storeId = UUID.fromString(principal.getClaim("storeId", String::class)!!)

                val request = call.receive<PushRequest>()

                // Validate batch size
                if (request.operations.size > 50) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                        code = "SYNC_BATCH_TOO_LARGE",
                        message = "Maximum batch size is 50 operations"
                    ))
                    return@post
                }

                val result = syncProcessor.processPush(
                    storeId = storeId,
                    deviceId = request.deviceId,
                    operations = request.operations,
                )

                call.respond(HttpStatusCode.OK, PushResponse(
                    accepted = result.accepted.size,
                    rejected = result.rejected.size,
                    conflicts = result.conflicts,
                    serverVectorClock = result.serverTimestamp,
                ))
            }

            // GET /v1/sync/pull?since=<cursor>&limit=50
            get("/pull") {
                val principal = call.principal<JWTPrincipal>()!!
                val storeId = UUID.fromString(principal.getClaim("storeId", String::class)!!)
                val deviceId = call.request.queryParameters["deviceId"] ?: "unknown"
                val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50

                val result = deltaEngine.computeDelta(
                    storeId = storeId,
                    deviceId = deviceId,
                    since = since,
                    limit = limit,
                )

                call.respond(HttpStatusCode.OK, PullResponse(
                    operations = result.operations,
                    serverVectorClock = result.serverTimestamp,
                    hasMore = result.hasMore,
                ))
            }
        }
    }
}
```

---

## 7. Multi-Store Data Isolation

### 7.1 Enforcement Strategy

Every sync query is scoped by `store_id` extracted from the JWT `storeId` claim. This is enforced at three levels:

| Level | How |
|-------|-----|
| **JWT** | Token contains `storeId` claim, signed by RS256 private key |
| **API Routes** | Extract `storeId` from JWT principal, pass to all repository methods |
| **PostgreSQL** | All sync tables have `store_id` column, all queries filter by it |

### 7.2 Row-Level Security (Optional Hardening)

```sql
-- Optional: PostgreSQL RLS for defense-in-depth
ALTER TABLE sync_operations ENABLE ROW LEVEL SECURITY;

CREATE POLICY sync_ops_store_isolation ON sync_operations
    USING (store_id = current_setting('app.store_id')::uuid);

-- Set per-request: SET LOCAL app.store_id = '<store-uuid>';
```

> **Note:** RLS is optional defense-in-depth. The primary isolation is at the application layer (JWT claim extraction). RLS adds a second barrier in case of application bugs.

---

## 8. Performance Considerations

### 8.1 Indexing Strategy

| Query Pattern | Index | Expected Performance |
|--------------|-------|---------------------|
| Pull delta (`WHERE store_id AND server_seq > cursor`) | `idx_sync_ops_pull` (B-tree) | O(log n) seek + O(k) scan |
| Dedup check (`WHERE id IN (...)`) | `idx_sync_ops_dedup` (unique) | O(k × log n) |
| Conflict check (`WHERE store_id AND entity_type AND entity_id`) | `idx_sync_ops_entity` | O(log n) |
| Entity snapshot lookup | `entity_snapshots` PK | O(1) hash |

### 8.2 Connection Pooling

```kotlin
// HikariCP configuration for sync workload
val hikariConfig = HikariConfig().apply {
    maximumPoolSize = 20           // 500 terminals ÷ 25 ops/sec avg = 20 connections
    minimumIdle = 5
    connectionTimeout = 10_000     // 10s
    idleTimeout = 300_000          // 5 min
    maxLifetime = 1_800_000        // 30 min
    leakDetectionThreshold = 30_000
}
```

### 8.3 Batch Processing

- Push operations are processed in a single database transaction (all-or-nothing per batch)
- Use `COPY` for bulk inserts when batch size > 20 (2-5x faster than individual INSERTs)
- Connection pool is tuned for the expected 500-terminal load

### 8.4 Expected Throughput

| Metric | Target | Rationale |
|--------|--------|-----------|
| Push p50 latency | < 50ms | Fast for real-time POS |
| Push p95 latency | < 200ms | Acceptable under load |
| Pull p50 latency | < 30ms | Indexed cursor scan |
| Pull p95 latency | < 100ms | Large delta pulls |
| Throughput | 1,000 ops/sec | 500 terminals × 2 ops/sec peak |
| WebSocket broadcast | < 100ms fan-out | Redis Pub/Sub + in-memory hub |

### 8.5 Partitioning (Future — 10,000+ Stores)

When sync_operations exceeds 100M rows:

```sql
-- Partition by store_id (hash) for parallel query execution
CREATE TABLE sync_operations (
    ...
) PARTITION BY HASH (store_id);

-- Create 16 partitions
CREATE TABLE sync_operations_p0 PARTITION OF sync_operations FOR VALUES WITH (MODULUS 16, REMAINDER 0);
-- ... through p15
```

---

## 9. Sync Metrics & Monitoring

### 9.1 Micrometer Metrics

```kotlin
class SyncMetrics(registry: MeterRegistry) {
    // Counters
    val opsAccepted = registry.counter("sync.ops.accepted")
    val opsRejected = registry.counter("sync.ops.rejected")
    val conflictsTotal = registry.counter("sync.conflicts.total")
    val deadLettersTotal = registry.counter("sync.dead_letters.total")

    // Timers
    val pushTimer = registry.timer("sync.push.duration")
    val pullTimer = registry.timer("sync.pull.duration")
    val conflictResolutionTimer = registry.timer("sync.conflict.resolution.duration")

    // Gauges
    val wsConnectionsGauge = registry.gauge("sync.ws.connections", AtomicInteger(0))
    val pendingOpsGauge = registry.gauge("sync.queue.pending", AtomicLong(0))
}
```

### 9.2 Health Endpoint Extension

```kotlin
// Add to /health response
get("/health/sync") {
    val queueDepth = syncOpRepo.countPending()
    val conflictRate = syncMetrics.conflictsTotal.count() /
        maxOf(syncMetrics.opsAccepted.count(), 1.0)
    val wsConnections = hub.totalConnections()

    call.respond(mapOf(
        "status" to "ok",
        "queue_depth" to queueDepth,
        "conflict_rate" to "%.4f".format(conflictRate),
        "ws_connections" to wsConnections,
        "ops_accepted_total" to syncMetrics.opsAccepted.count(),
    ))
}
```

### 9.3 Uptime Kuma Integration (007c)

Add sync-specific monitors:

| Monitor | Type | URL | Alert Threshold |
|---------|------|-----|----------------|
| Sync Queue Depth | HTTP (keyword) | `http://api:8080/health/sync` | `queue_depth` > 10,000 |
| Sync Push Latency | HTTP (response time) | `http://api:8080/v1/sync/push` | p95 > 500ms |
| WS Connections | HTTP (keyword) | `http://sync:8082/health` | connections = 0 (if stores active) |

---

## 10. Admin Panel Integration (007a)

### 10.1 Sync Dashboard Page

The admin panel (007a) should display a sync management page:

```
┌──────────────────────────────────────────────────────┐
│  Sync Management                                     │
│                                                      │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌────────┐ │
│  │ 24,531  │  │  0.3%   │  │  142    │  │  3     │ │
│  │ Ops/day │  │Conflict │  │  WS     │  │ Dead   │ │
│  │         │  │  Rate   │  │  Conns  │  │Letters │ │
│  └─────────┘  └─────────┘  └─────────┘  └────────┘ │
│                                                      │
│  Per-Store Sync Status                               │
│  ┌───────────┬──────────┬──────────┬──────────────┐ │
│  │ Store     │ Last Sync│ Queue    │ Status       │ │
│  ├───────────┼──────────┼──────────┼──────────────┤ │
│  │ Store A   │ 2m ago   │ 0        │ ● Synced     │ │
│  │ Store B   │ 15m ago  │ 23       │ ● Syncing    │ │
│  │ Store C   │ 2h ago   │ 156      │ ● Behind     │ │
│  │ Store D   │ 3d ago   │ 1,200    │ ● Offline    │ │
│  └───────────┴──────────┴──────────┴──────────────┘ │
│                                                      │
│  Recent Conflicts                                    │
│  • Store B: PRODUCT "Coca Cola" — LWW resolved      │
│  • Store A: CUSTOMER "John Doe" — Field merge        │
│                                                      │
│  Dead Letters (requires review)                      │
│  • Store C: ORDER #1234 — Invalid payload format     │
│    [Review] [Retry] [Discard]                        │
└──────────────────────────────────────────────────────┘
```

### 10.2 Admin API Endpoints for Sync

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/admin/sync/status` | Per-store sync status overview |
| GET | `/admin/sync/stores/{storeId}` | Detailed sync status for one store |
| POST | `/admin/sync/{storeId}/force` | Force immediate sync for a store |
| GET | `/admin/sync/conflicts` | List unresolved conflicts |
| POST | `/admin/sync/conflicts/{id}/resolve` | Manually resolve a conflict |
| GET | `/admin/sync/dead-letters` | List dead letter operations |
| POST | `/admin/sync/dead-letters/{id}/retry` | Retry a dead letter |
| DELETE | `/admin/sync/dead-letters/{id}` | Discard a dead letter |

---

## 11. Testing Strategy

### 11.1 Unit Tests

| Component | Test File | Focus |
|-----------|-----------|-------|
| SyncValidator | `SyncValidatorTest.kt` | Valid/invalid operations, edge cases |
| ServerConflictResolver | `ServerConflictResolverTest.kt` | LWW, tiebreak, field merge |
| DeltaEngine | `DeltaEngineTest.kt` | Cursor pagination, hasMore, empty results |
| WebSocketHub | `WebSocketHubTest.kt` | Register/unregister, broadcast, exclusion |

### 11.2 Integration Tests

| Test | What it verifies |
|------|-----------------|
| Push → Pull roundtrip | Operation pushed by device A appears in device B's pull |
| Conflict detection | Two devices modify same entity, conflict logged |
| Dedup idempotency | Same operation ID pushed twice, accepted once |
| Multi-store isolation | Store A's operations never appear in Store B's pull |
| WebSocket notification | Push triggers WS message to other connected devices |
| Dead letter handling | Invalid operations land in dead letter queue |
| Cursor-based pagination | Large deltas paginated correctly with hasMore |

### 11.3 Load Tests

```bash
# Using k6 for load testing
# 500 virtual POS terminals, each pushing 2 ops/sec
k6 run --vus 500 --duration 5m sync-load-test.js
```

Target: < 200ms p95 at 1,000 ops/sec sustained.

---

## 12. Implementation Steps (Ordered)

| Step | Task | Time | Dependencies |
|------|------|------|-------------|
| 1 | Write Flyway migration `V4__sync_engine.sql` (all tables + indexes + triggers) | 1.5 hrs | PostgreSQL running |
| 2 | Implement `SyncOperationRepository` (CRUD for sync_operations) | 1.5 hrs | Step 1 |
| 3 | Implement `SyncCursorRepository` + `ConflictLogRepository` + `DeadLetterRepository` | 1.5 hrs | Step 1 |
| 4 | Implement `EntitySnapshotRepository` | 1 hr | Step 1 |
| 5 | Implement `SyncValidator` + unit tests | 1 hr | — |
| 6 | Implement `ServerConflictResolver` + unit tests | 1.5 hrs | — |
| 7 | Implement `DeltaEngine` + unit tests | 1 hr | Steps 2-3 |
| 8 | Implement `SyncProcessor` (push orchestrator) | 2 hrs | Steps 2-6 |
| 9 | Implement `EntityApplier` (fan-out to normalized tables) | 1.5 hrs | Step 4 |
| 10 | Update `SyncRoutes.kt` — full push/pull implementation | 1 hr | Steps 7-8 |
| 11 | Implement `WebSocketHub` + `ConnectionRegistry` | 1 hr | — |
| 12 | Implement `RedisPubSubListener` | 1 hr | Step 11 |
| 13 | Update `SyncWebSocketRoutes.kt` — full WS implementation | 1 hr | Steps 11-12 |
| 14 | Implement `SyncMetrics` (Micrometer) | 30 min | — |
| 15 | Write integration tests (push→pull roundtrip, conflicts, isolation) | 2 hrs | Steps 1-13 |
| 16 | Add sync-specific monitors to Uptime Kuma | 30 min | 007c |
| 17 | Update sync admin endpoints for 007a panel | 1.5 hrs | Steps 2-3 |
| 18 | Load testing with k6 | 1 hr | Steps 1-13 |
| 19 | Update API docs spec (`sync-v1.yaml`) | 1 hr | 007e |
| 20 | Deploy to VPS and verify with real POS terminal | 1 hr | All above |

**Total estimated time: ~5 working days**

---

## 13. Files to Create / Modify

```
backend/api/src/main/
├── resources/db/migration/
│   └── V4__sync_engine.sql                     # NEW — all sync tables
├── kotlin/.../api/
│   ├── routes/
│   │   └── SyncRoutes.kt                       # MODIFY — full implementation
│   ├── sync/
│   │   ├── SyncProcessor.kt                    # NEW — push orchestrator
│   │   ├── ServerConflictResolver.kt            # NEW — LWW conflict resolution
│   │   ├── DeltaEngine.kt                      # NEW — cursor-based pull
│   │   ├── SyncValidator.kt                    # NEW — batch validation
│   │   ├── EntityApplier.kt                    # NEW — normalized table updates
│   │   └── SyncMetrics.kt                      # NEW — Micrometer metrics
│   ├── repository/
│   │   ├── SyncOperationRepository.kt           # NEW
│   │   ├── SyncCursorRepository.kt              # NEW
│   │   ├── ConflictLogRepository.kt             # NEW
│   │   ├── DeadLetterRepository.kt              # NEW
│   │   └── EntitySnapshotRepository.kt          # NEW
│   └── models/
│       └── SyncModels.kt                        # MODIFY — add server models

backend/sync/src/main/kotlin/.../sync/
├── routes/
│   └── SyncWebSocketRoutes.kt                   # MODIFY — full WS implementation
├── hub/
│   ├── WebSocketHub.kt                          # NEW
│   ├── ConnectionRegistry.kt                    # NEW
│   └── RedisPubSubListener.kt                   # NEW
└── models/
    └── WebSocketMessages.kt                     # NEW

backend/api/src/test/kotlin/.../
├── sync/
│   ├── SyncValidatorTest.kt                     # NEW
│   ├── ServerConflictResolverTest.kt             # NEW
│   └── DeltaEngineTest.kt                       # NEW
└── integration/
    ├── SyncPushPullIntegrationTest.kt            # NEW
    └── MultiStoreIsolationTest.kt                # NEW

backend/sync/src/test/kotlin/.../
└── hub/
    └── WebSocketHubTest.kt                       # NEW
```

---

## 14. Validation Checklist

### Database
- [ ] `V4__sync_engine.sql` migration runs without errors
- [ ] All 5 tables created: `sync_operations`, `sync_cursors`, `sync_conflict_log`, `sync_dead_letters`, `entity_snapshots`
- [ ] All indexes created and verified with `EXPLAIN ANALYZE`
- [ ] Trigger `trg_sync_op_snapshot` fires on INSERT

### Push Pipeline
- [ ] Valid batch accepted (200 OK with accepted count)
- [ ] Invalid operations rejected with error reasons
- [ ] Duplicate operation IDs handled idempotently
- [ ] Batch > 50 rejected with `SYNC_BATCH_TOO_LARGE`
- [ ] Conflict detected when same entity modified by different devices
- [ ] LWW resolution picks correct winner (later timestamp)
- [ ] Device ID tiebreak works when timestamps equal
- [ ] PRODUCT field merge fills null fields from loser
- [ ] Conflict logged in `sync_conflict_log`
- [ ] Dead letters created for permanently invalid operations

### Pull Pipeline
- [ ] Delta returns only operations after cursor
- [ ] `hasMore=true` when more operations exist
- [ ] Cursor updated after successful pull
- [ ] Empty result when no new operations
- [ ] Limit parameter respected (max 200)

### Multi-Store Isolation
- [ ] Store A push never visible in Store B pull
- [ ] JWT without `storeId` claim rejected (403)
- [ ] Invalid `storeId` returns empty results (not error)

### WebSocket
- [ ] Connection established with valid JWT
- [ ] Connection rejected with invalid JWT (4001)
- [ ] Ack message sent on connection
- [ ] Push from device A triggers WS message to device B (same store)
- [ ] Push from device A does NOT trigger WS to device A (no echo)
- [ ] Push from Store A does NOT trigger WS to Store B devices
- [ ] Ping/pong keep-alive working
- [ ] Disconnection cleanup (unregister from hub)

### Performance
- [ ] Push p95 < 200ms with 50-operation batch
- [ ] Pull p95 < 100ms for 50-operation delta
- [ ] 500 concurrent WS connections stable
- [ ] No memory leaks after 1 hour sustained load

### Monitoring
- [ ] `/health/sync` returns queue depth + conflict rate + WS connections
- [ ] Uptime Kuma monitors configured for sync endpoints
- [ ] Micrometer metrics exposed for push/pull duration, op counts

---

## 15. Client-Side Changes Required

The KMM app's `SyncEngine` and `ApiService` are already implemented and compatible. Minor updates needed:

| Change | File | Description |
|--------|------|-------------|
| Add `cursor` to pull response | `SyncDto.kt` | Server now returns `cursor` field for pagination |
| Use `cursor` instead of `lastSyncTimestamp` | `SyncEngine.kt` | Replace timestamp-based pull with cursor-based |
| Handle `hasMore` in pull | `SyncEngine.kt` | Loop pull until `hasMore=false` |
| Add WS reconnection | `SyncEngine.kt` | Connect to `wss://sync.zyntapos.com/v1/sync/ws` for real-time notifications |
| Add `deviceId` to push request | `KtorApiService.kt` | Include device identifier in push payload |

These are backward-compatible additions — the existing sync pipeline continues to work during migration.

---

## 16. Migration Path (Phase 2 → Phase 3)

### Phase 2 (This TODO): LWW Sync
- Server-side LWW conflict resolution
- Cursor-based pull with pagination
- WebSocket notifications
- Dead letter queue

### Phase 3 (Future): CRDT Sync
- Enable `version_vectors` table (already staged in client SQLDelight schema)
- Replace LWW with Hybrid Logical Clock (HLC)
- Field-level CRDT merge for all entity types (not just PRODUCT)
- Cross-store sync for multi-store transfers
- Tombstone cleanup (garbage collection for deleted entities)
