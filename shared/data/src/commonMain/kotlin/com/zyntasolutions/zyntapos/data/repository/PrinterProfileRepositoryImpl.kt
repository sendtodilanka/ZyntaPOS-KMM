package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.db.Printer_profiles
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.PrinterJobType
import com.zyntasolutions.zyntapos.domain.model.PrinterProfile
import com.zyntasolutions.zyntapos.domain.repository.PrinterProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PrinterProfileRepositoryImpl(
    private val db: ZyntaDatabase,
) : PrinterProfileRepository {

    private val q get() = db.printer_profilesQueries

    override fun getAll(): Flow<List<PrinterProfile>> =
        q.getAllProfiles()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override suspend fun getById(id: String): Result<PrinterProfile> = withContext(Dispatchers.IO) {
        runCatching {
            q.getProfileById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("Printer profile not found: $id"))
        }.fold(
            onSuccess = { row -> Result.Success(toDomain(row)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Failed to load printer profile", cause = t)) },
        )
    }

    override suspend fun getDefault(jobType: PrinterJobType): Result<PrinterProfile?> =
        withContext(Dispatchers.IO) {
            runCatching {
                q.getDefaultProfile(jobType.name).executeAsOneOrNull()?.let(::toDomain)
            }.fold(
                onSuccess = { profile -> Result.Success(profile) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Failed to load default profile", cause = t)) },
            )
        }

    override suspend fun save(profile: PrinterProfile): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            db.transaction {
                // If this profile is the default, clear other defaults for the same job type first.
                if (profile.isDefault) {
                    q.clearDefaultsForJobType(profile.jobType.name, profile.id)
                }
                q.upsertProfile(
                    id                = profile.id,
                    name              = profile.name,
                    job_type          = profile.jobType.name,
                    printer_type      = profile.printerType,
                    tcp_host          = profile.tcpHost,
                    tcp_port          = profile.tcpPort.toLong(),
                    serial_port       = profile.serialPort,
                    baud_rate         = profile.baudRate.toLong(),
                    bt_address        = profile.btAddress,
                    paper_width_mm    = profile.paperWidthMm.toLong(),
                    is_default        = if (profile.isDefault) 1L else 0L,
                    backup_profile_id = profile.backupProfileId,
                    created_at        = profile.createdAt,
                    updated_at        = profile.updatedAt,
                )
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Failed to save printer profile", cause = t)) },
        )
    }

    override suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            q.deleteProfile(id)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Failed to delete printer profile", cause = t)) },
        )
    }

    private fun toDomain(row: Printer_profiles) = PrinterProfile(
        id              = row.id,
        name            = row.name,
        jobType         = PrinterJobType.valueOf(row.job_type),
        printerType     = row.printer_type,
        tcpHost         = row.tcp_host,
        tcpPort         = row.tcp_port.toInt(),
        serialPort      = row.serial_port,
        baudRate        = row.baud_rate.toInt(),
        btAddress       = row.bt_address,
        paperWidthMm    = row.paper_width_mm.toInt(),
        isDefault       = row.is_default == 1L,
        backupProfileId = row.backup_profile_id,
        createdAt       = row.created_at,
        updatedAt       = row.updated_at,
    )
}
