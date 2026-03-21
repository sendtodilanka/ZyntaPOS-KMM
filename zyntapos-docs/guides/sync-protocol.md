# Sync Protocol Guide

ZyntaPOS uses an offline-first sync protocol combining REST push/pull with WebSocket
real-time notifications. Devices operate independently on local SQLite and sync
asynchronously when connected.

## Architecture

```
POS App (SQLite) ──push──► API Service (PostgreSQL) ──notify──► Redis Pub/Sub
                                                                      │
POS App ◄──delta──── Sync Service (WebSocket) ◄────subscribe──────────┘
```

1. **POS App** writes to local SQLite immediately (offline-first)
2. **SyncEngine** batches pending operations and pushes via REST `POST /v1/sync/push`
3. **API Service** persists operations, applies to normalized tables, publishes to Redis
4. **Sync Service** receives Redis notification, relays to all connected store devices via WebSocket
5. **POS App** pulls delta operations via `GET /v1/sync/pull`

## Outbox Pattern (Client-Side)

Every local write enqueues a `pending_operations` row in the same transaction:

```
[Local Write] ──► PENDING ──► SyncEngine polls (batch 50) ──► SYNCING
                                                                  │
                                              ┌───────────────────┤
                                          Server ACK         Server NACK
                                              │                   │
                                           SYNCED          retry_count += 1
                                                                  │
                                                         retry < 5 → PENDING
                                                         retry ≥ 5 → FAILED
```

**Crash recovery:** On startup, rows stuck in SYNCING for >10 minutes are reset to PENDING.

## Push Operations

```bash
POST /v1/sync/push
Content-Type: application/json
Authorization: Bearer <rs256-jwt>

{
  "device_id": "android-tablet-001",
  "operations": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "entity_type": "PRODUCT",
      "entity_id": "prod-123",
      "operation": "UPDATE",
      "payload": "{\"name\":\"Chicken Burger\",\"price\":950.00}",
      "created_at": 1710326400000,
      "retry_count": 0
    }
  ]
}
```

**Response:**

```json
{
  "accepted": ["550e8400-e29b-41d4-a716-446655440000"],
  "rejected": [],
  "conflicts": [],
  "delta_operations": [],
  "server_timestamp": 1710326401000
}
```

- **accepted** — Operation IDs the server stored. Client marks these SYNCED.
- **rejected** — Operation IDs permanently rejected (e.g., invalid entity). Client marks FAILED.
- **conflicts** — Operation IDs that triggered conflict resolution (resolved server-side via LWW).
- **delta_operations** — Piggyback deltas in the push ACK (server changes the client missed).
- **server_timestamp** — Client stores this as `LAST_SYNC_TS` for the next pull.

**Constraints:** Maximum 50 operations per push request. GZIP compression is supported.

## Pull Operations

```bash
GET /v1/sync/pull?since=1710326400000&limit=100&deviceId=android-tablet-001
Authorization: Bearer <rs256-jwt>
```

**Response:**

```json
{
  "operations": [
    {
      "id": "op-server-uuid",
      "entity_type": "PRODUCT",
      "entity_id": "prod-456",
      "operation": "CREATE",
      "payload": "{\"id\":\"prod-456\",\"name\":\"New Item\",\"price\":500.00}",
      "created_at": 1710326500000
    }
  ],
  "server_timestamp": 1710326501000,
  "server_vector_clock": 142,
  "has_more": false
}
```

- **since** — Epoch ms; fetch operations created after this timestamp.
- **limit** — 1–200 (default 100). Use `has_more` for pagination.
- **has_more** — If true, call pull again with the new `server_timestamp` as `since`.

## Entity Types

