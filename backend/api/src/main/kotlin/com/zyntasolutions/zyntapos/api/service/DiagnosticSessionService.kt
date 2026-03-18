package com.zyntasolutions.zyntapos.api.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zyntasolutions.zyntapos.api.config.AppConfig
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

// ── Exposed table ────────────────────────────────────────────────────────────

object DiagnosticSessions : Table("diagnostic_sessions") {
    val id                   = uuid("id")
    val storeId              = uuid("store_id")
    val technicianId         = uuid("technician_id")
    val requestedBy          = uuid("requested_by")
    val tokenHash            = varchar("token_hash", 64)
    val dataScope            = varchar("data_scope", 32)
    val status               = varchar("status", 32)
    val visitType            = varchar("visit_type", 16).default("REMOTE")
    val siteVisitTokenHash   = varchar("site_visit_token_hash", 64).nullable()
    val hardwareScope        = varchar("hardware_scope", 256).nullable()
    val siteVisitPresentedAt = long("site_visit_presented_at").nullable()
    val consentGrantedAt     = long("consent_granted_at").nullable()
    val expiresAt            = long("expires_at")
    val revokedAt            = long("revoked_at").nullable()
    val revokedBy            = uuid("revoked_by").nullable()
    val createdAt            = long("created_at")
    override val primaryKey  = PrimaryKey(id)
}

// ── Response models ──────────────────────────────────────────────────────────

@Serializable
data class DiagnosticSessionResponse(
    val id: String,
    val storeId: String,
    val technicianId: String,
    val requestedBy: String,
    val dataScope: String,
    val status: String,
    val visitType: String,
    val expiresAt: Long,
    val consentGrantedAt: Long?,
    val createdAt: Long,
    /** Raw JIT token — only present at creation time, never stored or returned again. */
    val token: String? = null,
    /**
     * Raw site visit token for ON_SITE sessions — only present at creation time.
     * Must be presented by the technician at the customer site (NFC/QR).
     * Never stored in plaintext; backend stores SHA-256 hash only.
     */
    val siteVisitToken: String? = null,
    /**
     * Comma-separated hardware component identifiers the technician may access.
     * Non-null only for ON_SITE sessions. Example: "PRINTER,SCANNER,CASH_DRAWER".
     */
    val hardwareScope: String? = null,
    /** Epoch-ms when the technician presented the site visit token on-device (null until validated). */
    val siteVisitPresentedAt: Long? = null,
)

// ── Service ──────────────────────────────────────────────────────────────────

class DiagnosticSessionService(private val config: AppConfig) {

    private val log = LoggerFactory.getLogger(DiagnosticSessionService::class.java)

    private val TOKEN_TTL_MS = 15L * 60 * 1000   // 15 minutes

    /**
     * Creates a new JIT diagnostic session token for the given store/technician.
     * Any existing PENDING_CONSENT or ACTIVE session for the store is revoked first.
     * Returns [DiagnosticSessionResponse] with `token` populated (single-use, never returned again).
     */
    suspend fun createSession(
        storeId: UUID,
        technicianId: UUID,
        requestedBy: UUID,
        scope: String,
        visitType: String = "REMOTE",
    ): DiagnosticSessionResponse = newSuspendedTransaction {
        val now       = java.time.Instant.now().toEpochMilli()
        val expiresAt = now + TOKEN_TTL_MS
        val sessionId = UUID.randomUUID()

        // Revoke any existing active/pending sessions for this store
        DiagnosticSessions.update({
            (DiagnosticSessions.storeId eq storeId) and
            (DiagnosticSessions.status inList listOf("PENDING_CONSENT", "ACTIVE"))
        }) {
            it[status]    = "REVOKED"
            it[revokedAt] = now
            it[revokedBy] = requestedBy
        }

        // Generate raw token and store only its hash
        val rawToken  = generateToken(sessionId, technicianId, storeId, scope, expiresAt)
        val tokenHash = sha256Hex(rawToken)

        DiagnosticSessions.insert {
            it[id]               = sessionId
            it[DiagnosticSessions.storeId]      = storeId
            it[DiagnosticSessions.technicianId] = technicianId
            it[DiagnosticSessions.requestedBy]  = requestedBy
            it[DiagnosticSessions.tokenHash]    = tokenHash
            it[dataScope]        = scope
            it[status]           = "PENDING_CONSENT"
            it[DiagnosticSessions.visitType]    = visitType
            it[DiagnosticSessions.expiresAt]    = expiresAt
            it[createdAt]        = now
        }

        DiagnosticSessionResponse(
            id               = sessionId.toString(),
            storeId          = storeId.toString(),
            technicianId     = technicianId.toString(),
            requestedBy      = requestedBy.toString(),
            dataScope        = scope,
            status           = "PENDING_CONSENT",
            visitType        = visitType,
            expiresAt        = expiresAt,
            consentGrantedAt = null,
            createdAt        = now,
            token            = rawToken,
        )
    }

