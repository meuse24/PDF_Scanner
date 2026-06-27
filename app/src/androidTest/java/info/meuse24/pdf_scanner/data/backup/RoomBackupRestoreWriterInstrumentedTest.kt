package info.meuse24.pdf_scanner.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import info.meuse24.pdf_scanner.data.local.AppDatabase
import info.meuse24.pdf_scanner.domain.backup.BackupArchiveException
import info.meuse24.pdf_scanner.domain.backup.BackupDocumentEntry
import info.meuse24.pdf_scanner.domain.backup.BackupFileEntry
import info.meuse24.pdf_scanner.domain.backup.BackupFailure
import info.meuse24.pdf_scanner.domain.backup.BackupManifest
import info.meuse24.pdf_scanner.domain.backup.BackupOcrEntry
import info.meuse24.pdf_scanner.domain.backup.BackupStagingResult
import info.meuse24.pdf_scanner.domain.gateway.StorageProvider
import info.meuse24.pdf_scanner.domain.pdf.PdfRenderingOps
import info.meuse24.pdf_scanner.domain.usecase.ImagePdfOptions
import info.meuse24.pdf_scanner.domain.usecase.PdfCompressionPreset
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RoomBackupRestoreWriterInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var rootDir: File
    private lateinit var scansDir: File
    private lateinit var writer: RoomBackupRestoreWriter

    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        rootDir = File(context.cacheDir, "backup_restore_androidtest_${System.nanoTime()}").apply {
            deleteRecursively()
            mkdirs()
        }
        scansDir = File(rootDir, "scans").apply { mkdirs() }
        writer = RoomBackupRestoreWriter(
            restoreStore = RoomBackupRestoreStore(database),
            storageProvider = object : StorageProvider {
                override fun scansDir(): File = scansDir
                override fun tempDir(): File = File(rootDir, "temp").apply { mkdirs() }
            },
            pdfRenderingOps = NoOpPdfRenderingOps
        )
    }

    @After
    fun tearDown() {
        database.close()
        rootDir.deleteRecursively()
    }

    @Test
    fun restoreViaRealRoomRebuildsFtsIndexFromOcrText() = runBlocking {
        val staging = staging(
            document("document-1", filename = "FTS Invoice", ocr = true),
            ocrText = "restored unicorn phrase"
        )

        writer.restoreFromStaging(staging)

        val restored = database.scanDao().searchScansFlow("unicorn").first()
        assertEquals(1, restored.size)
        assertEquals("FTS Invoice", restored.single().filename)
        assertEquals("restored unicorn phrase", restored.single().extractedText)
        assertFalse(staging.stagingDir.exists())
    }

    @Test
    fun restoreTransactionRollsBackRealRoomWhenLaterDocumentFails() = runBlocking {
        val valid = document("document-1", filename = "Valid before failure", ocr = true)
        val invalid = document("document-2", filename = "Invalid OCR", ocr = true)
        val staging = staging(valid, invalid, corruptOcrFor = invalid.backupDocumentId)

        val error = assertBackupArchiveException {
            writer.restoreFromStaging(staging)
        }

        assertEquals(BackupFailure.InvalidManifest, error.failure)
        assertTrue(database.scanDao().getScansByIds(listOf(1L, 2L)).isEmpty())
        assertTrue(database.scanDao().searchScansFlow("rollbacktoken").first().isEmpty())
        assertFalse(File(scansDir, "Valid before failure.pdf").exists())
        assertFalse(File(scansDir, "Invalid OCR.pdf").exists())
        assertFalse(staging.stagingDir.exists())
    }

    private fun staging(
        vararg documents: BackupDocumentEntry,
        ocrText: String = "rollbacktoken",
        corruptOcrFor: String? = null
    ): BackupStagingResult {
        val stagingDir = File(rootDir, "staging_${System.nanoTime()}").apply { mkdirs() }
        val files = linkedMapOf<String, File>()
        documents.forEachIndexed { index, document ->
            files[document.pdf.path] = File(stagingDir, document.pdf.path).apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3, index.toByte()))
            }
            document.ocr?.let { ocr ->
                files[ocr.path] = File(stagingDir, ocr.path).apply {
                    parentFile?.mkdirs()
                    if (document.backupDocumentId == corruptOcrFor) {
                        writeText("{not valid json")
                    } else {
                        writeText(
                            json.encodeToString(
                                BackupOcrEntry(
                                    backupDocumentId = document.backupDocumentId,
                                    extractedText = ocrText,
                                    ocrPageTextJson = """["$ocrText"]""",
                                    ocrConfidence = 0.91f,
                                    ocrLanguage = "eng"
                                )
                            )
                        )
                    }
                }
            }
        }
        return BackupStagingResult(
            manifest = BackupManifest(
                sourceDatabaseVersion = 9,
                exportedAtEpochMillis = 1L,
                documents = documents.toList()
            ),
            stagingDir = stagingDir,
            files = files
        )
    }

    private fun document(
        id: String,
        filename: String,
        ocr: Boolean
    ): BackupDocumentEntry =
        BackupDocumentEntry(
            backupDocumentId = id,
            filename = filename,
            timestamp = 123L,
            pageCount = 1,
            fileSize = 4L,
            isSearchable = ocr,
            isEncrypted = false,
            pdf = BackupFileEntry("documents/$id.pdf", sizeBytes = 4L, sha256Base64 = ""),
            ocr = if (ocr) BackupFileEntry("ocr/$id.json", sizeBytes = 1L, sha256Base64 = "") else null
        )

    private suspend fun assertBackupArchiveException(block: suspend () -> Unit): BackupArchiveException =
        try {
            block()
            throw AssertionError("Expected BackupArchiveException")
        } catch (exception: BackupArchiveException) {
            exception
        }
}

private object NoOpPdfRenderingOps : PdfRenderingOps {
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

    override fun generateThumbnail(pdfFile: File, outputFile: File): Boolean = false

    override fun renderPageThumbnail(pdfFile: File, pageIndex: Int, maxSizePx: Int): Any? = null
}
