package info.meuse24.pdf_scanner.data.backup

import info.meuse24.pdf_scanner.domain.backup.BackupArchiveException
import info.meuse24.pdf_scanner.domain.backup.BackupDocumentEntry
import info.meuse24.pdf_scanner.domain.backup.BackupExportData
import info.meuse24.pdf_scanner.domain.backup.BackupExportDocument
import info.meuse24.pdf_scanner.domain.backup.BackupFileEntry
import info.meuse24.pdf_scanner.domain.backup.BackupFailure
import info.meuse24.pdf_scanner.domain.backup.BackupFolderEntry
import info.meuse24.pdf_scanner.domain.backup.BackupManifest
import info.meuse24.pdf_scanner.domain.backup.BackupOcrEntry
import info.meuse24.pdf_scanner.domain.gateway.BackupPayloadWriter
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Singleton
class BackupZipPayloadWriter @Inject constructor() : BackupPayloadWriter {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    override fun writePayload(exportData: BackupExportData, outputStream: OutputStream) {
        val preparedDocuments = exportData.documents.map(::prepareDocument)
        val manifest = exportData.toManifest(preparedDocuments)

        ZipOutputStream(outputStream).use { zip ->
            zip.writeJsonEntry(MANIFEST_ENTRY, json.encodeToString(manifest).encodeToByteArray())
            preparedDocuments.forEach { prepared ->
                zip.writeStoredFileEntry(prepared.pdfPath, prepared.document.pdfFile, prepared.pdfStats)
                prepared.thumbnail?.let { thumbnail ->
                    zip.writeStoredFileEntry(thumbnail.path, thumbnail.file, thumbnail.stats)
                }
                prepared.ocr?.let { ocr ->
                    zip.writeJsonEntry(ocr.path, prepared.document.toOcrBytes())
                }
            }
        }
    }

    private fun prepareDocument(document: BackupExportDocument): PreparedDocument {
        if (!document.pdfFile.isFile) {
            throw BackupArchiveException(
                BackupFailure.MissingDocumentFile,
                "Missing PDF file for backup document ${document.filename}: ${document.pdfFile.absolutePath}"
            )
        }

        val pdfPath = "documents/${document.backupDocumentId}.pdf"
        val thumbnail = document.thumbnailFile
            ?.takeIf(File::isFile)
            ?.let { file ->
                val path = "thumbnails/${document.backupDocumentId}.jpg"
                PreparedThumbnail(
                    path = path,
                    file = file,
                    stats = file.computeStats()
                )
            }
        val ocr = document.toPreparedOcr()

        return PreparedDocument(
            document = document,
            pdfPath = pdfPath,
            pdfStats = document.pdfFile.computeStats(),
            thumbnail = thumbnail,
            ocr = ocr
        )
    }

    private fun BackupExportDocument.toPreparedOcr(): PreparedOcr? {
        if (!hasOcrPayload) return null
        val path = "ocr/$backupDocumentId.json"
        return PreparedOcr(
            path = path,
            stats = toOcrBytes().computeStats()
        )
    }

    private fun BackupExportDocument.toOcrBytes(): ByteArray =
        json.encodeToString(
            BackupOcrEntry(
                backupDocumentId = backupDocumentId,
                extractedText = extractedText,
                ocrPageTextJson = ocrPageTextJson,
                ocrConfidence = ocrConfidence,
                ocrLanguage = ocrLanguage
            )
        ).encodeToByteArray()

    private fun BackupExportData.toManifest(preparedDocuments: List<PreparedDocument>): BackupManifest =
        BackupManifest(
            sourceDatabaseVersion = sourceDatabaseVersion,
            exportedAtEpochMillis = exportedAtEpochMillis,
            appVersionName = appVersionName,
            appVersionCode = appVersionCode,
            options = options,
            folders = folders.map { folder ->
                BackupFolderEntry(
                    backupFolderId = folder.backupFolderId,
                    name = folder.name,
                    colorArgb = folder.colorArgb,
                    createdAt = folder.createdAt
                )
            },
            documents = preparedDocuments.map { prepared ->
                val document = prepared.document
                BackupDocumentEntry(
                    backupDocumentId = document.backupDocumentId,
                    filename = document.filename,
                    timestamp = document.timestamp,
                    pageCount = document.pageCount,
                    fileSize = document.fileSize,
                    isSearchable = document.isSearchable,
                    isEncrypted = document.isEncrypted,
                    tags = document.tags,
                    folderBackupId = document.folderBackupId,
                    isFavorite = document.isFavorite,
                    deletedAt = document.deletedAt,
                    pdf = prepared.pdfStats.toBackupFileEntry(prepared.pdfPath),
                    thumbnail = prepared.thumbnail?.let { it.stats.toBackupFileEntry(it.path) },
                    ocr = prepared.ocr?.let { it.stats.toBackupFileEntry(it.path) }
                )
            }
        )

    private fun ZipOutputStream.writeJsonEntry(path: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(path))
        write(bytes)
        closeEntry()
    }

    private fun ZipOutputStream.writeStoredFileEntry(path: String, file: File, stats: FileStats) {
        val entry = ZipEntry(path).apply {
            method = ZipEntry.STORED
            size = stats.sizeBytes
            compressedSize = stats.sizeBytes
            crc = stats.crc32
        }
        putNextEntry(entry)
        file.inputStream().use { input -> input.copyTo(this) }
        closeEntry()
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun File.computeStats(): FileStats {
        val digest = MessageDigest.getInstance("SHA-256")
        val crc = CRC32()
        var size = 0L
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
                crc.update(buffer, 0, read)
                size += read
            }
        }
        return FileStats(
            sizeBytes = size,
            sha256Base64 = Base64.encode(digest.digest()),
            crc32 = crc.value
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun ByteArray.computeStats(): FileStats {
        val crc = CRC32()
        crc.update(this)
        return FileStats(
            sizeBytes = size.toLong(),
            sha256Base64 = Base64.encode(MessageDigest.getInstance("SHA-256").digest(this)),
            crc32 = crc.value
        )
    }

    private fun FileStats.toBackupFileEntry(path: String): BackupFileEntry =
        BackupFileEntry(
            path = path,
            sizeBytes = sizeBytes,
            sha256Base64 = sha256Base64
        )

    private data class PreparedDocument(
        val document: BackupExportDocument,
        val pdfPath: String,
        val pdfStats: FileStats,
        val thumbnail: PreparedThumbnail?,
        val ocr: PreparedOcr?
    )

    private data class PreparedThumbnail(
        val path: String,
        val file: File,
        val stats: FileStats
    )

    private data class PreparedOcr(
        val path: String,
        val stats: FileStats
    )

    private data class FileStats(
        val sizeBytes: Long,
        val sha256Base64: String,
        val crc32: Long
    )

    private companion object {
        const val MANIFEST_ENTRY = "manifest.json"
    }
}
