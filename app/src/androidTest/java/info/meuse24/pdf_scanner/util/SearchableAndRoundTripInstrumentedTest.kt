package info.meuse24.pdf_scanner.util

import android.content.ContentUris
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.text.PDFTextStripper
import info.meuse24.pdf_scanner.data.local.ScanDao
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.ExportAsJpgUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExportScanUseCase
import info.meuse24.pdf_scanner.domain.usecase.ImportFileUseCase
import info.meuse24.pdf_scanner.domain.usecase.ImportScanUseCase
import info.meuse24.pdf_scanner.domain.usecase.MakeSearchableUseCase
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

@RunWith(AndroidJUnit4::class)
class SearchableAndRoundTripInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver
    private val storageProvider = AndroidStorageProvider(context)
    private val resourceProvider = AndroidResourceProvider(context)
    private val ocrManager = OcrManager()
    private val ocrModelInstaller = AndroidOcrModelInstaller(context)
    private val ocrPipeline = OcrPipeline(ocrManager, ocrModelInstaller)
    private val textRecognizerRunner = MlKitTextRecognizerRunner()
    private val pdfEditor = PdfEditor()
    private val searchablePdfBuilder = SearchablePdfBuilder(
        context,
        ocrPipeline,
        textRecognizerRunner,
        DefaultDispatcherProvider()
    )
    private val scansDir: File
        get() = storageProvider.scansDir()

    @Before
    fun setUp() {
        cleanupLocalArtifacts()
        cleanupDownloads()
    }

    @After
    fun tearDown() {
        cleanupLocalArtifacts()
        cleanupDownloads()
    }

    @Test
    fun searchablePdfBuilderAddsExtractableTextLayerFromImagePdf() = runBlocking {
        val source = createImagePdf(
            File(scansDir, "androidtest_searchable_builder_source.pdf"),
            lines = listOf("SCAN TEST", "1234")
        )

        val extracted = searchablePdfBuilder.makeSearchable(source, "en", onProgress = { _, _ -> })
        val pdfText = extractPdfText(source)

        assertTrue(normalizeText(extracted).contains("SCANTEST"))
        assertTrue(normalizeText(extracted).contains("1234"))
        assertTrue(normalizeText(pdfText).contains("SCANTEST"))
        assertTrue(normalizeText(pdfText).contains("1234"))
    }

    @Test
    fun makeSearchableUseCaseUpdatesRepositoryWithExtractedText() = runBlocking {
        val source = createImagePdf(
            File(scansDir, "androidtest_searchable_usecase_source.pdf"),
            lines = listOf("PDF SCANNER", "5678")
        )
        val dao = TrackingScanDao()
        val useCase = MakeSearchableUseCase(searchablePdfBuilder, ScanRepository(dao))
        val record = ScanRecord(
            id = 42L,
            filename = "androidtest_searchable_usecase_source",
            filepath = source.absolutePath,
            timestamp = 0L,
            pageCount = 1,
            fileSize = source.length()
        )

        val count = useCase(listOf(record), "en")

        assertEquals(1, count)
        assertEquals(1, dao.searchableUpdates.size)
        val update = dao.searchableUpdates.single()
        assertEquals(42L, update.id)
        assertTrue(normalizeText(update.text).contains("PDFSCANNER"))
        assertTrue(normalizeText(update.text).contains("5678"))
        assertTrue(normalizeText(extractPdfText(source)).contains("PDFSCANNER"))
    }

    @Test
    fun searchablePdfBuilderProcessesMixedMultiPageDocumentAndReportsProgress() = runBlocking {
        val vectorPart = createVectorTextPdf(
            File(scansDir, "androidtest_searchable_mixed_vector.pdf"),
            listOf(VectorTextPage(text = "VECTOR 1111"))
        )
        val imagePart = createImagePdfPages(
            File(scansDir, "androidtest_searchable_mixed_image.pdf"),
            listOf(
                ImagePageSpec(lines = listOf("IMAGE 2222")),
                ImagePageSpec(lines = listOf("IMAGE 3333"))
            )
        )
        val mixed = File(scansDir, "androidtest_searchable_mixed_result.pdf")
        pdfEditor.mergePdfs(listOf(vectorPart, imagePart), mixed)

        val progress = mutableListOf<Pair<Int, Int>>()
        val extracted = searchablePdfBuilder.makeSearchable(mixed, "en", onProgress = { current, total ->
            progress.add(current to total)
        })
        val pdfText = normalizeText(extractPdfText(mixed))
        val normalizedExtracted = normalizeText(extracted)

        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), progress)
        assertEquals(3, pdfEditor.getPageCount(mixed))
        assertTrue(normalizedExtracted.contains("VECTOR1111"))
        assertTrue(normalizedExtracted.contains("IMAGE2222"))
        assertTrue(normalizedExtracted.contains("IMAGE3333"))
        assertTrue(pdfText.contains("VECTOR1111"))
        assertTrue(pdfText.contains("IMAGE2222"))
        assertTrue(pdfText.contains("IMAGE3333"))
    }

    @Test
    fun exportScanAndImportFileRoundTripPreservesPlainPdf() = runBlocking {
        val source = createVectorPdf(
            File(scansDir, "androidtest_roundtrip_plain_source.pdf"),
            listOf(
                RoundTripPage(width = 401f, height = 601f),
                RoundTripPage(width = 402f, height = 602f)
            )
        )
        val displayNameBase = "androidtest_roundtrip_plain_export"

        val exportedDisplayName = ExportScanUseCase(AndroidDownloadsStorage(context))(
            ScanRecord(
                id = 1L,
                filename = displayNameBase,
                filepath = source.absolutePath,
                timestamp = 0L,
                pageCount = 2,
                fileSize = source.length()
            )
        )
        val uri = requireNotNull(findDownloadUri(exportedDisplayName))

        val imported = createImportFileUseCase().invoke(uri, "androidtest_roundtrip_plain_import")

        assertFalse(imported.isEncrypted)
        assertEquals(2, imported.pageCount)
        assertNotNull(imported.thumbnailPath)
        assertTrue(File(imported.filepath).exists())
        assertTrue(File(imported.thumbnailPath!!).exists())
    }

    @Test
    fun exportProtectedPdfAndImportFileKeepsPasswordProtectedState() = runBlocking {
        val plain = createVectorPdf(
            File(scansDir, "androidtest_roundtrip_protected_plain.pdf"),
            listOf(RoundTripPage(width = 430f, height = 630f))
        )
        val protected = pdfEditor.protectPdf(plain, scansDir, "secret123")
        val exportedDisplayName = ExportScanUseCase(AndroidDownloadsStorage(context))(
            ScanRecord(
                id = 2L,
                filename = "androidtest_roundtrip_protected_export",
                filepath = protected.absolutePath,
                timestamp = 0L,
                pageCount = 1,
                fileSize = protected.length(),
                isEncrypted = true
            )
        )
        val uri = requireNotNull(findDownloadUri(exportedDisplayName))

        val imported = createImportFileUseCase().invoke(uri, "androidtest_roundtrip_protected_import")

        assertTrue(imported.isEncrypted)
        assertEquals(0, imported.pageCount)
        assertNull(imported.thumbnailPath)
        assertTrue(pdfEditor.isPdfEncrypted(File(imported.filepath)))
    }

    @Test
    fun exportRestrictedPdfAndImportFileKeepsRestrictionStateReadable() = runBlocking {
        val plain = createVectorPdf(
            File(scansDir, "androidtest_roundtrip_restricted_plain.pdf"),
            listOf(RoundTripPage(width = 440f, height = 640f))
        )
        val restricted = pdfEditor.restrictUsage(
            input = plain,
            outputDir = scansDir,
            ownerPassword = "owner-secret",
            canPrint = false,
            canCopy = false,
            canEdit = false
        )
        val exportedDisplayName = ExportScanUseCase(AndroidDownloadsStorage(context))(
            ScanRecord(
                id = 3L,
                filename = "androidtest_roundtrip_restricted_export",
                filepath = restricted.absolutePath,
                timestamp = 0L,
                pageCount = 1,
                fileSize = restricted.length(),
                isEncrypted = true
            )
        )
        val uri = requireNotNull(findDownloadUri(exportedDisplayName))

        val imported = createImportFileUseCase().invoke(uri, "androidtest_roundtrip_restricted_import")

        assertTrue(imported.isEncrypted)
        assertEquals(1, imported.pageCount)
        assertNotNull(imported.thumbnailPath)
        assertTrue(File(imported.thumbnailPath!!).exists())

        val unlocked = pdfEditor.removePassword(File(imported.filepath), scansDir)
        assertFalse(pdfEditor.isPdfEncrypted(unlocked))
    }

    @Test
    fun importScanUseCaseStoresThumbnailAndExtractedTextForSearchableImport() = runBlocking {
        val sourcePdf = createImagePdf(
            File(scansDir, "androidtest_importscan_source.pdf"),
            lines = listOf("IMPORT SCAN", "2468")
        )
        val thumbnailFile = createJpegImage(
            File(scansDir, "androidtest_importscan_thumb.jpg"),
            label = "THUMB"
        )
        val dao = TrackingScanDao()
        val useCase = ImportScanUseCase(
            fileUtil = FileUtil(context, storageProvider, resourceProvider),
            searchablePdfBuilder = searchablePdfBuilder,
            repository = ScanRepository(dao)
        )

        val record = useCase(
            pdfUri = fileUri(sourcePdf),
            pageCount = 1,
            filename = "androidtest_importscan_result",
            thumbnailUri = fileUri(thumbnailFile),
            makeSearchable = true,
            languageCode = "en"
        )

        assertEquals(1, dao.inserted.size)
        assertTrue(record.isSearchable)
        assertNotNull(record.thumbnailPath)
        assertTrue(File(record.thumbnailPath!!).exists())
        assertTrue(normalizeText(record.extractedText).contains("IMPORTSCAN"))
        assertTrue(normalizeText(record.extractedText).contains("2468"))
        assertTrue(normalizeText(extractPdfText(File(record.filepath))).contains("IMPORTSCAN"))
    }

    @Test
    fun exportAsJpgUseCaseWritesOneDecodableImagePerPdfPage() = runBlocking {
        val source = createVectorPdf(
            File(scansDir, "androidtest_exportjpg_source.pdf"),
            listOf(
                RoundTripPage(width = 401f, height = 601f),
                RoundTripPage(width = 402f, height = 602f),
                RoundTripPage(width = 403f, height = 603f)
            )
        )
        val baseName = "androidtest_exportjpg_result"
        val exportedPages = ExportAsJpgUseCase(
            downloadsStorage = AndroidDownloadsStorage(context),
            pdfPageJpgRenderer = AndroidPdfPageJpgRenderer()
        )(
            ScanRecord(
                id = 4L,
                filename = baseName,
                filepath = source.absolutePath,
                timestamp = 0L,
                pageCount = 3,
                fileSize = source.length()
            )
        )

        assertEquals(3, exportedPages)

        val uris = findDownloadUris(listOf("${baseName}_p1.jpg", "${baseName}_p2.jpg", "${baseName}_p3.jpg"))
        assertEquals(3, uris.size)
        uris.forEach { uri ->
            resolver.openInputStream(uri).use { input ->
                val bitmap = BitmapFactory.decodeStream(input)
                assertNotNull(bitmap)
                bitmap!!.useBitmap {
                    assertTrue(it.width > 0)
                    assertTrue(it.height > 0)
                }
            }
        }
    }

    @Test
    fun exportAsJpgUseCaseHandlesLargerSixPageDocument() = runBlocking {
        val source = createImagePdfPages(
            File(scansDir, "androidtest_exportjpg_large_source.pdf"),
            listOf(
                ImagePageSpec(lines = listOf("PAGE 1")),
                ImagePageSpec(lines = listOf("PAGE 2")),
                ImagePageSpec(lines = listOf("PAGE 3")),
                ImagePageSpec(lines = listOf("PAGE 4")),
                ImagePageSpec(lines = listOf("PAGE 5")),
                ImagePageSpec(lines = listOf("PAGE 6"))
            )
        )
        val baseName = "androidtest_exportjpg_large"

        val exportedPages = ExportAsJpgUseCase(
            downloadsStorage = AndroidDownloadsStorage(context),
            pdfPageJpgRenderer = AndroidPdfPageJpgRenderer()
        )(
            ScanRecord(
                id = 5L,
                filename = baseName,
                filepath = source.absolutePath,
                timestamp = 0L,
                pageCount = 6,
                fileSize = source.length()
            )
        )

        assertEquals(6, exportedPages)
        val uris = findDownloadUris((1..6).map { "${baseName}_p$it.jpg" })
        assertEquals(6, uris.size)
        uris.forEach { uri ->
            resolver.openInputStream(uri).use { input ->
                val bitmap = BitmapFactory.decodeStream(input)
                assertNotNull(bitmap)
                bitmap!!.useBitmap {
                    assertTrue(it.width > 0)
                    assertTrue(it.height > 0)
                }
            }
        }
    }

    private fun createImportFileUseCase(): ImportFileUseCase =
        ImportFileUseCase(
            fileUtil = FileUtil(context, storageProvider, resourceProvider),
            pdfEditor = pdfEditor,
            repository = ScanRepository(TrackingScanDao()),
            resourceProvider = resourceProvider
        )

    private fun fileUri(file: File): Uri =
        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

    private fun findDownloadUri(displayName: String): Uri? {
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        resolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ?",
            arrayOf(displayName),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                return ContentUris.withAppendedId(collection, id)
            }
        }
        return null
    }

    private fun findDownloadUris(displayNames: List<String>): List<Uri> =
        displayNames.mapNotNull(::findDownloadUri)

    private fun cleanupLocalArtifacts() {
        scansDir.listFiles().orEmpty()
            .filter { it.name.startsWith("androidtest_") }
            .forEach { it.deleteRecursively() }
    }

    private fun cleanupDownloads() {
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        resolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME),
            "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
            arrayOf("androidtest_%"),
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                resolver.delete(ContentUris.withAppendedId(collection, id), null, null)
            }
        }
    }
}

