package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.PrinterJobType
import com.zyntasolutions.zyntapos.domain.model.PrinterProfile
import kotlinx.coroutines.flow.Flow

/**
 * Persistence contract for [PrinterProfile] records.
 *
 * Implementations live in `:shared:data`.
 */
interface PrinterProfileRepository {

    /**
     * Streams all printer profiles, ordered by creation time.
     * Emits on every change.
     */
    fun getAll(): Flow<List<PrinterProfile>>

    /**
     * Retrieves a single profile by its UUID.
     *
     * @return [Result.Success] with the profile, or [Result.Error] if not found.
     */
    suspend fun getById(id: String): Result<PrinterProfile>

    /**
     * Returns the default profile for the given [jobType], or `null` if none is set.
     */
    suspend fun getDefault(jobType: PrinterJobType): Result<PrinterProfile?>

    /**
     * Creates or updates a [PrinterProfile].
     *
     * If [profile.isDefault] is `true`, all other profiles of the same [jobType]
     * must have their `isDefault` cleared (enforced by the implementation).
     */
    suspend fun save(profile: PrinterProfile): Result<Unit>

    /**
     * Permanently deletes the profile with the given [id].
     */
    suspend fun delete(id: String): Result<Unit>
}
