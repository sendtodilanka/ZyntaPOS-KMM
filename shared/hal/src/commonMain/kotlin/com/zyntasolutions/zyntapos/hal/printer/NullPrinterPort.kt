package com.zyntasolutions.zyntapos.hal.printer

import com.zyntasolutions.zyntapos.core.result.ZentaException

/**
 * ZyntaPOS — Hardware Abstraction Layer
 *
 * A no-operation [PrinterPort] used as the Phase 1 default binding when no
 * printer has been configured yet.
 *
 * All operations return a descriptive [ZentaException.HalException] so that
 * the UI can surface a "Printer not configured" message rather than crashing.
 *
 * ### When this is replaced
 * The `:feature:settings` module (Sprint 23) overrides this singleton via
 * `koin.loadModules(listOf(module { single<PrinterPort> { realPort } }), allowOverride = true)`
 * once the operator has selected and saved a printer connection in Settings.
 *
 * This stub is intentionally **not** a platform-specific class so it can be
 * placed in `commonMain` and reused across both Android and Desktop if needed.
 */
class NullPrinterPort : PrinterPort {

    override suspend fun connect(): Result<Unit> =
        Result.failure(notConfigured("connect"))

    override suspend fun disconnect(): Result<Unit> =
        Result.success(Unit) // disconnect is a no-op on an unconfigured port

    override suspend fun isConnected(): Boolean = false

    override suspend fun print(commands: ByteArray): Result<Unit> =
        Result.failure(notConfigured("print"))

    override suspend fun openCashDrawer(): Result<Unit> =
        Result.failure(notConfigured("openCashDrawer"))

    override suspend fun cutPaper(): Result<Unit> =
        Result.failure(notConfigured("cutPaper"))

    // ─── Helper ──────────────────────────────────────────────────────────────

    private fun notConfigured(op: String) = ZentaException.HalException(
        message = "Printer not configured — cannot execute '$op'. " +
                  "Go to Settings → Hardware → Printer to configure a connection.",
    )
}
