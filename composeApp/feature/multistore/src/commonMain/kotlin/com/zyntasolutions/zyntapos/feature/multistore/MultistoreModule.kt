package com.zyntasolutions.zyntapos.feature.multistore

import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.usecase.multistore.CommitStockTransferUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin DI module for `:composeApp:feature:multistore`.
 *
 * ### Use Cases (factory — new instance per injection)
 * - [CommitStockTransferUseCase] — validates and commits a pending transfer
 *
 * ### Repository Dependencies (resolved from `:shared:data` DI graph)
 * - `WarehouseRepository` — warehouse CRUD + stock transfer lifecycle
 * - `AuthRepository`      — session resolver for currentUserId / currentStoreId
 */
val multistoreModule = module {
    // ── Use Cases ─────────────────────────────────────────────────────────────
    factoryOf(::CommitStockTransferUseCase)

    // ── ViewModel ─────────────────────────────────────────────────────────────
    // storeId and userId resolved from the active session at creation time.
    viewModel {
        val session = runBlocking { get<AuthRepository>().getSession().first() }
        WarehouseViewModel(
            warehouseRepository = get(),
            commitTransferUseCase = get(),
            currentStoreId = session?.storeId ?: "default",
            currentUserId = session?.id ?: "unknown",
        )
    }
}
