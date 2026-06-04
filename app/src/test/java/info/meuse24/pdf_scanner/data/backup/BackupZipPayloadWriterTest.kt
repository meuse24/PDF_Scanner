package info.meuse24.pdf_scanner.data.backup

import info.meuse24.pdf_scanner.domain.backup.BackupArchiveException
import info.meuse24.pdf_scanner.domain.backup.BackupExportData
import info.meuse24.pdf_scanner.domain.backup.BackupExportDocument
import info.meuse24.pdf_scanner.domain.backup.BackupExportFolder
import info.meuse24.pdf_scanner.domain.backup.BackupExportOptions
import info.meuse24.pdf_scanner.domain.backup.BackupFailure
import info.meuse24.pdf_scanner.domain.backup.BackupManifest
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class BackupZipPayloadWriterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val writer = BackupZipPayloadWriter()
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `writes manifest documents thumbnails and ocr without local paths or inline ocr text`() {
        val pdfFile = temporaryFolder.newFile("local-scan.pdf").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val thumbnailFile = temporaryFolder.newFile("local-thumb.jpg").apply { writeBytes(byteArrayOf(9, 8, 7)) }
        val output = temporaryFolder.newFile("payload.zip")

        output.outputStream().use { stream ->
            writer.writePayload(exportData(pdfFile, thumbnailFile), stream)
        }

        ZipFile(output).use { zip ->
            val manifestJson = zip.readTextEntry("manifest.json")
            assertFalse(manifestJson.contains(pdfFile.absolutePath))
            assertFalse(manifestJson.contains(thumbnailFile.absolutePath))
            assertFalse(manifestJson.contains("Secret OCR text"))
            assertFalse(manifestJson.contains("deu"))

            val manifest = json.decodeFromString<BackupManifest>(manifestJson)
            assertEquals(1, manifest.folders.size)
            assertEquals("folder-10", manifest.folders.single().backupFolderId)
            val document = manifest.documents.single()
            assertEquals("document-99", document.backupDocumentId)
            assertEquals("documents/document-99.pdf", document.pdf.path)
            assertEquals("thumbnails/document-99.jpg", document.thumbnail?.path)
            assertEquals("ocr/document-99.json", document.ocr?.path)
            assertEquals(pdfFile.length(), document.pdf.sizeBytes)
            assertEquals(pdfFile.sha256Base64(), document.pdf.sha256Base64)
            assertEquals(thumbnailFile.sha256Base64(), document.thumbnail?.sha256Base64)

            assertEquals(ZipEntry.STORED, zip.getEntry(document.pdf.path).method)
            assertEquals(ZipEntry.STORED, zip.getEntry(document.thumbnail!!.path).method)
            assertArrayEquals(pdfFile.readBytes(), zip.readBytesEntry(document.pdf.path))
            assertArrayEquals(thumbnailFile.readBytes(), zip.readBytesEntry(document.thumbnail!!.path))

            val ocrJson = zip.readTextEntry(document.ocr!!.path)
            assertTrue(ocrJson.contains("Secret OCR text"))
            assertTrue(ocrJson.contains("deu"))
            assertEquals(ocrJson.encodeToByteArray().size.toLong(), document.ocr!!.sizeBytes)
            assertEquals(ocrJson.encodeToByteArray().sha256Base64(), document.ocr!!.sha256Base64)
        }
    }

    @Test
    fun `omits missing optional thumbnail from manifest`() {
        val pdfFile = temporaryFolder.newFile("local-scan.pdf").apply { writeText("pdf") }
        val missingThumbnail = temporaryFolder.root.resolve("missing-thumb.jpg")
        val output = temporaryFolder.newFile("payload.zip")

        output.outputStream().use { stream ->
            writer.writePayload(exportData(pdfFile, missingThumbnail), stream)
        }

        ZipFile(output).use { zip ->
            val manifest = json.decodeFromString<BackupManifest>(zip.readTextEntry("manifest.json"))
            assertNull(manifest.documents.single().thumbnail)
            assertNull(zip.getEntry("thumbnails/document-99.jpg"))
        }
    }

    @Test
    fun `missing pdf fails with typed error`() {
        val missingPdf = temporaryFolder.root.resolve("missing.pdf")
        val output = ByteArrayOutputStream()

        val error = try {
            writer.writePayload(exportData(missingPdf, thumbnailFile = null), output)
            throw AssertionError("Expected BackupArchiveException")
        } catch (e: BackupArchiveException) {
            e
        }

        assertEquals(BackupFailure.MissingDocumentFile, error.failure)
    }

    private fun exportData(
        pdfFile: File,
        thumbnailFile: File?
    ): BackupExportData =
        BackupExportData(
            sourceDatabaseVersion = 9,
            exportedAtEpochMillis = 1234L,
            appVersionName = "2.2",
            appVersionCode = 8,
            options = BackupExportOptions(),
            folders = listOf(
                BackupExportFolder(
                    localId = 10L,
                    backupFolderId = "folder-10",
                    name = "Receipts",
                    colorArgb = 0xff00ff,
                    createdAt = 111L
                )
            ),
            documents = listOf(
                BackupExportDocument(
                    localId = 99L,
                    backupDocumentId = "document-99",
                    filename = "receipt",
                    timestamp = 222L,
                    pageCount = 1,
                    fileSize = 4L,
                    isSearchable = true,
                    isEncrypted = false,
                    tags = "invoice,tax",
                    folderBackupId = "folder-10",
                    isFavorite = true,
                    extractedText = "Secret OCR text",
                    ocrConfidence = 0.92f,
                    ocrLanguage = "deu",
                    ocrPageTextJson = """["Secret OCR text"]""",
                    pdfFile = pdfFile,
                    thumbnailFile = thumbnailFile
                )
            )
        )

    private fun ZipFile.readTextEntry(path: String): String =
        getInputStream(checkNotNull(getEntry(path)) { "Missing ZIP entry $path" }).use { input ->
            input.readBytes().decodeToString()
        }

    private fun ZipFile.readBytesEntry(path: String): ByteArray =
        getInputStream(checkNotNull(getEntry(path)) { "Missing ZIP entry $path" }).use { input ->
            input.readBytes()
        }

    @OptIn(ExperimentalEncodingApi::class)
    private fun File.sha256Base64(): String = readBytes().sha256Base64()

    @OptIn(ExperimentalEncodingApi::class)
    private fun ByteArray.sha256Base64(): String =
        Base64.encode(MessageDigest.getInstance("SHA-256").digest(this))
}
