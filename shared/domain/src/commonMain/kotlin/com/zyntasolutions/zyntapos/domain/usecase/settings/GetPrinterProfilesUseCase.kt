package com.zyntasolutions.zyntapos.domain.usecase.settings

import com.zyntasolutions.zyntapos.domain.model.PrinterProfile
import com.zyntasolutions.zyntapos.domain.model.PrinterJobType
import com.zyntasolutions.zyntapos.domain.repository.PrinterProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Emits the list of all configured [PrinterProfile] entries, re-emitting on changes.
 *
 * An optional [jobType] filter limits the results to a specific print-job category,
 * e.g., only RECEIPT profiles or only KITCHEN profiles.
 *
 * @param repository Persistence contract for printer profiles.
 */
class GetPrinterProfilesUseCase(
    private val repository: PrinterProfileRepository,
) {

    /**
     * Returns a [Flow] of all profiles, optionally filtered by [jobType].
     *
     * @param jobType When non-null, only profiles of this [PrinterJobType] are emitted.
     */
    operator fun invoke(jobType: PrinterJobType? = null): Flow<List<PrinterProfile>> {
        val all = repository.getAll()
        return if (jobType == null) {
            all
        } else {
            all.map { list -> list.filter { it.jobType == jobType } }
        }
    }
}
