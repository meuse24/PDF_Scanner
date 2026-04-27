package info.meuse24.pdf_scanner.ui.imagestopdf

import android.net.Uri
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.mapper.toDomain
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.PdfMarginPreset
import info.meuse24.pdf_scanner.domain.model.PdfPageSetup
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.service.ScanArtifactPersister
import info.meuse24.pdf_scanner.domain.usecase.CreatePdfFromImagesResult
import info.meuse24.pdf_scanner.domain.usecase.CreatePdfFromImagesUseCase
import info.meuse24.pdf_scanner.domain.usecase.ImagePdfBuilder
import info.meuse24.pdf_scanner.domain.usecase.ImagePdfOptions
import info.meuse24.pdf_scanner.domain.usecase.ImagePageLayout
import info.meuse24.pdf_scanner.testutil.FakeSettingsRepository
import info.meuse24.pdf_scanner.testutil.TestDispatcherProvider
import info.meuse24.pdf_scanner.testutil.TestStorageProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ImagesToPdfViewModelTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    @Before fun setUp()    { Dispatchers.setMain(testDispatcher) }
    @After  fun tearDown() { Dispatchers.resetMain() }

    private fun fakeUri(): Uri = mock(Uri::class.java)

    private fun buildVm(
        useCase: CreatePdfFromImagesUseCase,
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository()
    ): ImagesToPdfViewModel =
        ImagesToPdfViewModel(
            createPdfFromImagesUseCase = useCase,
            storageProvider = TestStorageProvider(tmpFolder.root),
            dispatcherProvider = TestDispatcherProvider(testDispatcher),
            settingsRepository = settingsRepository
        )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `editLoading ist true direkt nach createPdf-Aufruf`() = runTest(testDispatcher) {
        val vm = buildVm(StubSuccessUseCase())
        vm.createPdf(listOf(fakeUri()), "test", ImagePageLayout.SINGLE)

        assertTrue(vm.editLoading.value)
    }

    @Test
    fun `success nach erfolgreichem createPdf`() = runTest(testDispatcher) {
        val vm = buildVm(StubSuccessUseCase(skipped = 0))
        vm.createPdf(listOf(fakeUri()), "test", ImagePageLayout.SINGLE)
        advanceUntilIdle()

        assertTrue(vm.success.value)
        assertFalse(vm.editLoading.value)
        assertNull(vm.error.value)
        assertEquals(0, vm.skippedCount.value)
    }

    @Test
    fun `skippedCount wird korrekt weitergeleitet`() = runTest(testDispatcher) {
        val vm = buildVm(StubSuccessUseCase(skipped = 3))
        vm.createPdf(listOf(fakeUri()), "test", ImagePageLayout.SINGLE)
        advanceUntilIdle()

        assertEquals(3, vm.skippedCount.value)
        assertTrue(vm.success.value)
    }

    @Test
    fun `error bei fehlgeschlagenem createPdf`() = runTest(testDispatcher) {
        val vm = buildVm(StubFailUseCase("disk full"))
        vm.createPdf(listOf(fakeUri()), "test", ImagePageLayout.SINGLE)
        advanceUntilIdle()

        assertNotNull(vm.error.value)
        assertFalse(vm.success.value)
        assertFalse(vm.editLoading.value)
    }

    @Test
    fun `zweiter Aufruf waehrend editLoading wird ignoriert`() = runTest(testDispatcher) {
        val useCase = StubSuccessUseCase()
        val vm = buildVm(useCase)

        vm.createPdf(listOf(fakeUri()), "test", ImagePageLayout.SINGLE)
        vm.createPdf(listOf(fakeUri()), "test", ImagePageLayout.SINGLE) // ignoriert
        advanceUntilIdle()

        assertTrue(vm.success.value)
        assertEquals(1, useCase.callCount)
    }

    @Test
    fun `clearError loescht Fehlerzustand`() = runTest(testDispatcher) {
        val vm = buildVm(StubFailUseCase("err"))
        vm.createPdf(listOf(fakeUri()), "test", ImagePageLayout.SINGLE)
        advanceUntilIdle()
        assertNotNull(vm.error.value)

        vm.clearError()

        assertNull(vm.error.value)
    }

    @Test
    fun `updatePageSetup aktualisiert State und Repository`() = runTest(testDispatcher) {
        val repository = FakeSettingsRepository()
        val vm = buildVm(StubSuccessUseCase(), repository)
        val setup = PdfPageSetup(marginPreset = PdfMarginPreset.LARGE)

        vm.updatePageSetup(setup)

        assertEquals(setup, vm.pageSetup.value)
        assertEquals(setup, repository.settings.value.defaultImagePdfPageSetup)
    }

    @Test
    fun `createPdf reicht Layout und PageSetup als Optionen weiter`() = runTest(testDispatcher) {
        val setup = PdfPageSetup(marginPreset = PdfMarginPreset.SMALL)
        val useCase = StubSuccessUseCase()
        val vm = buildVm(useCase)
        vm.updatePageSetup(setup)

        vm.createPdf(listOf(fakeUri()), "test", ImagePageLayout.FOUR_PER_PAGE)
        advanceUntilIdle()

        assertEquals(ImagePageLayout.FOUR_PER_PAGE, useCase.lastOptions?.layout)
        assertEquals(setup, useCase.lastOptions?.pageSetup)
    }
}

