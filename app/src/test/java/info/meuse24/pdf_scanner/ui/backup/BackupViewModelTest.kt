package info.meuse24.pdf_scanner.ui.backup

import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.backup.BackupArchiveHeader
import info.meuse24.pdf_scanner.domain.backup.BackupDocumentEntry
import info.meuse24.pdf_scanner.domain.backup.BackupExportData
import info.meuse24.pdf_scanner.domain.backup.BackupExportOptions
import info.meuse24.pdf_scanner.domain.backup.BackupFileEntry
import info.meuse24.pdf_scanner.domain.backup.BackupKdfParameters
import info.meuse24.pdf_scanner.domain.backup.BackupKeyWrapInfo
import info.meuse24.pdf_scanner.domain.backup.BackupManifest
import info.meuse24.pdf_scanner.domain.backup.BackupProgress
import info.meuse24.pdf_scanner.domain.backup.BackupRestoreSummary
import info.meuse24.pdf_scanner.domain.backup.BackupStagingResult
import info.meuse24.pdf_scanner.domain.gateway.BackupArchiveCodec
import info.meuse24.pdf_scanner.domain.gateway.BackupDataSource
import info.meuse24.pdf_scanner.domain.gateway.BackupExportTarget
import info.meuse24.pdf_scanner.domain.gateway.BackupImportSource
import info.meuse24.pdf_scanner.domain.gateway.BackupLocationFactory
import info.meuse24.pdf_scanner.domain.gateway.BackupManifestReader
import info.meuse24.pdf_scanner.domain.gateway.BackupPayloadStagingReader
import info.meuse24.pdf_scanner.domain.gateway.BackupPayloadWriter
import info.meuse24.pdf_scanner.domain.gateway.BackupRestoreWriter
import info.meuse24.pdf_scanner.domain.usecase.ExportBackupUseCase
import info.meuse24.pdf_scanner.domain.usecase.RestoreBackupUseCase
import info.meuse24.pdf_scanner.testutil.FakeResourceProvider
import info.meuse24.pdf_scanner.testutil.TestDispatcherProvider
import info.meuse24.pdf_scanner.testutil.TestStorageProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun viewModel(
        locationFactory: BackupLocationFactory = FakeLocationFactory(),
        restoreWriter: FakeRestoreWriter = FakeRestoreWriter()
    ): BackupViewModel {
        val dispatchers = TestDispatcherProvider(dispatcher)
        val manifest = manifest(documentCount = 2)
        return BackupViewModel(
            exportBackup = ExportBackupUseCase(
                dataSource = FakeDataSource(),
                payloadWriter = NoopPayloadWriter(),
                codec = FakeCodec(manifest),
                manifestReader = FakeManifestReader(manifest),
                locationFactory = locationFactory,
                dispatchers = dispatchers
            ),
            restoreBackup = RestoreBackupUseCase(
                locationFactory = locationFactory,
                codec = FakeCodec(manifest),
                stagingReader = FakeStagingReader(manifest, tmpFolder.root),
                restoreWriter = restoreWriter,
                storageProvider = TestStorageProvider(tmpFolder.root),
                dispatchers = dispatchers
            ),
            resourceProvider = FakeResourceProvider()
        )
    }

    @Test
    fun `export flow walks options then passphrase then asks for a location`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onCreateBackupClicked()
        assertTrue(vm.uiState.value.dialog is BackupDialog.ExportOptions)

        vm.onExportOptionsConfirmed(BackupExportOptions())
        assertTrue(vm.uiState.value.dialog is BackupDialog.ExportPassphrase)

        vm.onExportPassphraseConfirmed("longenough", "longenough")
        assertNull(vm.uiState.value.dialog)
        assertNotNull(vm.uiState.value.pendingExportFilename)
    }

    @Test
    fun `short passphrase keeps the dialog with an error`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onCreateBackupClicked()
        vm.onExportOptionsConfirmed(BackupExportOptions())

        vm.onExportPassphraseConfirmed("short", "short")

        val dialog = vm.uiState.value.dialog
        assertTrue(dialog is BackupDialog.ExportPassphrase)
        assertEquals(R.string.backup_error_passphrase_too_short, (dialog as BackupDialog.ExportPassphrase).errorRes)
    }

    @Test
    fun `mismatched passphrase keeps the dialog with an error`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onCreateBackupClicked()
        vm.onExportOptionsConfirmed(BackupExportOptions())

        vm.onExportPassphraseConfirmed("longenough", "different")

        val dialog = vm.uiState.value.dialog as BackupDialog.ExportPassphrase
        assertEquals(R.string.backup_error_passphrase_mismatch, dialog.errorRes)
    }

    @Test
    fun `choosing a location runs the export and reports success`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onCreateBackupClicked()
        vm.onExportOptionsConfirmed(BackupExportOptions())
        vm.onExportPassphraseConfirmed("longenough", "longenough")
        vm.onExportFilenameConsumed()

        vm.onExportLocationChosen("content://target")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.message is BackupMessage.Success)
        assertNull(vm.uiState.value.operation)
    }

    @Test
    fun `restore flow reaches summary and confirm reports success`() = runTest(dispatcher) {
        val writer = FakeRestoreWriter()
        val vm = viewModel(restoreWriter = writer)

        vm.onRestoreBackupClicked()
        assertTrue(vm.uiState.value.launchOpenDocument)
        vm.onOpenDocumentConsumed()

        vm.onRestoreLocationChosen("content://source")
        assertTrue(vm.uiState.value.dialog is BackupDialog.RestorePassphrase)

        vm.onRestorePassphraseConfirmed("secret")
        advanceUntilIdle()

        val dialog = vm.uiState.value.dialog
        assertTrue(dialog is BackupDialog.RestoreSummary)
        assertEquals(2, (dialog as BackupDialog.RestoreSummary).preview.documentCount)

        vm.onRestoreConfirmed()
        advanceUntilIdle()

        assertTrue(writer.called)
        assertTrue(vm.uiState.value.message is BackupMessage.Success)
    }

    @Test
    fun `empty restore passphrase keeps the dialog with an error`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onRestoreLocationChosen("content://source")

        vm.onRestorePassphraseConfirmed("")

        val dialog = vm.uiState.value.dialog as BackupDialog.RestorePassphrase
        assertEquals(R.string.backup_error_passphrase_empty, dialog.errorRes)
    }

    private fun manifest(documentCount: Int) = BackupManifest(
        sourceDatabaseVersion = 9,
        exportedAtEpochMillis = 1L,
        options = BackupExportOptions(),
        folders = emptyList(),
        documents = (1..documentCount).map { index ->
            BackupDocumentEntry(
                backupDocumentId = "document-$index",
                filename = "scan-$index",
                timestamp = 1L,
                pageCount = 1,
                fileSize = 10L,
                isSearchable = false,
                isEncrypted = false,
                pdf = BackupFileEntry("documents/document-$index.pdf", 10L, "")
            )
        }
    )
}

