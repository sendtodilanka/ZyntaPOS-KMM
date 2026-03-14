package com.zyntasolutions.zyntapos.domain.model

/**
 * Represents a remote diagnostic session initiated by a Zynta technician for a specific store device.
 *
 * ## Lifecycle
 * 1. Technician creates session via admin panel → status = [DiagnosticSessionStatus.PENDING_CONSENT]
 * 2. Store operator accepts on-device consent prompt → status = [DiagnosticSessionStatus.ACTIVE]
 * 3. Session is used for diagnostics → expires after 15 min TTL or revoked explicitly
 *
 * ## Visit Types
 * - **REMOTE** — Standard JIT token session over the internet; technician accesses diagnostic
 *   relay from any network location. Requires store operator consent on-device.
 * - **ON_SITE** — Site-visit token bound to a specific hardware fingerprint. Technician must
 *   be physically present and present the token via NFC or QR code at the customer site.
 *   Adds an extra layer of physical proof-of-presence beyond the remote consent flow.
 *
 * ## Security model
 * - JIT token is single-use and signed with HMAC-SHA256 using the admin JWT secret
 * - Token carries: sessionId, technicianId, storeId, scope, visitType, exp (15-min TTL)
 * - Store operator consent is required before the session becomes ACTIVE (both visit types)
 * - ON_SITE sessions additionally require hardware token validation at the customer site
 * - Revocation is immediate and cannot be reversed
 *
 * @property id                UUID v4 session identifier.
 * @property storeId           UUID of the store being diagnosed.
 * @property technicianId      Admin user UUID of the technician performing diagnostics.
 * @property requestedBy       Admin user UUID who created the session request.
 * @property consentGrantedAt  Epoch-ms timestamp when the store operator granted consent (null if pending).
 * @property expiresAt         Epoch-ms timestamp when the session token expires (15-min from creation).
 * @property status            Current lifecycle state of this session.
 * @property dataScope         Level of data access granted to the technician.
 * @property visitType         Whether this is a remote or on-site visit session.
 * @property siteVisitToken    HMAC-SHA256 token for on-site hardware validation (null for REMOTE sessions).
 *                             Only present at session creation; stored as a hash on the backend.
 * @property hardwareScope     Hardware components the on-site technician is authorised to access
 *                             (null for REMOTE sessions, e.g. "PRINTER,SCANNER,CASH_DRAWER").
 */
data class DiagnosticSession(
    val id: String,
    val storeId: String,
    val technicianId: String,
    val requestedBy: String,
    val consentGrantedAt: Long?,
    val expiresAt: Long,
    val status: DiagnosticSessionStatus,
    val dataScope: DiagnosticDataScope,
    val visitType: VisitType = VisitType.REMOTE,
    /** Short-lived token for on-site technician kiosk authentication (null for REMOTE sessions). */
    val siteVisitToken: String? = null,
    val hardwareScope: String? = null,
)

/**
 * Lifecycle states for a [DiagnosticSession].
 */
enum class DiagnosticSessionStatus {
    /** Created — waiting for store operator to accept or deny the consent prompt. */
    PENDING_CONSENT,
    /** Consent granted by store operator — technician has active access. */
    ACTIVE,
    /** Session TTL elapsed without consent being granted. */
    EXPIRED,
    /** Explicitly revoked by admin or store operator. */
    REVOKED,
}

/**
 * Whether the diagnostic session is conducted remotely or on-site.
 *
 * - [REMOTE]  — technician connects via the admin panel WebSocket tunnel.
 * - [ON_SITE] — technician is physically present and authenticates via a short-lived
 *               [DiagnosticSession.siteVisitToken] scanned as a QR code on the store device.
 */
enum class VisitType {
    /** Standard remote session — technician accesses the diagnostic relay over the internet. */
    REMOTE,
    /**
     * On-site session — technician is physically present at the customer's location.
     * A [DiagnosticSession.siteVisitToken] is generated and must be presented (e.g., via NFC/QR)
     * at the device to prove physical presence before hardware-level access is granted.
     * The token is scoped to specific hardware components in [DiagnosticSession.hardwareScope].
     */
    ON_SITE,
}

/**
 * Defines the scope of data the technician can access during a [DiagnosticSession].
 */
enum class DiagnosticDataScope {
    /** Read-only access to diagnostic data only (logs, metrics, DB health). No transaction data. */
    READ_ONLY_DIAGNOSTICS,
    /** Full read-only access including transaction records (requires elevated justification). */
    FULL_READ_ONLY,
}
