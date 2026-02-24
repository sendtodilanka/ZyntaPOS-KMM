# ZyntaPOS — Phase 3 Sprint 1: Staff & HR SQLDelight Schema

> **Document ID:** ZYNTA-PLAN-PHASE3-SPRINT1-v1.0
> **Phase:** 3 — Enterprise (Months 13–18)
> **Sprint:** 1 of 24 | Week 1
> **Module(s):** `:shared:data`
> **Author:** Senior KMP Architect & Lead Engineer
> **Reference:** ZYNTA-MASTER-PLAN-v1.0 | ZYNTA-ER-DIAGRAM-v1.0 | ZYNTA-PLAN-PHASE2-v1.0

---

## Goal

Add all Staff & HR tables to the encrypted local database. This is the first Phase 3 database migration (v4 → v5), extending the SQLDelight schema with 5 new tables covering employee profiles, daily attendance, leave management, payroll records, and shift scheduling.

---

## Context

Phase 2 ended at DB version 4 (`conflict_log`, `sync_state`, `warehouses`, `stock_transfers`, `notifications` added). Phase 3 starts by extending the schema for the Staff & HR domain (ER Diagram Domain 8: 5 entities, 6 relationships).

---

## New Files to Create

### SQLDelight Schema Files

**Location:** `shared/data/src/commonMain/sqldelight/com/zyntasolutions/zyntapos/db/`

#### `employees.sq`
```sql
CREATE TABLE IF NOT EXISTS employees (
    id                  TEXT PRIMARY KEY,
    user_id             TEXT REFERENCES users(id),      -- Optional: linked system account
    store_id            TEXT NOT NULL REFERENCES stores(id),
    first_name          TEXT NOT NULL,
    last_name           TEXT NOT NULL,
    email               TEXT,
    phone               TEXT,
    address             TEXT,
    date_of_birth       TEXT,
    hire_date           TEXT NOT NULL,
    department          TEXT,
    position            TEXT NOT NULL,
    salary              REAL,
    salary_type         TEXT NOT NULL DEFAULT 'MONTHLY', -- 'HOURLY', 'DAILY', 'WEEKLY', 'MONTHLY'
    commission_rate     REAL NOT NULL DEFAULT 0.0,       -- Percentage on sales
    emergency_contact   TEXT,
    documents           TEXT,                            -- JSON: [{name, url, type}]
    is_active           INTEGER NOT NULL DEFAULT 1,
    created_at          TEXT NOT NULL,
    updated_at          TEXT NOT NULL,
    deleted_at          TEXT,
    sync_id             TEXT NOT NULL,
    sync_version        INTEGER NOT NULL DEFAULT 1,
    sync_status         TEXT NOT NULL DEFAULT 'PENDING',
    store_id_filter     TEXT                             -- Redundant for index; equals store_id
);

CREATE INDEX idx_employees_store ON employees(store_id);
CREATE INDEX idx_employees_user ON employees(user_id);
CREATE INDEX idx_employees_active ON employees(is_active);

-- Queries
selectByStore:
SELECT * FROM employees
WHERE store_id = :storeId AND deleted_at IS NULL
ORDER BY first_name ASC;

selectById:
SELECT * FROM employees WHERE id = :id AND deleted_at IS NULL;

selectByUserId:
SELECT * FROM employees WHERE user_id = :userId AND deleted_at IS NULL LIMIT 1;

upsert:
INSERT OR REPLACE INTO employees VALUES ?;

softDelete:
UPDATE employees SET deleted_at = :deletedAt, updated_at = :updatedAt,
sync_status = 'PENDING', sync_version = sync_version + 1 WHERE id = :id;
```

#### `attendance_records.sq`
```sql
CREATE TABLE IF NOT EXISTS attendance_records (
    id              TEXT PRIMARY KEY,
    employee_id     TEXT NOT NULL REFERENCES employees(id),
    clock_in        TEXT NOT NULL,
    clock_out       TEXT,
    total_hours     REAL,
    overtime_hours  REAL NOT NULL DEFAULT 0.0,
    notes           TEXT,
    status          TEXT NOT NULL DEFAULT 'PRESENT', -- 'PRESENT', 'ABSENT', 'LATE', 'LEAVE'
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL,
    sync_id         TEXT NOT NULL,
    sync_version    INTEGER NOT NULL DEFAULT 1,
    sync_status     TEXT NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX idx_attendance_employee ON attendance_records(employee_id);
CREATE INDEX idx_attendance_date ON attendance_records(clock_in);
-- Composite: all records for employee in a date range
CREATE INDEX idx_attendance_emp_date ON attendance_records(employee_id, clock_in);

-- Queries
selectByEmployee:
SELECT * FROM attendance_records WHERE employee_id = :employeeId ORDER BY clock_in DESC;

selectByEmployeeForPeriod:
SELECT * FROM attendance_records
WHERE employee_id = :employeeId AND clock_in >= :from AND clock_in <= :to
ORDER BY clock_in ASC;

selectOpenRecord:
SELECT * FROM attendance_records
WHERE employee_id = :employeeId AND clock_out IS NULL LIMIT 1;

selectTodayForAllEmployees:
SELECT * FROM attendance_records
WHERE clock_in LIKE :datePrefix || '%'
ORDER BY employee_id ASC;

upsert:
INSERT OR REPLACE INTO attendance_records VALUES ?;

clockOut:
UPDATE attendance_records
SET clock_out = :clockOut, total_hours = :totalHours, overtime_hours = :overtimeHours,
    updated_at = :updatedAt, sync_status = 'PENDING', sync_version = sync_version + 1
WHERE id = :id;
```