private class FakeDataSource : BackupDataSource {
    override suspend fun loadExportData(options: BackupExportOptions): BackupExportData =
        BackupExportData(
            sourceDatabaseVersion = 9,
            exportedAtEpochMillis = 1L,
            options = options,
            folders = emptyList(),
            documents = emptyList()
        )
}

private class NoopPayloadWriter : BackupPayloadWriter {
    override fun writePayload(exportData: BackupExportData, outputStream: OutputStream) = Unit
}

private class FakeManifestReader(private val manifest: BackupManifest) : BackupManifestReader {
    override fun readManifest(payload: InputStream): BackupManifest = manifest
}

private class FakeCodec(private val manifest: BackupManifest) : BackupArchiveCodec {
    override fun writeBackup(
        outputStream: OutputStream,
        passphrase: CharArray,
        progressCallback: (BackupProgress) -> Unit,
        payloadWriter: (OutputStream) -> Unit
    ): BackupArchiveHeader {
        payloadWriter(outputStream)
        return header()
    }

    override fun readBackup(
        inputStream: InputStream,
        passphrase: CharArray,
        progressCallback: (BackupProgress) -> Unit,
        payloadReader: (InputStream, BackupArchiveHeader) -> Unit
    ): BackupArchiveHeader {
        payloadReader(ByteArrayInputStream(ByteArray(0)), header())
        return header()
    }

    private fun header() = BackupArchiveHeader(
        createdAtEpochMillis = 0L,
        kdf = BackupKdfParameters(saltBase64 = ""),
        keyWrap = BackupKeyWrapInfo(nonceBase64 = "", encryptedStreamingKeysetBase64 = "")
    )
}

private class FakeStagingReader(
    private val manifest: BackupManifest,
    private val root: File
) : BackupPayloadStagingReader {
    override fun readToStaging(inputStream: InputStream, stagingDir: File): BackupStagingResult {
        stagingDir.mkdirs()
        return BackupStagingResult(manifest = manifest, stagingDir = stagingDir, files = emptyMap())
    }
}

private class FakeRestoreWriter : BackupRestoreWriter {
    var called = false
        private set

    override suspend fun restoreFromStaging(staging: BackupStagingResult): BackupRestoreSummary {
        called = true
        return BackupRestoreSummary(
            documentsRestored = staging.manifest.documents.size,
            foldersCreated = 0,
            foldersReused = 0,
            thumbnailsCopied = 0,
            thumbnailsRegenerated = 0
        )
    }
}

private class FakeLocationFactory : BackupLocationFactory {
    override fun exportTarget(locationToken: String): BackupExportTarget =
        object : BackupExportTarget {
            override val displayName: String = "backup"
            override fun openOutputStream(): OutputStream = ByteArrayOutputStream()
            override fun openInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))
            override fun delete(): Boolean = true
        }

    override fun importSource(locationToken: String): BackupImportSource =
        object : BackupImportSource {
            override val displayName: String = "backup"
            override fun openInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))
        }
}
