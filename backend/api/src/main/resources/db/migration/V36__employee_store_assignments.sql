-- V36: Employee store assignments for multi-store roaming (C3.4)
-- Tracks which employees are assigned to which additional stores beyond their primary store.

CREATE TABLE employee_store_assignments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id     TEXT NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    store_id        TEXT NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    start_date      DATE NOT NULL DEFAULT CURRENT_DATE,
    end_date        DATE,
    is_temporary    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (employee_id, store_id)
);

CREATE INDEX idx_employee_store_assignments_employee ON employee_store_assignments(employee_id);
CREATE INDEX idx_employee_store_assignments_store ON employee_store_assignments(store_id);
CREATE INDEX idx_employee_store_assignments_active ON employee_store_assignments(employee_id) WHERE end_date IS NULL;

-- Add store_id to attendance_records for cross-store clock-in tracking
ALTER TABLE attendance_records ADD COLUMN IF NOT EXISTS store_id TEXT REFERENCES stores(id);