private data class RoundTripPage(
    val width: Float,
    val height: Float
)

private data class VectorTextPage(
    val text: String,
    val width: Float = PDRectangle.A4.width,
    val height: Float = PDRectangle.A4.height
)

private data class ImagePageSpec(
    val lines: List<String>,
    val rotation: Int = 0
)

private fun createVectorPdf(file: File, pages: List<RoundTripPage>): File {
    PDDocument().use { document ->
        pages.forEach { spec ->
            val page = PDPage(PDRectangle(spec.width, spec.height))
            document.addPage(page)
            PDPageContentStream(document, page).use { content ->
                content.addRect(16f, 16f, (spec.width / 3f).coerceAtLeast(16f), (spec.height / 4f).coerceAtLeast(16f))
                content.stroke()
            }
        }
        document.save(file)
    }
    return file
}

private fun createVectorTextPdf(file: File, pages: List<VectorTextPage>): File {
    PDDocument().use { document ->
        val font = loadDeviceFont(document)
        pages.forEach { spec ->
            val page = PDPage(PDRectangle(spec.width, spec.height))
            document.addPage(page)
            PDPageContentStream(document, page).use { content ->
                content.beginText()
                content.setFont(font, 42f)
                content.newLineAtOffset(72f, spec.height - 120f)
                content.showText(spec.text)
                content.endText()
            }
        }
        document.save(file)
    }
    return file
}

