package info.meuse24.pdf_scanner.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import info.meuse24.pdf_scanner.data.local.ScanDao
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.util.PdfEditor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Unit-Tests für CreatePdfFromImagesUseCase.
 *
 * Der Context wird per Mockito abgebildet: URIs mit bestimmten Strings liefern
 * Fake-Bytes über den ContentResolver, alle anderen liefern null.
 */
class CreatePdfFromImagesUseCaseTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    // Minimale nicht-leere Bytes als Platzhalter für Bilddaten
    private val fakeBytes = ByteArray(16) { it.toByte() }

    private fun okUri(tag: String = "img"): Uri {
        val uri = mock(Uri::class.java)
        `when`(uri.toString()).thenReturn("content://ok_$tag")
        return uri
    }

    private fun badUri(tag: String = "bad"): Uri {
        val uri = mock(Uri::class.java)
        `when`(uri.toString()).thenReturn("content://bad_$tag")
        return uri
    }

    private fun buildContext(okUris: Set<Uri>): Context {
        val resolver = mock(ContentResolver::class.java)
        // Alle ok-URIs liefern einen ByteArrayInputStream, alle anderen null
        okUris.forEach { uri ->
            `when`(resolver.openInputStream(uri)).thenReturn(ByteArrayInputStream(fakeBytes))
        }
        val ctx = mock(Context::class.java)
        `when`(ctx.contentResolver).thenReturn(resolver)
        return ctx
    }

    private fun buildUseCase(
        okUris: Set<Uri> = emptySet(),
        pdfEditor: PdfEditor = FakeImagesPdfEditor(tmpFolder)
    ): CreatePdfFromImagesUseCase {
        return CreatePdfFromImagesUseCase(buildContext(okUris), pdfEditor, ScanRepository(FakeImagesScanDao()))
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `leere URI-Liste wirft Exception`() = runTest {
        buildUseCase().invoke(emptyList(), "test", ImagePageLayout.SINGLE, tmpFolder.root)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `alle Bilder unlesbar wirft Exception`() = runTest {
        val uris = listOf(badUri("1"), badUri("2"))
        buildUseCase(okUris = emptySet()).invoke(uris, "test", ImagePageLayout.SINGLE, tmpFolder.root)
    }

    @Test
    fun `1 Bild SINGLE ergibt 1 Seite`() = runTest {
        val u1 = okUri("1")
        val uris = listOf(u1)
        val editor = FakeImagesPdfEditor(tmpFolder)
        buildUseCase(setOf(u1), editor).invoke(uris, "test", ImagePageLayout.SINGLE, tmpFolder.root)

        assertEquals(1, editor.lastPageCount)
    }

    @Test
    fun `3 Bilder TWO_PER_PAGE ergibt 2 Seiten`() = runTest {
        val uris = (1..3).map { okUri("$it") }
        val editor = FakeImagesPdfEditor(tmpFolder)
        buildUseCase(uris.toSet(), editor).invoke(uris, "test", ImagePageLayout.TWO_PER_PAGE, tmpFolder.root)

        assertEquals(2, editor.lastPageCount)
    }

    @Test
    fun `4 Bilder FOUR_PER_PAGE ergibt 1 Seite`() = runTest {
        val uris = (1..4).map { okUri("$it") }
        val editor = FakeImagesPdfEditor(tmpFolder)
        buildUseCase(uris.toSet(), editor).invoke(uris, "test", ImagePageLayout.FOUR_PER_PAGE, tmpFolder.root)

        assertEquals(1, editor.lastPageCount)
    }

    @Test
    fun `5 Bilder FOUR_PER_PAGE ergibt 2 Seiten`() = runTest {
        val uris = (1..5).map { okUri("$it") }
        val editor = FakeImagesPdfEditor(tmpFolder)
        buildUseCase(uris.toSet(), editor).invoke(uris, "test", ImagePageLayout.FOUR_PER_PAGE, tmpFolder.root)

        assertEquals(2, editor.lastPageCount)
    }

    @Test
    fun `teilweise unlesbare Bilder liefern skippedCount`() = runTest {
        val goodUris = (1..3).map { okUri("g$it") }
        val badUris  = (1..2).map { badUri("b$it") }
        val all = goodUris + badUris

        val result = buildUseCase(goodUris.toSet())
            .invoke(all, "test", ImagePageLayout.SINGLE, tmpFolder.root)

        assertEquals(2, result.skippedCount)
    }

    @Test
    fun `Dateiname wird eindeutig aufgeloest bei Konflikt`() = runTest {
        val scansDir = tmpFolder.newFolder("scans")
        File(scansDir, "mein_pdf.pdf").writeText("x")
        val u = okUri("1")

        val result = buildUseCase(setOf(u))
            .invoke(listOf(u), "mein_pdf", ImagePageLayout.SINGLE, scansDir)

        assertEquals("mein_pdf_2", result.baseName)
    }

    @Test
    fun `tags bleiben null kein AutoTag`() = runTest {
        val u = okUri("1")
        val dao = FakeImagesScanDao()
        val useCase = CreatePdfFromImagesUseCase(
            buildContext(setOf(u)), FakeImagesPdfEditor(tmpFolder), ScanRepository(dao)
        )
        useCase.invoke(listOf(u), "test", ImagePageLayout.SINGLE, tmpFolder.root)

        assertNull(dao.lastInserted?.tags)
    }
}

// ── Fake PdfEditor ────────────────────────────────────────────────────────────

private class FakeImagesPdfEditor(
    private val tmpFolder: TemporaryFolder
) : PdfEditor() {

    var lastPageCount: Int = -1

    override fun createPdfFromImages(
        imageBytes: List<ByteArray?>,
        layout: ImagePageLayout,
        outputFile: File
    ): File {
        lastPageCount = (imageBytes.size + layout.imagesPerPage - 1) / layout.imagesPerPage
        outputFile.writeText("fake-pdf")
        return outputFile
    }

    override fun generateThumbnail(pdfFile: File, outputFile: File): Boolean {
        outputFile.writeText("thumb")
        return true
    }
}

// ── Fake DAO ──────────────────────────────────────────────────────────────────

internal class FakeImagesScanDao : ScanDao {
    var lastInserted: ScanRecord? = null
    override fun getAllScans(): Flow<List<ScanRecord>> = flowOf(emptyList())
    override suspend fun insert(record: ScanRecord): Long {
        lastInserted = record
        return 1L
    }
    override suspend fun insertAll(records: List<ScanRecord>) {}
    override suspend fun delete(record: ScanRecord) {}
    override fun searchScansFlow(query: String): Flow<List<ScanRecord>> = flowOf(emptyList())
    override suspend fun markSearchableWithContent(id: Long, fileSize: Long, text: String?, tags: String?) {}
    override suspend fun markSearchable(id: Long, fileSize: Long) {}
    override suspend fun updateFileSize(id: Long, fileSize: Long) {}
    override suspend fun updatePageMetrics(id: Long, pageCount: Int, fileSize: Long) {}
    override suspend fun updateFilenameAndPath(id: Long, filename: String, filepath: String, thumbnailPath: String?) {}
}
