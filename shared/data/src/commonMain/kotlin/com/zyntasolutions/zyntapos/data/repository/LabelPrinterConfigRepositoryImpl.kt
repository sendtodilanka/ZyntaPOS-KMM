package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.LabelPrinterConfig
import com.zyntasolutions.zyntapos.domain.repository.LabelPrinterConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock

class LabelPrinterConfigRepositoryImpl(
    private val db: ZyntaDatabase,
) : LabelPrinterConfigRepository {

    private val q get() = db.label_printer_configQueries

    override suspend fun get(): Result<LabelPrinterConfig?> = withContext(Dispatchers.IO) {
        runCatching {
            q.getConfig().executeAsOneOrNull()?.let { row ->
                LabelPrinterConfig(
                    printerType    = row.printer_type,
                    tcpHost        = row.tcp_host,
                    tcpPort        = row.tcp_port.toInt(),
                    serialPort     = row.serial_port,
                    baudRate       = row.baud_rate.toInt(),
                    btAddress      = row.bt_address,
                    darknessLevel  = row.darkness_level.toInt(),
                    speedLevel     = row.speed_level.toInt(),
                )
            }
        }.fold(
            onSuccess = { config -> Result.Success(config) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Failed to load label printer config", cause = t)) },
        )
    }

    override suspend fun save(config: LabelPrinterConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            q.upsertConfig(
                printer_type   = config.printerType,
                tcp_host       = config.tcpHost,
                tcp_port       = config.tcpPort.toLong(),
                serial_port    = config.serialPort,
                baud_rate      = config.baudRate.toLong(),
                bt_address     = config.btAddress,
                darkness_level = config.darknessLevel.toLong(),
                speed_level    = config.speedLevel.toLong(),
                updated_at     = Clock.System.now().toEpochMilliseconds(),
            )
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Failed to save label printer config", cause = t)) },
        )
    }
}
