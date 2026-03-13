package com.zyntasolutions.zyntapos.api.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zyntasolutions.zyntapos.api.config.AppConfig
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
    val id                  = uuid("id")
    val storeId             = uuid("store_id")
    val technicianId        = uuid("technician_id")
    val requestedBy         = uuid("requested_by")
    val tokenHash           = varchar("token_hash", 64)
    val dataScope           = varchar("data_scope", 32)
    val status              = varchar("status", 32)
    val visitType           = varchar("visit_type", 16).default("REMOTE")
    val siteVisitTokenHash  = varchar("site_visit_token_hash", 64).nullable()
    val consentGrantedAt    = long("consent_granted_at").nullable()
    val expiresAt           = long("expires_at")
    val revokedAt           = long("revoked_at").nullable()
    val revokedBy           = uuid("revoked_by").nullable()
    val createdAt           = long("created_at")
    override val primaryKey = PrimaryKey(id)
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
     * Raw site visit token for ON_SITE sessions — only present when issued via
     * [DiagnosticSessionService.createSiteVisitToken], never stored or returned again.
     */
    val siteVisitToken: String? = null,
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

    /**
     * Issues a short-lived site-visit token for an existing ACTIVE or PENDING_CONSENT ON_SITE
     * diagnostic session. The token is a 32-byte cryptographically random hex string that the
     * technician presents (e.g., as a QR code) to authenticate at the store kiosk.
     *
     * Only the SHA-256 hash of the token is persisted — the raw token is returned once and
     * never stored or retrievable again.
     *
     * Returns null if the session does not exist, is not in an eligible state, or has expired.
     */
    suspend fun createSiteVisitToken(sessionId: UUID): DiagnosticSessionResponse? =
        newSuspendedTransaction {
            val now = java.time.Instant.now().toEpochMilli()

            val row = DiagnosticSessions.selectAll()
                .where {
                    (DiagnosticSessions.id eq sessionId) and
                    (DiagnosticSessions.status inList listOf("PENDING_CONSENT", "ACTIVE")) and
                    (DiagnosticSessions.visitType eq "ON_SITE")
                }
                .firstOrNull()

            if (row == null) {
                log.warn("createSiteVisitToken: session {} not found or not eligible", sessionId)
                return@newSuspendedTransaction null
            }

            if (row[DiagnosticSessions.expiresAt] < now) {
                DiagnosticSessions.update({ DiagnosticSessions.id eq sessionId }) {
                    it[status] = "EXPIRED"
                }
                log.warn("createSiteVisitToken: session {} has expired", sessionId)
                return@newSuspendedTransaction null
            }

            // Generate a cryptographically random 32-byte token and store its hash
            val rawBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val rawToken = rawBytes.joinToString("") { "%02x".format(it) }
            val tokenHash = sha256Hex(rawToken)

            DiagnosticSessions.update({ DiagnosticSessions.id eq sessionId }) {
                it[siteVisitTokenHash] = tokenHash
            }

            log.info("createSiteVisitToken: issued site visit token for session {}", sessionId)

            DiagnosticSessionResponse(
                id               = row[DiagnosticSessions.id].toString(),
                storeId          = row[DiagnosticSessions.storeId].toString(),
                technicianId     = row[DiagnosticSessions.technicianId].toString(),
                requestedBy      = row[DiagnosticSessions.requestedBy].toString(),
                dataScope        = row[DiagnosticSessions.dataScope],
                status           = row[DiagnosticSessions.status],
                visitType        = row[DiagnosticSessions.visitType],
                expiresAt        = row[DiagnosticSessions.expiresAt],
                consentGrantedAt = row[DiagnosticSessions.consentGrantedAt],
                createdAt        = row[DiagnosticSessions.createdAt],
                siteVisitToken   = rawToken,
            )
        }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun generateToken(
        sessionId: UUID,
        technicianId: UUID,
        storeId: UUID,
        scope: String,
        expiresAtMs: Long,
    ): String {
        val algorithm = Algorithm.HMAC256(config.adminJwtSecret)
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

    private fun ResultRow.toResponse() = DiagnosticSessionResponse(
        id               = this[DiagnosticSessions.id].toString(),
        storeId          = this[DiagnosticSessions.storeId].toString(),
        technicianId     = this[DiagnosticSessions.technicianId].toString(),
        requestedBy      = this[DiagnosticSessions.requestedBy].toString(),
        dataScope        = this[DiagnosticSessions.dataScope],
        status           = this[DiagnosticSessions.status],
        visitType        = this[DiagnosticSessions.visitType],
        expiresAt        = this[DiagnosticSessions.expiresAt],
        consentGrantedAt = this[DiagnosticSessions.consentGrantedAt],
        createdAt        = this[DiagnosticSessions.createdAt],
        token            = null,
        siteVisitToken   = null,
    )
}
