package com.zyntasolutions.zyntapos.feature.pos.printer

import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import com.zyntasolutions.zyntapos.core.result.HalException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.domain.model.AuditEventType
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.printer.ReceiptPrinterPort
import com.zyntasolutions.zyntapos.domain.repository.AuditRepository
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository
import com.zyntasolutions.zyntapos.hal.printer.CharacterSet
import com.zyntasolutions.zyntapos.hal.printer.EscPosReceiptBuilder
import com.zyntasolutions.zyntapos.hal.printer.PaperWidth
import com.zyntasolutions.zyntapos.hal.printer.PrinterConfig
import com.zyntasolutions.zyntapos.hal.printer.PrinterManager
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Infrastructure adapter that implements [ReceiptPrinterPort] using the HAL printer
 * pipeline and the domain [AuditRepository] for audit logging.
 *
 * This class is the **sole owner** of all printing infrastructure concerns:
 * 1. Resolves [PrinterConfig] from [SettingsRepository].
 * 2. Builds ESC/POS byte commands via [EscPosReceiptBuilder].
 * 3. Opens the printer transport via [PrinterManager.connect].
 * 4. Delivers the byte buffer via [PrinterManager.print].
 * 5. Appends an [AuditEntry] on success via [AuditRepository].
 *
 * ### Clean Architecture note (MERGED-G2.1)
 * Previously this adapter imported `SecurityAuditLogger` from `:shared:security`,
 * creating a feature→infrastructure boundary violation. Refactored to use the
 * domain-layer [AuditRepository] interface instead, eliminating the need for the
 * `:shared:security` dependency in `:composeApp:feature:pos`.
 *
 * @param settingsRepository  Key-value settings source for [PrinterConfig] assembly.
 * @param printerManager      HAL gateway for ESC/POS command delivery.
 * @param auditRepository     Domain audit trail for receipt print events.
 * @param deviceId            Hardware/installation ID for audit entries.
 */
@OptIn(ExperimentalUuidApi::class)
class PrinterManagerReceiptAdapter(
    private val settingsRepository: SettingsRepository,
    private val printerManager: PrinterManager,
    private val auditRepository: AuditRepository,
    private val deviceId: String,
) : ReceiptPrinterPort {

    override suspend fun print(order: Order, cashierId: String): Result<Unit> {
        // 1. Resolve printer configuration — fall back to DEFAULT on any error
        val config: PrinterConfig = runCatching { loadPrinterConfig() }
            .getOrDefault(PrinterConfig.DEFAULT)

        // 2. Build ESC/POS byte commands (pure — no I/O)
        val receiptBytes = EscPosReceiptBuilder(config).buildReceipt(order, config)

        // 3. Ensure printer transport is open
        val connectResult = printerManager.connect()
        if (connectResult.isFailure) {
            ZyntaLogger.e(TAG, "Printer connect failed: ${connectResult.exceptionOrNull()?.message}")
            return Result.Error(
                HalException(
                    "Printer connection failed: ${connectResult.exceptionOrNull()?.message}",
                    device = "printer",
                ),
                connectResult.exceptionOrNull(),
            )
        }

        // 4. Deliver to printer (PrinterManager retries internally up to MAX_RETRIES)
        val printResult = printerManager.print(receiptBytes)
        if (printResult.isFailure) {
            ZyntaLogger.e(TAG, "Receipt print failed: ${printResult.exceptionOrNull()?.message}")
            return Result.Error(
                HalException(
                    "Receipt print failed after retries: ${printResult.exceptionOrNull()?.message}",
                    device = "printer",
                ),
                printResult.exceptionOrNull(),
            )
        }

        // 5. Audit trail — fire-and-forget; must not block or throw
        runCatching {
            auditRepository.insert(
                AuditEntry(
                    id = Uuid.random().toString(),
                    eventType = AuditEventType.DATA_EXPORT,
                    userId = cashierId,
                    deviceId = deviceId,
                    payload = """{"action":"RECEIPT_PRINT","orderId":"${order.id}"}""",
                    success = true,
                    createdAt = Clock.System.now(),
                ),
            )
        }

        return Result.Success(Unit)
    }

    /**
     * Builds a [PrinterConfig] from persisted settings keys.
     */
    private suspend fun loadPrinterConfig(): PrinterConfig {
        val paperWidthKey = settingsRepository.get(SETTINGS_PAPER_WIDTH)
        val paperWidth = when (paperWidthKey) {
            "MM_58" -> PaperWidth.MM_58
            else    -> PaperWidth.MM_80
        }
        val showQr   = settingsRepository.get(SETTINGS_SHOW_QR_CODE)?.toBooleanStrictOrNull() ?: true
        val showLogo = settingsRepository.get(SETTINGS_SHOW_LOGO)?.toBooleanStrictOrNull() ?: false
        val headerLines = (1..5).mapNotNull { idx ->
            settingsRepository.get("$SETTINGS_HEADER_PREFIX$idx")?.takeIf { it.isNotBlank() }
        }
        val footerLines = (1..3).mapNotNull { idx ->
            settingsRepository.get("$SETTINGS_FOOTER_PREFIX$idx")?.takeIf { it.isNotBlank() }
        }
        return PrinterConfig(
            paperWidth   = paperWidth,
            showQrCode   = showQr,
            showLogo     = showLogo,
            headerLines  = headerLines,
            footerLines  = footerLines,
            characterSet = CharacterSet.PC437,
        )
    }

    companion object {
        private const val TAG = "PrinterManagerReceiptAdapter"

        const val SETTINGS_PAPER_WIDTH   = "printer.paper_width"
        const val SETTINGS_SHOW_QR_CODE  = "printer.show_qr_code"
        const val SETTINGS_SHOW_LOGO     = "printer.show_logo"
        const val SETTINGS_HEADER_PREFIX = "printer.header_line_"
        const val SETTINGS_FOOTER_PREFIX = "printer.footer_line_"
    }
}
