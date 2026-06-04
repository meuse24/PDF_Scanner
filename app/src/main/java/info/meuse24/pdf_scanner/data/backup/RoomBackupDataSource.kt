package info.meuse24.pdf_scanner.data.backup

import info.meuse24.pdf_scanner.BuildConfig
import info.meuse24.pdf_scanner.data.local.FolderDao
import info.meuse24.pdf_scanner.data.local.FolderEntity
import info.meuse24.pdf_scanner.data.local.ScanDao
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.local.TrashDao
import info.meuse24.pdf_scanner.domain.backup.BackupExportData
import info.meuse24.pdf_scanner.domain.backup.BackupExportDocument
import info.meuse24.pdf_scanner.domain.backup.BackupExportFolder
import info.meuse24.pdf_scanner.domain.backup.BackupExportOptions
import info.meuse24.pdf_scanner.domain.gateway.BackupDataSource
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class RoomBackupDataSource @Inject constructor(
    private val scanDao: ScanDao,
    private val trashDao: TrashDao,
    private val folderDao: FolderDao
) : BackupDataSource {
    override suspend fun loadExportData(options: BackupExportOptions): BackupExportData {
        val folders = folderDao.getFoldersOnce()
        val folderBackupIds = folders.associate { folder -> folder.id to folder.backupId() }
        val activeScans = scanDao.getAllScans().first()
        val trashedScans = if (options.includeTrash) trashDao.getTrashedScans().first() else emptyList()

        return BackupExportData(
            sourceDatabaseVersion = SOURCE_DATABASE_VERSION,
            exportedAtEpochMillis = System.currentTimeMillis(),
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE.toLong(),
            options = options,
            folders = folders.map { it.toBackupExportFolder() },
            documents = (activeScans + trashedScans).map { record ->
                record.toBackupExportDocument(
                    folderBackupIds = folderBackupIds,
                    includeOcrText = options.includeOcrText,
                    includeThumbnails = options.includeThumbnails
                )
            }
        )
    }

    private fun FolderEntity.toBackupExportFolder(): BackupExportFolder =
        BackupExportFolder(
            localId = id,
            backupFolderId = backupId(),
            name = name,
            colorArgb = colorArgb,
            createdAt = createdAt
        )

    private fun ScanRecord.toBackupExportDocument(
        folderBackupIds: Map<Long, String>,
        includeOcrText: Boolean,
        includeThumbnails: Boolean
    ): BackupExportDocument =
        BackupExportDocument(
            localId = id,
            backupDocumentId = backupId(),
            filename = filename,
            timestamp = timestamp,
            pageCount = pageCount,
            fileSize = fileSize,
            isSearchable = isSearchable,
            isEncrypted = isEncrypted,
            tags = tags,
            folderBackupId = folderId?.let(folderBackupIds::get),
            isFavorite = isFavorite,
            deletedAt = deletedAt,
            extractedText = extractedText.takeIf { includeOcrText },
            ocrConfidence = ocrConfidence.takeIf { includeOcrText },
            ocrLanguage = ocrLanguage.takeIf { includeOcrText },
            ocrPageTextJson = ocrPageTextJson.takeIf { includeOcrText },
            pdfFile = File(filepath),
            thumbnailFile = thumbnailPath
                ?.takeIf { includeThumbnails }
                ?.let(::File)
        )

    private fun FolderEntity.backupId(): String = "folder-$id"

    private fun ScanRecord.backupId(): String = "document-$id"

    private companion object {
        const val SOURCE_DATABASE_VERSION = 9
    }
}
