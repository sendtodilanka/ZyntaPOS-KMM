package com.zyntasolutions.zyntapos.feature.settings

import android.content.Context
import com.zyntasolutions.zyntapos.feature.settings.backup.AndroidBackupService
import com.zyntasolutions.zyntapos.feature.settings.backup.BackupService
import org.koin.dsl.module

/**
 * Android-specific Koin module for the settings feature.
 *
 * Binds [AndroidBackupService] as the [BackupService] implementation,
 * providing platform-specific file I/O for database backup and restore.
 */
fun androidSettingsModule(context: Context) = module {
    single<BackupService> { AndroidBackupService(context) }
}
