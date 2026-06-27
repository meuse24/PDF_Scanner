package info.meuse24.pdf_scanner

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDMetadata
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.text.PDFTextStripper
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.data.local.ScanDao
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.mapper.toDomain
import info.meuse24.pdf_scanner.data.mapper.toEntity
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.AnnotationOval
import info.meuse24.pdf_scanner.domain.usecase.AnnotationRect
import info.meuse24.pdf_scanner.domain.usecase.AnnotationShapeStyle
import info.meuse24.pdf_scanner.domain.usecase.AnnotationText
import info.meuse24.pdf_scanner.domain.usecase.ImagePageLayout
import info.meuse24.pdf_scanner.domain.usecase.ImagePdfOptions
import info.meuse24.pdf_scanner.domain.usecase.ImportFileUseCase
import info.meuse24.pdf_scanner.domain.usecase.HighlightRect
import info.meuse24.pdf_scanner.domain.usecase.RedactionRect
import info.meuse24.pdf_scanner.util.AndroidResourceProvider
import info.meuse24.pdf_scanner.util.AndroidStorageProvider
import info.meuse24.pdf_scanner.util.FileUtil
import info.meuse24.pdf_scanner.util.PdfEditor
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
import java.util.Calendar
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
                AnnotationRect(
                    left = 0.55f,
                    top = 0.15f,
                    right = 0.75f,
                    bottom = 0.28f,
                    pageIndex = 0,
                    style = AnnotationShapeStyle.FILLED
                )
            ),
            ovals = emptyList(),
            comments = listOf(
                AnnotationText(
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
    fun applyAnnotationsRendersColoredFilledOval() {
        val input = createPdf(
            File(scansDir, "androidtest_annotate_oval_source.pdf"),
            listOf(TestPage(width = 500f, height = 700f, drawMarker = false))
        )

        val annotated = pdfEditor.applyAnnotations(
            input = input,
            outputDir = scansDir,
            strokes = emptyList(),
            rects = emptyList(),
            ovals = listOf(
                AnnotationOval(
                    left = 0.20f,
                    top = 0.20f,
                    right = 0.45f,
                    bottom = 0.45f,
                    pageIndex = 0,
                    color = 0x660091EA,
                    style = AnnotationShapeStyle.FILLED
                )
            ),
            comments = emptyList()
        )

        val bitmap = pdfEditor.renderPageThumbnail(annotated, pageIndex = 0, maxSizePx = 220)
        assertNotNull(bitmap)
        bitmap!!.useBitmap {
            val target = pixelAtNormalized(it, 0.32f, 0.32f)
            val control = pixelAtNormalized(it, 0.80f, 0.80f)
            assertTrue(isBlueHighlight(target))
            assertTrue(isMostlyWhite(control))
        }
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
            rects = emptyList(),
            ovals = emptyList(),
            comments = listOf(
                AnnotationText(
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
    fun secureRedactionRemovesExtractableTextAndBurnsBlackRect() {
        val input = createPdf(
            File(scansDir, "androidtest_redact_source.pdf"),
            listOf(TestPage(width = 520f, height = 720f, drawMarker = false))
        )
        val annotated = pdfEditor.applyAnnotations(
            input = input,
            outputDir = scansDir,
            strokes = emptyList(),
            rects = emptyList(),
            ovals = emptyList(),
            comments = listOf(
                AnnotationText(
                    pageIndex = 0,
                    anchorX = 0.22f,
                    anchorY = 0.28f,
                    text = "GEHEIM-123",
                    fontSizeFraction = 0.05f
                )
            )
        )
        val redactionRect = RedactionRect(
            left = 0.16f,
            top = 0.18f,
            right = 0.56f,
            bottom = 0.34f,
            pageIndex = 0
        )

        assertTrue(extractPdfText(annotated).contains("GEHEIM-123"))

        val redacted = pdfEditor.applySecureRedaction(annotated, scansDir, listOf(redactionRect))

        assertTrue(extractPdfText(redacted).isBlank())
        val bitmap = pdfEditor.renderPageThumbnail(redacted, pageIndex = 0, maxSizePx = 220)
        assertNotNull(bitmap)
        bitmap!!.useBitmap {
            val redactedPixel = pixelAtNormalized(it, 0.30f, 0.26f)
            val controlPixel = pixelAtNormalized(it, 0.80f, 0.80f)
            assertTrue(isBlack(redactedPixel))
            assertTrue(isMostlyWhite(controlPixel))
        }
    }

    @Test
    fun secureRedactionKeepsTextOnUntouchedPagesAndRemovesSecretBytes() {
        val input = createRawAsciiTextPdf(
            File(scansDir, "androidtest_redact_multipage_source.pdf"),
            listOf("GEHEIM-123", "PUBLIC-456"),
            pageWidth = 520,
            pageHeight = 720
        )
        val redacted = pdfEditor.applySecureRedaction(
            input,
            scansDir,
            listOf(
                RedactionRect(
                    left = 0.15f,
                    top = 0.18f,
                    right = 0.56f,
                    bottom = 0.34f,
                    pageIndex = 0
                )
            )
        )

        val extractedText = extractPdfText(redacted)
        assertFalse(extractedText.contains("GEHEIM-123"))
        assertTrue(extractedText.contains("PUBLIC-456"))
        assertTrue(fileContainsAsciiToken(input, "GEHEIM-123"))
        assertFalse(fileContainsAsciiToken(redacted, "GEHEIM-123"))
    }

    @Test
    fun secureRedactionOnRotatedPageKeepsLandscapeDisplayAndBurnsExpectedArea() {
        val input = createPdf(
            File(scansDir, "androidtest_redact_rotated_source.pdf"),
            listOf(TestPage(width = 500f, height = 700f, rotation = 90, drawMarker = false))
        )
        val annotated = pdfEditor.applyAnnotations(
            input = input,
            outputDir = scansDir,
            strokes = emptyList(),
            rects = emptyList(),
            ovals = emptyList(),
            comments = listOf(
                AnnotationText(
                    pageIndex = 0,
                    anchorX = 0.22f,
                    anchorY = 0.28f,
                    text = "ROTATE-SECRET",
                    fontSizeFraction = 0.05f
                )
            )
        )
        val redacted = pdfEditor.applySecureRedaction(
            annotated,
            scansDir,
            listOf(
                RedactionRect(
                    left = 0.16f,
                    top = 0.18f,
                    right = 0.56f,
                    bottom = 0.34f,
                    pageIndex = 0
                )
            )
        )

        assertFalse(extractPdfText(redacted).contains("ROTATE-SECRET"))
        val bitmap = pdfEditor.renderPageThumbnail(redacted, pageIndex = 0, maxSizePx = 240)
        assertNotNull(bitmap)
        bitmap!!.useBitmap {
            assertTrue(it.width > it.height)
            val redactedPixel = pixelAtNormalized(it, 0.30f, 0.26f)
            val controlPixel = pixelAtNormalized(it, 0.80f, 0.80f)
            assertTrue(isBlack(redactedPixel))
            assertTrue(isMostlyWhite(controlPixel))
        }
    }

    @Test
    fun secureRedactionSanitizesFormsLinksAttachmentsAndActions() {
        val input = createPdf(
            File(scansDir, "androidtest_redact_sanitize_source.pdf"),
            listOf(
                TestPage(width = 520f, height = 720f, drawMarker = false),
                TestPage(width = 520f, height = 720f, drawMarker = false)
            )
        )
        enrichPdfWithInteractiveContent(input)

        val redacted = pdfEditor.applySecureRedaction(
            input,
            scansDir,
            listOf(
                RedactionRect(
                    left = 0.16f,
                    top = 0.18f,
                    right = 0.56f,
                    bottom = 0.34f,
                    pageIndex = 0
                )
            )
        )

        PDDocument.load(redacted).use { document ->
            val catalog = document.documentCatalog.cosObject
            assertNull(catalog.getDictionaryObject(COSName.ACRO_FORM))
            assertNull(catalog.getDictionaryObject(COSName.OPEN_ACTION))
            assertNull(catalog.getDictionaryObject(COSName.AA))
            assertNull(catalog.getDictionaryObject(COSName.getPDFName("AF")))

            val names = catalog.getDictionaryObject(COSName.NAMES) as? COSDictionary
            assertTrue(
                names == null ||
                    (names.getDictionaryObject(COSName.getPDFName("EmbeddedFiles")) == null &&
                        names.getDictionaryObject(COSName.getPDFName("JavaScript")) == null)
            )

            repeat(document.numberOfPages) { pageIndex ->
                val page = document.getPage(pageIndex).cosObject
                assertNull(page.getDictionaryObject(COSName.AA))
                assertNull(page.getDictionaryObject(COSName.getPDFName("AF")))
                val annots = page.getDictionaryObject(COSName.ANNOTS) as? COSArray
                assertTrue(annots == null || annots.size() == 0)
            }
        }
    }

    @Test
    fun secureRedactionSanitizesDocumentInfoAndXmpMetadata() {
        val input = createPdf(
            File(scansDir, "androidtest_redact_metadata_source.pdf"),
            listOf(TestPage(width = 520f, height = 720f, drawMarker = false))
        )
        enrichPdfWithMetadata(input)

        val redacted = pdfEditor.applySecureRedaction(
            input,
            scansDir,
            listOf(
                RedactionRect(
                    left = 0.16f,
                    top = 0.18f,
                    right = 0.56f,
                    bottom = 0.34f,
                    pageIndex = 0
                )
            )
        )

        val metadata = pdfEditor.readMetadata(redacted)
        assertNull(metadata.title)
        assertNull(metadata.author)
        assertNull(metadata.creator)
        assertNull(metadata.subject)
        assertNull(metadata.keywords)
        assertNull(metadata.creationDate)
        assertNull(metadata.modificationDate)

        PDDocument.load(redacted).use { document ->
            assertNull(document.documentCatalog.metadata)
            repeat(document.numberOfPages) { pageIndex ->
                assertNull(document.getPage(pageIndex).metadata)
            }
            assertNull(document.document.trailer.getDictionaryObject(COSName.INFO))
        }
    }

    @Test
    fun secureRedactionPreservesDisplayedCropBoxDimensionsOnRotatedPage() {
        val input = createPdf(
            File(scansDir, "androidtest_redact_cropbox_source.pdf"),
            listOf(
                TestPage(
                    width = 600f,
                    height = 800f,
                    rotation = 90,
                    cropBox = TestCropBox(
                        lowerLeftX = 40f,
                        lowerLeftY = 60f,
                        width = 300f,
                        height = 500f
                    ),
                    drawMarker = false
                )
            )
        )
        val annotated = pdfEditor.applyAnnotations(
            input = input,
            outputDir = scansDir,
            strokes = emptyList(),
            rects = emptyList(),
            ovals = emptyList(),
            comments = listOf(
                AnnotationText(
                    pageIndex = 0,
                    anchorX = 0.20f,
                    anchorY = 0.22f,
                    text = "CROP-SECRET",
                    fontSizeFraction = 0.05f
                )
            )
        )

        val redacted = pdfEditor.applySecureRedaction(
            annotated,
            scansDir,
            listOf(
                RedactionRect(
                    left = 0.12f,
                    top = 0.12f,
                    right = 0.52f,
                    bottom = 0.30f,
                    pageIndex = 0
                )
            )
        )

        PDDocument.load(redacted).use { document ->
            val page = document.getPage(0)
            assertEquals(500f, page.mediaBox.width, 0.1f)
            assertEquals(300f, page.mediaBox.height, 0.1f)
            assertEquals(500f, page.cropBox.width, 0.1f)
            assertEquals(300f, page.cropBox.height, 0.1f)
            assertEquals(0, page.rotation)
        }

        val bitmap = pdfEditor.renderPageThumbnail(redacted, pageIndex = 0, maxSizePx = 240)
        assertNotNull(bitmap)
        bitmap!!.useBitmap {
            assertTrue(it.width > it.height)
            val redactedPixel = pixelAtNormalized(it, 0.25f, 0.20f)
            val controlPixel = pixelAtNormalized(it, 0.82f, 0.82f)
            assertTrue(isBlack(redactedPixel))
            assertTrue(isMostlyWhite(controlPixel))
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

    // ── createPdfFromImages ───────────────────────────────────────────────────

    @Test
    fun createPdfFromImages_singleLayout_producesOnePagePerImage() {
        val imageBytes = listOf(createJpegBytes(200, 200, android.graphics.Color.RED))
        val outputFile = File(scansDir, "androidtest_imgpdf_single.pdf")

        createPdfFromImageBytes(imageBytes, ImagePdfOptions(ImagePageLayout.SINGLE), outputFile)

        assertTrue(outputFile.exists())
        assertEquals(1, pdfEditor.getPageCount(outputFile))
    }

    @Test
    fun createPdfFromImages_twoPerPage_twoImages_createsOnePage() {
        val imageBytes = listOf(
            createJpegBytes(200, 200, android.graphics.Color.RED),
            createJpegBytes(200, 200, android.graphics.Color.BLUE)
        )
        val outputFile = File(scansDir, "androidtest_imgpdf_two2.pdf")

        createPdfFromImageBytes(imageBytes, ImagePdfOptions(ImagePageLayout.TWO_PER_PAGE), outputFile)

        assertTrue(outputFile.exists())
        assertEquals(1, pdfEditor.getPageCount(outputFile))
    }

    @Test
    fun createPdfFromImages_twoPerPage_threeImages_createsTwoPages() {
        val imageBytes = (1..3).map { createJpegBytes(200, 200, android.graphics.Color.GREEN) }
        val outputFile = File(scansDir, "androidtest_imgpdf_two3.pdf")

        createPdfFromImageBytes(imageBytes, ImagePdfOptions(ImagePageLayout.TWO_PER_PAGE), outputFile)

        assertEquals(2, pdfEditor.getPageCount(outputFile))
    }

    @Test
    fun createPdfFromImages_fourPerPage_fourImages_createsOnePage() {
        val imageBytes = (1..4).map { createJpegBytes(200, 200, android.graphics.Color.CYAN) }
        val outputFile = File(scansDir, "androidtest_imgpdf_four4.pdf")

        createPdfFromImageBytes(imageBytes, ImagePdfOptions(ImagePageLayout.FOUR_PER_PAGE), outputFile)

        assertEquals(1, pdfEditor.getPageCount(outputFile))
    }

    @Test
    fun createPdfFromImages_twoPerPage_bothSlotsRendered() {
        // Regressionstest: Beide Bilder müssen sichtbar sein (kein ContentStream-Überschreibungsfehler).
        val redBytes  = createJpegBytes(200, 300, android.graphics.Color.RED)
        val blueBytes = createJpegBytes(200, 300, android.graphics.Color.BLUE)
        val outputFile = File(scansDir, "androidtest_imgpdf_slots.pdf")

        createPdfFromImageBytes(
            listOf(redBytes, blueBytes),
            ImagePdfOptions(ImagePageLayout.TWO_PER_PAGE),
            outputFile
        )

        val bitmap = pdfEditor.renderPageThumbnail(outputFile, pageIndex = 0, maxSizePx = 400)
        assertNotNull(bitmap)
        bitmap!!.useBitmap { bmp ->
            // Slot 0 liegt in der oberen Hälfte → rötlich
            val topPixel    = pixelAtNormalized(bmp, 0.5f, 0.15f)
            // Slot 1 liegt in der unteren Hälfte → bläulich
            val bottomPixel = pixelAtNormalized(bmp, 0.5f, 0.85f)
            assertTrue("Oberer Slot sollte rot gerendert sein",  topPixel[0]    > topPixel[2])
            assertTrue("Unterer Slot sollte blau gerendert sein", bottomPixel[2] > bottomPixel[0])
        }
    }

    @Test
    fun createPdfFromImages_thumbnailIsGenerated() {
        val imageBytes = listOf(createJpegBytes(300, 400, android.graphics.Color.MAGENTA))
        val outputFile = File(scansDir, "androidtest_imgpdf_thumb.pdf")
        val thumbFile  = File(scansDir, "androidtest_imgpdf_thumb.jpg")

        createPdfFromImageBytes(imageBytes, ImagePdfOptions(ImagePageLayout.SINGLE), outputFile)
        pdfEditor.generateThumbnail(outputFile, thumbFile)

        assertTrue(thumbFile.exists())
        assertTrue(thumbFile.length() > 0L)
    }

    private fun createJpegBytes(width: Int, height: Int, color: Int): ByteArray {
        val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
        bmp.recycle()
        return out.toByteArray()
    }

    private fun createPdfFromImageBytes(
        imageBytes: List<ByteArray?>,
        options: ImagePdfOptions,
        outputFile: File
    ) = runBlocking {
        pdfEditor.createPdfFromImages(
            imageCount = imageBytes.size,
            imageProvider = imageBytes::get,
            options = options,
            outputFile = outputFile
        )
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
    val cropBox: TestCropBox? = null,
    val drawMarker: Boolean = true
)

private data class TestCropBox(
    val lowerLeftX: Float,
    val lowerLeftY: Float,
    val width: Float,
    val height: Float
)

private fun createPdf(file: File, pages: List<TestPage>): File {
    PDDocument().use { document ->
        pages.forEach { spec ->
            val page = PDPage(PDRectangle(spec.width, spec.height)).apply {
                rotation = spec.rotation
                spec.cropBox?.let { crop ->
                    cropBox = PDRectangle(
                        crop.lowerLeftX,
                        crop.lowerLeftY,
                        crop.width,
                        crop.height
                    )
                }
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

private fun enrichPdfWithMetadata(file: File) {
    PDDocument.load(file).use { document ->
        document.setDocumentInformation(
            PDDocumentInformation().apply {
                title = "Top Secret Title"
                author = "Secret Author"
                creator = "Secret Creator"
                subject = "Secret Subject"
                keywords = "secret,internal"
                producer = "Secret Producer"
                creationDate = Calendar.getInstance().apply { set(2024, Calendar.JANUARY, 4) }
                modificationDate = Calendar.getInstance().apply { set(2025, Calendar.FEBRUARY, 5) }
                setCustomMetadataValue("Classification", "Strictly Confidential")
            }
        )
        document.documentCatalog.metadata = PDMetadata(document).apply {
            importXMPMetadata(
                """
                <x:xmpmeta xmlns:x="adobe:ns:meta/">
                  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                    <rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/">
                      <dc:title>Top Secret XMP</dc:title>
                    </rdf:Description>
                  </rdf:RDF>
                </x:xmpmeta>
                """.trimIndent().toByteArray()
            )
        }
        repeat(document.numberOfPages) { pageIndex ->
            document.getPage(pageIndex).metadata = PDMetadata(document).apply {
                importXMPMetadata(
                    """
                    <x:xmpmeta xmlns:x="adobe:ns:meta/">
                      <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                        <rdf:Description rdf:about="" xmlns:pdfscanner="https://meuse24.info/pdf_scanner/test">
                          <pdfscanner:page>$pageIndex</pdfscanner:page>
                        </rdf:Description>
                      </rdf:RDF>
                    </x:xmpmeta>
                    """.trimIndent().toByteArray()
                )
            }
        }
        document.save(file)
    }
}

private fun enrichPdfWithInteractiveContent(file: File) {
    PDDocument.load(file).use { document ->
        val catalog = document.documentCatalog.cosObject
        catalog.setItem(COSName.ACRO_FORM, COSDictionary().apply {
            setItem(COSName.FIELDS, COSArray())
        })
        catalog.setItem(COSName.OPEN_ACTION, actionDictionary("JavaScript"))
        catalog.setItem(COSName.AA, COSDictionary().apply {
            setItem(COSName.getPDFName("WC"), actionDictionary("JavaScript"))
        })
        catalog.setItem(COSName.getPDFName("AF"), COSArray())
        catalog.setItem(COSName.NAMES, COSDictionary().apply {
            setItem(COSName.getPDFName("EmbeddedFiles"), COSDictionary())
            setItem(COSName.getPDFName("JavaScript"), COSDictionary())
        })

        val untouchedPage = document.getPage(1).cosObject
        untouchedPage.setItem(COSName.AA, COSDictionary().apply {
            setItem(COSName.getPDFName("O"), actionDictionary("URI"))
        })
        untouchedPage.setItem(COSName.getPDFName("AF"), COSArray())
        untouchedPage.setItem(COSName.ANNOTS, COSArray().apply {
            add(annotationDictionary(subtype = "Link", includeAction = true))
            add(annotationDictionary(subtype = "FileAttachment", includeFileSpec = true))
            add(annotationDictionary(subtype = "Widget", includeAdditionalAction = true))
        })

        document.save(file)
    }
}

private fun actionDictionary(actionType: String): COSDictionary =
    COSDictionary().apply {
        setName(COSName.getPDFName("S"), actionType)
    }

private fun annotationDictionary(
    subtype: String,
    includeAction: Boolean = false,
    includeAdditionalAction: Boolean = false,
    includeFileSpec: Boolean = false
): COSDictionary {
    return COSDictionary().apply {
        setName(COSName.TYPE, "Annot")
        setName(COSName.SUBTYPE, subtype)
        setItem(COSName.RECT, COSArray().apply {
            add(COSFloat(12f))
            add(COSFloat(12f))
            add(COSFloat(144f))
            add(COSFloat(48f))
        })
        if (includeAction) {
            setItem(COSName.A, actionDictionary("URI"))
        }
        if (includeAdditionalAction) {
            setItem(COSName.AA, COSDictionary().apply {
                setItem(COSName.getPDFName("D"), actionDictionary("JavaScript"))
            })
        }
        if (includeFileSpec) {
            setItem(COSName.getPDFName("FS"), COSDictionary().apply {
                setName(COSName.TYPE, "Filespec")
                setString(COSName.getPDFName("F"), "secret.bin")
            })
            setItem(COSName.getPDFName("AF"), COSArray())
        }
    }
}

private fun createRawAsciiTextPdf(
    file: File,
    pageTexts: List<String>,
    pageWidth: Int,
    pageHeight: Int
): File {
    require(pageTexts.isNotEmpty()) { "At least one page text is required" }

    val pageObjectNumbers = pageTexts.indices.map { 4 + it }
    val contentObjectNumbers = pageTexts.indices.map { 4 + pageTexts.size + it }
    val kids = pageObjectNumbers.joinToString(" ") { "$it 0 R" }
    val objects = mutableListOf<String>()

    objects += "<< /Type /Catalog /Pages 2 0 R >>"
    objects += "<< /Type /Pages /Kids [ $kids ] /Count ${pageTexts.size} >>"
    objects += "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"

    pageTexts.forEachIndexed { index, _ ->
        objects += """
            << /Type /Page
               /Parent 2 0 R
               /MediaBox [0 0 $pageWidth $pageHeight]
               /Resources << /Font << /F1 3 0 R >> >>
               /Contents ${contentObjectNumbers[index]} 0 R
            >>
        """.trimIndent().replace("\n", " ")
    }

    pageTexts.forEach { text ->
        val escapedText = text
            .replace("\\", "\\\\")
            .replace("(", "\\(")
            .replace(")", "\\)")
        val stream = "BT\n/F1 36 Tf\n100 500 Td\n($escapedText) Tj\nET\n"
        val length = stream.toByteArray(Charsets.US_ASCII).size
        objects += "<< /Length $length >>\nstream\n$stream" + "endstream"
    }

    val builder = StringBuilder()
    builder.append("%PDF-1.4\n")
    val offsets = mutableListOf(0)
    objects.forEachIndexed { index, body ->
        offsets += builder.toString().toByteArray(Charsets.US_ASCII).size
        builder.append("${index + 1} 0 obj\n")
        builder.append(body)
        builder.append("\nendobj\n")
    }
    val xrefOffset = builder.toString().toByteArray(Charsets.US_ASCII).size
    builder.append("xref\n")
    builder.append("0 ${objects.size + 1}\n")
    builder.append("0000000000 65535 f \n")
    offsets.drop(1).forEach { offset ->
        builder.append(offset.toString().padStart(10, '0'))
        builder.append(" 00000 n \n")
    }
    builder.append("trailer\n")
    builder.append("<< /Size ${objects.size + 1} /Root 1 0 R >>\n")
    builder.append("startxref\n")
    builder.append(xrefOffset)
    builder.append("\n%%EOF")

    file.writeText(builder.toString(), Charsets.US_ASCII)
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

private fun isBlueHighlight(rgb: IntArray): Boolean =
    rgb[2] > 220 && rgb[0] < 220 && rgb[1] < 240

private fun isMostlyWhite(rgb: IntArray): Boolean =
    rgb[0] > 245 && rgb[1] > 245 && rgb[2] > 245

private fun isGray(rgb: IntArray): Boolean =
    abs(rgb[0] - rgb[1]) <= 8 && abs(rgb[1] - rgb[2]) <= 8

private fun isBlack(rgb: IntArray): Boolean =
    rgb[0] < 12 && rgb[1] < 12 && rgb[2] < 12

private fun fileContainsAsciiToken(file: File, token: String): Boolean {
    val bytes = file.readBytes()
    val needle = token.toByteArray(Charsets.US_ASCII)
    if (needle.isEmpty() || bytes.size < needle.size) return false
    return bytes.indices.any { start ->
        if (start + needle.size > bytes.size) return@any false
        var matches = true
        for (index in needle.indices) {
            if (bytes[start + index] != needle[index]) {
                matches = false
                break
            }
        }
        matches
    }
}

private inline fun <T> android.graphics.Bitmap.useBitmap(block: (android.graphics.Bitmap) -> T): T =
    try {
        block(this)
    } finally {
        recycle()
    }

private class InstrumentedFakeScanDao : ScanDao {
    val inserted = mutableListOf<Document>()

    override fun getAllScans(): Flow<List<ScanRecord>> = flowOf(emptyList())

    override fun searchScansFlow(query: String): Flow<List<ScanRecord>> = flowOf(emptyList())

    override suspend fun insert(record: ScanRecord): Long {
        inserted.add(record.toDomain())
        return inserted.size.toLong()
    }

    override suspend fun insertAll(records: List<ScanRecord>) {
        inserted.addAll(records.map { it.toDomain() })
    }

    override suspend fun delete(record: ScanRecord) = Unit

    override suspend fun markSearchable(id: Long, fileSize: Long) = Unit

    override suspend fun markSearchableWithContent(
        id: Long,
        fileSize: Long,
        text: String?,
        tags: String?,
        confidence: Float?,
        language: String?,
        pageTextJson: String?
    ) = Unit

    override suspend fun updateExtractedTextAndOcrStats(
        id: Long,
        text: String?,
        confidence: Float?,
        language: String?,
        pageTextJson: String?
    ) = Unit

    override suspend fun updateFileSize(id: Long, fileSize: Long) = Unit

    override suspend fun updatePageMetrics(id: Long, pageCount: Int, fileSize: Long) = Unit

    override suspend fun invalidateAfterAppend(id: Long, fileSize: Long, pageCount: Int) = Unit

    override suspend fun updateFilenameAndPath(id: Long, filename: String, filepath: String, thumbnailPath: String?) = Unit

    override fun getScansInFolder(folderId: Long): Flow<List<ScanRecord>> = flowOf(emptyList())

    override fun getFavoriteScans(): Flow<List<ScanRecord>> = flowOf(emptyList())

    override suspend fun moveScans(ids: List<Long>, folderId: Long?) = Unit

    override suspend fun setFavorite(ids: List<Long>, favorite: Boolean) = Unit
}

