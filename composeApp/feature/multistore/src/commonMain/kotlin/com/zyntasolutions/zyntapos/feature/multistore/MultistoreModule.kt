package com.zyntasolutions.zyntapos.feature.multistore

import com.zyntasolutions.zyntapos.domain.usecase.multistore.CommitStockTransferUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rack.DeleteWarehouseRackUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rack.GetWarehouseRacksUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rack.SaveWarehouseRackUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin DI module for `:composeApp:feature:multistore`.
 *
 * ### Use Cases (factory — new instance per injection)
 * - [CommitStockTransferUseCase] — validates and commits a pending transfer
 * - [GetWarehouseRacksUseCase]   — reactive rack list for a warehouse (Sprint 18)
 * - [SaveWarehouseRackUseCase]   — insert or update a rack record (Sprint 18)
 * - [DeleteWarehouseRackUseCase] — soft-delete a rack record (Sprint 18)
 *
 * ### Repository Dependencies (resolved from `:shared:data` DI graph)
 * - `WarehouseRepository`      — warehouse CRUD + stock transfer lifecycle
 * - `WarehouseRackRepository`  — rack CRUD (Sprint 18)
 * - `AuthRepository`           — session resolver for currentUserId / currentStoreId
 */
val multistoreModule = module {

    // ── Use Cases ─────────────────────────────────────────────────────────────
    factoryOf(::CommitStockTransferUseCase)

    // Sprint 18: Warehouse rack use cases
    factoryOf(::GetWarehouseRacksUseCase)
    factoryOf(::SaveWarehouseRackUseCase)
    factoryOf(::DeleteWarehouseRackUseCase)

    // ── ViewModel ─────────────────────────────────────────────────────────────
    viewModel {
        WarehouseViewModel(
            warehouseRepository = get(),
            productRepository = get(),
            commitTransferUseCase = get(),
            getWarehouseRacksUseCase = get(),
            saveWarehouseRackUseCase = get(),
            deleteWarehouseRackUseCase = get(),
            authRepository = get(),
        )
    }
}
