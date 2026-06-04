package info.meuse24.pdf_scanner.data.backup

import androidx.room.withTransaction
import info.meuse24.pdf_scanner.data.local.AppDatabase
import info.meuse24.pdf_scanner.data.local.FolderEntity
import info.meuse24.pdf_scanner.data.local.ScanRecord
import javax.inject.Inject
import javax.inject.Singleton

interface BackupRestoreStore {
    suspend fun <T> withTransaction(block: suspend BackupRestoreTransaction.() -> T): T
}

interface BackupRestoreTransaction {
    suspend fun loadFolders(): List<FolderEntity>
    suspend fun insertFolder(folder: FolderEntity): Long
    suspend fun insertScan(record: ScanRecord): Long
}

@Singleton
class RoomBackupRestoreStore @Inject constructor(
    private val database: AppDatabase
) : BackupRestoreStore {
    private val transaction = object : BackupRestoreTransaction {
        override suspend fun loadFolders(): List<FolderEntity> =
            database.folderDao().getFoldersOnce()

        override suspend fun insertFolder(folder: FolderEntity): Long =
            database.folderDao().insert(folder)

        override suspend fun insertScan(record: ScanRecord): Long =
            database.scanDao().insert(record)
    }

    override suspend fun <T> withTransaction(block: suspend BackupRestoreTransaction.() -> T): T =
        database.withTransaction {
            transaction.block()
        }
}
