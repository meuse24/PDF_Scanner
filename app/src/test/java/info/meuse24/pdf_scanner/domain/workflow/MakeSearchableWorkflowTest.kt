package info.meuse24.pdf_scanner.domain.workflow

import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.FakeScanDao
import info.meuse24.pdf_scanner.domain.usecase.FakeSearchablePdfBuilder
import info.meuse24.pdf_scanner.domain.usecase.MakeSearchableUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MakeSearchableWorkflowTest {

    @get:Rule val tmpFolder = TemporaryFolder()

    private fun workflow(
        onMakeSearchable: (File, String) -> Unit = { _, _ -> }
    ): MakeSearchableWorkflow {
        val repository = ScanRepository(FakeScanDao())
        val useCase = MakeSearchableUseCase(
            FakeSearchablePdfBuilder(onMakeSearchable),
            repository
        )
        return MakeSearchableWorkflow(useCase)
    }

    private fun record(
        id: Long,
        isSearchable: Boolean,
        exists: Boolean = true,
        extractedText: String? = null
    ): ScanRecord {
        val filePath = if (exists) {
            tmpFolder.newFile("scan_$id.pdf").absolutePath
        } else {
            tmpFolder.root.resolve("missing_$id.pdf").absolutePath
        }
        return ScanRecord(
            id            = id,
            filename      = "scan_$id",
            filepath      = filePath,
            timestamp     = 0L,
            pageCount     = 1,
            fileSize      = 0L,
            isSearchable  = isSearchable,
            extractedText = extractedText
        )
    }

    @Test
    fun `leere Auswahl liefert NothingSelected`() = runTest {
        val result = workflow()(emptyList(), "de")

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(ScanWorkflowError.NothingSelected, (result as WorkflowResult.Failure).error)
    }

    @Test
    fun `bereits durchsuchbare Auswahl liefert NoEligibleScans`() = runTest {
        // isSearchable=true + extractedText gesetzt → vollständig verarbeitet → NoEligibleScans
        val result = workflow()(listOf(record(1L, isSearchable = true, extractedText = "Rechnung")), "de")

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(ScanWorkflowError.NoEligibleScans, (result as WorkflowResult.Failure).error)
    }

    @Test
    fun `fehlende Datei liefert MissingFiles und startet keinen OCR Lauf`() = runTest {
        var invocations = 0
        val result = workflow { _, _ -> invocations++ }(
            listOf(record(1L, isSearchable = false, exists = false)),
            "de"
        )

        assertTrue(result is WorkflowResult.Failure)
        assertEquals(
            ScanWorkflowError.MissingFiles(listOf("scan_1")),
            (result as WorkflowResult.Failure).error
        )
        assertEquals(0, invocations)
    }

    @Test
    fun `erfolgreicher Ablauf liefert verarbeiteten Count und Dateinamen`() = runTest {
        val result = workflow()(listOf(record(7L, isSearchable = false)), "de")

        assertTrue(result is WorkflowResult.Success)
        val data = (result as WorkflowResult.Success).value
        assertEquals(1, data.processedCount)
        assertEquals("scan_7", data.firstFilename)
    }

    @Test
    fun `force=true erzwingt Verarbeitung auch bei bereits durchsuchbaren Scans`() = runTest {
        var invocations = 0
        val record = record(1L, isSearchable = true, extractedText = "Existiert")
        val result = workflow { _, _ -> invocations++ }(
            records = listOf(record),
            languageCode = "de",
            force = true
        )

        assertTrue(result is WorkflowResult.Success)
        assertEquals(1, invocations)
    }
}
