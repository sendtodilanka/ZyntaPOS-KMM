# ZyntaPOS — Phase 3 Sprint 2: Media + Accounting + Infrastructure Schema

> **Document ID:** ZYNTA-PLAN-PHASE3-SPRINT2-v1.0
> **Phase:** 3 — Enterprise (Months 13–18)
> **Sprint:** 2 of 24 | Week 2
> **Module(s):** `:shared:data`
> **Author:** Senior KMP Architect & Lead Engineer
> **Reference:** ZYNTA-MASTER-PLAN-v1.0 | ZYNTA-ER-DIAGRAM-v1.0

---

## Goal

Add Media files, Warehouse Racks, Accounting Entries, and Version Vectors tables to the local database (migration v5 → v6). These tables underpin the Media Manager (M20), Warehouse Rack Manager, double-entry accounting ledger, and multi-device CRDT version tracking introduced in Phase 3.

---

## New Files to Create

### SQLDelight Schema Files

**Location:** `shared/data/src/commonMain/sqldelight/com/zyntasolutions/zyntapos/db/`

#### `media_files.sq`
```sql
CREATE TABLE IF NOT EXISTS media_files (
    id              TEXT PRIMARY KEY,
    file_name       TEXT NOT NULL,
    file_path       TEXT NOT NULL,             -- Local storage path (device-relative)
    remote_url      TEXT,                      -- URL after upload to backend (nullable until synced)
    file_type       TEXT NOT NULL,             -- 'IMAGE', 'DOCUMENT'
    mime_type       TEXT NOT NULL,             -- 'image/jpeg', 'image/png', 'application/pdf'
    file_size       INTEGER NOT NULL,          -- Bytes
    thumbnail_path  TEXT,                      -- Local path to generated thumbnail
    entity_type     TEXT,                      -- Polymorphic: 'Product', 'Category', 'Store', 'Employee'
    entity_id       TEXT,                      -- FK to the owning entity
    is_primary      INTEGER NOT NULL DEFAULT 0, -- Primary image for the entity
    uploaded_by     TEXT NOT NULL REFERENCES users(id),
    upload_status   TEXT NOT NULL DEFAULT 'LOCAL', -- 'LOCAL', 'UPLOADING', 'UPLOADED', 'FAILED'
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL,
    deleted_at      TEXT,
    sync_id         TEXT NOT NULL,
    sync_version    INTEGER NOT NULL DEFAULT 1,
    sync_status     TEXT NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX idx_media_entity ON media_files(entity_type, entity_id);
CREATE INDEX idx_media_uploader ON media_files(uploaded_by);
CREATE INDEX idx_media_type ON media_files(file_type);
CREATE INDEX idx_media_upload_status ON media_files(upload_status);

-- Queries
selectByEntity:
SELECT * FROM media_files
WHERE entity_type = :entityType AND entity_id = :entityId AND deleted_at IS NULL
ORDER BY is_primary DESC, created_at ASC;

selectPrimaryForEntity:
SELECT * FROM media_files
WHERE entity_type = :entityType AND entity_id = :entityId
  AND is_primary = 1 AND deleted_at IS NULL LIMIT 1;

selectPendingUpload:
SELECT * FROM media_files WHERE upload_status = 'LOCAL' AND deleted_at IS NULL LIMIT 20;

selectAll:
SELECT * FROM media_files WHERE deleted_at IS NULL ORDER BY created_at DESC;

updateUploadStatus:
UPDATE media_files
SET upload_status = :status, remote_url = :remoteUrl, updated_at = :updatedAt,
    sync_status = 'PENDING', sync_version = sync_version + 1
WHERE id = :id;

upsert:
INSERT OR REPLACE INTO media_files VALUES ?;

softDelete:
UPDATE media_files SET deleted_at = :deletedAt, updated_at = :updatedAt,
sync_status = 'PENDING', sync_version = sync_version + 1 WHERE id = :id;
```

#### `warehouse_racks.sq`
```sql
CREATE TABLE IF NOT EXISTS warehouse_racks (
    id              TEXT PRIMARY KEY,
    warehouse_id    TEXT NOT NULL REFERENCES warehouses(id),
    name            TEXT NOT NULL,             -- 'A1', 'B3', 'Shelf-2', 'Cold-Storage-01'
    description     TEXT,
    capacity        INTEGER,                   -- Max units storable; NULL = unlimited
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL,
    deleted_at      TEXT,
    sync_id         TEXT NOT NULL,
    sync_version    INTEGER NOT NULL DEFAULT 1,
    sync_status     TEXT NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX idx_racks_warehouse ON warehouse_racks(warehouse_id);
CREATE UNIQUE INDEX idx_racks_warehouse_name ON warehouse_racks(warehouse_id, name);

-- Queries
selectByWarehouse:
SELECT * FROM warehouse_racks
WHERE warehouse_id = :warehouseId AND deleted_at IS NULL
ORDER BY name ASC;

selectById:
SELECT * FROM warehouse_racks WHERE id = :id AND deleted_at IS NULL;

upsert:
INSERT OR REPLACE INTO warehouse_racks VALUES ?;

softDelete:
UPDATE warehouse_racks SET deleted_at = :deletedAt, updated_at = :updatedAt,
sync_status = 'PENDING', sync_version = sync_version + 1 WHERE id = :id;
```

