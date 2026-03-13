package com.zyntasolutions.zyntapos.data.backup

/**
 * Platform-specific file manager for database backups.
 *
 * Handles copying the encrypted SQLite DB file to/from the platform-specific
 * backups directory. Each platform has different filesystem APIs:
 * - **Android actual:** Uses [android.content.Context] for storage paths.
 * - **JVM actual:** Uses [java.nio.file] APIs with the app data directory.
 *
 * Injected via Koin platform modules:
 * - `androidDataModule`: `single { BackupFileManager(context = androidContext()) }`
 * - `desktopDataModule`: `single { BackupFileManager(appDataDir = get()) }`
 */
expect class BackupFileManager {

    /**
     * Returns the absolute path to the platform-specific backups directory.
     * Creates the directory if it does not exist.
     */
    fun backupsDir(): String

    /**
     * Copies the live encrypted DB file to a new backup file named [backupFileName]
     * inside [backupsDir].
     *
     * @return Size of the created backup file in bytes.
     * @throws IOException if the source DB file does not exist or the copy fails.
     */
    fun copyDbToBackup(backupFileName: String): Long

    /**
     * Copies a backup file back over the live DB file for restoration.
     *
     * **Important:** The DB driver must be closed and re-opened (or the app restarted)
     * for the restored database to take effect. SQLite cannot swap the underlying file
     * while a connection is open.
     *
     * @throws IOException if the backup file is not found or the copy fails.
     */
    fun copyBackupToDb(backupFileName: String)

    /**
     * Deletes a backup file from [backupsDir].
     * No-op if the file does not exist.
     */
    fun deleteBackupFile(backupFileName: String)

    /**
     * Copies a backup file to an arbitrary [exportPath] outside the backups directory.
     * Creates parent directories as needed.
     *
     * @throws IOException if the backup file is not found or the export fails.
     */
    fun exportBackupFile(backupFileName: String, exportPath: String)
}
