# TODO-003: Enterprise Audit Logging (Two-Tier Architecture)

**Status:** Pending
**Priority:** HIGH — Legal SLA requirement (99.99% uptime)
**Phase:** Phase 1 (start expanding now)
**Created:** 2026-03-01

---

## Problem Statement

The system is legally bound to provide 99.99% bug-free uptime. Current audit logging has a solid foundation but critical gaps:

**What exists (good):**
- `AuditEntry` model with 11 event types
- `audit_entries` table with SHA-256 hash chain (tamper detection)
- `SecurityAuditLogger` with 8 logging methods
- Admin UI viewer with filtering
- Append-only, immutable design

**Critical gaps:**
- Only 11 event types — need micro-atomic coverage of every operation
- Hash chain computed but **never verified**
- No automatic retention/purge policy enforced
- No log rotation for Kermit operational logs
- `getRecentLoginFailures` query exists but brute-force detection is not wired
- Several event triggers documented but not wired in feature ViewModels
- Text-based log files will become unmanageable at scale

---

## Decision: Two-Tier Database-Backed Logging

### Why Database Instead of Text Files

| Aspect | Text Files | SQLite Table |
|--------|-----------|--------------|
| **Query by date range** | Parse entire file | Indexed query: instant |
| **Filter by level/tag** | grep (slow on large files) | WHERE clause: instant |
| **Size management** | Manual rotation scripts | `DELETE WHERE createdAt < cutoff` |
| **Concurrent writes** | File locking issues | SQLite WAL mode handles it |
| **Corruption risk** | High (app crash mid-write) | SQLite transactions protect |
| **Searchability** | Limited | Full SQL power |
| **Export** | Already text | Export to CSV/JSON trivially |
| **Encryption** | Separate encryption needed | Already in SQLCipher |

---

## Tier 1: Business Audit Log (Legal Compliance Trail)

Every business-critical operation must be logged atomically. Expand from 11 to ~40 event types.

### Expanded Event Types

```kotlin
enum class AuditEventType {
    // === Authentication (5) ===
    LOGIN_ATTEMPT,           // Success/fail with source (password/pin/biometric)
    LOGOUT,                  // Explicit logout
    SESSION_TIMEOUT,         // Auto-lock triggered
    PIN_CHANGE,              // PIN created/changed
    PASSWORD_CHANGE,         // Password changed

    // === Authorization (2) ===
    PERMISSION_DENIED,       // RBAC guard denial
    ROLE_CHANGED,            // User's role modified

    // === POS Operations (8) ===
    ORDER_CREATED,           // New order finalized
    ORDER_VOIDED,            // Order cancelled (with reason)
    ORDER_REFUNDED,          // Refund processed
    DISCOUNT_APPLIED,        // Manual discount (who, how much, why)
    PAYMENT_PROCESSED,       // Payment received (method, amount)
    ORDER_HELD,              // Order put on hold
    ORDER_RESUMED,           // Held order resumed
    PRICE_OVERRIDE,          // Manual price change at POS

    // === Inventory (5) ===
    STOCK_ADJUSTED,          // Manual stock adjustment (reason required)
    PRODUCT_CREATED,         // New product added
    PRODUCT_MODIFIED,        // Product details changed
    PRODUCT_DELETED,         // Product removed (soft delete)
    STOCKTAKE_COMPLETED,     // Stocktake submitted

    // === Cash Register (4) ===
    REGISTER_OPENED,         // Cash session start (opening float)
    REGISTER_CLOSED,         // Cash session end (expected vs actual)
    CASH_IN,                 // Cash added to register
    CASH_OUT,                // Cash removed from register

    // === User Management (4) ===
    USER_CREATED,            // New user account
    USER_DEACTIVATED,        // Account disabled
    USER_REACTIVATED,        // Account re-enabled
    CUSTOM_ROLE_MODIFIED,    // RBAC role permissions changed

    // === Financial (3) ===
    EXPENSE_APPROVED,        // Expense approval workflow
    JOURNAL_POSTED,          // Accounting journal entry posted
    TAX_CONFIG_CHANGED,      // Tax rate/group modified

    // === System (5) ===
    SETTINGS_CHANGED,        // App configuration modified
    BACKUP_CREATED,          // Database backup
    BACKUP_RESTORED,         // Database restore (critical!)
    DATA_EXPORTED,           // CSV/PDF export (what data, who)
    DIAGNOSTIC_SESSION,      // Technician diagnostic session (start/end)

    // === Data (3) ===
    SYNC_COMPLETED,          // Sync cycle success
    SYNC_FAILED,             // Sync cycle failure
    DATA_PURGED,             // Old data removed (retention policy)
}
```

### Enhanced AuditEntry Model

