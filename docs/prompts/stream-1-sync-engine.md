# Stream 1: Sync Engine (Critical Path) — Item A1 — REMAINING ITEMS ONLY

**Master Plan:** `todo/missing-features-implementation-plan.md` (Section A1)
**Size:** M (1-2 sessions) — remaining items after previous sessions completed EntityApplier expansion
**Conflict Risk:** LOW-MEDIUM — touches `backend/sync/` and `backend/api/` sync files
**Dependencies:** None — start immediately

> **STATUS (2026-03-18):** EntityApplier entity type expansion is COMPLETE (17 types).
> SyncValidator field-level validation is COMPLETE (15 entity types, 30+ rules).
> store_id JWT validation middleware is COMPLETE on push + pull routes.
> This session focuses on the 4 remaining A1 items: WebSocket, JWT on WS, token revocation, heartbeat replay.

---

## ✅ COMPLETED (do NOT re-implement)

The following items are VERIFIED as implemented in the codebase:

- [x] `EntityApplier` — 17 entity types: PRODUCT, CATEGORY, CUSTOMER, SUPPLIER, ORDER, ORDER_ITEM, AUDIT_ENTRY, STOCK_ADJUSTMENT, CASH_REGISTER, REGISTER_SESSION, CASH_MOVEMENT, TAX_GROUP, UNIT_OF_MEASURE, PAYMENT_SPLIT, COUPON, EXPENSE, SETTINGS
- [x] STOCK_ADJUSTMENT handler with `products.stock_qty` side-effect (lines 537-553)
- [x] AUDIT_ENTRY append-only enforcement (`insertIgnore()`, DELETE/UPDATE ignored)
- [x] `SyncValidator.kt` — field-level validation for 15 entity types (30+ rules)
- [x] `SyncRoutes.kt` — store_id extracted from JWT + `verifyStoreExists()` on push + pull
- [x] `ServerConflictResolver.kt` — generic LWW + PRODUCT field-level merge
- [x] `SyncProcessor.kt` — batch processing, conflict detection, deduplication, dead letter queue

---

## What's STILL MISSING (implement these)

### 1. WebSocket Push Notifications After Sync (HIGH)

**Problem:** After `SyncProcessor` commits operations, connected clients on the same store are NOT notified. They must manually pull to discover new data.

**Implementation:**
1. Read `backend/sync/src/main/kotlin/.../hub/WebSocketHub.kt` — understand per-store broadcast
2. Read `backend/api/src/main/kotlin/.../sync/SyncProcessor.kt` — find where ops are committed
3. After `entityApplier.applyInTransaction()` succeeds, publish to Redis `sync:delta:{storeId}`
4. `RedisPubSubListener` in sync service already subscribes — verify it broadcasts to WebSocket clients
5. Message format: `{"type":"sync_update","storeId":"X","entityTypes":["ORDER","CUSTOMER"],"count":5}`

**Key Files:**
- `backend/api/src/main/kotlin/.../sync/SyncProcessor.kt` (modify — add Redis publish after commit)
- `backend/sync/src/main/kotlin/.../hub/WebSocketHub.kt` (read — understand broadcast)
- `backend/sync/src/main/kotlin/.../hub/RedisPubSubListener.kt` (read — verify subscription)

### 2. JWT Validation on WebSocket Upgrade (HIGH)

**Problem:** WebSocket connections in `backend/sync` may not validate JWT on the upgrade handshake. Unauthenticated clients could connect.

**Implementation:**
1. Read `backend/sync/src/main/kotlin/.../routes/` — find WebSocket route registration
2. Check if `authenticate("jwt-rs256")` wraps the WebSocket route
3. If not: add JWT validation on WebSocket upgrade request (extract Bearer token from `Sec-WebSocket-Protocol` header or query param)
4. Extract `storeId` from JWT claims — only allow subscription to matching store channel
5. Reject upgrade with 401 if token invalid/expired

**Key Files:**
- `backend/sync/src/main/kotlin/.../routes/SyncWebSocketRoutes.kt` (or similar)
- `backend/sync/src/main/kotlin/.../plugins/Security.kt` (JWT config)

### 3. POS Token Revocation Check (MEDIUM)