    /**
     * Marks a PENDING_CONSENT session as ACTIVE after the store operator grants consent.
     */
    suspend fun activateSession(sessionId: UUID, consentGrantedAt: Long): DiagnosticSessionResponse? =
        newSuspendedTransaction {
            val row = DiagnosticSessions.selectAll()
                .where { (DiagnosticSessions.id eq sessionId) and (DiagnosticSessions.status eq "PENDING_CONSENT") }
                .firstOrNull() ?: return@newSuspendedTransaction null

            val now = java.time.Instant.now().toEpochMilli()
            if (row[DiagnosticSessions.expiresAt] < now) {
                DiagnosticSessions.update({ DiagnosticSessions.id eq sessionId }) {
                    it[status] = "EXPIRED"
                }
                return@newSuspendedTransaction null
            }

            DiagnosticSessions.update({ DiagnosticSessions.id eq sessionId }) {
                it[status]           = "ACTIVE"
                it[DiagnosticSessions.consentGrantedAt] = consentGrantedAt
            }
            row.toResponse()
        }

    /**
     * Revokes an active or pending session (admin or store operator triggered).
     */
    suspend fun revokeSession(sessionId: UUID, revokedBy: UUID): Boolean = newSuspendedTransaction {
        val count = DiagnosticSessions.update({
            (DiagnosticSessions.id eq sessionId) and
            (DiagnosticSessions.status inList listOf("PENDING_CONSENT", "ACTIVE"))
        }) {
            it[status]    = "REVOKED"
            it[revokedAt] = java.time.Instant.now().toEpochMilli()
            it[DiagnosticSessions.revokedBy] = revokedBy
        }
        count > 0
    }

    /**
     * Creates a site visit token for an ACTIVE session.
     *
     * The site visit token is an HMAC-SHA256 token scoped to specific hardware components
     * (e.g., "PRINTER,SCANNER,CASH_DRAWER"). The technician must present this token
     * physically at the customer site (via NFC or QR code) before hardware-level access
     * is granted.
     *
     * Requirements:
     * - The session must be ACTIVE (store operator consent already granted)
     * - The session must be within its 15-minute TTL
     *
     * @param sessionId     UUID of the ACTIVE diagnostic session.
     * @param hardwareScope Comma-separated hardware component identifiers to scope the token.
     * @return Updated [DiagnosticSessionResponse] with [DiagnosticSessionResponse.siteVisitToken]
     *   populated (raw token — returned only once, stored as hash), or null if the session
     *   is not ACTIVE / not found / expired.
     */
    suspend fun createSiteVisitToken(
        sessionId: UUID,
        hardwareScope: String,
    ): DiagnosticSessionResponse? = newSuspendedTransaction {
        val now = java.time.Instant.now().toEpochMilli()

        val row = DiagnosticSessions.selectAll()
            .where {
                (DiagnosticSessions.id eq sessionId) and
                (DiagnosticSessions.status eq "ACTIVE") and
                (DiagnosticSessions.expiresAt greaterEq now)
            }
            .firstOrNull() ?: return@newSuspendedTransaction null

        // Generate a cryptographically random site visit token
        val rawSiteToken = generateSiteVisitToken(
            sessionId    = sessionId,
            hardwareScope = hardwareScope,
        )
        val tokenHash = sha256Hex(rawSiteToken)

        DiagnosticSessions.update({ DiagnosticSessions.id eq sessionId }) {
            it[DiagnosticSessions.visitType]          = "ON_SITE"
            it[DiagnosticSessions.siteVisitTokenHash] = tokenHash
            it[DiagnosticSessions.hardwareScope]      = hardwareScope
        }

        // Return the raw token once — it won't be retrievable again
        row.toResponse().copy(
            visitType      = "ON_SITE",
            siteVisitToken = rawSiteToken,
            hardwareScope  = hardwareScope,
        )
    }

