package com.zyntasolutions.zyntapos.seed

import kotlinx.serialization.json.Json

/**
 * Default seed dataset — **Sri Lankan Neighborhood Grocery Store** (Demo profile).
 *
 * Loaded from `seeds/demo.json` at runtime via the classpath resource loader.
 * The JSON fixture contains:
 * - 12 product categories
 * - 8 suppliers (Sri Lankan wholesale companies)
 * - 55+ products (several intentionally low-stock for dashboard alert testing)
 * - 25 customers with Sri Lankan names and phone numbers (+94)
 *
 * All IDs use the `seed-*` prefix to distinguish from production records.
 * [SeedRunner] is idempotent — records are skipped if the ID already exists.
 */
object DefaultSeedDataSet {

    private val json = Json { ignoreUnknownKeys = true }

    fun build(): SeedDataSet = json.decodeFromString(loadSeedJson("seeds/demo.json"))
}
