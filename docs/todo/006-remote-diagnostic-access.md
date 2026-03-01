# TODO-006: Remote Diagnostic Access (License-Gated Lockbox Pattern)

**Status:** Pending
**Priority:** HIGH — Design now, implement Phase 2
**Phase:** Phase 2 (Growth)
**Created:** 2026-03-01

---

## Problem Statement

ZyntaPOS technicians need remote access to customer production deployments for periodic maintenance and bug fixes. However:

- Customer financial data (sales, revenue, margins, payroll) must **never** be accessible to technicians
- Site visits should be minimized (only for hardware issues)
- The system must be secure against both external attackers and insider threats
- Full audit trail is legally required
- Customer consent must be obtained for every access session

---

## International Best Practices Research

### How Major Companies Handle This

| Company | Pattern |
|---------|---------|
| **Shopify** | Collaborator accounts — merchant shares 4-digit code, explicitly approves, assigns granular permissions. Cannot access bank details or payments by default. |
| **Microsoft Azure** | Customer Lockbox — JIT access, customer must approve/deny from portal, time-bound, granular scope, fully audited |
| **NCR Aloha** | Command Center — purpose-built diagnostic tool, outbound-only connections (POS initiates), system health only |
| **Square** | Device codes + POS codes as two auth layers, continuous security patches pushed automatically |

### Compliance Framework Requirements

| Framework | Key Requirement |
|-----------|----------------|
| **PCI-DSS v4.0.1** | MFA mandatory for ALL remote access. JIT access recommended. Copy/relocation of PAN data must be prevented. |
| **SOC 2** | Vendor access must be logged, privilege escalation approved, evidence retained 6-12 months |
| **ISO 27001** | No generic accounts (every action traceable to individual). Privileged access reviewed every 30 days. Logs retained 12+ months in tamper-proof storage. |
| **GDPR** | Data Processing Agreement (DPA) mandatory. Processor must act on controller's documented instructions only. |

---

## Decision: License-Gated Diagnostic Mode (No OPERATOR Role)

With a unique license per deployment, a separate OPERATOR role is unnecessary. Instead, implement a **Diagnostic Mode** that is architecturally separated from business data.

### Why This Is Superior to an OPERATOR Role

| Aspect | OPERATOR Role | Diagnostic Mode |
|--------|--------------|-----------------|
| Standing access | Always exists | Zero standing privileges (JIT) |
| Data exposure | Depends on permissions | Architecturally impossible to access business data |
| Customer control | None (role is built-in) | Full consent required per session |
| Attack surface | Permanent credential to steal | Ephemeral, time-limited, scoped token |
| Compliance | Harder to audit | PCI-DSS / SOC 2 / ISO 27001 aligned |
| Trust model | Trust the technician | Trust the architecture |

---

## Architecture: Three-Layer Isolation

```
+--------------------------------------------------+
|              CUSTOMER LAYER                      |
|  Full POS: Sales, Orders, Revenue, Customers,    |
|  Bank details, Inventory costs, Margins          |
|  Access: ADMIN, STORE_MANAGER, CASHIER, etc.     |
+--------------------------------------------------+
                    ^ FIREWALL (no cross-access)
+--------------------------------------------------+
|           DIAGNOSTIC LAYER                       |
|  System health, Error logs, DB integrity,        |
|  Sync status, Hardware diagnostics, Config,      |
|  Performance metrics, License validation         |
|  Access: ZyntaPOS Technician (via Diagnostic Mode)|
+--------------------------------------------------+
                    ^ FIREWALL (no cross-access)
+--------------------------------------------------+
|           AUDIT LAYER (Read-only)                |
|  All actions logged immutably                    |
|  Visible to: ADMIN + Technician (own actions)    |
+--------------------------------------------------+
```

---

## Flow: Step by Step

### 1. License-Based Authentication

```
Technician at ZyntaPOS HQ
  -> Logs into ZyntaPOS Support Portal (panel.zyntapos.com)
  -> Selects customer by license ID
  -> Generates a "Support Token" (signed JWT)
     - Contains: technician_id, license_id, scope="diagnostic",
       ttl=2h, ticket_number, reason
  -> Token sent to technician
```

### 2. Customer Consent (Lockbox Pattern)

```
Customer's ZyntaPOS App
  -> Shows notification: "ZyntaPOS Support requests diagnostic access"
  -> Shows: Technician name, Reason, Duration (2h), Scope (diagnostic only)
  -> Shows: "Financial data will NOT be accessible"
  -> ADMIN must approve (PIN/password confirmation)
  -> If denied -> no access. Technician must call customer.
```

### 3. Diagnostic Session (Scoped Access)

After approval, the app enters "Diagnostic Mode" with a visual indicator in the status bar.

**Technician CAN access:**
- System health metrics (CPU, memory, storage)
- Error/crash logs (last 30 days)
- Database integrity check results
- Sync engine status (queue depth, last sync, errors)
- Hardware diagnostics (printer, scanner, drawer status)
- App configuration (non-secret settings)
- License status and feature flags
- Network connectivity diagnostics
- SQLite VACUUM / REINDEX operations
- Apply patches/migrations

**Technician CANNOT access:**
- Sales data, orders, revenue
- Customer personal data
- Financial reports, P&L, margins
- Employee personal data, payroll
- Bank/payment details
- Inventory costs/margins
- Audit logs of business operations

