package com.zyntasolutions.zyntapos.data.backup

import co.touchlab.kermit.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 * JVM (Desktop) actual implementation of [BackupFileManager].
 *
 * - **Backups directory:** `<appDataDir>/../backups/` (sibling of the data directory).
 *   On macOS/Linux: `~/.zyntapos/backups/`
 *   On Windows: `%APPDATA%/ZyntaPOS/backups/`
 * - **Database file:** `<appDataDir>/zyntapos_encrypted.db`
 *
 * @param appDataDir Absolute path to the application data directory (provided by Koin).
 */
actual class BackupFileManager(private val appDataDir: String) {

    private val log = Logger.withTag("BackupFileManager")

    actual fun backupsDir(): String {
        // Place backups in a sibling `backups/` directory next to `data/`
        val dataDir = File(appDataDir)
        val dir = dataDir.parentFile?.resolve("backups") ?: dataDir.resolve("backups")
        dir.mkdirs()
        return dir.absolutePath
    }

    actual fun copyDbToBackup(backupFileName: String): Long {
        val src = dbFile().toPath()
        check(Files.exists(src)) { "Database file not found at: $src" }
        val dst = Paths.get(backupsDir(), backupFileName)
        Files.createDirectories(dst.parent)
        log.i { "Copying DB to backup: $src → $dst" }
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING)
        val size = Files.size(dst)
        log.i { "Backup created: $backupFileName ($size bytes)" }
        return size
    }

    actual fun copyBackupToDb(backupFileName: String) {
        val src = Paths.get(backupsDir(), backupFileName)
        check(Files.exists(src)) { "Backup file not found: $src" }
        val dst = dbFile().toPath()
        Files.createDirectories(dst.parent)
        log.i { "Restoring backup: $src → $dst" }
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING)
        log.i { "Database restored from backup: $backupFileName" }
    }

    actual fun deleteBackupFile(backupFileName: String) {
        val path = Paths.get(backupsDir(), backupFileName)
        val deleted = Files.deleteIfExists(path)
        if (deleted) log.i { "Deleted backup file: $backupFileName" }
    }

    actual fun exportBackupFile(backupFileName: String, exportPath: String) {
        val src = Paths.get(backupsDir(), backupFileName)
        check(Files.exists(src)) { "Backup file not found: $src" }
        val dst = Paths.get(exportPath)
        Files.createDirectories(dst.parent)
        log.i { "Exporting backup: $src → $dst" }
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING)
        log.i { "Backup exported: $dst" }
    }

    private fun dbFile(): File = File(appDataDir, DB_FILE_NAME)

    private companion object {
        const val DB_FILE_NAME = "zyntapos_encrypted.db"
    }
}
