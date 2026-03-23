package com.zyntasolutions.zyntapos.domain.usecase.multistore

import com.zyntasolutions.zyntapos.domain.model.Store
import com.zyntasolutions.zyntapos.domain.repository.StoreRepository
import kotlinx.coroutines.flow.Flow

/**
 * Returns a reactive list of all active stores.
 *
 * Used by the multi-store dashboard to populate the store selector
 * and display per-store KPI cards.
 */
class GetAllStoresUseCase(
    private val storeRepository: StoreRepository,
) {
    operator fun invoke(): Flow<List<Store>> =
        storeRepository.getAllStores()
}