| Entity Type | Operation Types | CRDT Strategy |
|-------------|----------------|---------------|
| `PRODUCT` | CREATE, UPDATE, DELETE | LWW + field-level merge |
| `ORDER` | CREATE, UPDATE | LWW |
| `CUSTOMER` | CREATE, UPDATE, DELETE | LWW |
| `CATEGORY` | CREATE, UPDATE, DELETE | LWW |
| `SUPPLIER` | CREATE, UPDATE, DELETE | LWW |
| `STOCK_ADJUSTMENT` | CREATE | APPEND_ONLY (G-Counter) |
| `USER` | Server-managed (read-only on device) | — |

## Conflict Resolution

### Strategy: Last-Write-Wins (LWW)

When two devices modify the same entity concurrently:

1. **Primary rule — LWW:** The operation with the later `created_at` timestamp wins.
2. **Tiebreaker — Device ID:** If timestamps are identical, the device ID that sorts later
   alphabetically wins (deterministic across all nodes).
3. **PRODUCT field merge:** For PRODUCT conflicts, non-null fields from the *losing* operation
   are carried into the winner's payload if the winner's field is null/blank. This reduces
   data loss from partial-update scenarios.

### CRDT Strategies

| Strategy | Behavior | Used for |
|----------|----------|----------|
| `LWW` | Last write wins by timestamp | Products, orders, customers, categories, suppliers |
| `FIELD_MERGE` | LWW + non-null field carry-forward | Products (automatic) |
| `APPEND_ONLY` | No conflict — all entries kept | Stock adjustments, accounting entries |

### Conflict Audit Trail

Every conflict is recorded in the `conflict_log` table:

```json
{
  "entity_type": "PRODUCT",
  "entity_id": "prod-123",
  "local_value": "{\"price\":900}",
  "server_value": "{\"price\":950}",
  "resolved_by": "SERVER",
  "resolved_at": 1710326500000
}
```

Resolution types: `LOCAL`, `SERVER`, `MERGE`, `MANUAL`.

## Version Vectors

Each entity is tracked with a version vector per device:

```sql
version_vectors (entity_type, entity_id, device_id, version, updated_at)
```

`SyncEnqueuer` increments the version on every local write. Version vectors enable
detection of concurrent modifications across devices.

## Multi-Store Isolation

All sync operations are scoped to a `store_id`. A device belonging to Store A never
receives operations from Store B. The API validates store ownership on every push.

## Sync Priority

Operations are synced in priority order:

| Priority | Entity Types |
|----------|-------------|
| Highest | Orders, payments |
| High | Products, stock adjustments |
| Normal | Customers, categories, suppliers |
| Low | Settings, preferences |

## WebSocket Real-Time Notifications

### Connection

```
WSS wss://sync.zyntapos.com/v1/sync/ws?deviceId=android-tablet-001
Authorization: Bearer <rs256-jwt>
```

### Message Format

All messages are JSON with a `type` field:

```json
{"type": "sync_delta", "payload": {...}}
```

### Server → Client Messages

| Type | Description | Action |
|------|-------------|--------|
| `pong` | Ping response | Reset keepalive timer |
| `sync_delta` | New delta operations available | Apply directly or trigger pull |
| `force_sync` | Admin-triggered sync | Pull immediately |
| `diag_command` | Diagnostic command from technician | Execute and respond |

### Client → Server Messages

| Type | Description |
|------|-------------|
| `ping` | Keep-alive heartbeat |
| `sync_push` | Push operations via WebSocket |
| `sync_pull` | Request delta pull |
| `diag_response` | Diagnostic data response |

## Reconnection Strategy

POS apps implement exponential backoff:

1. Immediate retry
2. Wait 1s → 2s → 4s → 8s → 16s → cap at 30s

On reconnection, always perform a pull to catch up on missed changes.

## Bandwidth Optimization

- **GZIP** — HTTP Content-Encoding for push/pull requests
- **Deduplication** — Before push, only the latest pending operation per entity is sent
- **Batch size** — Max 50 operations per push; paginated pull with `has_more`
- **Delta piggybacking** — Push response includes delta operations the client missed