```kotlin
data class AuditEntry(
    val id: String,                   // UUID v4
    val eventType: AuditEventType,    // ~40 types
    val userId: String,               // Actor
    val userName: String,             // Actor display name (denormalized for log readability)
    val userRole: Role,               // Actor's role at time of action
    val deviceId: String,             // Terminal/device identifier
    val entityType: String?,          // "ORDER", "PRODUCT", "USER", etc.
    val entityId: String?,            // PK of affected entity
    val payload: String,              // Structured JSON with event-specific details
    val previousValue: String?,       // Before-state (for change tracking)
    val newValue: String?,            // After-state (for change tracking)
    val success: Boolean,             // Operation outcome
    val ipAddress: String?,           // Source IP (for remote sessions)
    val hash: String,                 // SHA-256 chain link
    val previousHash: String,         // Previous entry's hash
    val createdAt: Instant,           // UTC timestamp (millisecond precision)
)
```

**Why before/after values matter:** For the SLA, you need to prove exactly what changed. "Settings changed" is not enough — you need "Tax rate changed from 12% to 15% by admin@store.com at 14:32:05 UTC".

### SQLDelight Schema

```sql
CREATE TABLE audit_entries (
    id TEXT PRIMARY KEY NOT NULL,
    event_type TEXT NOT NULL,
    user_id TEXT NOT NULL,
    user_name TEXT NOT NULL,
    user_role TEXT NOT NULL,
    device_id TEXT NOT NULL DEFAULT '',
    entity_type TEXT,
    entity_id TEXT,
    payload TEXT NOT NULL DEFAULT '{}',
    previous_value TEXT,
    new_value TEXT,
    success INTEGER NOT NULL DEFAULT 1,
    ip_address TEXT,
    hash TEXT NOT NULL,
    previous_hash TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE INDEX idx_audit_event_type ON audit_entries(event_type);
CREATE INDEX idx_audit_user_id ON audit_entries(user_id);
CREATE INDEX idx_audit_user_role ON audit_entries(user_role);
CREATE INDEX idx_audit_entity ON audit_entries(entity_type, entity_id);
CREATE INDEX idx_audit_created_at ON audit_entries(created_at);
CREATE INDEX idx_audit_success ON audit_entries(success);
```

---

## Tier 2: Operational/Diagnostic Log (System Health Trail)

For debugging, performance monitoring, and uptime proof. Replaces text-based Kermit log files for persistent storage.

### Model

```kotlin
data class OperationalLog(
    val id: Long,                     // Auto-increment (not UUID — performance)
    val level: LogLevel,              // VERBOSE, DEBUG, INFO, WARN, ERROR, FATAL
    val tag: String,                  // Module/class name
    val message: String,             // Log message
    val stackTrace: String?,          // Exception stack (if error)
    val threadName: String?,          // Coroutine/thread context
    val sessionId: String?,           // Current user session
    val metadata: String?,            // JSON: memory, CPU, battery, etc.
    val createdAt: Long,              // Unix millis (fast indexing)
)
```

### SQLDelight Schema

```sql
CREATE TABLE operational_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    level TEXT NOT NULL,
    tag TEXT NOT NULL,
    message TEXT NOT NULL,
    stack_trace TEXT,
    thread_name TEXT,
    session_id TEXT,
    metadata TEXT,
    created_at INTEGER NOT NULL
);

CREATE INDEX idx_oplog_level ON operational_logs(level);
CREATE INDEX idx_oplog_tag ON operational_logs(tag);
CREATE INDEX idx_oplog_created_at ON operational_logs(created_at);
CREATE INDEX idx_oplog_level_created ON operational_logs(level, created_at);
```

---

## Retention Policy (Automated)

Run daily via WorkManager (Android) / ScheduledExecutor (JVM):

```kotlin
class LogRetentionJob {
    // Tier 1 (Business Audit): 7 years minimum (legal/tax compliance)
    // Tier 1 can be exported to secure archive after 1 year

    // Tier 2 (Operational):
    //   ERROR/FATAL: 90 days
    //   WARN: 30 days
    //   INFO: 14 days
    //   DEBUG/VERBOSE: 3 days (debug builds only; never stored in release)
}
```

### Retention SQL

```sql
-- Tier 2 daily cleanup
DELETE FROM operational_logs
WHERE (level = 'DEBUG' AND created_at < :threeDaysAgo)
   OR (level = 'VERBOSE' AND created_at < :threeDaysAgo)
   OR (level = 'INFO' AND created_at < :fourteenDaysAgo)
   OR (level = 'WARN' AND created_at < :thirtyDaysAgo)
   OR (level IN ('ERROR', 'FATAL') AND created_at < :ninetyDaysAgo);

-- Tier 1: NEVER auto-delete. Export to archive only.
```

---

## Hash Chain Verification

The existing hash chain is a great foundation but must be **actively verified**.

