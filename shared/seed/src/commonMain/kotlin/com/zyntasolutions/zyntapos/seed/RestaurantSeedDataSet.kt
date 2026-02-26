package com.zyntasolutions.zyntapos.seed

import kotlinx.serialization.json.Json

/**
 * Restaurant seed dataset — **Sri Lankan Local Restaurant / Kade** (Restaurant profile).
 *
 * Loaded from `seeds/restaurant.json` at runtime via the classpath resource loader.
 * The JSON fixture contains:
 * - 12 menu categories (rice & curry, short eats, kottu, hoppers, desserts, beverages, etc.)
 * - 8 food suppliers / wholesalers
 * - 55+ menu items (made-to-order dishes use stockQty = 999; pre-prepared items have real quantities)
 * - 25 regular customers
 *
 * All IDs use the `seed-res-*` prefix to avoid collision with other datasets.
 * [SeedRunner] is idempotent — records are skipped if the ID already exists.
 */
object RestaurantSeedDataSet {

    private val json = Json { ignoreUnknownKeys = true }

    fun build(): SeedDataSet = json.decodeFromString(loadSeedJson("seeds/restaurant.json"))
}
