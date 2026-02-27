package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.LabelPrinterConfig

/**
 * Persistence contract for the singleton [LabelPrinterConfig] record.
 *
 * Only one label printer configuration exists per terminal/store.
 * Implementations live in `:shared:data`.
 */
interface LabelPrinterConfigRepository {

    /**
     * Retrieves the current label printer configuration.
     *
     * @return [Result.Success] with the config (or `null` if never saved),
     *         or [Result.Error] on database failure.
     */
    suspend fun get(): Result<LabelPrinterConfig?>

    /**
     * Persists the label printer configuration (upsert — creates if absent).
     */
    suspend fun save(config: LabelPrinterConfig): Result<Unit>
}
