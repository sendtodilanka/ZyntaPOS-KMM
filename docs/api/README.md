# API Documentation — ZyntaPOS Network Layer

**Status:** All documented endpoints verified against server source. Full OpenAPI 3.0 spec available at [`docs/api/openapi.yaml`](openapi.yaml).
**Last updated:** 2026-03-11
**Sources:** `shared/data/src/commonMain/kotlin/.../remote/api/ApiService.kt`, `ApiClient.kt`,
`backend/api/src/main/kotlin/com/zyntasolutions/zyntapos/api/models/Models.kt`

---

## 1. Overview

ZyntaPOS communicates with a cloud backend via a Ktor HTTP client configured in
`shared/data/src/commonMain/kotlin/com/zyntasolutions/zyntapos/data/remote/api/ApiClient.kt`.

The client is built by the `buildApiClient()` factory function and injected as a Koin singleton.
All requests are authenticated with Bearer tokens; the base URL is injected from
`BuildConfig.ZYNTA_API_BASE_URL` (populated from `local.properties` via the Gradle Secrets Plugin).

---

## 2. Base URL

```
https://${BuildConfig.ZYNTA_API_BASE_URL}
```

The base URL is configured per environment in `local.properties`:

```properties
ZYNTA_API_BASE_URL=api.zyntapos.example.com   # without protocol prefix
```

All POS terminal endpoints are prefixed with `/v1/` (no `/api` infix).
Admin panel endpoints are prefixed with `/admin/`.

---

## 3. Authentication

All endpoints (except `/v1/auth/login` and `/.well-known/public-key`) require a **Bearer token**
in the `Authorization` header.

```
Authorization: Bearer <access_token>
```

Tokens are persisted in `SecurePreferences` (AES-256-GCM encrypted storage) under the keys:
- `KEY_ACCESS_TOKEN` — short-lived JWT (60 min expiry for POS; 15 min for admin panel)
- `KEY_REFRESH_TOKEN` — long-lived JWT (30 days for POS; 7 days for admin panel)

### Token Refresh

The Ktor `Auth` plugin handles token refresh automatically on 401 responses. The `refreshTokens`
callback in `buildApiClient()` surfaces the stored refresh token; `AuthRepositoryImpl` is
responsible for calling `POST /v1/auth/refresh` and persisting the new access token.

Client-side JWT expiry is checked proactively using `JwtManager.isTokenExpired()` with a 30-second
clock-skew buffer. Tokens are RS256-signed and the public key is available at
`GET /.well-known/public-key` (TOFU model, cached in `SecurePreferences`).

---

## 4. HTTP Client Configuration

**File:** `ApiClient.kt`

```kotlin
// JSON: ignoreUnknownKeys=true, isLenient=true, coerceInputValues=true, encodeDefaults=true
install(ContentNegotiation) { json(...) }

// Bearer auth: loads tokens from SecurePreferences, refreshes on 401
install(Auth) { bearer { ... } }

// Timeouts
install(HttpTimeout) {
    connectTimeoutMillis = 10_000L
    requestTimeoutMillis = 30_000L
    socketTimeoutMillis  = 30_000L
}

// Retry: up to 3 attempts, exponential backoff — 1 s, 2 s, 4 s (max 4 s)
install(HttpRequestRetry) {
    retryOnServerErrors(maxRetries = 3)
    retryOnException(maxRetries = 3, retryOnTimeout = true)
    exponentialDelay(base = 2.0, maxDelayMs = 4_000L)
}

// Logging (DEBUG builds only): HEADERS level via Kermit
if (AppConfig.IS_DEBUG) { install(Logging) { level = LogLevel.HEADERS } }
```

---

## 5. Endpoint Reference

All POS terminal endpoints are defined in `ApiService` interface:
`shared/data/src/commonMain/kotlin/com/zyntasolutions/zyntapos/data/remote/api/ApiService.kt`

| Method | Path | KMM Function | Request Type | Response Type |
|--------|------|----------|--------------|---------------|
| POST | `/v1/auth/login` | `login()` | `AuthRequestDto` | `AuthResponseDto` |
| POST | `/v1/auth/refresh` | `refreshToken()` | `refreshToken: String` | `AuthRefreshResponseDto` |
| GET | `/v1/products` | `getProducts()` | _(query params)_ | `List<ProductDto>` |
| POST | `/v1/sync/push` | `pushOperations()` | `List<SyncOperationDto>` | `SyncResponseDto` |
| GET | `/v1/sync/pull` | `pullOperations()` | `lastSyncTimestamp: Long` | `SyncPullResponseDto` |
| GET | `/.well-known/public-key` | `fetchPublicKey()` | _(none)_ | `PublicKeyResponseDto` |

