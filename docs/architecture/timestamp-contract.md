# Timestamp Contract — ZyntaPOS

**Created:** 2026-03-21
**Status:** Accepted

---

## Overview

All timestamps in ZyntaPOS follow a single canonical rule per API surface.
This document defines the contract for each layer.

---

## Wire Format (JSON APIs)

### POS API (sync operations, auth, products)

| Field | Type | Format | Example |
|-------|------|--------|---------|
| `created_at` | `Long` | Epoch milliseconds (UTC) | `1711036800000` |
| `updated_at` | `Long` | Epoch milliseconds (UTC) | `1711036800000` |
| `server_timestamp` | `Long` | Epoch milliseconds (UTC) | `1711036800000` |
| `expires_in` | `Long` | Duration in seconds | `3600` |

**Rule:** All POS API timestamps are **epoch milliseconds** (`Long`).
The KMM client produces and consumes this format exclusively.

### Admin Panel API

| Field | Type | Format | Example |
|-------|------|--------|---------|
| `created_at` | `String` | ISO 8601 (UTC) | `"2025-03-21T12:00:00Z"` |
| `updated_at` | `String` | ISO 8601 (UTC) | `"2025-03-21T12:00:00Z"` |
| Session `created_at` | `Long` | Epoch milliseconds | `1711036800000` |
| Session `expires_at` | `Long` | Epoch milliseconds | `1711040400000` |

**Rule:** Admin API audit/display timestamps use **ISO 8601 strings**.
Session-related timestamps use **epoch milliseconds** for token math.

### License API

| Field | Type | Format | Example |
|-------|------|--------|---------|
| Admin model timestamps | `String` | ISO 8601 (UTC) | `"2025-03-21T12:00:00Z"` |
| Activation response `expires_at` | `Long?` | Epoch milliseconds | `1711036800000` |
| Heartbeat `server_timestamp` | `Long` | Epoch milliseconds | `1711036800000` |
| Status response timestamps | `Long` | Epoch milliseconds | `1711036800000` |

---

## Database Storage

### PostgreSQL (backend services)

All `created_at`, `updated_at`, `expires_at` columns use
`TIMESTAMP WITH TIME ZONE` (mapped to `OffsetDateTime` in Exposed).
PostgreSQL stores these in UTC internally.

### Sync metadata columns

`client_timestamp`, `local_timestamp`, `server_ts` in sync tables use
`BIGINT` (epoch ms) for fast numeric cursor comparison.

### SQLite (KMM client)

All timestamp columns are `TEXT` storing ISO 8601 strings,
or `INTEGER` storing epoch milliseconds, depending on the table.
The `sync_queue.created_at` column uses epoch ms (`INTEGER`).

---

## Conversion Rules

| From | To | Method |
|------|----|--------|
| `OffsetDateTime` → epoch ms | `TimestampUtils.toEpochMs(odt)` |
| epoch ms → `OffsetDateTime` | `TimestampUtils.fromEpochMs(epochMs)` |
| `OffsetDateTime` → ISO 8601 | `TimestampUtils.toIso8601(odt)` |
| epoch ms → ISO 8601 | `TimestampUtils.epochMsToIso8601(epochMs)` |

**All conversion logic is centralized in:**
`backend/common/src/main/kotlin/.../common/TimestampUtils.kt`

---

## Validation Rules

### Sync operations (`SyncValidator`)

1. **Non-negative:** `created_at >= 0`
2. **Clock skew:** `created_at <= now + 60s`
3. **Payload timestamps (strict):** `created_at`, `updated_at` fields inside
   entity payloads must be either absent, null, or in range
   `[2020-01-01T00:00:00Z, now + 60s]`.

### Minimum valid timestamp

`2020-01-01T00:00:00Z` (epoch ms: `1577836800000`).
Timestamps before this are rejected in strict mode (payload field validation).
The operation-level `created_at` allows zero for backward compatibility.

---

## Clock Skew Handling

- Max tolerance: **60 seconds** ahead of server time.
- Sync operations with `created_at > now + 60s` are rejected.
- KMM client uses device clock for `created_at`; server records
  its own `server_timestamp` independently.
- No NTP requirement on client — the 60s buffer accommodates
  typical device clock drift.

---

## Key Constant

```kotlin
TimestampUtils.MIN_VALID_EPOCH_MS = 1_577_836_800_000L  // 2020-01-01T00:00:00Z
TimestampUtils.MAX_CLOCK_SKEW_MS  = 60_000L             // 60 seconds
```
