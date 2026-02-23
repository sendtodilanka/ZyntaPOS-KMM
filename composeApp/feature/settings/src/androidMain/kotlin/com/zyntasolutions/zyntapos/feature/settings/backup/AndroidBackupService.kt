package com.zyntasolutions.zyntapos.feature.settings.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Android actual — BackupService
//
// Creates a timestamped copy of the encrypted DB in the app's external
// files directory. Restores by copying a selected file (content:// URI)
// over the existing database file.
//
// WAL checkpoint (PRAGMA wal_checkpoint(TRUNCATE)) should be performed
// before backup to ensure all data is in the main DB file. This is handled
// by the SettingsViewModel before calling createBackup().
// ─────────────────────────────────────────────────────────────────────────────

private const val DB_FILE_NAME = "zyntapos_encrypted.db"
private const val BACKUP_DIR = "backups"
private const val BUFFER_SIZE = 8192

/**
 * Android implementation of [BackupService].
 *
 * @param context Application [Context] for accessing database and external files directories.
 */
class AndroidBackupService(private val context: Context) : BackupService {

    override suspend fun createBackup(): Result<BackupResult> = withContext(Dispatchers.IO) {
        runCatching {
            val dbFile = context.getDatabasePath(DB_FILE_NAME)
            if (!dbFile.exists()) {
                throw IllegalStateException("Database file not found: ${dbFile.absolutePath}")
            }

            val backupDir = File(context.getExternalFilesDir(null), BACKUP_DIR)
            backupDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val backupFile = File(backupDir, "zyntapos_backup_$timestamp.db")

            FileInputStream(dbFile).use { input ->
                FileOutputStream(backupFile).use { output ->
                    input.copyTo(output, BUFFER_SIZE)
                }
            }

            // Also copy WAL and SHM files if they exist
            listOf("-wal", "-shm").forEach { suffix ->
                val walFile = File(dbFile.absolutePath + suffix)
                if (walFile.exists()) {
                    val walBackup = File(backupFile.absolutePath + suffix)
                    FileInputStream(walFile).use { input ->
                        FileOutputStream(walBackup).use { output ->
                            input.copyTo(output, BUFFER_SIZE)
                        }
                    }
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
            val dbFile = context.getDatabasePath(DB_FILE_NAME)
            val dbDir = dbFile.parentFile
            dbDir?.mkdirs()

            // Handle content:// URIs (from SAF file picker) and file:// paths
            val uri = Uri.parse(sourcePath)
            val inputStream = if (uri.scheme == "content" || uri.scheme == "file") {
                context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot open file: $sourcePath")
            } else {
                // Treat as absolute file path
                val sourceFile = File(sourcePath)
                if (!sourceFile.exists()) {
                    throw IllegalStateException("Backup file not found: $sourcePath")
                }
                FileInputStream(sourceFile)
            }

            // Delete existing WAL/SHM files before restore
            listOf("-wal", "-shm").forEach { suffix ->
                File(dbFile.absolutePath + suffix).delete()
            }

            // Copy the backup over the existing database
            inputStream.use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output, BUFFER_SIZE)
                }
            }
            Unit
        }
    }

    override fun getDefaultBackupDirectory(): String {
        val backupDir = File(context.getExternalFilesDir(null), BACKUP_DIR)
        return backupDir.absolutePath
    }
}
