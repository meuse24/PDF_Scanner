package info.meuse24.pdf_scanner.domain.usecase

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.util.OcrManager
import info.meuse24.pdf_scanner.util.SearchablePdfBuilder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit-Tests für MakeSearchableUseCase.
 * Idempotenz: nur Records mit isSearchable=false ODER extractedText=null werden verarbeitet.
 */
class MakeSearchableUseCaseTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private fun makeUseCase(
        onMakeSearchable: (File, String) -> Unit = { _, _ -> }
    ): Pair<MakeSearchableUseCase, FakeScanDao> {
        val dao        = FakeScanDao()
        val repository = ScanRepository(dao)
        val builder    = FakeSearchablePdfBuilder(onMakeSearchable)
        val autoTag    = AutoTagUseCase()
        return MakeSearchableUseCase(builder, repository, autoTag) to dao
    }

    private fun record(
        id: Long,
        isSearchable: Boolean,
        exists: Boolean = true,
        extractedText: String? = null
    ): ScanRecord {
        val filepath = if (exists) {
            tmpFolder.newFile("scan_$id.pdf").absolutePath
        } else {
            "/nicht/vorhanden/scan_$id.pdf"
        }
        return ScanRecord(
            id            = id,
            filename      = "scan_$id",
            filepath      = filepath,
            timestamp     = 0L,
            pageCount     = 1,
            fileSize      = 0L,
            isSearchable  = isSearchable,
            extractedText = extractedText
        )
    }

    @Test
    fun `verarbeitet nur nicht-durchsuchbare Records`() = runTest {
        val processedFiles = mutableListOf<File>()
        val (useCase, _) = makeUseCase { file, _ -> processedFiles.add(file) }

        // isSearchable=true + extractedText gesetzt → wirklich vollständig verarbeitet → wird übersprungen
        val alreadySearchable = record(1L, isSearchable = true, extractedText = "Rechnung")
        val notYetSearchable  = record(2L, isSearchable = false)

        val count = useCase(listOf(alreadySearchable, notYetSearchable), "de")

        assertEquals(1, count)
        assertEquals(1, processedFiles.size)
        assertEquals(notYetSearchable.filepath, processedFiles[0].absolutePath)
    }

    @Test
    fun `leere Liste oder alle vollstaendig verarbeitet gibt 0 zurueck`() = runTest {
        val (useCase, dao) = makeUseCase()

        // Beide Records haben isSearchable=true + extractedText gesetzt → werden übersprungen
        val result = useCase(
            listOf(
                record(1L, isSearchable = true, extractedText = "Text A"),
                record(2L, isSearchable = true, extractedText = "Text B")
            ),
            "en"
        )

        assertEquals(0, result)
        assertTrue(dao.searchableWithContentUpdates.isEmpty())
    }

    @Test
    fun `isSearchable=true aber extractedText=null wird nachverarbeitet (Backfill v4)`() = runTest {
        val processedFiles = mutableListOf<File>()
        val (useCase, dao) = makeUseCase { file, _ -> processedFiles.add(file) }

        // Simuliert v4-Migration: isSearchable=true, aber extractedText noch nicht gespeichert
        val legacyRecord = record(1L, isSearchable = true, extractedText = null)

        val count = useCase(listOf(legacyRecord), "de")

        assertEquals(1, count)
        assertEquals(1, processedFiles.size)
        assertEquals(1, dao.searchableWithContentUpdates.size)
    }

    @Test
    fun `ruft markSearchableWithContent in der DB für jeden verarbeiteten Record auf`() = runTest {
        val (useCase, dao) = makeUseCase()
        val rec = record(42L, isSearchable = false)

        useCase(listOf(rec), "en")

        assertEquals(1, dao.searchableWithContentUpdates.size)
        assertEquals(42L, dao.searchableWithContentUpdates[0].first)
    }

    @Test
    fun `überspringt Records deren Datei nicht existiert`() = runTest {
        val processedFiles = mutableListOf<File>()
        val (useCase, dao) = makeUseCase { file, _ -> processedFiles.add(file) }

        val missing = record(1L, isSearchable = false, exists = false)
        val present = record(2L, isSearchable = false, exists = true)

        useCase(listOf(missing, present), "de")

        assertEquals(1, processedFiles.size)
        assertEquals(1, dao.searchableWithContentUpdates.size)
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
    ): String {
        onMakeSearchable(pdfFile, languageCode)
        return ""
    }
}
