package com.zyntasolutions.zyntapos.data.backup

import android.content.Context
import co.touchlab.kermit.Logger
import java.io.File

/**
 * Android actual implementation of [BackupFileManager].
 *
 * - **Backups directory:** External app-specific storage → `getExternalFilesDir("backups")`,
 *   falling back to `filesDir/backups` if external storage is unavailable.
 * - **Database file:** Located via `Context.getDatabasePath("zyntapos_encrypted.db")`.
 *
 * @param context Application [Context] for resolving storage paths.
 */
actual class BackupFileManager(private val context: Context) {

    private val log = Logger.withTag("BackupFileManager")

    actual fun backupsDir(): String {
        val dir = context.getExternalFilesDir("backups")
            ?: context.filesDir.resolve("backups")
        dir.mkdirs()
        return dir.absolutePath
    }

    actual fun copyDbToBackup(backupFileName: String): Long {
        val src = dbFile()
        check(src.exists()) { "Database file not found at: ${src.absolutePath}" }
        val dst = File(backupsDir(), backupFileName)
        log.i { "Copying DB to backup: ${src.absolutePath} → ${dst.absolutePath}" }
        src.copyTo(dst, overwrite = true)
        log.i { "Backup created: ${dst.name} (${dst.length()} bytes)" }
        return dst.length()
    }

    actual fun copyBackupToDb(backupFileName: String) {
        val src = File(backupsDir(), backupFileName)
        check(src.exists()) { "Backup file not found: ${src.absolutePath}" }
        val dst = dbFile()
        dst.parentFile?.mkdirs()
        log.i { "Restoring backup: ${src.absolutePath} → ${dst.absolutePath}" }
        src.copyTo(dst, overwrite = true)
        log.i { "Database restored from backup: ${src.name}" }
    }

    actual fun deleteBackupFile(backupFileName: String) {
        val file = File(backupsDir(), backupFileName)
        if (file.exists()) {
            file.delete()
            log.i { "Deleted backup file: $backupFileName" }
        }
    }

    actual fun exportBackupFile(backupFileName: String, exportPath: String) {
        val src = File(backupsDir(), backupFileName)
        check(src.exists()) { "Backup file not found: ${src.absolutePath}" }
        val dst = File(exportPath)
        dst.parentFile?.mkdirs()
        log.i { "Exporting backup: ${src.absolutePath} → ${dst.absolutePath}" }
        src.copyTo(dst, overwrite = true)
        log.i { "Backup exported: ${dst.absolutePath}" }
    }

    private fun dbFile(): File = context.getDatabasePath(DB_FILE_NAME)

    private companion object {
        const val DB_FILE_NAME = "zyntapos_encrypted.db"
    }
}
