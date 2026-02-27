package com.zyntasolutions.zyntapos.hal.printer

import com.zyntasolutions.zyntapos.core.result.HalException

/**
 * ZyntaPOS — Hardware Abstraction Layer
 *
 * A no-operation [LabelPrinterPort] used as the default binding when no label
 * printer has been configured yet.
 *
 * All print operations return a descriptive [HalException] so the UI can surface
 * a "Label printer not configured" message rather than crashing.
 *
 * Replaced at runtime by the `:feature:settings` module once the operator has
 * selected and saved a label printer connection.
 */
class NullLabelPrinterPort : LabelPrinterPort {

    override suspend fun connect(): Result<Unit> =
        Result.failure(notConfigured("connect"))

    override suspend fun disconnect(): Result<Unit> =
        Result.success(Unit)

    override suspend fun isConnected(): Boolean = false

    override suspend fun printZpl(commands: ByteArray): Result<Unit> =
        Result.failure(notConfigured("printZpl"))

    override suspend fun printTspl(commands: ByteArray): Result<Unit> =
        Result.failure(notConfigured("printTspl"))

    private fun notConfigured(op: String) = HalException(
        message = "Label printer not configured — cannot execute '$op'. " +
                  "Go to Settings → Hardware → Label Printer to configure a connection.",
        device = "label_printer",
    )
}