#### `leave_records.sq`
```sql
CREATE TABLE IF NOT EXISTS leave_records (
    id              TEXT PRIMARY KEY,
    employee_id     TEXT NOT NULL REFERENCES employees(id),
    leave_type      TEXT NOT NULL,             -- 'SICK', 'ANNUAL', 'PERSONAL', 'UNPAID'
    start_date      TEXT NOT NULL,
    end_date        TEXT NOT NULL,
    reason          TEXT,
    status          TEXT NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'APPROVED', 'REJECTED'
    approved_by     TEXT REFERENCES users(id),
    approved_at     TEXT,
    rejection_reason TEXT,
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL,
    sync_id         TEXT NOT NULL,
    sync_version    INTEGER NOT NULL DEFAULT 1,
    sync_status     TEXT NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX idx_leave_employee ON leave_records(employee_id);
CREATE INDEX idx_leave_status ON leave_records(status);
CREATE INDEX idx_leave_emp_status ON leave_records(employee_id, status);

-- Queries
selectByEmployee:
SELECT * FROM leave_records WHERE employee_id = :employeeId ORDER BY start_date DESC;

selectPending:
SELECT * FROM leave_records WHERE status = 'PENDING' ORDER BY created_at ASC;

selectPendingForStore:
SELECT lr.* FROM leave_records lr
JOIN employees e ON lr.employee_id = e.id
WHERE lr.status = 'PENDING' AND e.store_id = :storeId
ORDER BY lr.created_at ASC;

updateStatus:
UPDATE leave_records
SET status = :status, approved_by = :approvedBy, approved_at = :approvedAt,
    rejection_reason = :rejectionReason, updated_at = :updatedAt,
    sync_status = 'PENDING', sync_version = sync_version + 1
WHERE id = :id;

upsert:
INSERT OR REPLACE INTO leave_records VALUES ?;
```

#### `payroll_records.sq`
```sql
CREATE TABLE IF NOT EXISTS payroll_records (
    id              TEXT PRIMARY KEY,
    employee_id     TEXT NOT NULL REFERENCES employees(id),
    period_start    TEXT NOT NULL,
    period_end      TEXT NOT NULL,
    base_salary     REAL NOT NULL,
    overtime_pay    REAL NOT NULL DEFAULT 0.0,
    commission      REAL NOT NULL DEFAULT 0.0,
    deductions      REAL NOT NULL DEFAULT 0.0,
    net_pay         REAL NOT NULL,
    status          TEXT NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'PAID'
    paid_at         TEXT,
    payment_ref     TEXT,
    notes           TEXT,
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL,
    sync_id         TEXT NOT NULL,
    sync_version    INTEGER NOT NULL DEFAULT 1,
    sync_status     TEXT NOT NULL DEFAULT 'PENDING',
    UNIQUE(employee_id, period_start)               -- One payroll per employee per period
);

CREATE INDEX idx_payroll_employee ON payroll_records(employee_id);
CREATE INDEX idx_payroll_period ON payroll_records(period_start);
CREATE INDEX idx_payroll_status ON payroll_records(status);

-- Queries
selectByEmployee:
SELECT * FROM payroll_records WHERE employee_id = :employeeId ORDER BY period_start DESC;

selectByPeriod:
SELECT pr.*, e.first_name, e.last_name, e.position
FROM payroll_records pr JOIN employees e ON pr.employee_id = e.id
WHERE pr.period_start = :periodStart AND e.store_id = :storeId
ORDER BY e.last_name ASC;

selectByEmployeeAndPeriod:
SELECT * FROM payroll_records
WHERE employee_id = :employeeId AND period_start = :periodStart LIMIT 1;

updateStatus:
UPDATE payroll_records
SET status = :status, paid_at = :paidAt, payment_ref = :paymentRef,
    updated_at = :updatedAt, sync_status = 'PENDING', sync_version = sync_version + 1
WHERE id = :id;

upsert:
INSERT OR REPLACE INTO payroll_records VALUES ?;
```

