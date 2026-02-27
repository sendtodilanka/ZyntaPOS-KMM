package com.zyntasolutions.zyntapos.domain.usecase.settings

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.LabelPrinterConfig
import com.zyntasolutions.zyntapos.domain.repository.LabelPrinterConfigRepository

/**
 * Retrieves the current [LabelPrinterConfig], returning `null` when no
 * configuration has been saved yet (first-run or reset state).
 *
 * @param repository Persistence contract for the singleton label-printer config.
 */
class GetLabelPrinterConfigUseCase(
    private val repository: LabelPrinterConfigRepository,
) {

    /**
     * Fetches the stored [LabelPrinterConfig].
     *
     * @return [Result.Success] with the config or `null` if not configured;
     *         [Result.Error] on storage read failure.
     */
    suspend operator fun invoke(): Result<LabelPrinterConfig?> = repository.get()
}
