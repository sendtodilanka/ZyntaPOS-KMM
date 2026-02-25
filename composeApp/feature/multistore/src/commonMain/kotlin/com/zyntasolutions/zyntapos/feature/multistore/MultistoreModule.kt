package com.zyntasolutions.zyntapos.feature.multistore

import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.usecase.multistore.CommitStockTransferUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rack.DeleteWarehouseRackUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rack.GetWarehouseRacksUseCase
import com.zyntasolutions.zyntapos.domain.usecase.rack.SaveWarehouseRackUseCase
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
    // storeId and userId resolved from the active session at creation time.
    viewModel {
        val session = runBlocking { get<AuthRepository>().getSession().first() }
        WarehouseViewModel(
            warehouseRepository = get(),
            commitTransferUseCase = get(),
            getWarehouseRacksUseCase = get(),
            saveWarehouseRackUseCase = get(),
            deleteWarehouseRackUseCase = get(),
            currentStoreId = session?.storeId ?: "default",
            currentUserId = session?.id ?: "unknown",
        )
    }
}
