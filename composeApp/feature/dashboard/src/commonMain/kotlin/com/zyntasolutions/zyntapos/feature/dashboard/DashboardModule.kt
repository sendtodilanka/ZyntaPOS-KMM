package com.zyntasolutions.zyntapos.feature.dashboard

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for the home dashboard feature.
 *
 * Registered alongside other feature modules in [ZyntaApplication] (Android)
 * and `main()` (Desktop).
 */
val dashboardModule = module {
    viewModel {
        DashboardViewModel(
            orderRepository = get(),
            productRepository = get(),
            registerRepository = get(),
            authRepository = get(),
            storeRepository = get(),
            analytics = get(),
        )
    }
}
