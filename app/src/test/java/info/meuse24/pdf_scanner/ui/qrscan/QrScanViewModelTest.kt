package info.meuse24.pdf_scanner.ui.qrscan

import androidx.lifecycle.SavedStateHandle
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.data.local.ScanDao
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.QrCodeResult
import info.meuse24.pdf_scanner.domain.usecase.ScanQrCodesUseCase
import info.meuse24.pdf_scanner.testutil.FakeResourceProvider
import info.meuse24.pdf_scanner.testutil.TestDispatcherProvider
import info.meuse24.pdf_scanner.util.OcrModelInstaller
import info.meuse24.pdf_scanner.util.OcrPipelineStatus
import info.meuse24.pdf_scanner.util.QrCodeScanner
import org.mockito.Mockito.mock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class QrScanViewModelTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `zweiter startScan-Aufruf waehrend laufendem Scan wird ignoriert`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        val scanner = BlockingQrCodeScanner(
            onScan = { gate.await() },
            results = emptyList()
        )
        val viewModel = buildViewModel(scanner)

        runCurrent()
        assertTrue(viewModel.scanning.value)

        viewModel.startScan()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, scanner.invocationCount)
    }

    @Test
    fun `erfolgreicher Scan fuellt Ergebnisse und beendet scanning`() = runTest(testDispatcher) {
        val expected = listOf(
            QrCodeResult(
                rawValue = "WIFI:S:Office;",
                valueType = 7,
                displayUrl = null,
                wifiSsid = "Office",
                wifiPassword = "secret",
                pageNumber = 1
            )
        )
        val scanner = BlockingQrCodeScanner(results = expected)
        val viewModel = buildViewModel(scanner)

        advanceUntilIdle()

        assertEquals(expected, viewModel.results.value)
        assertFalse(viewModel.scanning.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `fehlerhafter Scan setzt error und beendet scanning`() = runTest(testDispatcher) {
        val scanner = BlockingQrCodeScanner(exception = IllegalStateException("boom"))
        val viewModel = buildViewModel(scanner)

        advanceUntilIdle()

        assertEquals("boom", viewModel.error.value)
        assertFalse(viewModel.scanning.value)
        assertTrue(viewModel.results.value.isEmpty())
    }

    @Test
    fun `clearError setzt error auf null`() = runTest(testDispatcher) {
        val scanner = BlockingQrCodeScanner(exception = IllegalStateException("boom"))
        val viewModel = buildViewModel(scanner)
        advanceUntilIdle()

        viewModel.clearError()

        assertNull(viewModel.error.value)
    }

    @Test
    fun `verschluesseltes PDF setzt Fehler und startet keinen Scan`() = runTest(testDispatcher) {
        val scanner = BlockingQrCodeScanner(results = emptyList())
        val viewModel = buildViewModel(
            scanner = scanner,
            record = encryptedRecord(tmpFolder.newFile("encrypted.pdf"))
        )

        advanceUntilIdle()

        assertEquals("Cannot scan encrypted PDF", viewModel.error.value)
        assertFalse(viewModel.scanning.value)
        assertTrue(viewModel.results.value.isEmpty())
        assertEquals(0, scanner.invocationCount)
    }

    private fun buildViewModel(
        scanner: QrCodeScanner,
        record: ScanRecord = record(tmpFolder.newFile("scan.pdf").apply { writeText("pdf") })
    ): QrScanViewModel {
        val dao = SingleRecordScanDao(record)
        val repository = ScanRepository(dao)
        val useCase = ScanQrCodesUseCase(scanner)

        return QrScanViewModel(
            repository = repository,
            scanQrCodesUseCase = useCase,
            resourceProvider = FakeResourceProvider(
                strings = mapOf(R.string.qr_scan_error_encrypted to "Cannot scan encrypted PDF")
            ),
            dispatcherProvider = TestDispatcherProvider(testDispatcher),
            savedStateHandle = SavedStateHandle(mapOf("scanId" to 1L))
        )
    }

    private fun record(file: File) = ScanRecord(
        id = 1L,
        filename = file.nameWithoutExtension,
        filepath = file.absolutePath,
        timestamp = 0L,
        pageCount = 1,
        fileSize = file.length()
    )

    private fun encryptedRecord(file: File) = record(file).copy(isEncrypted = true)
}

private class BlockingQrCodeScanner(
    private val onScan: suspend () -> Unit = {},
    private val results: List<QrCodeResult> = emptyList(),
    private val exception: Exception? = null
) : QrCodeScanner(mock(OcrModelInstaller::class.java)) {
    var invocationCount: Int = 0

    override suspend fun scan(
        pdfFile: File,
        onProgress: (page: Int, total: Int) -> Unit,
        onStatus: (OcrPipelineStatus) -> Unit
    ): List<QrCodeResult> {
        invocationCount += 1
        onProgress(1, 1)
        onScan()
        exception?.let { throw it }
        return results
    }
}

private class SingleRecordScanDao(
    private val record: ScanRecord
) : ScanDao {
    override fun getAllScans(): Flow<List<ScanRecord>> = flowOf(listOf(record))
    override fun searchScansFlow(query: String): Flow<List<ScanRecord>> = flowOf(emptyList())
    override suspend fun insert(record: ScanRecord): Long = record.id
    override suspend fun insertAll(records: List<ScanRecord>) = Unit
    override suspend fun delete(record: ScanRecord) = Unit
    override suspend fun markSearchable(id: Long, fileSize: Long) = Unit
    override suspend fun markSearchableWithContent(id: Long, fileSize: Long, text: String?, tags: String?) = Unit
    override suspend fun updateFileSize(id: Long, fileSize: Long) = Unit
    override suspend fun updatePageMetrics(id: Long, pageCount: Int, fileSize: Long) = Unit
    override suspend fun updateFilenameAndPath(id: Long, filename: String, filepath: String, thumbnailPath: String?) = Unit
}
