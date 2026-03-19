package com.zyntasolutions.zyntapos.feature.multistore

import com.zyntasolutions.zyntapos.domain.usecase.multistore.CommitStockTransferUseCase
import com.zyntasolutions.zyntapos.domain.usecase.multistore.GetLowStockByWarehouseUseCase
import com.zyntasolutions.zyntapos.domain.usecase.multistore.GetWarehouseStockUseCase
import com.zyntasolutions.zyntapos.domain.usecase.multistore.SetWarehouseStockUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rack.DeleteRackProductUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rack.DeleteWarehouseRackUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rack.GetRackProductsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rack.GetWarehouseRacksUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rack.SaveRackProductUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rack.SaveWarehouseRackUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin DI module for `:composeApp:feature:multistore`.
 *
 * ### Use Cases (factory — new instance per injection)
 * - [CommitStockTransferUseCase]      — validates and commits a pending transfer
 * - [GetWarehouseRacksUseCase]        — reactive rack list for a warehouse (Sprint 18)
 * - [SaveWarehouseRackUseCase]        — insert or update a rack record (Sprint 18)
 * - [DeleteWarehouseRackUseCase]      — soft-delete a rack record (Sprint 18)
 * - [GetWarehouseStockUseCase]        — live per-warehouse stock list (C1.2)
 * - [SetWarehouseStockUseCase]        — set absolute stock quantity (C1.2)
 * - [GetLowStockByWarehouseUseCase]   — low-stock alerts per warehouse (C1.2)
 *
 * ### Repository Dependencies (resolved from `:shared:data` DI graph)
 * - `WarehouseRepository`      — warehouse CRUD + stock transfer lifecycle
 * - `WarehouseRackRepository`  — rack CRUD (Sprint 18)
 * - `WarehouseStockRepository` — per-warehouse stock levels (C1.2)
 * - `AuthRepository`           — session resolver for currentUserId / currentStoreId
 */
val multistoreModule = module {

    // ── Use Cases ─────────────────────────────────────────────────────────────
    factoryOf(::CommitStockTransferUseCase)

    // Sprint 18: Warehouse rack use cases
    factoryOf(::GetWarehouseRacksUseCase)
    factoryOf(::SaveWarehouseRackUseCase)
    factoryOf(::DeleteWarehouseRackUseCase)

    // C1.2: Per-warehouse stock level use cases
    factoryOf(::GetWarehouseStockUseCase)
    factoryOf(::SetWarehouseStockUseCase)
    factoryOf(::GetLowStockByWarehouseUseCase)

    // C1.2: Rack-product bin location use cases
    factoryOf(::GetRackProductsUseCase)
    factoryOf(::SaveRackProductUseCase)
    factoryOf(::DeleteRackProductUseCase)

    // ── ViewModel ─────────────────────────────────────────────────────────────
    viewModel {
        WarehouseViewModel(
            warehouseRepository = get(),
            productRepository = get(),
            commitTransferUseCase = get(),
            getWarehouseRacksUseCase = get(),
            saveWarehouseRackUseCase = get(),
            deleteWarehouseRackUseCase = get(),
            getWarehouseStockUseCase = get(),
            setWarehouseStockUseCase = get(),
            getLowStockByWarehouseUseCase = get(),
            getRackProductsUseCase = get(),
            saveRackProductUseCase = get(),
            deleteRackProductUseCase = get(),
            authRepository = get(),
            analytics = get(),
        )
    }
}