#### `shift_schedules.sq`
```sql
CREATE TABLE IF NOT EXISTS shift_schedules (
    id              TEXT PRIMARY KEY,
    employee_id     TEXT NOT NULL REFERENCES employees(id),
    store_id        TEXT NOT NULL REFERENCES stores(id),
    shift_date      TEXT NOT NULL,             -- ISO date: YYYY-MM-DD
    start_time      TEXT NOT NULL,             -- HH:MM (24h)
    end_time        TEXT NOT NULL,             -- HH:MM (24h)
    notes           TEXT,
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL,
    sync_id         TEXT NOT NULL,
    sync_version    INTEGER NOT NULL DEFAULT 1,
    sync_status     TEXT NOT NULL DEFAULT 'PENDING',
    UNIQUE(employee_id, shift_date)            -- One shift per employee per day
);

CREATE INDEX idx_shifts_employee ON shift_schedules(employee_id);
CREATE INDEX idx_shifts_store_date ON shift_schedules(store_id, shift_date);

-- Queries
selectByStoreAndWeek:
SELECT ss.*, e.first_name, e.last_name, e.position
FROM shift_schedules ss JOIN employees e ON ss.employee_id = e.id
WHERE ss.store_id = :storeId AND ss.shift_date >= :weekStart AND ss.shift_date <= :weekEnd
ORDER BY ss.shift_date ASC, e.last_name ASC;

selectByEmployee:
SELECT * FROM shift_schedules
WHERE employee_id = :employeeId AND shift_date >= :from AND shift_date <= :to
ORDER BY shift_date ASC;

upsert:
INSERT OR REPLACE INTO shift_schedules VALUES ?;

deleteById:
DELETE FROM shift_schedules WHERE id = :id;
```

### Migration File

**Location:** `shared/data/src/commonMain/sqldelight/com/zyntasolutions/zyntapos/db/migrations/5.sqm`

```sql
-- Migration 4 → 5: Phase 3 Staff & HR tables
-- Sprints: Phase 3 Sprint 1

CREATE TABLE IF NOT EXISTS employees (
    id TEXT PRIMARY KEY,
    user_id TEXT REFERENCES users(id),
    store_id TEXT NOT NULL,
    first_name TEXT NOT NULL,
    last_name TEXT NOT NULL,
    email TEXT,
    phone TEXT,
    address TEXT,
    date_of_birth TEXT,
    hire_date TEXT NOT NULL,
    department TEXT,
    position TEXT NOT NULL,
    salary REAL,
    salary_type TEXT NOT NULL DEFAULT 'MONTHLY',
    commission_rate REAL NOT NULL DEFAULT 0.0,
    emergency_contact TEXT,
    documents TEXT,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT,
    sync_id TEXT NOT NULL,
    sync_version INTEGER NOT NULL DEFAULT 1,
    sync_status TEXT NOT NULL DEFAULT 'PENDING',
    store_id_filter TEXT
);

CREATE INDEX IF NOT EXISTS idx_employees_store ON employees(store_id);
CREATE INDEX IF NOT EXISTS idx_employees_user ON employees(user_id);
CREATE INDEX IF NOT EXISTS idx_employees_active ON employees(is_active);

CREATE TABLE IF NOT EXISTS attendance_records (
    id TEXT PRIMARY KEY,
    employee_id TEXT NOT NULL,
    clock_in TEXT NOT NULL,
    clock_out TEXT,
    total_hours REAL,
    overtime_hours REAL NOT NULL DEFAULT 0.0,
    notes TEXT,
    status TEXT NOT NULL DEFAULT 'PRESENT',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    sync_id TEXT NOT NULL,
    sync_version INTEGER NOT NULL DEFAULT 1,
    sync_status TEXT NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX IF NOT EXISTS idx_attendance_employee ON attendance_records(employee_id);
CREATE INDEX IF NOT EXISTS idx_attendance_date ON attendance_records(clock_in);
CREATE INDEX IF NOT EXISTS idx_attendance_emp_date ON attendance_records(employee_id, clock_in);

CREATE TABLE IF NOT EXISTS leave_records (
    id TEXT PRIMARY KEY,
    employee_id TEXT NOT NULL,
    leave_type TEXT NOT NULL,
    start_date TEXT NOT NULL,
    end_date TEXT NOT NULL,
    reason TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING',
    approved_by TEXT,
    approved_at TEXT,
    rejection_reason TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    sync_id TEXT NOT NULL,
    sync_version INTEGER NOT NULL DEFAULT 1,
    sync_status TEXT NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX IF NOT EXISTS idx_leave_employee ON leave_records(employee_id);
CREATE INDEX IF NOT EXISTS idx_leave_status ON leave_records(status);
CREATE INDEX IF NOT EXISTS idx_leave_emp_status ON leave_records(employee_id, status);

CREATE TABLE IF NOT EXISTS payroll_records (
    id TEXT PRIMARY KEY,
    employee_id TEXT NOT NULL,
    period_start TEXT NOT NULL,
    period_end TEXT NOT NULL,
    base_salary REAL NOT NULL,
    overtime_pay REAL NOT NULL DEFAULT 0.0,
    commission REAL NOT NULL DEFAULT 0.0,
    deductions REAL NOT NULL DEFAULT 0.0,
    net_pay REAL NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    paid_at TEXT,
    payment_ref TEXT,
    notes TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    sync_id TEXT NOT NULL,
    sync_version INTEGER NOT NULL DEFAULT 1,
    sync_status TEXT NOT NULL DEFAULT 'PENDING'
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_payroll_emp_period ON payroll_records(employee_id, period_start);
CREATE INDEX IF NOT EXISTS idx_payroll_employee ON payroll_records(employee_id);
CREATE INDEX IF NOT EXISTS idx_payroll_status ON payroll_records(status);

CREATE TABLE IF NOT EXISTS shift_schedules (
    id TEXT PRIMARY KEY,
    employee_id TEXT NOT NULL,
    store_id TEXT NOT NULL,
    shift_date TEXT NOT NULL,
    start_time TEXT NOT NULL,
    end_time TEXT NOT NULL,
    notes TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    sync_id TEXT NOT NULL,
    sync_version INTEGER NOT NULL DEFAULT 1,
    sync_status TEXT NOT NULL DEFAULT 'PENDING'
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_shifts_emp_date ON shift_schedules(employee_id, shift_date);
CREATE INDEX IF NOT EXISTS idx_shifts_store_date ON shift_schedules(store_id, shift_date);
```