### 4. Session End

```
  -> Token expires (auto-end after 2h max)
  -> OR Technician ends session manually
  -> OR ADMIN revokes access from settings
  -> Diagnostic Mode deactivated
  -> Full session summary logged to audit trail
  -> Customer ADMIN receives summary notification
```

---

## Communication Flow (Panel as Relay)

```
+---------------+         +---------------+         +---------------+
|  Technician   |         |  ZyntaPOS     |         |  Customer     |
|  (Browser)    |         |  Panel API    |         |  POS App      |
+------+--------+         +------+--------+         +------+--------+
       |                         |                         |
       |  1. Request diagnostic  |                         |
       |  session for license    |                         |
       |  ZYNTA-XXXX             |                         |
       +------------------------>|                         |
       |                         |  2. Push notification   |
       |                         |  "Support requests      |
       |                         |   diagnostic access"    |
       |                         +------------------------>|
       |                         |                         |
       |                         |  3. ADMIN approves      |
       |                         |<------------------------+
       |                         |                         |
       |  4. WebSocket tunnel    |  5. WebSocket tunnel    |
       |  established            |  established            |
       |<----------------------->|<----------------------->|
       |                         |                         |
       |  6. Diagnostic commands |  7. Scoped execution    |
       |  (system health, logs)  |  (no business data)     |
       +------------------------>+------------------------>|
       |                         |                         |
       |  8. Results             |  9. Results             |
       |<------------------------+<------------------------+
       |                         |                         |
       |  10. End session        |  11. Audit log written  |
       +------------------------>+------------------------>|
```

**Key:** The panel acts as a relay — the technician never connects directly to the customer's device. The POS app initiates the outbound WebSocket connection (firewall-friendly, no port opening needed on customer network).

---

## Implementation Plan

### Domain Models

**File:** `shared/domain/src/commonMain/.../model/DiagnosticSession.kt`

```kotlin
data class DiagnosticSession(
    val id: String,
    val licenseId: String,
    val technicianId: String,
    val technicianName: String,
    val reason: String,
    val ticketNumber: String,
    val scopes: Set<DiagnosticScope>,
    val grantedAt: Instant,
    val expiresAt: Instant,
    val revokedAt: Instant? = null,
    val approvedByUserId: String,  // ADMIN who approved
)

enum class DiagnosticScope {
    SYSTEM_HEALTH,      // Read system metrics
    ERROR_LOGS,         // Read error/crash logs
    DB_MAINTENANCE,     // VACUUM, REINDEX, integrity check
    HARDWARE_DIAG,      // Printer/scanner/drawer test
    SYNC_DIAG,          // Sync queue inspection
    CONFIG_VIEW,        // Non-secret configuration
    PATCH_APPLY,        // Apply hotfix migrations
}
```

### Token Validator

**File:** `shared/security/src/commonMain/.../DiagnosticTokenValidator.kt`

```kotlin
class DiagnosticTokenValidator(
    private val licenseManager: LicenseManager,
) {
    fun validateToken(jwt: String): Result<DiagnosticSession>
    fun isSessionActive(session: DiagnosticSession): Boolean
}
```

### Module Dependency Isolation (Compile-Time Enforcement)

The diagnostic feature module must have **no dependency** on business repositories (OrderRepository, CustomerRepository, ProductRepository, etc.). It only depends on SystemRepository, AuditRepository, and HAL interfaces. This makes data isolation compile-time enforced, not just permission-checked.

```
:composeApp:feature:diagnostic
  -> :shared:domain (DiagnosticSession model only)
  -> :shared:hal (hardware diagnostics)
  -> :shared:security (token validation)
  -> :shared:core (utilities)

  MUST NOT depend on:
  -> :shared:data (contains business repositories)
```

### For Hardware Issues (Site Visits)

When remote diagnostics determine a hardware issue:
- Technician generates a "Site Visit Token" (extended scope, includes hardware config)
- On-site, technician uses the same Diagnostic Mode on the physical device
- Still no access to business data
- Site visit audit trail records physical presence

---

## Security Attack Surface Analysis

| Attack Vector | Mitigation |
|---------------|-----------|
| Stolen diagnostic token | Time-limited (2h max), scoped, requires customer approval. Token revocable. |
| Technician collusion | Diagnostic module has no dependency on business repositories. Data isolation is architectural, not permission-based. |
| Man-in-the-middle | All WebSocket connections over TLS. Panel relay ensures no direct connection to customer device. |
| Token replay | One-time use session ID. Token bound to specific license ID. |
| Insider threat at ZyntaPOS | All diagnostic actions logged in customer's immutable audit trail. Technician identity traceable (no shared accounts). |
| Customer impersonation | Approval requires ADMIN PIN/password confirmation. |

---

## Validation Checklist

- [ ] DiagnosticSession model defined in `:shared:domain`
- [ ] DiagnosticTokenValidator in `:shared:security`
- [ ] Diagnostic feature module with no business data dependencies
- [ ] WebSocket relay architecture on panel.zyntapos.com
- [ ] Customer consent flow (notification + ADMIN approval)
- [ ] Visual indicator when Diagnostic Mode is active
- [ ] Automatic session expiry (2h max)
- [ ] ADMIN can revoke access at any time
- [ ] Full audit trail for every diagnostic command
- [ ] Session summary notification to ADMIN on completion
- [ ] Hardware site visit token support