private fun createImagePdf(file: File, lines: List<String>): File {
    return createImagePdfPages(file, listOf(ImagePageSpec(lines = lines)))
}

private fun createImagePdfPages(file: File, pages: List<ImagePageSpec>): File {
    PDDocument().use { document ->
        pages.forEach { spec ->
            val bitmap = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 120f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                }
                spec.lines.forEachIndexed { index, line ->
                    canvas.drawText(line, 100f, 280f + index * 180f, paint)
                }

                val page = PDPage(PDRectangle.A4).apply {
                    rotation = spec.rotation
                }
                document.addPage(page)
                val image = LosslessFactory.createFromImage(document, bitmap)
                PDPageContentStream(document, page).use { content ->
                    content.drawImage(image, 0f, 0f, page.mediaBox.width, page.mediaBox.height)
                }
            } finally {
                bitmap.recycle()
            }
        }
        document.save(file)
    }
    return file
}

private fun createJpegImage(file: File, label: String): File {
    val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(245, 245, 245))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 42f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText(label, 24f, 96f, paint)
        file.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
        }
        return file
    } finally {
        bitmap.recycle()
    }
}

private fun extractPdfText(file: File): String =
    PDDocument.load(file).use { document ->
        PDFTextStripper().getText(document).replace("\\s+".toRegex(), " ").trim()
    }