#### `accounting_entries.sq`
```sql
CREATE TABLE IF NOT EXISTS accounting_entries (
    id              TEXT PRIMARY KEY,
    store_id        TEXT NOT NULL REFERENCES stores(id),
    account_code    TEXT NOT NULL,              -- Chart of accounts code: '4000', '5100', etc.
    account_name    TEXT NOT NULL,              -- Human-readable: 'Sales Revenue', 'Wages Expense'
    entry_type      TEXT NOT NULL,              -- 'DEBIT', 'CREDIT'
    amount          REAL NOT NULL,
    reference_type  TEXT NOT NULL,              -- 'ORDER', 'EXPENSE', 'PAYMENT', 'CASH_MOVEMENT', 'ADJUSTMENT'
    reference_id    TEXT NOT NULL,              -- FK to the source record
    description     TEXT,
    entry_date      TEXT NOT NULL,              -- ISO date: YYYY-MM-DD
    fiscal_period   TEXT NOT NULL,              -- 'YYYY-MM' for monthly aggregation
    created_by      TEXT NOT NULL REFERENCES users(id),
    created_at      TEXT NOT NULL,
    sync_id         TEXT NOT NULL,
    sync_version    INTEGER NOT NULL DEFAULT 1,
    sync_status     TEXT NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX idx_accounting_date ON accounting_entries(entry_date);
CREATE INDEX idx_accounting_account ON accounting_entries(account_code);
CREATE INDEX idx_accounting_store_period ON accounting_entries(store_id, fiscal_period);
CREATE INDEX idx_accounting_reference ON accounting_entries(reference_type, reference_id);

-- Queries
selectByStoreAndPeriod:
SELECT * FROM accounting_entries
WHERE store_id = :storeId AND fiscal_period = :fiscalPeriod
ORDER BY entry_date ASC, account_code ASC;

selectByAccountAndPeriod:
SELECT * FROM accounting_entries
WHERE store_id = :storeId AND account_code = :accountCode AND fiscal_period = :fiscalPeriod
ORDER BY entry_date ASC;

selectByReference:
SELECT * FROM accounting_entries
WHERE reference_type = :referenceType AND reference_id = :referenceId;

sumByAccountForPeriod:
SELECT account_code, account_name, entry_type, SUM(amount) AS total
FROM accounting_entries
WHERE store_id = :storeId AND fiscal_period BETWEEN :fromPeriod AND :toPeriod
GROUP BY account_code, account_name, entry_type
ORDER BY account_code ASC;

insert:
INSERT INTO accounting_entries VALUES ?;
```

#### `version_vectors.sq`
```sql
-- Tracks per-device version vectors for CRDT conflict detection.
-- No standard sync columns needed — this IS the sync metadata.
CREATE TABLE IF NOT EXISTS version_vectors (
    entity_type     TEXT NOT NULL,
    entity_id       TEXT NOT NULL,
    device_id       TEXT NOT NULL,
    version         INTEGER NOT NULL DEFAULT 0,
    updated_at      TEXT NOT NULL,
    PRIMARY KEY (entity_type, entity_id, device_id)
);

CREATE INDEX idx_vv_entity ON version_vectors(entity_type, entity_id);

-- Queries
selectByEntity:
SELECT * FROM version_vectors WHERE entity_type = :entityType AND entity_id = :entityId;

upsert:
INSERT OR REPLACE INTO version_vectors(entity_type, entity_id, device_id, version, updated_at)
VALUES (:entityType, :entityId, :deviceId, :version, :updatedAt);

deleteByEntity:
DELETE FROM version_vectors WHERE entity_type = :entityType AND entity_id = :entityId;

incrementVersion:
UPDATE version_vectors
SET version = version + 1, updated_at = :updatedAt
WHERE entity_type = :entityType AND entity_id = :entityId AND device_id = :deviceId;
```

### Migration File

**Location:** `shared/data/src/commonMain/sqldelight/com/zyntasolutions/zyntapos/db/migrations/6.sqm`