### Modified File

**`shared/data/src/commonMain/kotlin/.../data/local/migration/DatabaseMigrations.kt`** — Add:
```kotlin
5 to { db ->
    db.execute(null, MIGRATION_5_SQL, 0)  // runs 5.sqm contents
}
```

---

## Tasks

- [ ] **1.1** Write `employees.sq` with full DDL + all indexes + selectByStore, selectById, selectByUserId, upsert, softDelete queries
- [ ] **1.2** Write `attendance_records.sq` with DDL + indexes + selectByEmployee, selectByEmployeeForPeriod, selectOpenRecord, selectTodayForAllEmployees, upsert, clockOut queries
- [ ] **1.3** Write `leave_records.sq` with DDL + indexes + selectByEmployee, selectPending, selectPendingForStore, updateStatus, upsert queries
- [ ] **1.4** Write `payroll_records.sq` with DDL + UNIQUE constraint on `(employee_id, period_start)` + all queries
- [ ] **1.5** Write `shift_schedules.sq` with DDL + UNIQUE constraint on `(employee_id, shift_date)` + all queries
- [ ] **1.6** Create `5.sqm` migration file with all 5 CREATE TABLE + index statements
- [ ] **1.7** Update `DatabaseMigrations.kt` to register migration 5
- [ ] **1.8** Run `./gradlew generateSqlDelightInterface` — verify no compilation errors
- [ ] **1.9** Run `./gradlew verifySqlDelightMigration` — verify migration chain is valid
- [ ] **1.10** Run `./gradlew detekt` — no new violations

---

## Verification

```bash
# Schema compiles
./gradlew :shared:data:generateCommonMainZyntaPosDatabaseInterface

# Migration chain validates
./gradlew :shared:data:verifySqlDelightMigration

# Static analysis
./gradlew :shared:data:detekt
```

---

## CRDT Notes

| Table | CRDT Type | Rationale |
|-------|-----------|-----------|
| `employees` | LWW-Register (field-level) | Profile changes are infrequent; last-write-wins per field |
| `attendance_records` | LWW-Register | Clock-in/out is device-local; server authoritative on conflict |
| `leave_records` | LWW-Register | Approval status updated by one manager at a time |
| `payroll_records` | LWW-Register | Generated periodically; immutable after PAID status |
| `shift_schedules` | LWW-Register | Schedule edits from manager's device; server wins |

---

## Definition of Done

- [ ] All 5 `.sq` files created and compile without errors
- [ ] Migration `5.sqm` applied successfully by `./gradlew verifySqlDelightMigration`
- [ ] `DatabaseMigrations.kt` updated to version 5
- [ ] `./gradlew detekt` passes
- [ ] Commit: `feat(data): add Staff & HR SQLDelight schema (migration v5)`
