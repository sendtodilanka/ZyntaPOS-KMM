package com.zyntasolutions.zyntapos.feature.settings.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Desktop (JVM) actual — BackupService
//
// Copies the encrypted DB file from the app data directory to a timestamped
// backup file in ~/.zyntapos/backups/. Restore copies the selected file back
// over the database.
//
// The appDataDir is the same directory used by DatabaseDriverFactory —
// injected via Koin from DesktopDataModule.appDataDirectory().
// ─────────────────────────────────────────────────────────────────────────────

private const val DB_FILE_NAME = "zyntapos_encrypted.db"
private const val BACKUP_DIR_NAME = "backups"

/**
 * JVM Desktop implementation of [BackupService].
 *
 * @param appDataDir Absolute path to the application's data directory,
 *   e.g. `~/.zyntapos/data/`. The database file lives here.
 */
class JvmBackupService(private val appDataDir: String) : BackupService {

    private val backupDir: File
        get() {
            // Place backups as a sibling to the data directory
            val parent = File(appDataDir).parentFile ?: File(appDataDir)
            val dir = File(parent, BACKUP_DIR_NAME)
            dir.mkdirs()
            return dir
        }

    override suspend fun createBackup(): Result<BackupResult> = withContext(Dispatchers.IO) {
        runCatching {
            val dbFile = File(appDataDir, DB_FILE_NAME)
            if (!dbFile.exists()) {
                throw IllegalStateException("Database file not found: ${dbFile.absolutePath}")
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val backupFile = File(backupDir, "zyntapos_backup_$timestamp.db")

            Files.copy(
                dbFile.toPath(),
                backupFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )

            // Copy WAL and SHM files if they exist
            listOf("-wal", "-shm").forEach { suffix ->
                val walFile = File(dbFile.absolutePath + suffix)
                if (walFile.exists()) {
                    Files.copy(
                        walFile.toPath(),
                        File(backupFile.absolutePath + suffix).toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }
            }

            BackupResult(
                filePath = backupFile.absolutePath,
                sizeBytes = backupFile.length(),
            )
        }
    }

    override suspend fun restoreFromBackup(sourcePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                throw IllegalStateException("Backup file not found: $sourcePath")
            }

            val dbFile = File(appDataDir, DB_FILE_NAME)

            // Delete existing WAL/SHM files before restore
            listOf("-wal", "-shm").forEach { suffix ->
                File(dbFile.absolutePath + suffix).delete()
            }

            Files.copy(
                sourceFile.toPath(),
                dbFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
            Unit
        }
    }

    override fun getDefaultBackupDirectory(): String = backupDir.absolutePath
}
