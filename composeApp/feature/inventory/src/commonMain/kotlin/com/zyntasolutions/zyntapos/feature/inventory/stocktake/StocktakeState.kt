package com.zyntasolutions.zyntapos.feature.inventory.stocktake

import com.zyntasolutions.zyntapos.domain.model.StocktakeCount
import com.zyntasolutions.zyntapos.domain.model.StocktakeSession

/**
 * Immutable UI state for [StocktakeViewModel].
 *
 * @property session          The active stocktake session, or `null` if none has been started.
 * @property counts           Current list of product counts in this session.
 * @property isScanning       `true` while the HAL scanner is actively listening for scans.
 * @property lastScannedBarcode The raw barcode value from the most recent scan event (for UI feedback).
 * @property isStarting       `true` while the session start call is in flight.
 * @property isCompleting     `true` while the session completion call is in flight.
 * @property error            Non-null when an operation failed; cleared by [StocktakeIntent.DismissError].
 */
data class StocktakeState(
    val session: StocktakeSession? = null,
    val counts: List<StocktakeCount> = emptyList(),
    val isScanning: Boolean = false,
    val lastScannedBarcode: String? = null,
    val isStarting: Boolean = false,
    val isCompleting: Boolean = false,
    val error: String? = null,
) {
    val isInProgress: Boolean get() = session != null
    val hasVariances: Boolean get() = counts.any { (it.computedVariance ?: 0) != 0 }
}
