package com.zyntasolutions.zyntapos.debug.actions

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.debug.model.SeedProfile
import com.zyntasolutions.zyntapos.seed.DefaultSeedDataSet
import com.zyntasolutions.zyntapos.seed.SeedRunner

/**
 * Abstracts seed-data operations for the Seeds tab.
 *
 * Implementations must remain debug-only; this interface must not be
 * included in any production Koin module.
 */
interface SeedActionHandler {
    /**
     * Seeds the database with the dataset that corresponds to [profile].
     * Operations are idempotent — existing records are skipped.
     */
    suspend fun runProfile(profile: SeedProfile): Result<SeedRunner.SeedSummary>
}

/**
 * Default implementation backed by the existing [SeedRunner] from `:shared:seed`.
 *
 * All three [SeedProfile] variants map to [DefaultSeedDataSet] in Phase 1.
 * Per-profile datasets can be introduced in later sprints without changing the interface.
 */
class SeedActionHandlerImpl(
    private val seedRunner: SeedRunner,
) : SeedActionHandler {

    override suspend fun runProfile(profile: SeedProfile): Result<SeedRunner.SeedSummary> {
        return try {
            val dataSet = DefaultSeedDataSet.build()
            val summary = seedRunner.run(dataSet)
            Result.Success(summary)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Seed run failed: ${e.message}"))
        }
    }
}
