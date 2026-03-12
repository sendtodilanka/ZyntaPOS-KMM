package com.zyntasolutions.zyntapos.feature.inventory

import com.zyntasolutions.zyntapos.domain.usecase.inventory.AdjustStockUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.CompleteStocktakeUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.CreateProductUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.DeleteCategoryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.DeleteLabelTemplateUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.GetLabelTemplatesUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.ManageUnitGroupUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.SaveCategoryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.SaveLabelTemplateUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.SaveSupplierUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.SaveTaxGroupUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.ScanStocktakeItemUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.SearchProductsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.SeedDefaultLabelTemplatesUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.StartStocktakeUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.UpdateProductUseCase
import com.zyntasolutions.zyntapos.security.audit.SecurityAuditLogger
import com.zyntasolutions.zyntapos.feature.inventory.label.BarcodeLabelPrintViewModel
import com.zyntasolutions.zyntapos.feature.inventory.stocktake.StocktakeViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Koin DI module for the `:composeApp:feature:inventory` feature.
 *
 * Covers **Sprint 18** (Products) and **Sprint 19** (Categories, Suppliers,
 * Tax Groups, Units) use cases, with a single [InventoryViewModel] wired to
 * all use case and repository dependencies injected from the shared-domain graph.
 *
 * ### Use-Case Registrations (factory — new instance per injection)
 *
 * **Sprint 18 — Products**
 * - [SearchProductsUseCase]            — full-text + category filter
 * - [CreateProductUseCase]             — barcode/SKU uniqueness + field validation
 * - [UpdateProductUseCase]             — same rules as create; product must exist
 * - [AdjustStockUseCase]               — increase / decrease / transfer with audit entry
 *
 * **Sprint 19 — Categories, Suppliers, Tax Groups, Units**
 * - [SaveCategoryUseCase]              — insert/update with name + self-ref guard
 * - [DeleteCategoryUseCase]            — soft-delete with referential-integrity check
 * - [SaveSupplierUseCase]              — insert/update with name/email/phone validation
 * - [SaveTaxGroupUseCase]              — insert/update with name + rate-range validation
 * - [ManageUnitGroupUseCase]           — insert/update/delete units with conversion-rate check
 *
 * **Label Printing — Barcode Label PDF Engine**
 * - [GetLabelTemplatesUseCase]         — live Flow of all saved templates
 * - [SaveLabelTemplateUseCase]         — validated insert/update of label template
 * - [DeleteLabelTemplateUseCase]       — delete template by ID
 * - [SeedDefaultLabelTemplatesUseCase] — seeds factory defaults on first run
 *
 * ### Repository Dependencies (resolved from shared DI graph)
 * - `ProductRepository`          — registered in `:shared:data` module
 * - `CategoryRepository`         — registered in `:shared:data` module
 * - `StockRepository`            — registered in `:shared:data` module
 * - `SupplierRepository`         — registered in `:shared:data` module
 * - `TaxGroupRepository`         — registered in `:shared:data` module
 * - `UnitGroupRepository`        — registered in `:shared:data` module
 * - `LabelTemplateRepository`    — registered in `:shared:data` module
 *
 * ### Platform Bindings (loaded separately)
 * - JVM Desktop: `jvmInventoryLabelModule` (registers [JvmLabelPdfRenderer])
 * - Android:     `androidInventoryLabelModule` (registers [AndroidLabelPdfRenderer])
 */
val inventoryModule = module {

    // ── Sprint 18: Product use cases ──────────────────────────────────────────
    factoryOf(::SearchProductsUseCase)
    factoryOf(::CreateProductUseCase)
    factoryOf(::UpdateProductUseCase)
    factoryOf(::AdjustStockUseCase)

    // ── Sprint 19: Category use cases ─────────────────────────────────────────
    factoryOf(::SaveCategoryUseCase)
    factoryOf(::DeleteCategoryUseCase)

    // ── Sprint 19: Supplier use cases ─────────────────────────────────────────
    factoryOf(::SaveSupplierUseCase)

    // ── Sprint 19: Tax Group use cases ────────────────────────────────────────
    factoryOf(::SaveTaxGroupUseCase)

    // ── Sprint 19: Unit Group use cases ───────────────────────────────────────
    factoryOf(::ManageUnitGroupUseCase)

    // ── Stocktake use cases ───────────────────────────────────────────────────
    factoryOf(::StartStocktakeUseCase)
    factoryOf(::ScanStocktakeItemUseCase)
    factoryOf(::CompleteStocktakeUseCase)

    // ── Barcode Label Printing use cases ──────────────────────────────────────
    factoryOf(::GetLabelTemplatesUseCase)
    factoryOf(::SaveLabelTemplateUseCase)
    factoryOf(::DeleteLabelTemplateUseCase)
    factoryOf(::SeedDefaultLabelTemplatesUseCase)

    // ── ViewModels ────────────────────────────────────────────────────────────
    viewModelOf(::StocktakeViewModel)

    viewModel {
        BarcodeLabelPrintViewModel(
            productRepository              = get(),
            getLabelTemplatesUseCase       = get(),
            saveLabelTemplateUseCase       = get(),
            deleteLabelTemplateUseCase     = get(),
            seedDefaultLabelTemplatesUseCase = get(),
            labelPdfRenderer               = get(),
        )
    }

    viewModel {
        InventoryViewModel(
            productRepository       = get(),
            categoryRepository      = get(),
            supplierRepository      = get(),
            taxGroupRepository      = get(),
            unitGroupRepository     = get(),
            _searchProductsUseCase  = get(),
            createProductUseCase    = get(),
            updateProductUseCase    = get(),
            adjustStockUseCase      = get(),
            authRepository          = get(),
            auditLogger             = get(),
        )
    }
}
