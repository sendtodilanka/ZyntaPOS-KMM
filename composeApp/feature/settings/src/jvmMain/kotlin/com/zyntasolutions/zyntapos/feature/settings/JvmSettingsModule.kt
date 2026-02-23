package com.zyntasolutions.zyntapos.feature.settings

import com.zyntasolutions.zyntapos.feature.settings.backup.BackupService
import com.zyntasolutions.zyntapos.feature.settings.backup.JvmBackupService
import org.koin.dsl.module

/**
 * JVM (Desktop) platform Koin module for the settings feature.
 *
 * Binds [JvmBackupService] as the [BackupService] singleton.
 * The `appDataDir` is expected to be already registered in the Koin graph
 * by [DesktopDataModule].
 */
val jvmSettingsModule = module {
    single<BackupService> { JvmBackupService(appDataDir = get()) }
}
