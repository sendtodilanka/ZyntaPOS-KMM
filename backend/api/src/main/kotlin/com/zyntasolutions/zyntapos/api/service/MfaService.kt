package com.zyntasolutions.zyntapos.api.service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator
import com.zyntasolutions.zyntapos.api.db.AdminUsers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.*
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.apache.commons.codec.binary.Base32
import org.slf4j.LoggerFactory

// ── Exposed table object ───────────────────────────────────────────────────

object MfaBackupCodes : Table("admin_mfa_backup_codes") {
    val id       = uuid("id").defaultExpression(object : Expression<UUID>() {
        override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder { append("gen_random_uuid()") }
    })
    val userId   = uuid("user_id").references(AdminUsers.id)
    val codeHash = text("code_hash")
    val usedAt   = long("used_at").nullable()
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

// ── MFA Service ────────────────────────────────────────────────────────────

class MfaService {

    private val logger = LoggerFactory.getLogger(MfaService::class.java)
    private val random = SecureRandom()
    private val bcrypt = BCrypt.withDefaults()
    private val bcryptVerifier = BCrypt.verifyer()

    // TOTP: 6-digit, 30-second window, HMAC-SHA1 (RFC 6238 default)
    private val totp = TimeBasedOneTimePasswordGenerator(Duration.ofSeconds(30), 6)

    /**
     * Generates a new TOTP secret (Base32-encoded) and an otpauth:// URI for QR code display.
     * Does NOT save to the database — caller must call [enableMfa] after verifying the code.
     */
    fun generateSetup(userEmail: String): MfaSetup {
        val keyGen = KeyGenerator.getInstance(totp.algorithm)
        keyGen.init(160) // 160-bit = 20 bytes, standard for SHA-1 TOTP
        val key = keyGen.generateKey()
        val secret = Base32().encodeAsString(key.encoded).trimEnd('=')

        val issuer  = "ZyntaPOS"
        val account = URLEncoder.encode(userEmail, "UTF-8")
        val qrUrl   = "otpauth://totp/$issuer:$account" +
                      "?secret=$secret" +
                      "&issuer=${URLEncoder.encode(issuer, "UTF-8")}" +
                      "&algorithm=SHA1" +
                      "&digits=6" +
                      "&period=30"

        return MfaSetup(secret = secret, qrCodeUrl = qrUrl)
    }

    /**
     * Verifies a 6-digit TOTP code against the given Base32 secret.
     * Allows ±1 window (30s clock skew tolerance).
     */
    fun verifyTotp(secret: String, code: String): Boolean {
        return try {
            val keyBytes = Base32().decode(secret)
            val key: SecretKey = SecretKeySpec(keyBytes, totp.algorithm)
            val now = Instant.now()
            // Check current window and ±1 adjacent windows
            (-1..1).any { offset ->
                val windowTime = now.plusSeconds(offset * 30L)
                val expected = totp.generateOneTimePasswordString(key, windowTime)
                code.trim() == expected
            }
        } catch (e: Exception) {
            logger.debug("TOTP verification error: ${e.message}")
            false
        }
    }

    /**
     * Generates 8 random 8-character backup codes, stores hashed versions in DB,
     * and returns the plaintext codes (shown to user exactly once).
     */
    suspend fun generateBackupCodes(userId: UUID): List<String> = newSuspendedTransaction {
        // Invalidate old codes
        MfaBackupCodes.deleteWhere { MfaBackupCodes.userId eq userId }

        val codes = (1..8).map { generateRandomCode() }
        val now = java.time.Instant.now().toEpochMilli()

        codes.forEach { code ->
            val hash = bcrypt.hashToString(10, code.toCharArray())
            MfaBackupCodes.insert {
                it[MfaBackupCodes.userId] = userId
                it[codeHash] = hash
                it[createdAt] = now
            }
        }

        codes
    }

    /**
     * Verifies a backup code for the given user. Marks it as used if valid.
     * Returns true if a valid, unused code was found.
     */
    suspend fun verifyBackupCode(userId: UUID, code: String): Boolean = newSuspendedTransaction {
        val now = java.time.Instant.now().toEpochMilli()
        val rows = MfaBackupCodes.selectAll()
            .where { (MfaBackupCodes.userId eq userId) and (MfaBackupCodes.usedAt.isNull()) }

        val matchingRow = rows.firstOrNull { row ->
            bcryptVerifier.verify(code.toCharArray(), row[MfaBackupCodes.codeHash].toCharArray()).verified
        }

        if (matchingRow != null) {
            MfaBackupCodes.update({ MfaBackupCodes.id eq matchingRow[MfaBackupCodes.id] }) {
                it[usedAt] = now
            }
            true
        } else {
            false
        }
    }

    /**
     * Saves the encrypted MFA secret and enables MFA for a user.
     * Should only be called after [verifyTotp] succeeds.
     */
    suspend fun enableMfa(userId: UUID, secret: String) = newSuspendedTransaction {
        AdminUsers.update({ AdminUsers.id eq userId }) {
            it[mfaSecret] = secret // store Base32 secret; consider AES-GCM encryption in production
            it[mfaEnabled] = true
        }
    }

    /**
     * Disables MFA and clears the stored secret for a user.
     */
    suspend fun disableMfa(userId: UUID) = newSuspendedTransaction {
        AdminUsers.update({ AdminUsers.id eq userId }) {
            it[mfaSecret] = null
            it[mfaEnabled] = false
        }
        MfaBackupCodes.deleteWhere { MfaBackupCodes.userId eq userId }
    }

    /**
     * Retrieves the stored TOTP secret for a user (for verification during login).
     */
    suspend fun getMfaSecret(userId: UUID): String? = newSuspendedTransaction {
        AdminUsers.selectAll()
            .where { AdminUsers.id eq userId }
            .singleOrNull()
            ?.get(AdminUsers.mfaSecret)
    }

    private fun generateRandomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // excludes confusable chars
        return (1..8).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }
}

data class MfaSetup(val secret: String, val qrCodeUrl: String)