// ── Stub-UseCases ─────────────────────────────────────────────────────────────

private class StubSuccessUseCase(private val skipped: Int = 0) : CreatePdfFromImagesUseCase(
    imagePdfBuilder = mock(ImagePdfBuilder::class.java),
    persister = ScanArtifactPersister(
        mock(info.meuse24.pdf_scanner.util.PdfEditor::class.java),
        ScanRepository(StubScanDao())
    )
) {
    var callCount = 0
    var lastOptions: ImagePdfOptions? = null
    override suspend fun invoke(
        imageUris: List<Uri>,
        filename: String,
        options: ImagePdfOptions,
        scansDir: File
    ): CreatePdfFromImagesResult {
        callCount++
        lastOptions = options
        return CreatePdfFromImagesResult("result", skipped)
    }
}

private class StubFailUseCase(private val message: String) : CreatePdfFromImagesUseCase(
    imagePdfBuilder = mock(ImagePdfBuilder::class.java),
    persister = ScanArtifactPersister(
        mock(info.meuse24.pdf_scanner.util.PdfEditor::class.java),
        ScanRepository(StubScanDao())
    )
) {
    override suspend fun invoke(
        imageUris: List<Uri>,
        filename: String,
        options: ImagePdfOptions,
        scansDir: File
    ): CreatePdfFromImagesResult = throw IOException(message)
}

private class StubScanDao : info.meuse24.pdf_scanner.data.local.ScanDao {
    override fun getAllScans(): Flow<List<ScanRecord>> = flowOf(emptyList())
    override suspend fun insert(record: ScanRecord) = 1L
    override suspend fun insertAll(records: List<ScanRecord>) {}
    override suspend fun delete(record: ScanRecord) {}
    override fun searchScansFlow(query: String): Flow<List<ScanRecord>> = flowOf(emptyList())
    override suspend fun markSearchableWithContent(
        id: Long,
        fileSize: Long,
        text: String?,
        tags: String?,
        confidence: Float?,
        language: String?,
        pageTextJson: String?
    ) {}
    override suspend fun updateExtractedTextAndOcrStats(
        id: Long,
        text: String?,
        confidence: Float?,
        language: String?,
        pageTextJson: String?
    ) {}
    override suspend fun markSearchable(id: Long, fileSize: Long) {}
    override suspend fun updateFileSize(id: Long, fileSize: Long) {}
    override suspend fun updatePageMetrics(id: Long, pageCount: Int, fileSize: Long) {}
    override suspend fun invalidateAfterAppend(id: Long, fileSize: Long, pageCount: Int) {}
    override suspend fun updateFilenameAndPath(id: Long, filename: String, filepath: String, thumbnailPath: String?) {}
    override fun getScansInFolder(folderId: Long): Flow<List<ScanRecord>> = flowOf(emptyList())
    override fun getFavoriteScans(): Flow<List<ScanRecord>> = flowOf(emptyList())
    override suspend fun moveScans(ids: List<Long>, folderId: Long?) {}
    override suspend fun setFavorite(ids: List<Long>, favorite: Boolean) {}
    override fun getScansWithTag(tagKey: String): kotlinx.coroutines.flow.Flow<List<info.meuse24.pdf_scanner.data.local.ScanRecord>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override suspend fun getAllSearchableWithoutTags(): List<info.meuse24.pdf_scanner.data.local.ScanRecord> = emptyList()
    override suspend fun updateTags(id: Long, tags: String) = Unit
}

