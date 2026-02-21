package com.zyntasolutions.zyntapos.security.auth

import org.mindrot.jbcrypt.BCrypt

/** Desktop (JVM) actual: delegates directly to jBCrypt. */
actual object PasswordHasher {
    actual fun hashPassword(plain: String): String = BCrypt.hashpw(plain, BCrypt.gensalt(12))
    actual fun verifyPassword(plain: String, hash: String): Boolean =
        runCatching { BCrypt.checkpw(plain, hash) }.getOrDefault(false)
}
