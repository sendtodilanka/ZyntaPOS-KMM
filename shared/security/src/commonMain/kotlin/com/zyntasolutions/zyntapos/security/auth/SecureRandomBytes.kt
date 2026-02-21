package com.zyntasolutions.zyntapos.security.auth

/**
 * KMP-safe cryptographically-secure random byte generator (expect/actual).
 *
 * Delegates to [java.security.SecureRandom] on both Android and Desktop JVM,
 * keeping [PinManager] free of platform imports in commonMain.
 *
 * @param size Number of random bytes to generate.
 * @return A fresh [ByteArray] filled with cryptographically-random data.
 */
internal expect fun secureRandomBytes(size: Int): ByteArray