```sql
-- Migration 5 → 6: Phase 3 Media + Racks + Accounting + VersionVectors
-- Sprint: Phase 3 Sprint 2

CREATE TABLE IF NOT EXISTS media_files (
    id TEXT PRIMARY KEY,
    file_name TEXT NOT NULL,
    file_path TEXT NOT NULL,
    remote_url TEXT,
    file_type TEXT NOT NULL,
    mime_type TEXT NOT NULL,
    file_size INTEGER NOT NULL,
    thumbnail_path TEXT,
    entity_type TEXT,
    entity_id TEXT,
    is_primary INTEGER NOT NULL DEFAULT 0,
    uploaded_by TEXT NOT NULL,
    upload_status TEXT NOT NULL DEFAULT 'LOCAL',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT,
    sync_id TEXT NOT NULL,
    sync_version INTEGER NOT NULL DEFAULT 1,
    sync_status TEXT NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX IF NOT EXISTS idx_media_entity ON media_files(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_media_uploader ON media_files(uploaded_by);
CREATE INDEX IF NOT EXISTS idx_media_upload_status ON media_files(upload_status);

CREATE TABLE IF NOT EXISTS warehouse_racks (
    id TEXT PRIMARY KEY,
    warehouse_id TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    capacity INTEGER,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT,
    sync_id TEXT NOT NULL,
    sync_version INTEGER NOT NULL DEFAULT 1,
    sync_status TEXT NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX IF NOT EXISTS idx_racks_warehouse ON warehouse_racks(warehouse_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_racks_warehouse_name ON warehouse_racks(warehouse_id, name);

CREATE TABLE IF NOT EXISTS accounting_entries (
    id TEXT PRIMARY KEY,
    store_id TEXT NOT NULL,
    account_code TEXT NOT NULL,
    account_name TEXT NOT NULL,
    entry_type TEXT NOT NULL,
    amount REAL NOT NULL,
    reference_type TEXT NOT NULL,
    reference_id TEXT NOT NULL,
    description TEXT,
    entry_date TEXT NOT NULL,
    fiscal_period TEXT NOT NULL,
    created_by TEXT NOT NULL,
    created_at TEXT NOT NULL,
    sync_id TEXT NOT NULL,
    sync_version INTEGER NOT NULL DEFAULT 1,
    sync_status TEXT NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX IF NOT EXISTS idx_accounting_date ON accounting_entries(entry_date);
CREATE INDEX IF NOT EXISTS idx_accounting_account ON accounting_entries(account_code);
CREATE INDEX IF NOT EXISTS idx_accounting_store_period ON accounting_entries(store_id, fiscal_period);

CREATE TABLE IF NOT EXISTS version_vectors (
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    device_id TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    updated_at TEXT NOT NULL,
    PRIMARY KEY (entity_type, entity_id, device_id)
);

CREATE INDEX IF NOT EXISTS idx_vv_entity ON version_vectors(entity_type, entity_id);
```

### Modified File

**`shared/data/src/commonMain/kotlin/.../data/local/migration/DatabaseMigrations.kt`** — Add migration 6 entry.

---

## Tasks

- [ ] **2.1** Write `media_files.sq` with full DDL + indexes + all queries (selectByEntity, selectPrimaryForEntity, selectPendingUpload, updateUploadStatus, upsert, softDelete)
- [ ] **2.2** Write `warehouse_racks.sq` with DDL + UNIQUE index on `(warehouse_id, name)` + all queries
- [ ] **2.3** Write `accounting_entries.sq` with DDL + 4 indexes + queries (selectByStoreAndPeriod, selectByAccountAndPeriod, sumByAccountForPeriod, insert)
- [ ] **2.4** Write `version_vectors.sq` — composite PRIMARY KEY `(entity_type, entity_id, device_id)` + upsert + increment queries. **No sync columns** (this IS sync metadata)
- [ ] **2.5** Create `6.sqm` migration file with all 4 CREATE TABLE + index statements
- [ ] **2.6** Update `DatabaseMigrations.kt` to register migration 6
- [ ] **2.7** Run `./gradlew generateSqlDelightInterface` — verify no compilation errors
- [ ] **2.8** Run `./gradlew verifySqlDelightMigration` — verify full chain 1→2→3→4→5→6 passes
- [ ] **2.9** Run `./gradlew detekt` — no new violations

---

## Verification

```bash
# Full migration chain validation
./gradlew :shared:data:verifySqlDelightMigration

# Schema compilation
./gradlew :shared:data:generateCommonMainZyntaPosDatabaseInterface

# Static analysis
./gradlew :shared:data:detekt
```

---

## Architecture Notes

### Media File Upload Strategy
`media_files.upload_status` follows this lifecycle:
```
LOCAL → (background worker) → UPLOADING → UPLOADED
                               └─ on failure → FAILED → (retry) → UPLOADING
```
The sync engine processes `upload_status = 'LOCAL'` records in batches (max 20 at a time, `selectPendingUpload` query). On successful backend upload, `remote_url` is populated and status set to `UPLOADED`.

### Double-Entry Accounting Invariant
For every financial event, the sum of DEBIT entries **must equal** the sum of CREDIT entries. Example for a $100 sale:
- DEBIT `1200` (Accounts Receivable) $100
- CREDIT `4000` (Sales Revenue) $85.47
- CREDIT `2200` (Tax Payable) $14.53

The `CreateAccountingEntryUseCase` (Sprint 4) enforces this invariant at the domain layer.

### Version Vector Usage
`version_vectors` is updated by `SyncEngine` whenever a local mutation is applied. It enables detection of concurrent edits from multiple devices on the same entity — the `ConflictResolver` (`:shared:data`) consults this table when `sync_status = 'CONFLICT'` is detected.

---

## Definition of Done

- [ ] All 4 `.sq` files created and compile without errors
- [ ] Migration `6.sqm` applied successfully
- [ ] Complete migration chain v1→v6 validates
- [ ] `./gradlew detekt` passes
- [ ] Commit: `feat(data): add Media, Racks, Accounting, VersionVectors schema (migration v6)`