```kotlin
class AuditIntegrityVerifier(
    private val auditRepository: AuditRepository,
) {
    // Run daily — verify the entire chain has not been tampered with
    suspend fun verifyChainIntegrity(): IntegrityReport {
        val entries = auditRepository.getAllChronological()
        var previousHash = "GENESIS" // First entry's previousHash
        val violations = mutableListOf<IntegrityViolation>()

        entries.forEach { entry ->
            val expectedHash = computeHash(entry, previousHash)
            if (entry.hash != expectedHash) {
                violations.add(IntegrityViolation(entry.id, "Hash mismatch"))
            }
            if (entry.previousHash != previousHash) {
                violations.add(IntegrityViolation(entry.id, "Chain break"))
            }
            previousHash = entry.hash
        }

        return IntegrityReport(
            totalEntries = entries.size,
            violations = violations,
            isIntact = violations.isEmpty(),
            verifiedAt = Clock.System.now(),
        )
    }
}

data class IntegrityViolation(
    val entryId: String,
    val reason: String,
)

data class IntegrityReport(
    val totalEntries: Int,
    val violations: List<IntegrityViolation>,
    val isIntact: Boolean,
    val verifiedAt: Instant,
)
```

---

## Admin Audit Log Viewer Enhancements

Current viewer only filters by user ID. For legal compliance, it needs:

### Filters

- Date range picker (from/to)
- Event type multi-select dropdown
- User/role filter
- Entity type filter (ORDER, PRODUCT, USER, etc.)
- Success/failure toggle
- Search in payload (text search)
- Export button (CSV with all fields)

### Display

- Paginated (50 entries per page — not load all)
- Expandable detail view (before/after diff)
- Hash chain integrity indicator
- Total entry count with date range

### Queries

```sql
-- Paginated filtered query
SELECT * FROM audit_entries
WHERE created_at BETWEEN :from AND :to
  AND (:eventType IS NULL OR event_type = :eventType)
  AND (:userId IS NULL OR user_id = :userId)
  AND (:userRole IS NULL OR user_role = :userRole)
  AND (:entityType IS NULL OR entity_type = :entityType)
  AND (:success IS NULL OR success = :success)
ORDER BY created_at DESC
LIMIT :pageSize OFFSET :offset;

-- Count for pagination
SELECT COUNT(*) FROM audit_entries
WHERE created_at BETWEEN :from AND :to
  AND (:eventType IS NULL OR event_type = :eventType)
  AND (:userId IS NULL OR user_id = :userId)
  AND (:userRole IS NULL OR user_role = :userRole)
  AND (:entityType IS NULL OR entity_type = :entityType)
  AND (:success IS NULL OR success = :success);
```

---

## Wiring Audit Events in Feature ViewModels

Every feature ViewModel must call the audit logger for business-critical operations. Example:

```kotlin
// In PosViewModel
private suspend fun finalizeOrder(order: Order) {
    val result = createOrderUseCase.execute(order)
    result.fold(
        onSuccess = {
            auditLogger.log(
                eventType = AuditEventType.ORDER_CREATED,
                entityType = "ORDER",
                entityId = it.id,
                payload = json.encodeToString(OrderAuditPayload(
                    totalAmount = it.totals.grandTotal,
                    itemCount = it.items.size,
                    paymentMethod = it.paymentMethod.name,
                )),
                success = true,
            )
        },
        onFailure = {
            auditLogger.log(
                eventType = AuditEventType.ORDER_CREATED,
                entityType = "ORDER",
                entityId = order.id,
                payload = json.encodeToString(mapOf("error" to it.message)),
                success = false,
            )
        }
    )
}
```

---

## Implementation Order

1. **Expand `AuditEventType` enum** — add all ~40 event types
2. **Enhance `AuditEntry` model** — add `previousValue`, `newValue`, `deviceId`, `userName`, `userRole`, `ipAddress`
3. **Update SQLDelight schema** — add new columns and indexes
4. **Create `operational_logs` table** — Tier 2 schema
5. **Implement `AuditIntegrityVerifier`** — hash chain verification
6. **Implement `LogRetentionJob`** — automated cleanup
7. **Wire audit events in all feature ViewModels** — micro-atomic logging
8. **Create Kermit-to-SQLite bridge** — route operational logs to DB
9. **Enhance admin audit viewer UI** — filters, pagination, export
10. **Wire brute-force detection** — connect `getRecentLoginFailures` to account lockout

---

## Validation Checklist

- [ ] AuditEventType expanded to ~40 types
- [ ] AuditEntry model includes before/after values and actor metadata
- [ ] SQLDelight schema updated with new columns and indexes
- [ ] operational_logs table created with appropriate indexes
- [ ] Hash chain verification runs daily
- [ ] Log retention job configured and running
- [ ] All feature ViewModels wired with audit logging
- [ ] Operational logs stored in SQLite (not text files)
- [ ] Admin viewer supports date/type/role filters
- [ ] Admin viewer supports pagination (50 per page)
- [ ] Admin viewer supports CSV export
- [ ] Brute-force detection wired to login flow
- [ ] Tests for AuditIntegrityVerifier
- [ ] Tests for LogRetentionJob
