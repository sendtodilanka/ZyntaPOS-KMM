package com.zyntasolutions.zyntapos.core.logger

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity

/**
 * ZyntaPOS centralised logging facade wrapping [Kermit](https://github.com/touchlab/Kermit).
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
class ZentaLogger(val defaultTag: String = "ZyntaPOS") {

    // ── Verbose ───────────────────────────────────────────────────────────────

    /** Emits a [Severity.Verbose] log entry. */
    fun v(message: String, tag: String = defaultTag, throwable: Throwable? = null) {
        Logger.withTag(tag).v(message, throwable)
    }

    // ── Debug ─────────────────────────────────────────────────────────────────

    /** Emits a [Severity.Debug] log entry. */
    fun d(message: String, tag: String = defaultTag, throwable: Throwable? = null) {
        Logger.withTag(tag).d(message, throwable)
    }

    // ── Info ──────────────────────────────────────────────────────────────────

    /** Emits a [Severity.Info] log entry. */
    fun i(message: String, tag: String = defaultTag, throwable: Throwable? = null) {
        Logger.withTag(tag).i(message, throwable)
    }

    // ── Warn ──────────────────────────────────────────────────────────────────

    /** Emits a [Severity.Warn] log entry. */
    fun w(message: String, tag: String = defaultTag, throwable: Throwable? = null) {
        Logger.withTag(tag).w(message, throwable)
    }

    // ── Error ─────────────────────────────────────────────────────────────────

    /** Emits a [Severity.Error] log entry. */
    fun e(message: String, tag: String = defaultTag, throwable: Throwable? = null) {
        Logger.withTag(tag).e(message, throwable)
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

        // ── Static convenience methods (Android-style tag-first signature) ───────

        /** Static [Severity.Verbose] — `ZentaLogger.v(TAG, "msg")` style. */
        fun v(tag: String, message: String, throwable: Throwable? = null) =
            Logger.withTag(tag).v(message, throwable)

        /** Static [Severity.Debug] — `ZentaLogger.d(TAG, "msg")` style. */
        fun d(tag: String, message: String, throwable: Throwable? = null) =
            Logger.withTag(tag).d(message, throwable)

        /** Static [Severity.Info] — `ZentaLogger.i(TAG, "msg")` style. */
        fun i(tag: String, message: String, throwable: Throwable? = null) =
            Logger.withTag(tag).i(message, throwable)

        /** Static [Severity.Warn] — `ZentaLogger.w(TAG, "msg")` style. */
        fun w(tag: String, message: String, throwable: Throwable? = null) =
            Logger.withTag(tag).w(message, throwable)

        /** Static [Severity.Error] — `ZentaLogger.e(TAG, "msg")` style. */
        fun e(tag: String, message: String, throwable: Throwable? = null) =
            Logger.withTag(tag).e(message, throwable)
    }
}
