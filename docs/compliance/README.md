# Compliance Documentation — ZyntaPOS

**Status:** Phase 1 compliance posture documented. Implementation gaps noted.
**Last updated:** 2026-02-25
**Jurisdiction:** Sri Lanka (primary); GDPR-adjacent personal data practices documented.

---

## 1. PCI-DSS

### Scope Reduction Strategy

ZyntaPOS is designed to minimise PCI-DSS scope. The system accepts card payments but does NOT
store, process, or transmit Primary Account Numbers (PANs).

**Verified:** The `orders` table schema (`orders.sq`) stores:
- `payment_method` — a text label (e.g., "CARD", "CASH", "SPLIT")
- `payment_splits_json` — a JSON snapshot of payment split amounts
- `amount_tendered`, `change_amount`, `total`

No PAN, CVV, expiry date, or magnetic stripe data is stored in any SQLite table. Card transaction
processing is delegated to an external payment terminal (integrated at the hardware/HAL layer).
ZyntaPOS receives only a payment confirmation and optional last-four-digits reference.

### Classification

ZyntaPOS is expected to qualify as **PCI-DSS SAQ-B** (payment terminal devices, no electronic
storage of cardholder data) or **SAQ-B-IP** (if the terminal communicates over IP). Formal
self-assessment has not been completed yet.

### Current PCI Controls in Place

| Control | Implementation | Status |
|---------|---------------|--------|
| Encrypted storage | SQLCipher 4.5 AES-256 for all local data | Implemented |
| No PAN storage | Verified — no card fields in SQLite schema | Confirmed |
| Secure key storage | Android Keystore / JCE PKCS12 (never plaintext on disk) | Implemented |
| Encrypted preferences | AES-256-GCM SecurePreferences | Implemented |
| Network security | TLS via Ktor (system trust store) | Implemented |
| Access control | RBAC with role-based permissions | Implemented |
| Audit trail | AuditEntry domain model + SecurityAuditLogger | **Partial — see gap below** |

### Known PCI Gap

`AuditRepositoryImpl` has 3 `TODO` stubs (MERGED-D2) — audit entries are generated in memory
by `SecurityAuditLogger` but are not persisted to the database. This means the audit trail
required by PCI-DSS Requirement 10 (track and monitor access) is not durably stored in Phase 1.

---

## 2. GDPR

### Personal Data Inventory (Customer Model)

The `Customer` domain model (`shared/domain/src/commonMain/.../model/Customer.kt`) stores:

| Field | PII Classification | Required? |
|-------|-------------------|-----------|
| `name` | Personal name | Yes |
| `phone` | Contact identifier | Yes |
| `email` | Personal email | No (optional) |
| `address` | Physical location | No (optional) |
| `gender` | Personal characteristic | No (optional) |
| `birthday` | Date of birth | No (optional) |
| `notes` | Free-text (may contain PII) | No (optional) |
| `groupId` | Customer segment (non-personal) | No |
| `loyaltyPoints` | Transactional (non-personal) | No |
| `creditLimit` / `creditEnabled` | Financial (borderline personal) | No |

Minimum required fields for a customer record: `name` and `phone`.

Walk-in customers (one-time purchases) are represented with `isWalkIn = true` and receive
no persistent profile — this is the privacy-respecting default for anonymous transactions.

### GDPR Rights Implementation Status

| Right | Status |
|-------|--------|
| Right to access (data export) | **Planned** — module comment in `:composeApp:feature:customers` references GDPR export. No `ExportCustomerDataUseCase` found in the codebase as of Phase 1. |
| Right to erasure (delete) | **Partial** — `CustomerIntent.DeleteCustomer` performs a soft-delete (`isActive = false`). Hard erasure (permanent record deletion) is not implemented. |
| Right to rectification | Implemented via `CustomerIntent.UpdateFormField` / `SaveCustomer`. |
| Data minimisation | Enforced by making most PII fields optional in the domain model. |
| Consent | Not tracked in the data model. Out of scope for Phase 1 (local POS). |

### Data Retention

No automated data retention policy is currently configured. Customer records, order history, and
audit logs are retained indefinitely in the local SQLite database. A configurable retention policy
(e.g., purge orders older than N years) is planned for Phase 2.

### Data Transfer

In Phase 1, customer PII is not transmitted to any third-party analytics or marketing service.
Sync operations push customer records to the ZyntaPOS cloud backend (operator-controlled). The
backend data processing agreement (DPA) between Zynta Solutions and merchants is a commercial
obligation outside the scope of this document.

---

## 3. IRD E-Invoice (Sri Lanka) — Phase 4