private fun normalizeText(text: String?): String =
    text.orEmpty().uppercase().replace("\\s+".toRegex(), "")

private inline fun <T> Bitmap.useBitmap(block: (Bitmap) -> T): T =
    try {
        block(this)
    } finally {
        recycle()
    }

private fun loadDeviceFont(document: PDDocument): PDFont {
    val fontFile = sequenceOf(
        "/system/fonts/Roboto-Regular.ttf",
        "/system/fonts/NotoSans-Regular.ttf",
        "/system/fonts/DroidSans.ttf"
    ).map(::File).firstOrNull(File::exists)
        ?: error("No device font available for instrumentation PDF fixture generation")

    return fontFile.inputStream().use { input ->
        PDType0Font.load(document, input, true)
    }
}

private class TrackingScanDao : ScanDao {
    data class SearchableUpdate(val id: Long, val fileSize: Long, val text: String?)

    val searchableUpdates = mutableListOf<SearchableUpdate>()
    val inserted = mutableListOf<ScanRecord>()

    override fun getAllScans(): Flow<List<ScanRecord>> = flowOf(emptyList())

    override fun searchScansFlow(query: String): Flow<List<ScanRecord>> = flowOf(emptyList())

    override suspend fun insert(record: ScanRecord): Long {
        inserted.add(record)
        return inserted.size.toLong()
    }

    override suspend fun insertAll(records: List<ScanRecord>) {
        inserted.addAll(records)
    }

    override suspend fun delete(record: ScanRecord) = Unit

    override suspend fun markSearchable(id: Long, fileSize: Long) = Unit

    override suspend fun markSearchableWithContent(id: Long, fileSize: Long, text: String?, tags: String?) {
        searchableUpdates.add(SearchableUpdate(id, fileSize, text))
    }

    override suspend fun updateFileSize(id: Long, fileSize: Long) = Unit

    override suspend fun updatePageMetrics(id: Long, pageCount: Int, fileSize: Long) = Unit

    override suspend fun invalidateAfterAppend(id: Long, fileSize: Long, pageCount: Int) = Unit

    override suspend fun updateFilenameAndPath(id: Long, filename: String, filepath: String, thumbnailPath: String?) = Unit
}
