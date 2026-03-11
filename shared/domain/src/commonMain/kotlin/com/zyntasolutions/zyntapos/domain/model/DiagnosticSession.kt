package com.zyntasolutions.zyntapos.domain.model

/**
 * Represents a remote diagnostic session initiated by a Zynta technician for a specific store device.
 *
 * ## Lifecycle
 * 1. Technician creates session via admin panel → status = [DiagnosticSessionStatus.PENDING_CONSENT]
 * 2. Store operator accepts on-device consent prompt → status = [DiagnosticSessionStatus.ACTIVE]
 * 3. Session is used for diagnostics → expires after 15 min TTL or revoked explicitly
 *
 * ## Security model
 * - JIT token is single-use and signed with HMAC-SHA256 using the admin JWT secret
 * - Token carries: sessionId, technicianId, storeId, scope, exp (15-min TTL)
 * - Store operator consent is required before the session becomes ACTIVE
 * - Revocation is immediate and cannot be reversed
 *
 * @property id               UUID v4 session identifier.
 * @property storeId          UUID of the store being diagnosed.
 * @property technicianId     Admin user UUID of the technician performing diagnostics.
 * @property requestedBy      Admin user UUID who created the session request.
 * @property consentGrantedAt Epoch-ms timestamp when the store operator granted consent (null if pending).
 * @property expiresAt        Epoch-ms timestamp when the session token expires (15-min from creation).
 * @property status           Current lifecycle state of this session.
 * @property dataScope        Level of data access granted to the technician.
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
 * Defines the scope of data the technician can access during a [DiagnosticSession].
 */
enum class DiagnosticDataScope {
    /** Read-only access to diagnostic data only (logs, metrics, DB health). No transaction data. */
    READ_ONLY_DIAGNOSTICS,
    /** Full read-only access including transaction records (requires elevated justification). */
    FULL_READ_ONLY,
}
