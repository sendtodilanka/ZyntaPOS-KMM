package com.zyntasolutions.zyntapos.core.logger

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity

/**
 * ZentaPOS centralised logging facade wrapping [Kermit](https://github.com/touchlab/Kermit).
 *
 * ### Usage (inject via Koin `coreModule`)
 * ```kotlin
 * class ProductViewModel(private val log: ZentaLogger) {
 *     fun load() {
 *         log.d("Loading products", tag = "ProductViewModel")
 *     }
 * }
 * ```
 *
 * ### Log Levels
 * | Level | Kermit Severity | When to use |
 * |-------|-----------------|-------------|
 * | [v]   | Verbose         | Highly detailed trace output (dev only) |
 * | [d]   | Debug           | Normal development information |
 * | [i]   | Info            | Key lifecycle / business events |
 * | [w]   | Warn            | Non-fatal anomalies |
 * | [e]   | Error           | Failures that need attention |
 *
 * @param defaultTag Default log tag; typically the module name (e.g., "shared:core").
 */
class ZentaLogger(val defaultTag: String = "ZentaPOS") {

    // ── Verbose ───────────────────────────────────────────────────────────────

    /** Emits a [Severity.Verbose] log entry. */
    fun v(message: String, tag: String = defaultTag, throwable: Throwable? = null) {
        Logger.v(tag = tag, throwable = throwable) { message }
    }

    // ── Debug ─────────────────────────────────────────────────────────────────

    /** Emits a [Severity.Debug] log entry. */
    fun d(message: String, tag: String = defaultTag, throwable: Throwable? = null) {
        Logger.d(tag = tag, throwable = throwable) { message }
    }

    // ── Info ──────────────────────────────────────────────────────────────────

    /** Emits a [Severity.Info] log entry. */
    fun i(message: String, tag: String = defaultTag, throwable: Throwable? = null) {
        Logger.i(tag = tag, throwable = throwable) { message }
    }

    // ── Warn ──────────────────────────────────────────────────────────────────

    /** Emits a [Severity.Warn] log entry. */
    fun w(message: String, tag: String = defaultTag, throwable: Throwable? = null) {
        Logger.w(tag = tag, throwable = throwable) { message }
    }

    // ── Error ─────────────────────────────────────────────────────────────────

    /** Emits a [Severity.Error] log entry. */
    fun e(message: String, tag: String = defaultTag, throwable: Throwable? = null) {
        Logger.e(tag = tag, throwable = throwable) { message }
    }

    // ── Convenience factory for module-tagged child loggers ───────────────────

    /**
     * Returns a new [ZentaLogger] whose [defaultTag] is set to [moduleName].
     *
     * ```kotlin
     * private val log = ZentaLogger.forModule("ProductRepository")
     * ```
     */
    fun withTag(moduleName: String): ZentaLogger = ZentaLogger(defaultTag = moduleName)

    companion object {
        /** Convenience factory. Equivalent to `ZentaLogger(moduleName)`. */
        fun forModule(moduleName: String): ZentaLogger = ZentaLogger(defaultTag = moduleName)
    }
}
