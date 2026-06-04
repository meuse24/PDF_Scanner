package info.meuse24.pdf_scanner.data.backup

import info.meuse24.pdf_scanner.data.local.FolderEntity
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.domain.backup.BackupArchiveException
import info.meuse24.pdf_scanner.domain.backup.BackupDocumentEntry
import info.meuse24.pdf_scanner.domain.backup.BackupFailure
import info.meuse24.pdf_scanner.domain.backup.BackupFolderEntry
import info.meuse24.pdf_scanner.domain.backup.BackupOcrEntry
import info.meuse24.pdf_scanner.domain.backup.BackupRestoreSummary
import info.meuse24.pdf_scanner.domain.backup.BackupStagingResult
import info.meuse24.pdf_scanner.domain.common.resolveUniqueFilename
import info.meuse24.pdf_scanner.domain.common.sanitizeFilename
import info.meuse24.pdf_scanner.domain.gateway.BackupRestoreWriter
import info.meuse24.pdf_scanner.domain.gateway.StorageProvider
import info.meuse24.pdf_scanner.domain.pdf.PdfRenderingOps
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Singleton
class RoomBackupRestoreWriter @Inject constructor(
    private val restoreStore: BackupRestoreStore,
    private val storageProvider: StorageProvider,
    private val pdfRenderingOps: PdfRenderingOps
) : BackupRestoreWriter {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override suspend fun restoreFromStaging(staging: BackupStagingResult): BackupRestoreSummary {
        validateRestoreManifest(staging)
        val scansDir = storageProvider.scansDir()
        val copiedFiles = mutableListOf<File>()
        var success = false

        return try {
            ensureDirectory(scansDir)
            ensureUsableSpace(scansDir, staging.requiredFinalCopyBytes())
            val restoredDocuments = staging.manifest.documents.map { document ->
                restoreDocumentFiles(
                    staging = staging,
                    document = document,
                    scansDir = scansDir,
                    copiedFiles = copiedFiles
                )
            }
            val summary = restoreStore.withTransaction {
                restoreFoldersAndDocuments(staging.manifest.folders, restoredDocuments)
            }
            success = true
            summary
        } catch (exception: BackupArchiveException) {
            throw exception
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: IOException) {
            throw BackupArchiveException(
                BackupFailure.Io(exception.message),
                "Could not restore backup files",
                exception
            )
        } catch (exception: SerializationException) {
            throw invalidManifest("Backup OCR payload is not valid JSON", exception)
        } catch (exception: Exception) {
            throw BackupArchiveException(
                BackupFailure.Io(exception.message),
                "Could not finalize backup restore",
                exception
            )
        } finally {
            if (!success) copiedFiles.deleteAll()
            staging.stagingDir.deleteRecursively()
        }
    }

    private fun validateRestoreManifest(staging: BackupStagingResult) {
        val folderIds = mutableSetOf<String>()
        staging.manifest.folders.forEach { folder ->
            if (!folderIds.add(folder.backupFolderId)) {
                throw invalidManifest("Backup contains duplicate folder id ${folder.backupFolderId}")
            }
        }
        val documentIds = mutableSetOf<String>()
        staging.manifest.documents.forEach { document ->
            if (!documentIds.add(document.backupDocumentId)) {
                throw invalidManifest("Backup contains duplicate document id ${document.backupDocumentId}")
            }
            document.folderBackupId?.let { folderId ->
                if (folderId !in folderIds) {
                    throw invalidManifest("Backup document references missing folder $folderId")
                }
            }
        }
    }

    private fun restoreDocumentFiles(
        staging: BackupStagingResult,
        document: BackupDocumentEntry,
        scansDir: File,
        copiedFiles: MutableList<File>
    ): RestoredDocument {
        val safeName = sanitizeFilename(document.filename, document.backupDocumentId)
        val baseName = resolveUniqueFilename(scansDir, safeName, conflictingExtensions = setOf("pdf", "jpg"))
        val pdfFile = File(scansDir, "$baseName.pdf")
        staging.requiredFile(document.pdf.path).copyCheckedTo(pdfFile)
        copiedFiles += pdfFile

        val thumbnailFile = File(scansDir, "$baseName.jpg")
        var thumbnailSource = ThumbnailSource.None
        val thumbnailPath = if (document.thumbnail != null) {
            staging.requiredFile(document.thumbnail.path).copyCheckedTo(thumbnailFile)
            copiedFiles += thumbnailFile
            thumbnailSource = ThumbnailSource.Copied
            thumbnailFile.absolutePath
        } else if (pdfRenderingOps.generateThumbnail(pdfFile, thumbnailFile) && thumbnailFile.isFile) {
            copiedFiles += thumbnailFile
            thumbnailSource = ThumbnailSource.Regenerated
            thumbnailFile.absolutePath
        } else {
            thumbnailFile.delete()
            null
        }

        return RestoredDocument(
            entry = document,
            filename = baseName,
            pdfFile = pdfFile,
            thumbnailPath = thumbnailPath,
            thumbnailSource = thumbnailSource,
            ocrFile = document.ocr?.let { ocrFile -> staging.requiredFile(ocrFile.path) }
        )
    }

    private suspend fun BackupRestoreTransaction.restoreFoldersAndDocuments(
        folders: List<BackupFolderEntry>,
        documents: List<RestoredDocument>
    ): BackupRestoreSummary {
        val existingFoldersByName = loadFolders().associateBy { it.name.normalizedFolderName() }.toMutableMap()
        val folderIdByBackupId = mutableMapOf<String, Long>()
        var foldersCreated = 0
        var foldersReused = 0

        folders.forEach { folder ->
            val key = folder.name.normalizedFolderName()
            val existing = existingFoldersByName[key]
            val folderId = if (existing != null) {
                foldersReused += 1
                existing.id
            } else {
                insertFolder(
                    FolderEntity(
                        name = folder.name,
                        colorArgb = folder.colorArgb,
                        createdAt = folder.createdAt
                    )
                ).also { newId ->
                    foldersCreated += 1
                    existingFoldersByName[key] = FolderEntity(
                        id = newId,
                        name = folder.name,
                        colorArgb = folder.colorArgb,
                        createdAt = folder.createdAt
                    )
                }
            }
            folderIdByBackupId[folder.backupFolderId] = folderId
        }

        documents.forEach { document ->
            insertScan(document.toScanRecord(folderIdByBackupId))
        }

        return BackupRestoreSummary(
            documentsRestored = documents.size,
            foldersCreated = foldersCreated,
            foldersReused = foldersReused,
            thumbnailsCopied = documents.count { it.thumbnailSource == ThumbnailSource.Copied },
            thumbnailsRegenerated = documents.count { it.thumbnailSource == ThumbnailSource.Regenerated }
        )
    }

    private fun RestoredDocument.toScanRecord(folderIdByBackupId: Map<String, Long>): ScanRecord =
        decodeOcr()?.let { ocr ->
            toScanRecord(folderIdByBackupId, ocr)
        } ?: toScanRecord(folderIdByBackupId, ocr = null)

    private fun RestoredDocument.toScanRecord(
        folderIdByBackupId: Map<String, Long>,
        ocr: BackupOcrEntry?
    ): ScanRecord =
        ScanRecord(
            filename = filename,
            filepath = pdfFile.absolutePath,
            timestamp = entry.timestamp,
            pageCount = entry.pageCount,
            fileSize = pdfFile.length(),
            thumbnailPath = thumbnailPath,
            isSearchable = entry.isSearchable,
            isEncrypted = entry.isEncrypted,
            extractedText = ocr?.extractedText,
            tags = entry.tags,
            ocrConfidence = ocr?.ocrConfidence,
            ocrLanguage = ocr?.ocrLanguage,
            ocrPageTextJson = ocr?.ocrPageTextJson,
            deletedAt = entry.deletedAt,
            folderId = entry.folderBackupId?.let { folderId ->
                folderIdByBackupId[folderId] ?: throw invalidManifest("Backup document references missing folder $folderId")
            },
            isFavorite = entry.isFavorite
        )

    private fun RestoredDocument.decodeOcr(): BackupOcrEntry? {
        val file = ocrFile ?: return null
        val ocr = json.decodeFromString<BackupOcrEntry>(file.readText())
        if (ocr.backupDocumentId != entry.backupDocumentId) {
            throw invalidManifest("Backup OCR payload belongs to another document")
        }
        return ocr
    }

    private fun BackupStagingResult.requiredFile(path: String): File =
        files[path]?.takeIf(File::isFile) ?: throw invalidManifest("Missing staged file $path")

    private fun BackupStagingResult.requiredFinalCopyBytes(): Long =
        manifest.documents.fold(0L) { total, document ->
            val withPdf = addSize(total, document.pdf.sizeBytes)
            document.thumbnail?.let { thumbnail -> addSize(withPdf, thumbnail.sizeBytes) } ?: withPdf
        }

    private fun File.copyCheckedTo(target: File) {
        ensureUsableSpace(target.parentFile ?: target, length())
        copyTo(target, overwrite = false)
    }

    private fun ensureUsableSpace(directory: File, requiredBytes: Long) {
        val availableBytes = storageProvider.usableSpace(directory)
        val requiredWithReserve = addSize(requiredBytes, MIN_FREE_SPACE_RESERVE_BYTES)
        if (availableBytes < requiredWithReserve) {
            throw BackupArchiveException(
                BackupFailure.InsufficientStorage(
                    requiredBytes = requiredWithReserve,
                    availableBytes = availableBytes
                ),
                "Not enough free storage to restore backup"
            )
        }
    }

    private fun ensureDirectory(directory: File) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create ${directory.absolutePath}")
        }
        if (!directory.isDirectory) {
            throw IOException("${directory.absolutePath} is not a directory")
        }
    }

    private fun List<File>.deleteAll() {
        asReversed().forEach { file -> file.delete() }
    }

    private fun String.normalizedFolderName(): String =
        trim().lowercase(Locale.ROOT)

    private fun addSize(current: Long, increment: Long): Long {
        val total = current + increment
        if (total < current) throw invalidManifest("Restore size overflow")
        return total
    }

    private fun invalidManifest(message: String, cause: Throwable? = null): BackupArchiveException =
        BackupArchiveException(BackupFailure.InvalidManifest, message, cause)

    private data class RestoredDocument(
        val entry: BackupDocumentEntry,
        val filename: String,
        val pdfFile: File,
        val thumbnailPath: String?,
        val thumbnailSource: ThumbnailSource,
        val ocrFile: File?
    )

    private enum class ThumbnailSource {
        None,
        Copied,
        Regenerated
    }

    private companion object {
        const val MIN_FREE_SPACE_RESERVE_BYTES = 16L * 1024L * 1024L
    }
}
