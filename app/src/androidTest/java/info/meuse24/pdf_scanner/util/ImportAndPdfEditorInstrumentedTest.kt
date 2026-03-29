package info.meuse24.pdf_scanner.util

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.text.PDFTextStripper
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.data.local.ScanDao
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.ImportFileUseCase
import info.meuse24.pdf_scanner.domain.usecase.HighlightRect
import info.meuse24.pdf_scanner.domain.usecase.TextComment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ImportAndPdfEditorInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val storageProvider = AndroidStorageProvider(context)
    private val resourceProvider = AndroidResourceProvider(context)
    private val pdfEditor = PdfEditor()
    private val scansDir: File
        get() = storageProvider.scansDir()

    @Before
    fun setUp() {
        cleanupTestArtifacts()
    }

    @After
    fun tearDown() {
        cleanupTestArtifacts()
    }

    @Test
    fun pdfEditorReadsPageCountAndWritesThumbnailOnDevice() {
        val pdfFile = createPdf(
            File(scansDir, "androidtest_render_source.pdf"),
            listOf(
                TestPage(width = 420f, height = 620f),
                TestPage(width = 440f, height = 640f)
            )
        )
        val thumbnailFile = File(scansDir, "androidtest_render_thumb.jpg")

        assertEquals(2, pdfEditor.getPageCount(pdfFile))
        assertTrue(pdfEditor.generateThumbnail(pdfFile, thumbnailFile))
        assertTrue(thumbnailFile.exists())
        assertTrue(thumbnailFile.length() > 0L)

        val bitmap = pdfEditor.renderPageThumbnail(pdfFile, pageIndex = 1, maxSizePx = 96)
        assertNotNull(bitmap)
        bitmap!!.useBitmap {
            assertTrue(it.width in 1..96)
            assertTrue(it.height in 1..96)
        }
    }

    @Test
    fun importFileUseCaseImportsContentUriAndStoresThumbnail() = runBlocking {
        val sourceFile = createPdf(
            File(scansDir, "androidtest_import_source.pdf"),
            listOf(
                TestPage(width = 401f, height = 601f),
                TestPage(width = 402f, height = 602f)
            )
        )
        val useCase = createImportFileUseCase()

        val record = useCase(fileProviderUri(sourceFile), "androidtest_imported")

        assertEquals(1, fakeDao.inserted.size)
        assertEquals("androidtest_imported", record.filename)
        assertEquals(2, record.pageCount)
        assertFalse(record.isEncrypted)
        assertTrue(File(record.filepath).exists())
        assertTrue(record.thumbnailPath != null)
        assertTrue(File(record.thumbnailPath!!).exists())
    }

    @Test
    fun importFileUseCaseDeletesCopiedFileForInvalidPdf() = runBlocking {
        val sourceFile = File(scansDir, "androidtest_invalid_source.pdf").apply {
            writeText("this is not a valid pdf")
        }
        val useCase = createImportFileUseCase()

        val error = runCatching {
            useCase(fileProviderUri(sourceFile), "androidtest_invalid_import")
        }.exceptionOrNull()

        assertEquals(context.getString(R.string.error_pdf_invalid), error?.message)
        assertFalse(File(scansDir, "androidtest_invalid_import.pdf").exists())
        assertFalse(File(scansDir, "androidtest_invalid_import.jpg").exists())
        assertTrue(fakeDao.inserted.isEmpty())
    }

    @Test
    fun importFileUseCaseKeepsEncryptedPdfWithoutThumbnail() = runBlocking {
        val plainSource = createPdf(
            File(scansDir, "androidtest_locked_plain.pdf"),
            listOf(TestPage(width = 430f, height = 630f))
        )
        val protectedSource = pdfEditor.protectPdf(plainSource, scansDir, "secret123")
        val useCase = createImportFileUseCase()

        val record = useCase(fileProviderUri(protectedSource), "androidtest_locked_import")

        assertTrue(record.isEncrypted)
        assertEquals(0, record.pageCount)
        assertNull(record.thumbnailPath)
        assertTrue(File(record.filepath).exists())
        assertEquals(1, fakeDao.inserted.size)
    }

    @Test
    fun applyHighlightOnRotatedPageRendersInExpectedDisplayRegion() {
        val input = createPdf(
            File(scansDir, "androidtest_highlight_source.pdf"),
            listOf(TestPage(width = 500f, height = 700f, rotation = 90, drawMarker = false))
        )
        val rect = HighlightRect(
            left = 0.15f,
            top = 0.20f,
            right = 0.30f,
            bottom = 0.35f,
            pageIndex = 0
        )

        val highlighted = pdfEditor.applyHighlight(input, scansDir, strokes = emptyList(), rects = listOf(rect))
        val bitmap = pdfEditor.renderPageThumbnail(highlighted, pageIndex = 0, maxSizePx = 240)

        assertNotNull(bitmap)
        bitmap!!.useBitmap {
            val target = pixelAtNormalized(it, 0.225f, 0.275f)
            val control = pixelAtNormalized(it, 0.75f, 0.75f)
            assertTrue(isYellowHighlight(target))
            assertTrue(isMostlyWhite(control))
        }
    }

    @Test
    fun applyAnnotationsAddsExtractableCommentTextOnRotatedPage() {
        val input = createPdf(
            File(scansDir, "androidtest_annotate_source.pdf"),
            listOf(TestPage(width = 500f, height = 700f, rotation = 270, drawMarker = false))
        )

        val annotated = pdfEditor.applyAnnotations(
            input = input,
            outputDir = scansDir,
            strokes = emptyList(),
            rects = listOf(
                HighlightRect(
                    left = 0.55f,
                    top = 0.15f,
                    right = 0.75f,
                    bottom = 0.28f,
                    pageIndex = 0
                )
            ),
            comments = listOf(
                TextComment(
                    pageIndex = 0,
                    anchorX = 0.60f,
                    anchorY = 0.42f,
                    text = "Comment 270",
                    fontSizeFraction = 0.045f
                )
            )
        )

        val extractedText = extractPdfText(annotated)
        assertTrue(extractedText.contains("Comment 270"))
    }

    @Test
    fun removeTextLayerRemovesExtractableTextButKeepsPdfRenderable() {
        val input = createPdf(
            File(scansDir, "androidtest_remove_text_source.pdf"),
            listOf(TestPage(width = 520f, height = 720f, drawMarker = false))
        )
        val annotated = pdfEditor.applyAnnotations(
            input = input,
            outputDir = scansDir,
            strokes = emptyList(),
            comments = listOf(
                TextComment(
                    pageIndex = 0,
                    anchorX = 0.20f,
                    anchorY = 0.25f,
                    text = "Layer Test",
                    fontSizeFraction = 0.05f
                )
            )
        )

        assertTrue(extractPdfText(annotated).contains("Layer Test"))

        val withoutTextLayer = pdfEditor.removeTextLayer(annotated, scansDir)

        assertEquals(1, pdfEditor.getPageCount(withoutTextLayer))
        assertTrue(extractPdfText(withoutTextLayer).isBlank())
        val bitmap = pdfEditor.renderPageThumbnail(withoutTextLayer, pageIndex = 0, maxSizePx = 180)
        assertNotNull(bitmap)
        bitmap!!.useBitmap {
            assertTrue(it.width > 0)
            assertTrue(it.height > 0)
        }
    }

    @Test
    fun convertToGrayscaleTurnsColoredHighlightGray() {
        val input = createPdf(
            File(scansDir, "androidtest_grayscale_source.pdf"),
            listOf(TestPage(width = 500f, height = 700f, drawMarker = false))
        )
        val highlighted = pdfEditor.applyHighlight(
            input = input,
            outputDir = scansDir,
            strokes = emptyList(),
            rects = listOf(
                HighlightRect(
                    left = 0.20f,
                    top = 0.20f,
                    right = 0.40f,
                    bottom = 0.40f,
                    pageIndex = 0
                )
            )
        )
        val originalBitmap = pdfEditor.renderPageThumbnail(highlighted, pageIndex = 0, maxSizePx = 220)
        assertNotNull(originalBitmap)
        val originalColor = originalBitmap!!.useBitmap { pixelAtNormalized(it, 0.30f, 0.30f) }
        assertTrue(isYellowHighlight(originalColor))

        val grayscale = pdfEditor.convertToGrayscale(highlighted, scansDir)
        val grayscaleBitmap = pdfEditor.renderPageThumbnail(grayscale, pageIndex = 0, maxSizePx = 220)

        assertNotNull(grayscaleBitmap)
        grayscaleBitmap!!.useBitmap {
            val grayPixel = pixelAtNormalized(it, 0.30f, 0.30f)
            assertTrue(isGray(grayPixel))
            assertTrue(grayPixel[0] < 250)
        }
    }

    private lateinit var fakeDao: InstrumentedFakeScanDao

    private fun createImportFileUseCase(): ImportFileUseCase {
        fakeDao = InstrumentedFakeScanDao()
        return ImportFileUseCase(
            fileUtil = FileUtil(context, storageProvider, resourceProvider),
            pdfEditor = pdfEditor,
            repository = ScanRepository(fakeDao),
            resourceProvider = resourceProvider
        )
    }

    private fun fileProviderUri(file: File) = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    private fun cleanupTestArtifacts() {
        scansDir.listFiles().orEmpty()
            .filter { it.name.startsWith("androidtest_") }
            .forEach { it.deleteRecursively() }
    }
}

