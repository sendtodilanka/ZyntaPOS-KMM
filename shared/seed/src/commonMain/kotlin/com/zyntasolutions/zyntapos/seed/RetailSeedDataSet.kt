package com.zyntasolutions.zyntapos.seed

import kotlinx.serialization.json.Json

/**
 * Retail seed dataset — **Sri Lankan Semi-urban General Store** (Retail profile).
 *
 * Loaded from `seeds/retail.json` at runtime via the classpath resource loader.
 * The JSON fixture contains:
 * - 12 product categories (clothing, hardware, agri, stationery, electronics, etc.)
 * - 8 Sri Lankan suppliers/distributors
 * - 55+ products (several low-stock items for alert testing)
 * - 25 customers with Sri Lankan names
 *
 * All IDs use the `seed-ret-*` prefix to avoid collision with the Demo dataset.
 * [SeedRunner] is idempotent — records are skipped if the ID already exists.
 */
object RetailSeedDataSet {

    private val json = Json { ignoreUnknownKeys = true }

    fun build(): SeedDataSet = json.decodeFromString(loadSeedJson("seeds/retail.json"))
}
