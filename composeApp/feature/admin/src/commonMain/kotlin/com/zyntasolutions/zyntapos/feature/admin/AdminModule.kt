package com.zyntasolutions.zyntapos.feature.admin

import com.zyntasolutions.zyntapos.domain.usecase.admin.CreateBackupUseCase
import com.zyntasolutions.zyntapos.domain.usecase.admin.DeleteBackupUseCase
import com.zyntasolutions.zyntapos.domain.usecase.admin.GetBackupsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.admin.GetDatabaseStatsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.admin.GetSystemHealthUseCase
import com.zyntasolutions.zyntapos.domain.usecase.admin.PurgeExpiredDataUseCase
import com.zyntasolutions.zyntapos.domain.usecase.admin.RestoreBackupUseCase
import com.zyntasolutions.zyntapos.domain.usecase.admin.VacuumDatabaseUseCase
import com.zyntasolutions.zyntapos.domain.usecase.admin.VerifyAuditIntegrityUseCase
import com.zyntasolutions.zyntapos.feature.admin.notification.NotificationViewModel
import com.zyntasolutions.zyntapos.security.audit.SecurityAuditLogger
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin DI module for the :composeApp:feature:admin feature (Sprints 13–15).
 *
 * ### Use-Case Registrations (factory — new instance per injection)
 *
 * **Sprint 13 — System Health**
 * - [GetSystemHealthUseCase], [GetDatabaseStatsUseCase], [VacuumDatabaseUseCase], [PurgeExpiredDataUseCase]
 *
 * **Sprint 14 — Backup / Restore**
 * - [GetBackupsUseCase], [CreateBackupUseCase], [RestoreBackupUseCase], [DeleteBackupUseCase]
 *
 * ### ViewModels
 * - [AdminViewModel] — System Health + Backup + Audit Log
 * - [NotificationViewModel] — in-app notification inbox
 */
val adminModule = module {

    // ── Sprint 13: System health ───────────────────────────────────────────
    factoryOf(::GetSystemHealthUseCase)
    factoryOf(::GetDatabaseStatsUseCase)
    factoryOf(::VacuumDatabaseUseCase)
    factoryOf(::PurgeExpiredDataUseCase)

    // ── Sprint 14: Backup / restore ───────────────────────────────────────
    factoryOf(::GetBackupsUseCase)
    factoryOf(::CreateBackupUseCase)
    factoryOf(::RestoreBackupUseCase)
    factoryOf(::DeleteBackupUseCase)

    // ── Sprint 15: Audit integrity ────────────────────────────────────────
    factory { VerifyAuditIntegrityUseCase(get(), SecurityAuditLogger::computeExpectedHash) }

    // ── AdminViewModel ────────────────────────────────────────────────────
    viewModel {
        AdminViewModel(
            getSystemHealthUseCase = get(),
            getDatabaseStatsUseCase = get(),
            systemRepository = get(),
            getBackupsUseCase = get(),
            createBackupUseCase = get(),
            restoreBackupUseCase = get(),
            deleteBackupUseCase = get(),
            auditRepository = get(),
            verifyAuditIntegrityUseCase = get(),
            auditLogger = get(),
            authRepository = get(),
        )
    }

    // ── NotificationViewModel ─────────────────────────────────────────────
    viewModel {
        NotificationViewModel(
            notificationRepository = get(),
            authRepository = get(),
        )
    }
}
