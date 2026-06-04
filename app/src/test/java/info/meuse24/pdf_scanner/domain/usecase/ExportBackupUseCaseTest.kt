package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.data.backup.BackupManifestZipReader
import info.meuse24.pdf_scanner.data.backup.BackupZipPayloadWriter
import info.meuse24.pdf_scanner.data.backup.TinkBackupArchiveCodec
import info.meuse24.pdf_scanner.domain.backup.BackupExportData
import info.meuse24.pdf_scanner.domain.backup.BackupExportDocument
import info.meuse24.pdf_scanner.domain.backup.BackupExportOptions
import info.meuse24.pdf_scanner.domain.backup.BackupFailure
import info.meuse24.pdf_scanner.domain.backup.BackupKdfParameters
import info.meuse24.pdf_scanner.domain.backup.BackupResult
import info.meuse24.pdf_scanner.domain.gateway.BackupDataSource
import info.meuse24.pdf_scanner.domain.gateway.BackupExportTarget
import info.meuse24.pdf_scanner.domain.gateway.BackupImportSource
import info.meuse24.pdf_scanner.domain.gateway.BackupKeyDeriver
import info.meuse24.pdf_scanner.domain.gateway.BackupLocationFactory
import info.meuse24.pdf_scanner.testutil.TestDispatcherProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
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
class ExportBackupUseCaseTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val codec = TinkBackupArchiveCodec(DeterministicKeyDeriver())
    private val payloadWriter = BackupZipPayloadWriter()
    private val manifestReader = BackupManifestZipReader()

    private fun useCase(dataSource: BackupDataSource, locationFactory: BackupLocationFactory) =
        ExportBackupUseCase(
            dataSource = dataSource,
            payloadWriter = payloadWriter,
            codec = codec,
            manifestReader = manifestReader,
            locationFactory = locationFactory,
            dispatchers = TestDispatcherProvider(dispatcher)
        )

    @Test
    fun `export writes encrypted archive and probe confirms manifest`() = runTest(dispatcher) {
        val pdf = tmpFolder.newFile("scan.pdf").apply { writeBytes(ByteArray(64) { it.toByte() }) }
        val factory = InMemoryLocationFactory()

        val result = useCase(FakeDataSource(listOf(document("document-1", "scan", pdf))), factory)
            .invoke("token", "correct horse battery".toCharArray(), BackupExportOptions())

        assertTrue(result is BackupResult.Success)
        assertEquals(1, (result as BackupResult.Success).manifest?.documents?.size)
        assertFalse(factory.target.deleted)
        assertTrue(factory.target.bytes().startsWith("M24PDFBACKUP".encodeToByteArray()))
    }

    @Test
    fun `missing pdf fails and compensates the target file`() = runTest(dispatcher) {
        val missing = File(tmpFolder.root, "missing.pdf")
        val factory = InMemoryLocationFactory()

        val result = useCase(FakeDataSource(listOf(document("document-1", "scan", missing))), factory)
            .invoke("token", "correct horse battery".toCharArray(), BackupExportOptions())

        assertTrue(result is BackupResult.Failure)
        assertEquals(BackupFailure.MissingDocumentFile, (result as BackupResult.Failure).reason)
        assertFalse(result.leftoverFileMayRemain)
        assertTrue(factory.target.deleted)
    }

    private fun document(id: String, filename: String, pdf: File) = BackupExportDocument(
        localId = 1L,
        backupDocumentId = id,
        filename = filename,
        timestamp = 1L,
        pageCount = 1,
        fileSize = pdf.length(),
        isSearchable = false,
        isEncrypted = false,
        pdfFile = pdf
    )

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && copyOf(prefix.size).contentEquals(prefix)
}

private class FakeDataSource(private val documents: List<BackupExportDocument>) : BackupDataSource {
    override suspend fun loadExportData(options: BackupExportOptions): BackupExportData =
        BackupExportData(
            sourceDatabaseVersion = 9,
            exportedAtEpochMillis = 1L,
            options = options,
            folders = emptyList(),
            documents = documents
        )
}

private class InMemoryLocationFactory : BackupLocationFactory {
    val target = InMemoryExportTarget()

    override fun exportTarget(locationToken: String): BackupExportTarget = target

    override fun importSource(locationToken: String): BackupImportSource =
        object : BackupImportSource {
            override val displayName: String = "backup"
            override fun openInputStream(): InputStream = ByteArrayInputStream(target.bytes())
        }
}

private class InMemoryExportTarget : BackupExportTarget {
    private var buffer = ByteArrayOutputStream()
    var deleted = false
        private set

    override val displayName: String = "backup"

    override fun openOutputStream(): OutputStream {
        buffer = ByteArrayOutputStream()
        return buffer
    }

    override fun openInputStream(): InputStream = ByteArrayInputStream(buffer.toByteArray())

    override fun delete(): Boolean {
        deleted = true
        return true
    }

    fun bytes(): ByteArray = buffer.toByteArray()
}

private class DeterministicKeyDeriver : BackupKeyDeriver {
    override fun deriveKey(passphrase: CharArray, parameters: BackupKdfParameters): ByteArray {
        val seed = (passphrase.concatToString() + parameters.saltBase64).encodeToByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(seed)
        return ByteArray(parameters.outputBytes) { index -> digest[index % digest.size] }
    }
}