private data class TestPage(
    val width: Float,
    val height: Float,
    val rotation: Int = 0,
    val drawMarker: Boolean = true
)

private fun createPdf(file: File, pages: List<TestPage>): File {
    PDDocument().use { document ->
        pages.forEach { spec ->
            val page = PDPage(PDRectangle(spec.width, spec.height)).apply {
                rotation = spec.rotation
            }
            document.addPage(page)
            PDPageContentStream(document, page).use { content ->
                if (spec.drawMarker) {
                    content.addRect(
                        16f,
                        16f,
                        (spec.width / 3f).coerceAtLeast(12f),
                        (spec.height / 4f).coerceAtLeast(12f)
                    )
                    content.stroke()
                }
            }
        }
        document.save(file)
    }
    return file
}

private fun extractPdfText(file: File): String =
    PDDocument.load(file).use { document ->
        PDFTextStripper().getText(document).replace("\\s+".toRegex(), " ").trim()
    }

private fun pixelAtNormalized(bitmap: android.graphics.Bitmap, nx: Float, ny: Float): IntArray {
    val x = ((bitmap.width - 1) * nx).toInt().coerceIn(0, bitmap.width - 1)
    val y = ((bitmap.height - 1) * ny).toInt().coerceIn(0, bitmap.height - 1)
    val color = bitmap.getPixel(x, y)
    return intArrayOf(
        android.graphics.Color.red(color),
        android.graphics.Color.green(color),
        android.graphics.Color.blue(color)
    )
}