> **Status:** IRD e-invoice integration has been deferred to **Phase 4**.
> The implementation code has been removed from the codebase pending IRD sandbox access and
> finalisation of the XML invoice format specification.
>
> ZyntaPOS still supports the underlying tax infrastructure required for IRD compliance:
> - Multiple tax rates per order (via `TaxGroup` domain model and `tax_groups` SQLite table)
> - Per-line-item tax calculation
> - VAT/TIN tracking for business customers
>
> Full IRD e-invoicing (mTLS client certificate, submission lifecycle, IRD reference number tracking)
> will be implemented in Phase 4.

---

## 4. Audit Trail

### AuditEntry Domain Model

**File:** `shared/domain/src/commonMain/.../model/AuditEntry.kt`

```kotlin
data class AuditEntry(
    val id: String,
    val eventType: AuditEventType,
    val userId: String,
    val deviceId: String,
    val payload: String,   // JSON event-specific data
    val success: Boolean,
    val createdAt: Instant,
)
```

Auditable event types (`AuditEventType`):
- `LOGIN_ATTEMPT`, `LOGOUT`
- `PERMISSION_DENIED`
- `ORDER_VOID`
- `STOCK_ADJUSTMENT`
- `USER_CREATED`, `USER_DEACTIVATED`
- `SETTINGS_CHANGED`
- `REGISTER_OPENED`, `REGISTER_CLOSED`
- `DATA_EXPORT`

### Implementation Status — CRITICAL GAP

**`AuditRepositoryImpl` has 3 unimplemented methods** (tracked as MERGED-D2):

```kotlin
override suspend fun insert(entry: AuditEntry): Unit = TODO("Requires audit_logs SQLDelight schema — tracked in MERGED-D2")
override fun observeAll(): Flow<List<AuditEntry>> = TODO("Requires audit_logs SQLDelight schema — tracked in MERGED-D2")
override fun observeByUserId(userId: String): Flow<List<AuditEntry>> = TODO("Requires audit_logs SQLDelight schema — tracked in MERGED-D2")
```

**Impact:** All audit events generated by `SecurityAuditLogger` are discarded — they are created
in memory but never written to the database. The `audit_log` SQLDelight schema exists in
`audit_log.sq` but the corresponding SQLDelight queries have not been implemented in
`AuditRepositoryImpl`.

Until MERGED-D2 is resolved, ZyntaPOS does not have a functioning audit trail. This is a
compliance risk for PCI-DSS Requirement 10 and any regulatory requirement for access logging.

---

## 5. Session Timeout (Auto-Lock)

`SessionManager` (`composeApp/feature/auth/src/commonMain/.../session/SessionManager.kt`)
enforces idle-based PIN lock:

| Role | Timeout |
|------|---------|
| CASHIER | 10 minutes |
| STORE_MANAGER | 20 minutes |
| ADMIN | 30 minutes |
| Default | 15 minutes |

After the timeout, `AuthEffect.ShowPinLock` is emitted and the PIN lock screen is displayed.
The cashier must re-enter their PIN to resume.

Session timeouts are currently hardcoded constants. Configurable per-role timeouts via
`SettingsRepository` are planned for Phase 2.

---

## 6. Data Retention Summary

| Data Category | Current Retention | Target Policy |
|--------------|-------------------|---------------|
| Orders | Indefinite (local SQLite) | Phase 2: configurable (e.g., 7 years for tax law) |
| Customers | Indefinite | Phase 2: GDPR erasure workflow |
| Audit logs | Not persisted (MERGED-D2 gap) | Phase 2: configurable purge (90 days minimum) |
| Sync queue | SYNCED rows kept indefinitely | `pruneSynced()` available, not scheduled |
| E-invoices | N/A (Phase 4) | IRD e-invoice integration deferred to Phase 4 |
| Session tokens | Cleared on logout | Immediate |

---

## 7. Compliance Gap Summary

| Requirement | Gap | Priority |
|-------------|-----|----------|
| Audit trail persistence (PCI-DSS R10) | `AuditRepositoryImpl` TODOs — MERGED-D2 | Critical |
| GDPR right to erasure | Soft-delete only; hard erase not implemented | High (Phase 2) |
| GDPR data export | `ExportCustomerDataUseCase` not yet implemented | High (Phase 2) |
| Data retention policy | No automated purge scheduled | Medium (Phase 2) |
| GDPR consent tracking | Not in data model | Low (Phase 1 local POS, no marketing) |
| PCI-DSS SAQ self-assessment | Not formally completed | Admin (pre-launch) |
