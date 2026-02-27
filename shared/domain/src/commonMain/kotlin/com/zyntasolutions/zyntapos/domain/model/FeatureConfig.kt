package com.zyntasolutions.zyntapos.domain.model

/**
 * Runtime configuration snapshot for a single [ZyntaFeature].
 *
 * Stored in the local database and optionally refreshed from the cloud licensing
 * service during sync. All timestamps are epoch milliseconds (UTC).
 *
 * @param feature      The feature this config row describes.
 * @param isEnabled    Whether the feature is currently active for this installation.
 * @param activatedAt  Epoch millis when the feature was first enabled, or null if never activated.
 * @param expiresAt    Epoch millis when the feature licence expires, or null for perpetual access.
 * @param updatedAt    Epoch millis of the most recent local write to this row.
 */
data class FeatureConfig(
    val feature: ZyntaFeature,
    val isEnabled: Boolean,
    val activatedAt: Long?,   // epoch millis when activated, null if never
    val expiresAt: Long?,     // null = perpetual
    val updatedAt: Long,      // epoch millis of last update
)
