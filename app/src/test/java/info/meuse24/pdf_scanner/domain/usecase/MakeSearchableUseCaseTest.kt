package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.util.OcrManager
import info.meuse24.pdf_scanner.util.SearchablePdfBuilder
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit-Tests für MakeSearchableUseCase.
 * Prüft insbesondere die Idempotenz-Logik (bereits durchsuchbare Records überspringen).
 */
class MakeSearchableUseCaseTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private fun makeUseCase(
        onMakeSearchable: (File, String) -> Unit = { _, _ -> }
    ): Pair<MakeSearchableUseCase, FakeScanDao> {
        val dao        = FakeScanDao()
        val repository = ScanRepository(dao)
        val builder    = FakeSearchablePdfBuilder(onMakeSearchable)
        return MakeSearchableUseCase(builder, repository) to dao
    }

    private fun record(id: Long, isSearchable: Boolean, exists: Boolean = true): ScanRecord {
        val filepath = if (exists) {
            tmpFolder.newFile("scan_$id.pdf").absolutePath
        } else {
            "/nicht/vorhanden/scan_$id.pdf"
        }
        return ScanRecord(
            id           = id,
            filename     = "scan_$id",
            filepath     = filepath,
            timestamp    = 0L,
            pageCount    = 1,
            fileSize     = 0L,
            isSearchable = isSearchable
        )
    }

    @Test
    fun `verarbeitet nur nicht-durchsuchbare Records`() = runTest {
        val processedFiles = mutableListOf<File>()
        val (useCase, dao) = makeUseCase { file, _ -> processedFiles.add(file) }

        val alreadySearchable = record(1L, isSearchable = true)
        val notYetSearchable  = record(2L, isSearchable = false)

        val count = useCase(listOf(alreadySearchable, notYetSearchable), "de")

        assertEquals(1, count)
        assertEquals(1, processedFiles.size)
        assertEquals(notYetSearchable.filepath, processedFiles[0].absolutePath)
    }

    @Test
    fun `leere Liste oder alle bereits durchsuchbar gibt 0 zurück`() = runTest {
        val (useCase, dao) = makeUseCase()

        val result = useCase(
            listOf(record(1L, isSearchable = true), record(2L, isSearchable = true)),
            "en"
        )

        assertEquals(0, result)
        assertTrue(dao.searchableUpdates.isEmpty())
    }

    @Test
    fun `ruft markSearchable in der DB für jeden verarbeiteten Record auf`() = runTest {
        val (useCase, dao) = makeUseCase()
        val rec = record(42L, isSearchable = false)

        useCase(listOf(rec), "en")

        assertEquals(1, dao.searchableUpdates.size)
        assertEquals(42L, dao.searchableUpdates[0].first)
    }

    @Test
    fun `überspringt Records deren Datei nicht existiert`() = runTest {
        val processedFiles = mutableListOf<File>()
        val (useCase, dao) = makeUseCase { file, _ -> processedFiles.add(file) }

        val missing = record(1L, isSearchable = false, exists = false)
        val present = record(2L, isSearchable = false, exists = true)

        useCase(listOf(missing, present), "de")

        assertEquals(1, processedFiles.size)
        assertEquals(1, dao.searchableUpdates.size)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Fake SearchablePdfBuilder – überschreibt makeSearchable ohne echte Implementierung
// ─────────────────────────────────────────────────────────────────────────────

class FakeSearchablePdfBuilder(
    private val onMakeSearchable: (File, String) -> Unit
) : SearchablePdfBuilder(OcrManager()) {
    override suspend fun makeSearchable(
        pdfFile:      File,
        languageCode: String,
        onProgress:   (Int, Int) -> Unit
    ): Path {
        onMakeSearchable(pdfFile, languageCode)
        return pdfFile.toPath()
    }
}
