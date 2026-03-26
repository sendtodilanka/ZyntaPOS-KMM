package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.domain.model.ReturnStockPolicy
import com.zyntasolutions.zyntapos.domain.model.Store

/**
 * Resolves the target store ID where returned stock should be added
 * during a cross-store refund (C4.2).
 *
 * The decision is driven by the processing store's [Store.returnStockPolicy]:
 * - [ReturnStockPolicy.RETURN_TO_CURRENT_STORE] → stock stays at the store
 *   handling the return (physical item is already on-site).
 * - [ReturnStockPolicy.RETURN_TO_ORIGINAL_STORE] → stock is routed back to
 *   the store where the original sale was made (may require an inter-store
 *   transfer for the physical item).
 *
 * For same-store returns the result is always [currentStore.id] regardless
 * of policy, since current and original are identical.
 */
class ResolveReturnStockDestinationUseCase {

    /**
     * @param currentStore The store that is processing the return.
     * @param originalStoreId The store where the original sale was made.
     * @return The store ID where the returned stock should be added.
     */
    operator fun invoke(
        currentStore: Store,
        originalStoreId: String,
    ): String {
        // Same-store return — policy is irrelevant.
        if (currentStore.id == originalStoreId) {
            return currentStore.id
        }

        return when (currentStore.returnStockPolicy) {
            ReturnStockPolicy.RETURN_TO_CURRENT_STORE -> currentStore.id
            ReturnStockPolicy.RETURN_TO_ORIGINAL_STORE -> originalStoreId
        }
    }
}
