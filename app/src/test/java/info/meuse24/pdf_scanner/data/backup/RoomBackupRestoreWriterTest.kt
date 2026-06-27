package info.meuse24.pdf_scanner.data.backup

import info.meuse24.pdf_scanner.data.local.FolderEntity
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.domain.backup.BackupArchiveException
import info.meuse24.pdf_scanner.domain.backup.BackupDocumentEntry
import info.meuse24.pdf_scanner.domain.backup.BackupFailure
import info.meuse24.pdf_scanner.domain.backup.BackupFileEntry
import info.meuse24.pdf_scanner.domain.backup.BackupFolderEntry
import info.meuse24.pdf_scanner.domain.backup.BackupManifest
import info.meuse24.pdf_scanner.domain.backup.BackupOcrEntry
import info.meuse24.pdf_scanner.domain.backup.BackupStagingResult
import info.meuse24.pdf_scanner.domain.gateway.StorageProvider
import info.meuse24.pdf_scanner.domain.pdf.PdfRenderingOps
import info.meuse24.pdf_scanner.domain.usecase.ImagePdfOptions
import info.meuse24.pdf_scanner.domain.usecase.PdfCompressionPreset
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RoomBackupRestoreWriterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun `restore reuses folder by name copies files and inserts ocr fields`() = runTest {
        val scansDir = temporaryFolder.newFolder("scans")
        File(scansDir, "Invoice.pdf").writeBytes(byteArrayOf(0))
        File(scansDir, "Invoice.jpg").writeBytes(byteArrayOf(0))
        val store = FakeBackupRestoreStore(
            folders = listOf(FolderEntity(id = 7L, name = "receipts", createdAt = 1L))
        )
        val writer = writer(scansDir, store)
        val staging = staging(
            folders = listOf(BackupFolderEntry("folder-1", "Receipts", colorArgb = 0xff00ff, createdAt = 10L)),
            document = document(folderBackupId = "folder-1", thumbnail = true, ocr = true)
        )

        val summary = writer.restoreFromStaging(staging)

        assertEquals(1, summary.documentsRestored)
        assertEquals(0, summary.foldersCreated)
        assertEquals(1, summary.foldersReused)
        assertEquals(1, summary.thumbnailsCopied)
        val inserted = store.insertedScans.single()
        assertEquals("Invoice_2", inserted.filename)
        assertEquals(File(scansDir, "Invoice_2.pdf").absolutePath, inserted.filepath)
        assertEquals(File(scansDir, "Invoice_2.jpg").absolutePath, inserted.thumbnailPath)
        assertEquals(7L, inserted.folderId)
        assertEquals("OCR full text", inserted.extractedText)
        assertEquals("""["OCR full text"]""", inserted.ocrPageTextJson)
        assertEquals(0.95f, inserted.ocrConfidence)
        assertEquals("deu", inserted.ocrLanguage)
        assertArrayEquals(byteArrayOf(1, 2, 3), File(inserted.filepath).readBytes())
        assertArrayEquals(byteArrayOf(4, 5), File(checkNotNull(inserted.thumbnailPath)).readBytes())
        assertFalse(staging.stagingDir.exists())
    }

    @Test
    fun `restore creates missing folder and regenerates missing thumbnail`() = runTest {
        val scansDir = temporaryFolder.newFolder("scans")
        val renderer = FakePdfRenderingOps(generateThumbnail = true)
        val store = FakeBackupRestoreStore()
        val writer = writer(scansDir, store, renderer)
        val staging = staging(
            folders = listOf(BackupFolderEntry("folder-1", "Contracts", colorArgb = 123, createdAt = 10L)),
            document = document(folderBackupId = "folder-1", thumbnail = false, ocr = false)
        )

        val summary = writer.restoreFromStaging(staging)

        assertEquals(1, summary.foldersCreated)
        assertEquals(0, summary.foldersReused)
        assertEquals(0, summary.thumbnailsCopied)
        assertEquals(1, summary.thumbnailsRegenerated)
        assertEquals("Contracts", store.insertedFolders.single().name)
        assertEquals(123, store.insertedFolders.single().colorArgb)
        val inserted = store.insertedScans.single()
        assertNotNull(inserted.thumbnailPath)
        assertTrue(File(checkNotNull(inserted.thumbnailPath)).isFile)
        assertEquals(File(scansDir, "Invoice.jpg"), renderer.generatedThumbnails.single())
        assertFalse(staging.stagingDir.exists())
    }

    @Test
    fun `restore deletes copied files and staging when database insert fails`() = runTest {
        val scansDir = temporaryFolder.newFolder("scans")
        val store = FakeBackupRestoreStore(failOnInsertScan = true)
        val writer = writer(scansDir, store)
        val staging = staging(
            folders = emptyList(),
            document = document(folderBackupId = null, thumbnail = true, ocr = false)
        )

        val error = assertBackupArchiveException {
            writer.restoreFromStaging(staging)
        }

        assertTrue(error.failure is BackupFailure.Io)
        assertFalse(File(scansDir, "Invoice.pdf").exists())
        assertFalse(File(scansDir, "Invoice.jpg").exists())
        assertFalse(staging.stagingDir.exists())
    }

    @Test
    fun `restore fails before copying when target has insufficient free space`() = runTest {
        val scansDir = temporaryFolder.newFolder("scans")
        val store = FakeBackupRestoreStore()
        val writer = writer(scansDir, store, usableSpace = 0L)
        val staging = staging(
            folders = emptyList(),
            document = document(folderBackupId = null, thumbnail = true, ocr = false)
        )

        val error = assertBackupArchiveException {
            writer.restoreFromStaging(staging)
        }

        assertTrue(error.failure is BackupFailure.InsufficientStorage)
        assertFalse(File(scansDir, "Invoice.pdf").exists())
        assertFalse(File(scansDir, "Invoice.jpg").exists())
        assertTrue(store.insertedScans.isEmpty())
        assertFalse(staging.stagingDir.exists())
    }

    private fun writer(
        scansDir: File,
        store: FakeBackupRestoreStore,
        renderer: FakePdfRenderingOps = FakePdfRenderingOps(),
        usableSpace: Long? = null
    ): RoomBackupRestoreWriter =
        RoomBackupRestoreWriter(
            restoreStore = store,
            storageProvider = object : StorageProvider {
                override fun scansDir(): File = scansDir
                override fun tempDir(): File = temporaryFolder.root.resolve("temp")
                override fun usableSpace(directory: File): Long =
                    usableSpace ?: directory.usableSpace
            },
            pdfRenderingOps = renderer
        )

    private fun staging(
        folders: List<BackupFolderEntry>,
        document: BackupDocumentEntry
    ): BackupStagingResult {
        val stagingDir = temporaryFolder.newFolder("staging-${System.nanoTime()}")
        val pdfFile = stagingDir.resolve(document.pdf.path).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val files = linkedMapOf(document.pdf.path to pdfFile)
        document.thumbnail?.let { thumbnail ->
            files[thumbnail.path] = stagingDir.resolve(thumbnail.path).apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(4, 5))
            }
        }
        document.ocr?.let { ocr ->
            files[ocr.path] = stagingDir.resolve(ocr.path).apply {
                parentFile?.mkdirs()
                writeText(
                    json.encodeToString(
                        BackupOcrEntry(
                            backupDocumentId = document.backupDocumentId,
                            extractedText = "OCR full text",
                            ocrPageTextJson = """["OCR full text"]""",
                            ocrConfidence = 0.95f,
                            ocrLanguage = "deu"
                        )
                    )
                )
            }
        }
        return BackupStagingResult(
            manifest = BackupManifest(
                sourceDatabaseVersion = 9,
                exportedAtEpochMillis = 1L,
                folders = folders,
                documents = listOf(document)
            ),
            stagingDir = stagingDir,
            files = files
        )
    }

    private fun document(
        folderBackupId: String?,
        thumbnail: Boolean,
        ocr: Boolean
    ): BackupDocumentEntry =
        BackupDocumentEntry(
            backupDocumentId = "document-1",
            filename = "Invoice",
            timestamp = 123L,
            pageCount = 2,
            fileSize = 3L,
            isSearchable = true,
            isEncrypted = false,
            tags = "tax",
            folderBackupId = folderBackupId,
            isFavorite = true,
            pdf = BackupFileEntry("documents/document-1.pdf", sizeBytes = 3L, sha256Base64 = ""),
            thumbnail = if (thumbnail) {
                BackupFileEntry("thumbnails/document-1.jpg", sizeBytes = 2L, sha256Base64 = "")
            } else {
                null
            },
            ocr = if (ocr) {
                BackupFileEntry("ocr/document-1.json", sizeBytes = 1L, sha256Base64 = "")
            } else {
                null
            }
        )

    private suspend fun assertBackupArchiveException(block: suspend () -> Unit): BackupArchiveException =
        try {
            block()
            throw AssertionError("Expected BackupArchiveException")
        } catch (exception: BackupArchiveException) {
            exception
        }
}

