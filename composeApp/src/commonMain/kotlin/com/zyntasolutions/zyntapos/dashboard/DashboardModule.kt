package com.zyntasolutions.zyntapos.dashboard

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for the home dashboard.
 *
 * Registered alongside feature modules in [ZyntaApplication] (Android)
 * and `main()` (Desktop).
 */
val dashboardModule = module {
    viewModel {
        DashboardViewModel(
            orderRepository = get(),
            productRepository = get(),
            registerRepository = get(),
            authRepository = get(),
        )
    }
}
