package com.zyntasolutions.zyntapos.feature.multistore

import com.zyntasolutions.zyntapos.domain.usecase.multistore.ApproveStockTransferUseCase
import com.zyntasolutions.zyntapos.domain.usecase.multistore.CommitStockTransferUseCase
import com.zyntasolutions.zyntapos.domain.usecase.multistore.DispatchStockTransferUseCase
import com.zyntasolutions.zyntapos.domain.usecase.multistore.ReceiveStockTransferUseCase
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
 * - [CommitStockTransferUseCase]   — legacy atomic warehouse-level commit
 * - [ApproveStockTransferUseCase]  — manager approval: PENDING → APPROVED (C1.3)
 * - [DispatchStockTransferUseCase] — dispatch goods: APPROVED → IN_TRANSIT (C1.3)
 * - [ReceiveStockTransferUseCase]  — receive goods: IN_TRANSIT → RECEIVED (C1.3)
 * - [GetWarehouseRacksUseCase]     — reactive rack list for a warehouse (Sprint 18)
 * - [SaveWarehouseRackUseCase]     — insert or update a rack record (Sprint 18)
 * - [DeleteWarehouseRackUseCase]   — soft-delete a rack record (Sprint 18)
 *
 * ### Repository Dependencies (resolved from `:shared:data` DI graph)
 * - `WarehouseRepository`      — warehouse CRUD + stock transfer lifecycle
 * - `WarehouseRackRepository`  — rack CRUD (Sprint 18)
 * - `AuthRepository`           — session resolver for currentUserId / currentStoreId
 */
val multistoreModule = module {

    // ── Use Cases ─────────────────────────────────────────────────────────────
    factoryOf(::CommitStockTransferUseCase)

    // C1.3: IST multi-step workflow use cases
    factoryOf(::ApproveStockTransferUseCase)
    factoryOf(::DispatchStockTransferUseCase)
    factoryOf(::ReceiveStockTransferUseCase)

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
            approveTransferUseCase = get(),
            dispatchTransferUseCase = get(),
            receiveTransferUseCase = get(),
            getWarehouseRacksUseCase = get(),
            saveWarehouseRackUseCase = get(),
            deleteWarehouseRackUseCase = get(),
            authRepository = get(),
            analytics = get(),
        )
    }
}
