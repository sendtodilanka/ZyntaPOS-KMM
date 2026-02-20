package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * Concrete implementation of [SettingsRepository].
 *
 * ## Storage model
 * Settings are persisted in the `settings` SQLite table as TEXT key-value rows.
 * All writes are upserts (INSERT OR UPDATE) so callers need not check for prior existence.
 *
 * ## Reactivity
 * [observe] wraps SQLDelight's `asFlow()` extension on the `getSetting` query, giving
 * zero-overhead reactive observation without a secondary cache layer. Any [set] call
 * triggers SQLDelight's internal invalidation, which propagates through the Flow.
 *
 * ## Thread-safety
 * All suspend functions switch to [Dispatchers.IO]. The returned [Flow] from [observe]
 * is collected on [Dispatchers.IO] via `mapToOneOrNull(Dispatchers.IO)`.
 *
 * @param db Encrypted [ZyntaDatabase] singleton, provided by Koin.
 */
class SettingsRepositoryImpl(
    private val db: ZyntaDatabase,
) : SettingsRepository {

    private val q get() = db.settingsQueries

    // ── One-shot read ────────────────────────────────────────────────

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        q.getSetting(key).executeAsOneOrNull()
    }

    // ── Write (upsert) ───────────────────────────────────────────────

    override suspend fun set(key: String, value: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            q.upsertSetting(key = key, value_ = value, updated_at = now)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t ->
                Result.Error(
                    DatabaseException(
                        message   = "Failed to persist setting '$key': ${t.message}",
                        operation = "upsertSetting",
                        cause     = t,
                    )
                )
            },
        )
    }

    // ── Bulk read ────────────────────────────────────────────────────

    override suspend fun getAll(): Map<String, String> = withContext(Dispatchers.IO) {
        q.getAllSettings().executeAsList()
            .associate { row -> row.key to row.value_ }
    }

    // ── Reactive observation ─────────────────────────────────────────

    /**
     * Returns a [Flow] backed by SQLDelight's query-result tracking.
     * Emits `null` when [key] is absent; re-emits on every [set] for the same key.
     */
    override fun observe(key: String): Flow<String?> =
        q.getSetting(key)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)

    // ── Well-known setting keys ──────────────────────────────────────

    /**
     * Canonical setting key constants.
     * Use these throughout the codebase to avoid magic strings.
     */
    object Keys {
        // Store identity
        const val STORE_NAME              = "store.name"
        const val STORE_ADDRESS           = "store.address"
        const val STORE_PHONE             = "store.phone"
        const val STORE_EMAIL             = "store.email"
        const val STORE_CURRENCY_SYMBOL   = "store.currency_symbol"
        const val STORE_CURRENCY_CODE     = "store.currency_code"
        const val STORE_TAX_RATE          = "store.tax_rate"
        const val STORE_TAX_NUMBER        = "store.tax_number"

        // POS behaviour
        const val POS_AUTO_PRINT_RECEIPT  = "pos.auto_print_receipt"
        const val POS_REQUIRE_CUSTOMER    = "pos.require_customer"
        const val POS_MAX_DISCOUNT_PCT    = "pos.max_discount_percent"
        const val POS_ALLOW_NEGATIVE_STOCK= "pos.allow_negative_stock"
        const val POS_RECEIPT_FOOTER      = "pos.receipt_footer"
        const val POS_RECEIPT_HEADER      = "pos.receipt_header"

        // Printer
        const val PRINTER_CONNECTION_TYPE = "printer.connection_type"  // USB|BLUETOOTH|SERIAL|NETWORK
        const val PRINTER_PAPER_WIDTH_MM  = "printer.paper_width_mm"   // 58|80
        const val PRINTER_NETWORK_HOST    = "printer.network_host"
        const val PRINTER_NETWORK_PORT    = "printer.network_port"
        const val PRINTER_SERIAL_PORT     = "printer.serial_port"

        // Sync
        const val SYNC_INTERVAL_MS        = "sync.interval_ms"
        const val SYNC_LAST_PULL_AT       = "sync.last_pull_at"
        const val SYNC_BASE_URL           = "sync.base_url"

        // Security
        const val SECURITY_PIN_TIMEOUT_MS = "security.pin_timeout_ms"
        const val SECURITY_IDLE_LOCK_MS   = "security.idle_lock_ms"
    }
}