private fun isYellowHighlight(rgb: IntArray): Boolean =
    rgb[0] > 230 && rgb[1] > 210 && rgb[2] < 220 && abs(rgb[0] - rgb[1]) > 5

private fun isMostlyWhite(rgb: IntArray): Boolean =
    rgb[0] > 245 && rgb[1] > 245 && rgb[2] > 245

private fun isGray(rgb: IntArray): Boolean =
    abs(rgb[0] - rgb[1]) <= 8 && abs(rgb[1] - rgb[2]) <= 8

private inline fun <T> android.graphics.Bitmap.useBitmap(block: (android.graphics.Bitmap) -> T): T =
    try {
        block(this)
    } finally {
        recycle()
    }

private class InstrumentedFakeScanDao : ScanDao {
    val inserted = mutableListOf<ScanRecord>()

    override fun getAllScans(): Flow<List<ScanRecord>> = flowOf(emptyList())

    override fun searchScansFlow(query: String): Flow<List<ScanRecord>> = flowOf(emptyList())

    override suspend fun insert(record: ScanRecord) {
        inserted.add(record)
    }

    override suspend fun insertAll(records: List<ScanRecord>) {
        inserted.addAll(records)
    }

    override suspend fun delete(record: ScanRecord) = Unit

    override suspend fun markSearchable(id: Long, fileSize: Long) = Unit

    override suspend fun markSearchableWithContent(id: Long, fileSize: Long, text: String?, tags: String?) = Unit

    override suspend fun updateFileSize(id: Long, fileSize: Long) = Unit

    override suspend fun updatePageMetrics(id: Long, pageCount: Int, fileSize: Long) = Unit

    override suspend fun updateFilenameAndPath(id: Long, filename: String, filepath: String, thumbnailPath: String?) = Unit
}
