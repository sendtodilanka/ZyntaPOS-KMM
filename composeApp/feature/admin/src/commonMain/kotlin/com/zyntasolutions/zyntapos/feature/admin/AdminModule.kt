package com.zyntasolutions.zyntapos.feature.admin

import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.feature.admin.notification.NotificationViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin DI module for the :composeApp:feature:admin feature.
 *
 * Provides:
 * - [NotificationViewModel] — in-app notification inbox ViewModel.
 *
 * The `currentUserId` is resolved synchronously from the active session StateFlow
 * at DI construction time. This is safe because the auth session always has an
 * initial value after login completes (StateFlow guarantee).
 */
val adminModule = module {
    viewModel {
        val userId = runBlocking {
            get<AuthRepository>().getSession().first()?.id ?: "unknown"
        }
        NotificationViewModel(
            notificationRepository = get(),
            currentUserId = userId,
        )
    }
}
