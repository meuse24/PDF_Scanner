package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.data.backup.BackupZipPayloadWriter
import info.meuse24.pdf_scanner.data.backup.BackupZipStagingReader
import info.meuse24.pdf_scanner.data.backup.TinkBackupArchiveCodec
import info.meuse24.pdf_scanner.domain.backup.BackupExportData
import info.meuse24.pdf_scanner.domain.backup.BackupExportDocument
import info.meuse24.pdf_scanner.domain.backup.BackupExportFolder
import info.meuse24.pdf_scanner.domain.backup.BackupExportOptions
import info.meuse24.pdf_scanner.domain.backup.BackupFailure
import info.meuse24.pdf_scanner.domain.backup.BackupKdfParameters
import info.meuse24.pdf_scanner.domain.backup.BackupRestorePrepareResult
import info.meuse24.pdf_scanner.domain.backup.BackupRestoreResult
import info.meuse24.pdf_scanner.domain.backup.BackupRestoreSummary
import info.meuse24.pdf_scanner.domain.backup.BackupStagingResult
import info.meuse24.pdf_scanner.domain.gateway.BackupImportSource
import info.meuse24.pdf_scanner.domain.gateway.BackupKeyDeriver
import info.meuse24.pdf_scanner.domain.gateway.BackupLocationFactory
import info.meuse24.pdf_scanner.domain.gateway.BackupExportTarget
import info.meuse24.pdf_scanner.domain.gateway.BackupRestoreWriter
import info.meuse24.pdf_scanner.testutil.TestDispatcherProvider
import info.meuse24.pdf_scanner.testutil.TestStorageProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class RestoreBackupUseCaseTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val codec = TinkBackupArchiveCodec(DeterministicRestoreKeyDeriver())
    private val payloadWriter = BackupZipPayloadWriter()
    private val stagingReader = BackupZipStagingReader()
    private val passphrase = "correct horse battery"

    private fun useCase(
        bytes: ByteArray,
        restoreWriter: BackupRestoreWriter
    ): RestoreBackupUseCase = RestoreBackupUseCase(
        locationFactory = bytesLocationFactory(bytes),
        codec = codec,
        stagingReader = stagingReader,
        restoreWriter = restoreWriter,
        storageProvider = TestStorageProvider(tmpFolder.root),
        dispatchers = TestDispatcherProvider(dispatcher)
    )

    @Test
    fun `prepare stages archive and returns a matching preview`() = runTest(dispatcher) {
        val pdf = tmpFolder.newFile("scan.pdf").apply { writeBytes(ByteArray(80) { it.toByte() }) }
        val bytes = buildBackup(documents = listOf(document(pdf)), folders = listOf(folder()))

        val result = useCase(bytes, RecordingRestoreWriter()).prepare("token", passphrase.toCharArray())

        assertTrue(result is BackupRestorePrepareResult.Ready)
        val preview = (result as BackupRestorePrepareResult.Ready).preview
        assertEquals(1, preview.documentCount)
        assertEquals(1, preview.folderCount)
        assertEquals(pdf.length(), preview.totalBytes)
        assertTrue(result.session.stagingDir.isDirectory)
    }

    @Test
    fun `confirm delegates to the restore writer`() = runTest(dispatcher) {
        val pdf = tmpFolder.newFile("scan.pdf").apply { writeBytes(ByteArray(40) { 1 }) }
        val bytes = buildBackup(documents = listOf(document(pdf)), folders = emptyList())
        val writer = RecordingRestoreWriter()
        val sut = useCase(bytes, writer)

        val prepared = sut.prepare("token", passphrase.toCharArray()) as BackupRestorePrepareResult.Ready
        val result = sut.confirm(prepared.session)

        assertTrue(result is BackupRestoreResult.Success)
        assertTrue(writer.called)
    }

    @Test
    fun `wrong passphrase fails prepare`() = runTest(dispatcher) {
        val pdf = tmpFolder.newFile("scan.pdf").apply { writeBytes(ByteArray(40) { 1 }) }
        val bytes = buildBackup(documents = listOf(document(pdf)), folders = emptyList())

        val result = useCase(bytes, RecordingRestoreWriter()).prepare("token", "wrong passphrase".toCharArray())

        assertTrue(result is BackupRestorePrepareResult.Failure)
        assertEquals(BackupFailure.WrongPassphrase, (result as BackupRestorePrepareResult.Failure).reason)
    }

    @Test
    fun `cancel removes the staging directory`() = runTest(dispatcher) {
        val pdf = tmpFolder.newFile("scan.pdf").apply { writeBytes(ByteArray(40) { 1 }) }
        val bytes = buildBackup(documents = listOf(document(pdf)), folders = emptyList())
        val sut = useCase(bytes, RecordingRestoreWriter())

        val prepared = sut.prepare("token", passphrase.toCharArray()) as BackupRestorePrepareResult.Ready
        sut.cancel(prepared.session)

        assertFalse(prepared.session.stagingDir.exists())
    }

    private fun buildBackup(
        documents: List<BackupExportDocument>,
        folders: List<BackupExportFolder>
    ): ByteArray {
        val output = ByteArrayOutputStream()
        codec.writeBackup(output, passphrase.toCharArray()) { payload ->
            payloadWriter.writePayload(
                BackupExportData(
                    sourceDatabaseVersion = 9,
                    exportedAtEpochMillis = 1L,
                    options = BackupExportOptions(),
                    folders = folders,
                    documents = documents
                ),
                payload
            )
        }
        return output.toByteArray()
    }

    private fun document(pdf: File) = BackupExportDocument(
        localId = 1L,
        backupDocumentId = "document-1",
        filename = "scan",
        timestamp = 1L,
        pageCount = 1,
        fileSize = pdf.length(),
        isSearchable = false,
        isEncrypted = false,
        pdfFile = pdf
    )

    private fun folder() = BackupExportFolder(
        localId = 5L,
        backupFolderId = "folder-5",
        name = "Receipts",
        createdAt = 1L
    )

    private fun bytesLocationFactory(bytes: ByteArray) = object : BackupLocationFactory {
        override fun exportTarget(locationToken: String): BackupExportTarget =
            throw UnsupportedOperationException()

        override fun importSource(locationToken: String): BackupImportSource =
            object : BackupImportSource {
                override val displayName: String = "backup"
                override fun openInputStream(): InputStream = ByteArrayInputStream(bytes)
            }
    }
}

private class RecordingRestoreWriter : BackupRestoreWriter {
    var called = false
        private set

    override suspend fun restoreFromStaging(staging: BackupStagingResult): BackupRestoreSummary {
        called = true
        staging.stagingDir.deleteRecursively()
        return BackupRestoreSummary(
            documentsRestored = staging.manifest.documents.size,
            foldersCreated = staging.manifest.folders.size,
            foldersReused = 0,
            thumbnailsCopied = 0,
            thumbnailsRegenerated = 0
        )
    }
}

private class DeterministicRestoreKeyDeriver : BackupKeyDeriver {
    override fun deriveKey(passphrase: CharArray, parameters: BackupKdfParameters): ByteArray {
        val seed = (passphrase.concatToString() + parameters.saltBase64).encodeToByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(seed)
        return ByteArray(parameters.outputBytes) { index -> digest[index % digest.size] }
    }
}
