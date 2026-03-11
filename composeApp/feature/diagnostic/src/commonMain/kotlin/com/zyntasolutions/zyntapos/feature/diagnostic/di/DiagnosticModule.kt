package com.zyntasolutions.zyntapos.feature.diagnostic.di

import com.zyntasolutions.zyntapos.feature.diagnostic.DiagnosticViewModel
import com.zyntasolutions.zyntapos.security.auth.DiagnosticTokenValidator
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val diagnosticModule = module {
    single { DiagnosticTokenValidator() }
    viewModel { DiagnosticViewModel(get()) }
}
