package com.zyntasolutions.zyntapos.debug.model

/**
 * Named seed profiles selectable from the Seeds tab.
 *
 * Each profile maps to a [com.zyntasolutions.zyntapos.seed.SeedDataSet].
 * Additional profiles (Retail, Restaurant) reuse [DefaultSeedDataSet] for Phase 1;
 * custom datasets can be added per-profile in subsequent sprints.
 */
enum class SeedProfile(val displayName: String, val description: String) {
    Demo(
        displayName = "Grocery Store (Demo)",
        description = "Sri Lankan neighborhood grocery: rice, dhal, spices, essentials — 25 products, 15 customers",
    ),
    Retail(
        displayName = "General Store (Retail)",
        description = "Sri Lankan semi-urban general store: clothing, hardware, agri, stationery — 25 products, 15 customers",
    ),
    Restaurant(
        displayName = "Restaurant / Kade",
        description = "Sri Lankan local restaurant: rice & curry, short eats, kottu, hoppers — 25 menu items, 15 customers",
    ),
}
