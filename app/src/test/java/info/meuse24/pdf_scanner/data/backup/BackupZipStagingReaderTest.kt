package info.meuse24.pdf_scanner.data.backup

import info.meuse24.pdf_scanner.domain.backup.BackupArchiveException
import info.meuse24.pdf_scanner.domain.backup.BackupExportData
import info.meuse24.pdf_scanner.domain.backup.BackupExportDocument
import info.meuse24.pdf_scanner.domain.backup.BackupExportFolder
import info.meuse24.pdf_scanner.domain.backup.BackupExportOptions
import info.meuse24.pdf_scanner.domain.backup.BackupFailure
import info.meuse24.pdf_scanner.domain.backup.BackupManifest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupZipStagingReaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val writer = BackupZipPayloadWriter()
    private val reader = BackupZipStagingReader()
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun `valid payload stages manifest referenced files`() {
        val pdf = temporaryFolder.newFile("scan.pdf").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val thumbnail = temporaryFolder.newFile("scan.jpg").apply { writeBytes(byteArrayOf(4, 5)) }
        val payload = writeValidPayload(pdf, thumbnail)
        val stagingDir = temporaryFolder.root.resolve("staging")

        val result = reader.readToStaging(ByteArrayInputStream(payload), stagingDir)

        val document = result.manifest.documents.single()
        assertTrue(stagingDir.isDirectory)
        assertEquals(setOf(document.pdf.path, document.thumbnail!!.path, document.ocr!!.path), result.files.keys)
        assertArrayEquals(pdf.readBytes(), checkNotNull(result.files[document.pdf.path]).readBytes())
        assertArrayEquals(thumbnail.readBytes(), checkNotNull(result.files[document.thumbnail!!.path]).readBytes())
        assertTrue(checkNotNull(result.files[document.ocr!!.path]).readText().contains("Secret OCR text"))
    }

    @Test
    fun `path traversal entry is rejected and staging is removed`() {
        val payload = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("../escape.pdf"))
                zip.write(byteArrayOf(1))
                zip.closeEntry()
            }
            output.toByteArray()
        }
        val stagingDir = temporaryFolder.root.resolve("staging")

        val error = assertBackupArchiveException {
            reader.readToStaging(ByteArrayInputStream(payload), stagingDir)
        }

        assertEquals(BackupFailure.InvalidManifest, error.failure)
        assertFalse(stagingDir.exists())
    }

    @Test
    fun `checksum mismatch is rejected and staging is removed`() {
        val pdf = temporaryFolder.newFile("scan.pdf").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val payload = writeValidPayload(pdf, thumbnail = null).replaceEntryBytes(
            path = "documents/document-99.pdf",
            replacement = byteArrayOf(9, 2, 3)
        )
        val stagingDir = temporaryFolder.root.resolve("staging")

        val error = assertBackupArchiveException {
            reader.readToStaging(ByteArrayInputStream(payload), stagingDir)
        }

        assertEquals(BackupFailure.InvalidManifest, error.failure)
        assertFalse(stagingDir.exists())
    }

    @Test
    fun `unreferenced file is rejected`() {
        val manifest = BackupManifest(
            sourceDatabaseVersion = 9,
            exportedAtEpochMillis = 1L
        )
        val payload = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.writeJsonEntry("manifest.json", json.encodeToString(manifest).encodeToByteArray())
                zip.writeBytesEntry("documents/orphan.pdf", byteArrayOf(1, 2, 3))
            }
            output.toByteArray()
        }

        val error = assertBackupArchiveException {
            reader.readToStaging(ByteArrayInputStream(payload), temporaryFolder.root.resolve("staging"))
        }

        assertEquals(BackupFailure.InvalidManifest, error.failure)
    }

    @Test
    fun `staging write io error is reported as io failure`() {
        val manifest = BackupManifest(
            sourceDatabaseVersion = 9,
            exportedAtEpochMillis = 1L
        )
        val payload = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.writeJsonEntry("manifest.json", json.encodeToString(manifest).encodeToByteArray())
                zip.writeBytesEntry("documents/collision.pdf", byteArrayOf(1))
                zip.writeBytesEntry("documents/collision.pdf/nested.pdf", byteArrayOf(2))
            }
            output.toByteArray()
        }
        val stagingDir = temporaryFolder.root.resolve("staging")

        val error = assertBackupArchiveException {
            reader.readToStaging(ByteArrayInputStream(payload), stagingDir)
        }

        assertTrue(error.failure is BackupFailure.Io)
        assertFalse(stagingDir.exists())
    }

    @Test
    fun `insufficient staging space is reported and staging is removed`() {
        val pdf = temporaryFolder.newFile("scan.pdf").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val payload = writeValidPayload(pdf, thumbnail = null)
        val stagingDir = temporaryFolder.root.resolve("staging")
        val lowSpaceReader = BackupZipStagingReader().apply {
            usableSpace = { 0L }
        }

        val error = assertBackupArchiveException {
            lowSpaceReader.readToStaging(ByteArrayInputStream(payload), stagingDir)
        }

        assertTrue(error.failure is BackupFailure.InsufficientStorage)
        assertFalse(stagingDir.exists())
    }

    @Test
    fun `future database version is rejected as future version`() {
        val manifest = BackupManifest(
            sourceDatabaseVersion = 10,
            exportedAtEpochMillis = 1L
        )
        val payload = ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.writeJsonEntry("manifest.json", json.encodeToString(manifest).encodeToByteArray())
            }
            output.toByteArray()
        }

        val error = assertBackupArchiveException {
            reader.readToStaging(ByteArrayInputStream(payload), temporaryFolder.root.resolve("staging"))
        }

        assertEquals(BackupFailure.UnsupportedFutureVersion, error.failure)
    }

    private fun writeValidPayload(pdf: File, thumbnail: File?): ByteArray =
        ByteArrayOutputStream().use { output ->
            writer.writePayload(exportData(pdf, thumbnail), output)
            output.toByteArray()
        }

    private fun exportData(pdfFile: File, thumbnailFile: File?): BackupExportData =
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
                    fileSize = pdfFile.length(),
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

    private fun ByteArray.replaceEntryBytes(path: String, replacement: ByteArray): ByteArray {
        val sourceZip = temporaryFolder.newFile("source.zip").apply { writeBytes(this@replaceEntryBytes) }
        return ByteArrayOutputStream().use { output ->
            ZipFile(sourceZip).use { source ->
                ZipOutputStream(output).use { target ->
                    source.entries().asSequence().forEach { entry ->
                        val bytes = if (entry.name == path) {
                            replacement
                        } else {
                            source.getInputStream(entry).use { it.readBytes() }
                        }
                        target.putNextEntry(ZipEntry(entry.name))
                        target.write(bytes)
                        target.closeEntry()
                    }
                }
            }
            output.toByteArray()
        }
    }

    private fun ZipOutputStream.writeJsonEntry(path: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(path))
        write(bytes)
        closeEntry()
    }

    private fun ZipOutputStream.writeBytesEntry(path: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(path))
        write(bytes)
        closeEntry()
    }

    private fun assertBackupArchiveException(block: () -> Unit): BackupArchiveException =
        try {
            block()
            throw AssertionError("Expected BackupArchiveException")
        } catch (exception: BackupArchiveException) {
            exception
        }
}
