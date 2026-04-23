package com.zyntasolutions.zyntapos.security.crypto

import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger

/**
 * Minimal OS credential-manager wrapper used to protect the PKCS12 keystore
 * password on JVM desktop platforms.
 *
 * The implementation shells out to the platform's built-in command-line
 * secret tool rather than pulling in a JNI library, because:
 *   * the POS desktop build must run on stock macOS / GNOME / Windows hosts
 *     without extra dependencies;
 *   * the `security` binary (macOS) and `secret-tool` (libsecret on Linux)
 *     ship with the OS / typical desktop environments;
 *   * failure modes collapse cleanly to `null`, letting [DatabaseKeyManager]
 *     fall back to the existing machine-fingerprint derivation.
 *
 * Windows is intentionally unsupported here — the fingerprint fallback is
 * retained for that platform until a proper JNA-based Credential Manager
 * binding is added.
 *
 * @see DatabaseKeyManager for the caller that orchestrates store / retrieve.
 */
internal object OsKeyring {

    private const val TAG      = "OsKeyring"
    private const val SERVICE  = "com.zyntasolutions.zyntapos"
    private const val ACCOUNT  = "db_keystore_password"
    private const val LABEL    = "ZyntaPOS database keystore password"
    private const val CLI_TIMEOUT_MS = 5_000L

    /** Returns the stored secret for [SERVICE]/[ACCOUNT], or `null` if absent / unsupported. */
    fun retrieve(): String? = when {
        isMac()   -> runMacRetrieve()
        isLinux() -> runLinuxRetrieve()
        else      -> null
    }

    /** Stores [secret] for [SERVICE]/[ACCOUNT]; returns `true` on success. */
    fun store(secret: String): Boolean = when {
        isMac()   -> runMacStore(secret)
        isLinux() -> runLinuxStore(secret)
        else      -> false
    }

    // ── macOS: /usr/bin/security ─────────────────────────────────────────

    private fun runMacRetrieve(): String? = runCatching {
        val p = ProcessBuilder(
            "security", "find-generic-password",
            "-s", SERVICE, "-a", ACCOUNT, "-w",
        ).redirectErrorStream(false).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        if (!p.waitForWithTimeout()) return null
        if (p.exitValue() == 0 && out.isNotEmpty()) out else null
    }.onFailure { ZyntaLogger.d(TAG, "macOS keyring retrieve failed: ${it.message}") }
      .getOrNull()

    private fun runMacStore(secret: String): Boolean = runCatching {
        // -U updates existing entries in place
        val p = ProcessBuilder(
            "security", "add-generic-password", "-U",
            "-s", SERVICE, "-a", ACCOUNT,
            "-l", LABEL,
            "-w", secret,
        ).redirectErrorStream(true).start()
        if (!p.waitForWithTimeout()) return false
        p.exitValue() == 0
    }.onFailure { ZyntaLogger.d(TAG, "macOS keyring store failed: ${it.message}") }
      .getOrDefault(false)

    // ── Linux: secret-tool (libsecret-tools) ─────────────────────────────

    private fun runLinuxRetrieve(): String? = runCatching {
        val p = ProcessBuilder(
            "secret-tool", "lookup",
            "service", SERVICE,
            "account", ACCOUNT,
        ).redirectErrorStream(false).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        if (!p.waitForWithTimeout()) return null
        if (p.exitValue() == 0 && out.isNotEmpty()) out else null
    }.onFailure { ZyntaLogger.d(TAG, "Linux keyring retrieve failed: ${it.message}") }
      .getOrNull()

    private fun runLinuxStore(secret: String): Boolean = runCatching {
        val p = ProcessBuilder(
            "secret-tool", "store",
            "--label=$LABEL",
            "service", SERVICE,
            "account", ACCOUNT,
        ).redirectErrorStream(true).start()
        // secret-tool reads the secret from stdin
        p.outputStream.use { it.write(secret.toByteArray(Charsets.UTF_8)) }
        if (!p.waitForWithTimeout()) return false
        p.exitValue() == 0
    }.onFailure { ZyntaLogger.d(TAG, "Linux keyring store failed: ${it.message}") }
      .getOrDefault(false)

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun isMac(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("mac")

    private fun isLinux(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("linux")

    private fun Process.waitForWithTimeout(): Boolean =
        waitFor(CLI_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS).also { ok ->
            if (!ok) destroyForcibly()
        }
}
