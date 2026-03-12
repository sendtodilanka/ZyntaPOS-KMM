package com.zyntasolutions.zyntapos.common

import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * S2-1: Centralized JWT defaults shared across all backend services.
 * Eliminates duplication of issuer/audience/PEM-parsing logic.
 */
object JwtDefaults {
    const val POS_ISSUER = "https://api.zyntapos.com"
    const val POS_AUDIENCE = "zyntapos-app"
    const val ADMIN_ISSUER = "https://panel.zyntapos.com"

    // Token TTL defaults
    const val POS_ACCESS_TOKEN_TTL_MINUTES = 60L
    const val POS_REFRESH_TOKEN_TTL_DAYS = 30L
    const val ADMIN_ACCESS_TOKEN_TTL_MINUTES = 15L
    const val ADMIN_REFRESH_TOKEN_TTL_DAYS = 7L

    /** Strips PEM header/footer lines and whitespace, returning raw Base64. */
    fun stripPemHeaders(pem: String): String =
        pem.replace("-----BEGIN.*?-----".toRegex(), "")
            .replace("-----END.*?-----".toRegex(), "")
            .replace("\\s".toRegex(), "")

    /** Reads a PEM key file from the path stored in the given environment variable. */
    fun readKeyFile(envVar: String): String? {
        val path = System.getenv(envVar) ?: return null
        return try { java.io.File(path).readText() } catch (_: Exception) { null }
    }

    /** Reads a secret from a Docker/K8s secret file path stored in the given env var. */
    fun readSecret(envVar: String): String? {
        val path = System.getenv(envVar) ?: return null
        return try { java.io.File(path).readText().trim() } catch (_: Exception) { null }
    }

    /** Parses an RSA public key from PEM format (raw or with headers). */
    fun parseRsaPublicKey(pem: String): PublicKey {
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(
            X509EncodedKeySpec(Base64.getDecoder().decode(stripPemHeaders(pem)))
        )
    }
}