private class FakeBackupRestoreStore(
    folders: List<FolderEntity> = emptyList(),
    private val failOnInsertScan: Boolean = false
) : BackupRestoreStore {
    private val initialFolders = folders.toMutableList()
    val insertedFolders = mutableListOf<FolderEntity>()
    val insertedScans = mutableListOf<ScanRecord>()

    override suspend fun <T> withTransaction(block: suspend BackupRestoreTransaction.() -> T): T =
        block(
            object : BackupRestoreTransaction {
                override suspend fun loadFolders(): List<FolderEntity> =
                    initialFolders + insertedFolders

                override suspend fun insertFolder(folder: FolderEntity): Long {
                    val id = (initialFolders + insertedFolders).maxOfOrNull { it.id }?.plus(1L) ?: 1L
                    insertedFolders += folder.copy(id = id)
                    return id
                }

                override suspend fun insertScan(record: ScanRecord): Long {
                    if (failOnInsertScan) throw IllegalStateException("database failed")
                    val id = insertedScans.size + 1L
                    insertedScans += record.copy(id = id)
                    return id
                }
            }
        )
}

private class FakePdfRenderingOps(
    private val generateThumbnail: Boolean = false
) : PdfRenderingOps {
    val generatedThumbnails = mutableListOf<File>()

    override fun generateThumbnail(pdfFile: File, outputFile: File): Boolean {
        if (!generateThumbnail) return false
        outputFile.writeBytes(byteArrayOf(9, 9, 9))
        generatedThumbnails += outputFile
        return true
    }

    override fun compressPdf(input: File, outputDir: File, preset: PdfCompressionPreset): File =
        throw UnsupportedOperationException()

    override fun convertToGrayscale(input: File, outputDir: File): File =
        throw UnsupportedOperationException()

    override suspend fun createPdfFromImages(
        imageCount: Int,
        imageProvider: suspend (index: Int) -> ByteArray?,
        options: ImagePdfOptions,
        outputFile: File
    ): File = throw UnsupportedOperationException()

    override fun getPageCount(pdfFile: File): Int =
        throw UnsupportedOperationException()

    override fun renderPageThumbnail(pdfFile: File, pageIndex: Int, maxSizePx: Int): Any? =
        throw UnsupportedOperationException()
}