    /**
     * Validates a site visit token presented at the customer device.
     *
     * Compares the SHA-256 hash of the presented [rawToken] against the stored hash.
     * If valid, records [siteVisitPresentedAt] and returns `true`.
     *
     * @param sessionId UUID of the session whose token is being validated.
     * @param rawToken  The raw token presented by the technician (via NFC/QR).
     * @return `true` if the token is valid and not yet consumed; `false` otherwise.
     */
    suspend fun validateSiteVisitToken(sessionId: UUID, rawToken: String): Boolean =
        newSuspendedTransaction {
            val now = java.time.Instant.now().toEpochMilli()
            val row = DiagnosticSessions.selectAll()
                .where {
                    (DiagnosticSessions.id eq sessionId) and
                    (DiagnosticSessions.status eq "ACTIVE") and
                    (DiagnosticSessions.visitType eq "ON_SITE") and
                    (DiagnosticSessions.expiresAt greaterEq now)
                }
                .firstOrNull() ?: return@newSuspendedTransaction false

            // Reject if token was already presented (single-use)
            if (row[DiagnosticSessions.siteVisitPresentedAt] != null) {
                log.warn("Site visit token for session $sessionId already consumed — rejecting re-use")
                return@newSuspendedTransaction false
            }

            val storedHash    = row[DiagnosticSessions.siteVisitTokenHash] ?: return@newSuspendedTransaction false
            val presentedHash = sha256Hex(rawToken)

            if (!MessageDigest.isEqual(storedHash.toByteArray(), presentedHash.toByteArray())) {
                log.warn("Site visit token hash mismatch for session $sessionId")
                return@newSuspendedTransaction false
            }

            // Mark token as consumed (single-use enforcement)
            DiagnosticSessions.update({ DiagnosticSessions.id eq sessionId }) {
                it[siteVisitPresentedAt] = now
            }
            log.info("Site visit token validated for session $sessionId at $now")
            true
        }

    /**
     * Returns the current active or pending session for a store, or null if none.
     */
    suspend fun getActiveSession(storeId: UUID): DiagnosticSessionResponse? = newSuspendedTransaction {
        val now = java.time.Instant.now().toEpochMilli()
        // Expire any sessions past their TTL
        DiagnosticSessions.update({
            (DiagnosticSessions.storeId eq storeId) and
            (DiagnosticSessions.status inList listOf("PENDING_CONSENT", "ACTIVE")) and
            (DiagnosticSessions.expiresAt less now)
        }) {
            it[status] = "EXPIRED"
        }

        DiagnosticSessions.selectAll()
            .where {
                (DiagnosticSessions.storeId eq storeId) and
                (DiagnosticSessions.status inList listOf("PENDING_CONSENT", "ACTIVE"))
            }
            .firstOrNull()
            ?.toResponse()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun generateToken(
        sessionId: UUID,
        technicianId: UUID,
        storeId: UUID,
        scope: String,
        expiresAtMs: Long,
    ): String {
        val algorithm = Algorithm.RSA256(config.adminJwtPublicKey as RSAPublicKey, config.adminJwtPrivateKey as RSAPrivateKey)
        return JWT.create()
            .withIssuer(config.adminJwtIssuer)
            .withClaim("session_id",    sessionId.toString())
            .withClaim("technician_id", technicianId.toString())
            .withClaim("store_id",      storeId.toString())
            .withClaim("scope",         scope)
            .withClaim("exp",           expiresAtMs / 1000)
            .withClaim("iat",           java.time.Instant.now().toEpochMilli() / 1000)
            .sign(algorithm)
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * Generates a cryptographically random site visit token.
     *
     * Token format: HMAC-SHA256(secret, "svt|{sessionId}|{hardwareScope}|{random32bytes}")
     * encoded as hex — produces a 64-character string.
     *
     * The randomness from [SecureRandom] ensures that even two tokens for the same
     * session + hardware scope are distinct, preventing replay from captured tokens.
     */
    private fun generateSiteVisitToken(sessionId: UUID, hardwareScope: String): String {
        val random = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val randomHex = random.joinToString("") { "%02x".format(it) }
        val payload = "svt|$sessionId|$hardwareScope|$randomHex"
        val mac = javax.crypto.Mac.getInstance("HmacSHA256").apply {
            init(javax.crypto.spec.SecretKeySpec(config.adminJwtPrivateKey.encoded, "HmacSHA256"))
        }
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun ResultRow.toResponse() = DiagnosticSessionResponse(
        id                   = this[DiagnosticSessions.id].toString(),
        storeId              = this[DiagnosticSessions.storeId].toString(),
        technicianId         = this[DiagnosticSessions.technicianId].toString(),
        requestedBy          = this[DiagnosticSessions.requestedBy].toString(),
        dataScope            = this[DiagnosticSessions.dataScope],
        status               = this[DiagnosticSessions.status],
        visitType            = this[DiagnosticSessions.visitType],
        expiresAt            = this[DiagnosticSessions.expiresAt],
        consentGrantedAt     = this[DiagnosticSessions.consentGrantedAt],
        createdAt            = this[DiagnosticSessions.createdAt],
        token                = null,
        siteVisitToken       = null,    // raw token never returned after creation
        hardwareScope        = this[DiagnosticSessions.hardwareScope],
        siteVisitPresentedAt = this[DiagnosticSessions.siteVisitPresentedAt],
    )
}
