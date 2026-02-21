package com.zyntasolutions.zyntapos.security.auth

/**
 * Platform-specific SHA-256 digest helper (expect/actual).
 *
 * Provides a single low-level function so [PinManager] can remain in commonMain
 * while delegating the `MessageDigest` call to JVM platform code.
 * Both Android and Desktop actuals use `java.security.MessageDigest`.
 */
internal expect fun sha256(input: ByteArray): ByteArray
