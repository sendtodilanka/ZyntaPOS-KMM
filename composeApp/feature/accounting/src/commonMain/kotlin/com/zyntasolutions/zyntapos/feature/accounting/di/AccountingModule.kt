package com.zyntasolutions.zyntapos.feature.accounting.di

import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.usecase.einvoice.CancelEInvoiceUseCase
import com.zyntasolutions.zyntapos.domain.usecase.einvoice.CreateEInvoiceUseCase
import com.zyntasolutions.zyntapos.domain.usecase.einvoice.GetEInvoiceByOrderUseCase
import com.zyntasolutions.zyntapos.domain.usecase.einvoice.GetEInvoicesUseCase
import com.zyntasolutions.zyntapos.domain.usecase.einvoice.SubmitEInvoiceToIrdUseCase
import com.zyntasolutions.zyntapos.feature.accounting.EInvoiceViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin DI module for `:composeApp:feature:accounting`.
 *
 * ### Use Cases (factory — new instance per injection)
 * - [GetEInvoicesUseCase]        — reactive e-invoice list for a store
 * - [GetEInvoiceByOrderUseCase]  — look up invoice by originating order
 * - [CreateEInvoiceUseCase]      — create a DRAFT e-invoice
 * - [SubmitEInvoiceToIrdUseCase] — submit DRAFT → IRD API
 * - [CancelEInvoiceUseCase]      — cancel DRAFT or SUBMITTED invoice
 *
 * ### Repository Dependencies (resolved from `:shared:data` DI graph)
 * - `EInvoiceRepository` — all e-invoice persistence operations
 * - `AuthRepository`     — session resolver for currentStoreId
 */
val accountingModule = module {

    // ── Use Cases ─────────────────────────────────────────────────────────────
    factoryOf(::GetEInvoicesUseCase)
    factoryOf(::GetEInvoiceByOrderUseCase)
    factoryOf(::CreateEInvoiceUseCase)
    factoryOf(::SubmitEInvoiceToIrdUseCase)
    factoryOf(::CancelEInvoiceUseCase)

    // ── ViewModel ─────────────────────────────────────────────────────────────
    // storeId resolved from the active session at creation time.
    viewModel {
        val session = runBlocking { get<AuthRepository>().getSession().first() }
        EInvoiceViewModel(
            getEInvoicesUseCase = get(),
            submitEInvoiceToIrdUseCase = get(),
            cancelEInvoiceUseCase = get(),
            currentStoreId = session?.storeId ?: "default",
        )
    }
}
