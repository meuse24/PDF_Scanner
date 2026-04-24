package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.mapper.toDomain
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.AnnotatePdfUseCase
import info.meuse24.pdf_scanner.domain.usecase.AnnotationOval
import info.meuse24.pdf_scanner.domain.usecase.AnnotationRect
import info.meuse24.pdf_scanner.domain.usecase.AnnotationShapeStyle
import info.meuse24.pdf_scanner.domain.usecase.AnnotationStroke
import info.meuse24.pdf_scanner.domain.usecase.AnnotationText
import info.meuse24.pdf_scanner.util.PdfEditor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class AnnotatePdfWorkflowTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private fun record(id: Long, exists: Boolean = true, tags: String? = null): Document {
        val file = if (exists) {
            tmpFolder.newFile("scan_$id.pdf").apply { writeText("pdf") }
        } else {
            File(tmpFolder.root, "missing_$id.pdf")
        }
        return Document(
            id = id,
            filename = "scan_$id",
            filepath = file.absolutePath,
            timestamp = 0L,
            pageCount = 2,
            fileSize = file.length(),
            isSearchable = true,
            tags = tags
        )
    }

    private fun stroke(page: Int = 0) = AnnotationStroke(
        points = listOf(0.1f to 0.5f, 0.5f to 0.5f),
        pageIndex = page
    )

    private fun rect(page: Int = 0) = AnnotationRect(
        left = 0.1f,
        top = 0.4f,
        right = 0.6f,
        bottom = 0.5f,
        pageIndex = page,
        style = AnnotationShapeStyle.FILLED
    )

    private fun oval(page: Int = 0) = AnnotationOval(
        left = 0.2f,
        top = 0.2f,
        right = 0.5f,
        bottom = 0.45f,
        pageIndex = page,
        style = AnnotationShapeStyle.FRAME
    )

    private fun comment(page: Int = 0) = AnnotationText(
        pageIndex = page,
        anchorX = 0.25f,
        anchorY = 0.3f,
        text = "note",
        fontSizeFraction = 0.03f
    )

    private fun workflow(pdfEditor: PdfEditor): Pair<AnnotatePdfWorkflow, FakeAnnotateScanDao> {
        val dao = FakeAnnotateScanDao()
        val repository = ScanRepository(dao)
        val useCase = AnnotatePdfUseCase(
            pdfEditor,
            info.meuse24.pdf_scanner.domain.service.ScanArtifactPersister(pdfEditor, repository)
        )
        return AnnotatePdfWorkflow(useCase, DocumentWorkflowGuard(pdfEditor)) to dao
    }

    @Test
    fun `leere annotationen liefern NoAnnotations`() = runTest {
        val (workflow, _) = workflow(FakeAnnotatePdfEditor())

        val result = workflow(record(1L), emptyList(), emptyList(), emptyList(), emptyList(), tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(ScanWorkflowError.NoAnnotations, (result as WorkflowResult.Failure).error)
    }

    @Test
    fun `fehlende datei liefert MissingFiles`() = runTest {
        val (workflow, _) = workflow(FakeAnnotatePdfEditor())

        val result = workflow(record(2L, exists = false), listOf(stroke()), emptyList(), emptyList(), emptyList(), tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(ScanWorkflowError.MissingFiles(listOf("scan_2")), (result as WorkflowResult.Failure).error)
    }

    @Test
    fun `geschuetzte pdf liefert ProtectedPdfUnsupported`() = runTest {
        val (workflow, _) = workflow(FakeAnnotatePdfEditor(encrypted = true))

        val result = workflow(record(3L), listOf(stroke()), emptyList(), emptyList(), emptyList(), tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(ScanWorkflowError.ProtectedPdfUnsupported, (result as WorkflowResult.Failure).error)
    }

    @Test
    fun `io fehler werden als StorageWriteFailed gemappt`() = runTest {
        val (workflow, _) = workflow(
            FakeAnnotatePdfEditor(onApply = { _, _, _, _, _, _ -> throw IOException("disk full") })
        )

        val result = workflow(record(4L), listOf(stroke()), emptyList(), emptyList(), emptyList(), tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertTrue((result as WorkflowResult.Failure).error is ScanWorkflowError.StorageWriteFailed)
    }

    @Test
    fun `unbekannter fehler wird als AnnotateFailed gemappt`() = runTest {
        val (workflow, _) = workflow(
            FakeAnnotatePdfEditor(onApply = { _, _, _, _, _, _ -> throw IllegalStateException("boom") })
        )

        val result = workflow(record(5L), listOf(stroke()), emptyList(), emptyList(), emptyList(), tmpFolder.root)

        assertTrue(result is WorkflowResult.Failure)
        assertTrue((result as WorkflowResult.Failure).error is ScanWorkflowError.AnnotateFailed)
    }

    @Test
    fun `verschiedene annotate elemente werden akzeptiert und tags bleiben erhalten`() = runTest {
        val (workflow, dao) = workflow(FakeAnnotatePdfEditor())

        val result = workflow(
            record(6L, tags = "invoice,bank"),
            listOf(stroke(0)),
            listOf(rect(1)),
            listOf(oval(0)),
            listOf(comment(1)),
            tmpFolder.root
        )

        assertTrue(result is WorkflowResult.Success)
        assertEquals(1, dao.inserted.size)
        assertEquals("invoice,bank", dao.inserted.single().tags)
    }
}

private class FakeAnnotatePdfEditor(
    private val encrypted: Boolean = false,
    private val onApply: (File, File, List<AnnotationStroke>, List<AnnotationRect>, List<AnnotationOval>, List<AnnotationText>) -> File =
        { input, outputDir, _, _, _, _ ->
            File(outputDir, "${input.nameWithoutExtension}_Annotiert.pdf").apply { writeText("copy") }
        }
) : PdfEditor() {
    override fun isPdfEncrypted(input: File): Boolean = encrypted

    override fun applyAnnotations(
        input: File,
        outputDir: File,
        strokes: List<AnnotationStroke>,
        rects: List<AnnotationRect>,
        ovals: List<AnnotationOval>,
        comments: List<AnnotationText>
    ): File = onApply(input, outputDir, strokes, rects, ovals, comments)

    override fun generateThumbnail(pdfFile: File, outputFile: File): Boolean {
        outputFile.writeText("thumb")
        return true
    }
}

private class FakeAnnotateScanDao : info.meuse24.pdf_scanner.data.local.ScanDao {
    val inserted = mutableListOf<Document>()

    override fun getAllScans(): Flow<List<ScanRecord>> = flowOf(emptyList())
    override suspend fun insert(record: ScanRecord): Long {
        inserted += record.toDomain()
        return inserted.size.toLong()
    }
    override suspend fun insertAll(records: List<ScanRecord>) { inserted += records.map { it.toDomain() } }
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
}

