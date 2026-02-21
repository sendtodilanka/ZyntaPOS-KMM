package com.zyntasolutions.zyntapos.feature.settings

/**
 * MVI — one-time side effects emitted by [SettingsViewModel].
 */
sealed interface SettingsEffect {
    data class ShowSnackbar(val message: String) : SettingsEffect
    data object GeneralSaved : SettingsEffect
    data object PosSaved : SettingsEffect
    data object PrinterSaved : SettingsEffect
    data object PrintTestPageSent : SettingsEffect
    data object UserSaved : SettingsEffect
    data class BackupComplete(val filePath: String) : SettingsEffect
    data object RestoreComplete : SettingsEffect
    data class ThemeModeChanged(val mode: ThemeMode) : SettingsEffect
    /** Instruct the nav layer to open a file picker for DB restore. */
    data object OpenFilePicker : SettingsEffect
}
