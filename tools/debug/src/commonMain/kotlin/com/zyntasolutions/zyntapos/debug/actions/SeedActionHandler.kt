package com.zyntasolutions.zyntapos.debug.actions

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.debug.model.SeedProfile
import com.zyntasolutions.zyntapos.seed.DefaultSeedDataSet
import com.zyntasolutions.zyntapos.seed.RestaurantSeedDataSet
import com.zyntasolutions.zyntapos.seed.RetailSeedDataSet
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
 * Each [SeedProfile] maps to a distinct Sri Lanka–localised dataset:
 * - [SeedProfile.Demo]       → [DefaultSeedDataSet]   (neighborhood grocery store)
 * - [SeedProfile.Retail]     → [RetailSeedDataSet]    (semi-urban general store)
 * - [SeedProfile.Restaurant] → [RestaurantSeedDataSet] (local restaurant / kade)
 *
 * [SeedRunner.run] is idempotent — records with existing IDs are skipped, so
 * calling the same profile multiple times is safe.
 */
class SeedActionHandlerImpl(
    private val seedRunner: SeedRunner,
) : SeedActionHandler {

    override suspend fun runProfile(profile: SeedProfile): Result<SeedRunner.SeedSummary> {
        return try {
            val dataSet = when (profile) {
                SeedProfile.Demo       -> DefaultSeedDataSet.build()
                SeedProfile.Retail     -> RetailSeedDataSet.build()
                SeedProfile.Restaurant -> RestaurantSeedDataSet.build()
            }
            val summary = seedRunner.run(dataSet)
            Result.Success(summary)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Seed run failed for profile ${profile.name}: ${e.message}"))
        }
    }
}