**Problem:** `revoked_tokens` table exists in the database but JWT validation pipeline does NOT check it. A revoked token remains valid until expiry.

**Implementation:**
1. Find `revoked_tokens` table — check which migration created it
2. Read current JWT validation in `backend/api` — find where RS256 token is verified
3. After signature verification passes, query `revoked_tokens` table for the token's `jti` claim
4. If found → return 401 "Token revoked"
5. Add cache layer (in-memory set with TTL) to avoid DB hit on every request
6. Expose `POST /admin/tokens/revoke` endpoint for admin to revoke specific tokens

**Key Files:**
- `backend/api/src/main/kotlin/.../plugins/Security.kt` (JWT validation pipeline)
- `backend/api/src/main/resources/db/migration/` (find `revoked_tokens` migration)

### 4. Heartbeat Replay Protection (LOW)

**Problem:** License heartbeat requests could be replayed by an attacker to keep a device active.

**Implementation:**
1. Read `backend/license/src/main/kotlin/.../routes/` — find heartbeat endpoint
2. Add nonce parameter to heartbeat request (client generates unique nonce per heartbeat)
3. Store nonce in DB/Redis with short TTL (5 min)
4. Reject heartbeat if nonce already seen (replay detected)
5. Add timestamp validation: reject heartbeats with timestamp > 60s old

---

## Minor Implementation Gaps (found during deep verification)

### 5. CATEGORY Circular Parent Reference Detection (LOW)

**Problem:** `EntityApplier.applyCategory()` stores `parentId` without validating circular hierarchies. A→B→C→A chains are not prevented.

**Implementation:**
1. In `EntityApplier.kt` `applyCategory()` method (around line 354-372)
2. Before upserting, if `parentId` is not null:
   - Walk the parent chain: query category's parent, then grandparent, etc.
   - If any ancestor's ID matches the current category's ID → reject with error
   - Limit depth to 10 levels to prevent infinite loop on corrupted data
3. Return descriptive error: "Circular parent reference detected"

### 6. ORDER → ORDER_ITEM Cascade Consideration (INFO — not a bug)

**Note:** ORDER and ORDER_ITEM are handled as separate sync operations (not nested). This is by design — the client pushes them independently. However, there is no cascade delete of ORDER_ITEMs when an ORDER is deleted via sync. If this becomes a problem, add cascade logic in `applyOrder()` for DELETE operations.

---

## Testing Requirements

Write tests for each new item:

- `WebSocketHub` test: verify broadcast after sync commit
- JWT on WS upgrade: test unauthorized upgrade rejected with 401
- Token revocation: test revoked token returns 401, valid token passes
- Heartbeat replay: test duplicate nonce rejected, fresh nonce accepted
- Category circular ref: test A→B→A detected, valid parent accepted

---

## Pre-Implementation (MANDATORY)

1. Read `CLAUDE.md` fully
2. Read `docs/adr/ADR-008-*` (RS256 key distribution — relevant to JWT on WS)
3. Run `echo $PAT` to confirm GitHub token
4. Sync: `git fetch origin main && git merge origin/main --no-edit`

---

## Commit + Push (per item or batch)

```bash
git fetch origin main && git merge origin/main --no-edit
git add backend/ todo/missing-features-implementation-plan.md
git commit -m "feat(sync): add WebSocket push notifications and JWT on WS upgrade [A1]

- Publish sync updates to Redis after SyncProcessor commit
- JWT validation on WebSocket upgrade handshake
- Token revocation check in JWT validation pipeline
- Heartbeat replay protection with nonce + timestamp

Plan file updated: A1 status ~80% → ~95%"
git push -u origin $(git branch --show-current)
```

---

## Pipeline Monitoring (after EVERY push)

```bash
REPO="sendtodilanka/ZyntaPOS-KMM"
BRANCH=$(git branch --show-current)

curl -s -H "Authorization: token $PAT" \
  "https://api.github.com/repos/$REPO/actions/runs?branch=$BRANCH&per_page=5" \
  | python3 -c "import sys,json; [print(f'[{r[\"status\"]:10}][{(r[\"conclusion\"] or \"pending\"):10}] {r[\"name\"]}') for r in json.load(sys.stdin).get('workflow_runs',[])]"
```