For the full endpoint reference including admin panel, license service, and sync WebSocket,
see [`docs/api/openapi.yaml`](openapi.yaml).

### POST `/v1/auth/login`

Authenticates the device with the server and returns JWT tokens.

**Request (server accepts):**
```json
{
  "licenseKey": "ZYNTA-XXXX-XXXX-XXXX",
  "deviceId": "device-uuid",
  "username": "cashier@store.com",
  "password": "..."
}
```

**Response:**
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "expiresIn": 3600,
  "tokenType": "Bearer",
  "userId": "user-uuid",
  "role": "CASHIER",
  "storeId": "store-uuid"
}
```

**Rate limit:** 10 requests/minute per IP.
**Error:** HTTP 401 `{"code":"INVALID_CREDENTIALS","message":"..."}` on bad credentials.

### POST `/v1/auth/refresh`

Exchanges a refresh token for a new access token.

**Error:** HTTP 401 (expired refresh token — requires re-login).

### GET `/v1/products`

Retrieves a paginated product catalog page. Used during initial seeding or after a full resync.

**Query params:** `?page=0&size=50&updatedSince=<epoch_ms>`

**Response (`PagedResponse<ProductDto>`):**
```json
{
  "data": [ { "id": "...", "name": "...", "price": 9.99, ... } ],
  "page": 0,
  "size": 50,
  "total": 312,
  "hasMore": true
}
```

**Error:** HTTP 5xx → `NetworkException`.

### POST `/v1/sync/push`

Pushes a batch of locally pending operations to the server.

**Request body (`SyncOperationDto`):**
```json
[{
  "id": "uuid",
  "entity_type": "product",
  "entity_id": "uuid",
  "operation": "UPDATE",
  "payload": "{}",
  "created_at": 1706000000000,
  "retry_count": 0
}]
```

**Response:**
```json
{
  "accepted": 3,
  "rejected": 0,
  "conflicts": [],
  "serverVectorClock": 1234567
}
```

**Error:** HTTP 4xx/5xx → `SyncException`.

### GET `/v1/sync/pull`

Pulls server-side changes since the given cursor position (cursor-based pagination).

**Query params:** `?since=<server_seq:Long>&limit=<Int>`

**Response:**
```json
{
  "operations": [ { "id": "...", "entity_type": "product", ... } ],
  "serverVectorClock": 1234600,
  "hasMore": false
}
```

When `hasMore` is `true`, issue another pull with `?since=<serverVectorClock>` until false.

**Error:** HTTP 5xx → `NetworkException`.

### GET `/.well-known/public-key`

Returns the current RS256 public key used to sign JWT tokens. Call after every successful online
login or token refresh. Pass the returned key to `JwtManager.cachePublicKey()` for offline
JWT verification and RBAC. Supports server-side key rotation without an app update (TOFU model).

---

## 6. Error Types

All HTTP errors are mapped to typed domain exceptions defined in `:shared:core`:

| Exception | HTTP Condition | Usage |
|-----------|---------------|-------|
| `AuthException` | 401 Unauthorized | Invalid credentials, expired tokens |
| `NetworkException` | Transport errors, timeouts, 5xx | General connectivity / server issues |
| `SyncException` | 4xx/5xx on sync endpoints | Sync-specific server rejections |

Standard error response body:
```json
{ "code": "ERROR_CODE", "message": "Human-readable description" }
```

The Ktor retry plugin automatically retries on server errors (5xx) and timeouts, up to 3 times
with exponential backoff.

---

## 7. Rate Limits

| Tier | Limit | Applied to |
|------|-------|------------|
| `auth` | 10 req/min | `/v1/auth/login`, `/v1/auth/refresh`, `/admin/auth/login` |
| `sync` | 60 req/min | `/v1/sync/push` |
| `api` | 300 req/min | All other endpoints |

---

## 8. Known Gaps (Resolved)

### SyncRepositoryImpl — Network Stubs (Phase 1) — ✅ Resolved

`SyncEngine` calls `ApiService` directly (bypassing `SyncRepositoryImpl`), so push/pull HTTP calls
function through `SyncEngine`. The `SyncRepository` interface contract layer remains a Phase 1
stub but does not block sync functionality.

### applyDeltaOperations — Sprint 7 — ✅ Implemented (2026-03-11)

The entity-type router in `SyncEngine.applyDeltaOperations()` now dispatches server delta
operations to the appropriate `RepositoryImpl.upsertFromSync()` method for each entity type.

### OpenAPI Specification — ✅ Available

See [`docs/api/openapi.yaml`](openapi.yaml) — full OpenAPI 3.0.3 spec covering 60+ endpoints
across three services (POS API, License, Admin panel).
