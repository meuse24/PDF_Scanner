package info.meuse24.pdf_scanner.ui.tableexport

import androidx.lifecycle.SavedStateHandle
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.gateway.CsvShareFileStore
import info.meuse24.pdf_scanner.domain.gateway.DownloadEntry
import info.meuse24.pdf_scanner.domain.gateway.DownloadsStorage
import info.meuse24.pdf_scanner.domain.gateway.OcrPositionedTextExtractor
import info.meuse24.pdf_scanner.domain.gateway.TableDraftStore
import info.meuse24.pdf_scanner.domain.model.CsvDelimiter
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.OcrElement
import info.meuse24.pdf_scanner.domain.model.OcrLine
import info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus
import info.meuse24.pdf_scanner.domain.model.OcrPositionedPage
import info.meuse24.pdf_scanner.domain.model.OcrRect
import info.meuse24.pdf_scanner.domain.model.TableDraft
import info.meuse24.pdf_scanner.domain.model.TableDraftCell
import info.meuse24.pdf_scanner.domain.model.TableDraftPage
import info.meuse24.pdf_scanner.domain.model.TableDraftRow
import info.meuse24.pdf_scanner.domain.model.TableIssue
import info.meuse24.pdf_scanner.domain.pdf.PdfSecurityOps
import info.meuse24.pdf_scanner.domain.usecase.ExportTableCsvUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExtractTableUseCase
import info.meuse24.pdf_scanner.domain.workflow.DocumentWorkflowGuard
import info.meuse24.pdf_scanner.domain.workflow.ExtractTableWorkflow
import info.meuse24.pdf_scanner.domain.workflow.WorkflowErrorMapper
import info.meuse24.pdf_scanner.testutil.FakeResourceProvider
import info.meuse24.pdf_scanner.testutil.TestDispatcherProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class TableExportViewModelTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: ScanRepository
    private lateinit var recordsFlow: MutableStateFlow<List<Document>>
    private lateinit var resourceProvider: FakeResourceProvider
    private lateinit var draftStore: FakeTableDraftStore
    private lateinit var downloadsStorage: FakeDownloadsStorage
    private lateinit var shareFileStore: FakeShareFileStore

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mock(ScanRepository::class.java)
        recordsFlow = MutableStateFlow(emptyList())
        `when`(repository.getAllScans()).thenReturn(recordsFlow)
        resourceProvider = FakeResourceProvider(
            strings = mapOf(
                R.string.table_export_save_success to "saved",
                R.string.table_export_save_error to "save failed",
                R.string.table_export_share_error to "share failed"
            )
        )
        draftStore = FakeTableDraftStore()
        downloadsStorage = FakeDownloadsStorage()
        shareFileStore = FakeShareFileStore(tmpFolder.root)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `laedt ohne vorhandenen Draft und erkennt automatisch`() = runTest(dispatcher) {
        recordsFlow.value = listOf(record())
        val viewModel = buildViewModel(extractor = FakeExtractor(pages = listOf(tablePage(0))))

        advanceUntilIdle()

        assertFalse(viewModel.loading.value)
        assertNull(viewModel.pendingDraft.value)
        assertEquals(1, viewModel.pages.value.size)
        assertEquals(listOf("R0C0", "R0C1"), viewModel.pages.value.single().rows[0].cells.map { it.text })
    }

    @Test
    fun `vorhandener Draft zeigt Restore-Dialog statt automatisch zu erkennen`() = runTest(dispatcher) {
        val extractor = FakeExtractor(pages = listOf(tablePage(0)))
        val doc = record()
        val fingerprint = matchingFingerprint(doc)
        draftStore.drafts[7L] = TableDraft(
            scanId = 7L,
            savedAtEpochMillis = 0L,
            delimiter = info.meuse24.pdf_scanner.domain.model.CsvDelimiter.SEMICOLON,
            protectFormulas = false,
            pages = listOf(
                TableDraftPage(0, true, listOf(TableDraftRow(true, listOf("gespeichert", "wert").map { TableDraftCell(it) })))
            ),
            sourceFileSize = fingerprint.first,
            sourceLastModified = fingerprint.second,
            sourcePageCount = fingerprint.third
        )
        recordsFlow.value = listOf(doc)
        val viewModel = buildViewModel(extractor)

        advanceUntilIdle()

        assertFalse(viewModel.loading.value)
        assertNotNull(viewModel.pendingDraft.value)
        assertTrue(viewModel.pages.value.isEmpty())
        assertEquals(0, extractor.callCount)
    }

    @Test
    fun `Draft mit veraltetem Fingerabdruck wird verworfen statt angeboten`() = runTest(dispatcher) {
        // Simuliert: Seiten der PDF wurden unter derselben scanId gedreht/geloescht/neu sortiert,
        // seit der Draft gespeichert wurde - Groesse/Aenderungszeit/Seitenzahl passen nicht mehr.
        val extractor = FakeExtractor(pages = listOf(tablePage(0)))
        val doc = record()
        draftStore.drafts[7L] = TableDraft(
            scanId = 7L,
            savedAtEpochMillis = 0L,
            delimiter = CsvDelimiter.SEMICOLON,
            protectFormulas = false,
            pages = listOf(
                TableDraftPage(0, true, listOf(TableDraftRow(true, listOf("veraltet", "wert").map { TableDraftCell(it) })))
            ),
            sourceFileSize = 999_999L,
            sourceLastModified = 123L,
            sourcePageCount = 7
        )
        recordsFlow.value = listOf(doc)
        val viewModel = buildViewModel(extractor)

        advanceUntilIdle()

        assertFalse(viewModel.loading.value)
        assertNull("Veralteter Draft darf keinen Restore-Dialog ausloesen", viewModel.pendingDraft.value)
        assertEquals(1, extractor.callCount)
        assertEquals(listOf("R0C0", "R0C1"), viewModel.pages.value.single().rows[0].cells.map { it.text })
        // Die erfolgreiche Neu-Erkennung speichert automatisch einen frischen Draft unter
        // derselben scanId (gewolltes Verhalten) - entscheidend ist, dass er die veralteten
        // Zellwerte nicht mehr enthaelt.
        assertEquals(
            listOf("R0C0", "R0C1"),
            draftStore.drafts.getValue(7L).pages.single().rows[0].cells.map { it.text }
        )
    }

    @Test
    fun `continueDraft uebernimmt Entwurfsinhalte`() = runTest(dispatcher) {
        val doc = record()
        val fingerprint = matchingFingerprint(doc)
        draftStore.drafts[7L] = TableDraft(
            scanId = 7L,
            savedAtEpochMillis = 0L,
            delimiter = info.meuse24.pdf_scanner.domain.model.CsvDelimiter.SEMICOLON,
            protectFormulas = false,
            pages = listOf(
                TableDraftPage(0, true, listOf(TableDraftRow(true, listOf("gespeichert", "wert").map { TableDraftCell(it) })))
            ),
            sourceFileSize = fingerprint.first,
            sourceLastModified = fingerprint.second,
            sourcePageCount = fingerprint.third
        )
        recordsFlow.value = listOf(doc)
        val viewModel = buildViewModel(FakeExtractor(pages = listOf(tablePage(0))))
        advanceUntilIdle()

        viewModel.continueDraft()

        assertNull(viewModel.pendingDraft.value)
        assertEquals(listOf("gespeichert", "wert"), viewModel.pages.value.single().rows[0].cells.map { it.text })
        assertEquals(info.meuse24.pdf_scanner.domain.model.CsvDelimiter.SEMICOLON, viewModel.dialect.value.delimiter)
        assertFalse(viewModel.dialect.value.protectFormulas)
    }

    @Test
    fun `discardDraftAndReextract loescht Draft und erkennt neu`() = runTest(dispatcher) {
        val doc = record()
        val fingerprint = matchingFingerprint(doc)
        draftStore.drafts[7L] = TableDraft(
            scanId = 7L,
            savedAtEpochMillis = 0L,
            delimiter = info.meuse24.pdf_scanner.domain.model.CsvDelimiter.COMMA,
            protectFormulas = true,
            pages = listOf(
                TableDraftPage(0, true, listOf(TableDraftRow(true, listOf("alt").map { TableDraftCell(it) })))
            ),
            sourceFileSize = fingerprint.first,
            sourceLastModified = fingerprint.second,
            sourcePageCount = fingerprint.third
        )
        recordsFlow.value = listOf(doc)
        val extractor = FakeExtractor(pages = listOf(tablePage(0)))
        val viewModel = buildViewModel(extractor)
        advanceUntilIdle()
        assertNotNull("Vorbedingung: der frische Draft muss den Restore-Dialog zeigen", viewModel.pendingDraft.value)

        viewModel.discardDraftAndReextract()
        advanceUntilIdle()

        assertNull(viewModel.pendingDraft.value)
        assertEquals(1, extractor.callCount)
        // Der alte Draft wurde geloescht, die erfolgreiche Neu-Erkennung speichert aber
        // automatisch einen frischen Draft mit dem neuen Ergebnis - das ist gewolltes Verhalten.
        assertEquals(1, viewModel.pages.value.size)
        assertTrue(draftStore.drafts.containsKey(7L))
        assertEquals(
            listOf("R0C0", "R0C1"),
            draftStore.drafts.getValue(7L).pages.single().rows[0].cells.map { it.text }
        )
    }

    @Test
    fun `tabellenloses Dokument setzt noTableMessage statt Fehler`() = runTest(dispatcher) {
        recordsFlow.value = listOf(record())
        val viewModel = buildViewModel(FakeExtractor(pages = listOf(textOnlyPage(0))))

        advanceUntilIdle()

        assertNotNull(viewModel.noTableMessage.value)
        assertNull(viewModel.error.value)
        assertTrue(viewModel.pages.value.isEmpty())
    }

    @Test
    fun `editCell aendert nur die Zielzelle`() = runTest(dispatcher) {
        recordsFlow.value = listOf(record())
        val viewModel = buildViewModel(FakeExtractor(pages = listOf(tablePage(0))))
        advanceUntilIdle()

        viewModel.editCell(pageIndex = 0, rowIndex = 0, columnIndex = 1, newText = "Bürostuhl")

        assertEquals("Bürostuhl", viewModel.pages.value.single().rows[0].cells[1].text)
    }

    @Test
    fun `toggleRowIncluded und togglePageSelectedForExport wirken unabhaengig`() = runTest(dispatcher) {
        recordsFlow.value = listOf(record())
        val viewModel = buildViewModel(FakeExtractor(pages = listOf(tablePage(0))))
        advanceUntilIdle()

        viewModel.toggleRowIncluded(pageIndex = 0, rowIndex = 1, included = false)
        viewModel.togglePageSelectedForExport(pageIndex = 0, selected = false)

        val page = viewModel.pages.value.single()
        assertTrue(page.rows[0].included)
        assertFalse(page.rows[1].included)
        assertFalse(page.selectedForExport)
    }

    @Test
    fun `resetPage verwirft Edits behaelt aber Export-Auswahl`() = runTest(dispatcher) {
        recordsFlow.value = listOf(record())
        val viewModel = buildViewModel(FakeExtractor(pages = listOf(tablePage(0))))
        advanceUntilIdle()

        viewModel.editCell(0, 0, 1, "geaendert")
        viewModel.togglePageSelectedForExport(0, false)
        viewModel.resetPage(0)

        val page = viewModel.pages.value.single()
        assertEquals("R0C1", page.rows[0].cells[1].text)
        assertFalse(page.selectedForExport)
    }

    @Test
    fun `resetPage funktioniert auch nach continueDraft und stellt Issues wieder her`() = runTest(dispatcher) {
        val doc = record()
        val fingerprint = matchingFingerprint(doc)
        draftStore.drafts[7L] = TableDraft(
            scanId = 7L,
            savedAtEpochMillis = 0L,
            delimiter = CsvDelimiter.SEMICOLON,
            protectFormulas = false,
            pages = listOf(
                TableDraftPage(
                    pageIndex = 0,
                    selectedForExport = true,
                    rows = listOf(TableDraftRow(true, listOf(TableDraftCell("bearbeitet"), TableDraftCell("wert")))),
                    originalRows = listOf(
                        TableDraftRow(
                            true,
                            listOf(
                                TableDraftCell("original", issues = setOf(TableIssue.LOW_CONFIDENCE)),
                                TableDraftCell("wert")
                            )
                        )
                    )
                )
            ),
            sourceFileSize = fingerprint.first,
            sourceLastModified = fingerprint.second,
            sourcePageCount = fingerprint.third
        )
        recordsFlow.value = listOf(doc)
        val viewModel = buildViewModel(FakeExtractor(pages = listOf(tablePage(0))))
        advanceUntilIdle()

        viewModel.continueDraft()
        viewModel.resetPage(0)

        val page = viewModel.pages.value.single()
        assertEquals("original", page.rows[0].cells[0].text)
        assertTrue(page.rows[0].cells[0].issues.contains(TableIssue.LOW_CONFIDENCE))
    }

    @Test
    fun `Draft wird nach Edit gedebounced gespeichert`() = runTest(dispatcher) {
        recordsFlow.value = listOf(record())
        val viewModel = buildViewModel(FakeExtractor(pages = listOf(tablePage(0))))
        advanceUntilIdle()
        assertTrue(draftStore.drafts.containsKey(7L)) // nach erfolgreicher Extraktion bereits gespeichert

        draftStore.drafts.clear()
        viewModel.editCell(0, 0, 0, "neu")
        assertTrue("Draft darf nicht sofort geschrieben werden", draftStore.drafts.isEmpty())

        advanceUntilIdle()

        assertEquals("neu", draftStore.drafts.getValue(7L).pages.single().rows[0].cells[0].text)
    }

    @Test
    fun `saveToDownloads erfolgreich setzt success und loescht Draft`() = runTest(dispatcher) {
        recordsFlow.value = listOf(record())
        val viewModel = buildViewModel(FakeExtractor(pages = listOf(tablePage(0))))
        advanceUntilIdle()

        viewModel.saveToDownloads()
        advanceUntilIdle()

        assertEquals("saved", viewModel.success.value)
        assertFalse(draftStore.drafts.containsKey(7L))
        assertEquals(1, downloadsStorage.written.size)
    }

    @Test
    fun `Export storniert einen laufenden Debounce-Save statt den geloeschten Draft wiederherzustellen`() = runTest(dispatcher) {
        recordsFlow.value = listOf(record())
        val viewModel = buildViewModel(FakeExtractor(pages = listOf(tablePage(0))))
        advanceUntilIdle()
        assertTrue(draftStore.drafts.containsKey(7L)) // nach erfolgreicher Extraktion bereits gespeichert

        // Ein Edit kurz vor dem Export plant einen verzoegerten Draft-Save (800ms). Der Export
        // muss ihn stornieren, statt dass er nach dem geloeschten Draft noch verspaetet einen
        // neuen schreibt.
        viewModel.editCell(0, 0, 0, "frisch editiert")
        viewModel.saveToDownloads()
        advanceUntilIdle()

        assertEquals("saved", viewModel.success.value)
        assertFalse(
            "Draft darf nach erfolgreichem Export nicht durch den alten Debounce-Save wieder auftauchen",
            draftStore.drafts.containsKey(7L)
        )
    }

    @Test
    fun `Seitenfortschritt loescht einen zuvor gesetzten Modellstatus`() = runTest(dispatcher) {
        recordsFlow.value = listOf(record())
        lateinit var viewModel: TableExportViewModel
        var ocrStatusWhileProgressing: OcrPipelineStatus? = OcrPipelineStatus.InstallingModel
        val extractor = FakeExtractor(
            pages = listOf(tablePage(0)),
            duringExtraction = { onStatus, onProgress ->
                onStatus(OcrPipelineStatus.InstallingModel)
                onProgress(1, 1)
                ocrStatusWhileProgressing = viewModel.ocrStatus.value
            }
        )
        viewModel = buildViewModel(extractor)

        advanceUntilIdle()

        assertNull("Modellstatus muss sobald Seitenfortschritt eintrifft geloescht sein", ocrStatusWhileProgressing)
    }

    @Test
    fun `Fehlgeschlagenes Laden des Drafts faellt fail-soft auf frische Extraktion zurueck`() = runTest(dispatcher) {
        draftStore.failLoad = true
        recordsFlow.value = listOf(record())
        val extractor = FakeExtractor(pages = listOf(tablePage(0)))

        // Wuerde die IOException aus loadDraft() unbehandelt aus der launch()-Coroutine
        // entkommen, liesse runTest() den Test mit genau dieser Exception fehlschlagen.
        val viewModel = buildViewModel(extractor)
        advanceUntilIdle()

        assertFalse(viewModel.loading.value)
        assertNull(viewModel.pendingDraft.value)
        assertEquals(1, extractor.callCount)
        assertEquals(1, viewModel.pages.value.size)
    }

    @Test
    fun `Fehlgeschlagenes Aufraeumen alter Drafts beim Start verhindert die Extraktion nicht`() = runTest(dispatcher) {
        draftStore.failCleanup = true
        recordsFlow.value = listOf(record())

        val viewModel = buildViewModel(FakeExtractor(pages = listOf(tablePage(0))))
        advanceUntilIdle()

        assertFalse(viewModel.loading.value)
        assertEquals(1, viewModel.pages.value.size)
    }

    @Test
    fun `Fehlgeschlagenes Speichern des Debounce-Drafts bringt den Screen nicht zum Absturz`() = runTest(dispatcher) {
        draftStore.failSave = true
        recordsFlow.value = listOf(record())
        val viewModel = buildViewModel(FakeExtractor(pages = listOf(tablePage(0))))
        advanceUntilIdle() // die automatische Speicherung nach der Extraktion schlaegt bereits fehl

        viewModel.editCell(0, 0, 0, "geaendert trotz Speicherfehler")
        advanceUntilIdle() // der verzoegerte Save nach dem Edit schlaegt ebenfalls fehl

        assertEquals("geaendert trotz Speicherfehler", viewModel.pages.value.single().rows[0].cells[0].text)
        assertNull(viewModel.error.value)
        assertTrue(draftStore.drafts.isEmpty())
    }

    @Test
    fun `Fehlgeschlagenes Loeschen des Drafts nach Export bleibt ein Erfolg`() = runTest(dispatcher) {
        draftStore.failDelete = true
        recordsFlow.value = listOf(record())
        val viewModel = buildViewModel(FakeExtractor(pages = listOf(tablePage(0))))
        advanceUntilIdle()

        viewModel.saveToDownloads()
        advanceUntilIdle()

        assertEquals("saved", viewModel.success.value)
        assertNull(viewModel.error.value)
    }

    @Test
    fun `saveToDownloads Fehler setzt error`() = runTest(dispatcher) {
        recordsFlow.value = listOf(record())
        downloadsStorage.shouldFail = true
        val viewModel = buildViewModel(FakeExtractor(pages = listOf(tablePage(0))))
        advanceUntilIdle()

        viewModel.saveToDownloads()
        advanceUntilIdle()

        assertEquals("save failed", viewModel.error.value)
    }

    @Test
    fun `shareFiles erfolgreich setzt shareRequest`() = runTest(dispatcher) {
        recordsFlow.value = listOf(record())
        val viewModel = buildViewModel(FakeExtractor(pages = listOf(tablePage(0))))
        advanceUntilIdle()

        viewModel.shareFiles()
        advanceUntilIdle()

        assertNotNull(viewModel.shareRequest.value)
        assertEquals(1, viewModel.shareRequest.value?.files?.size)

        viewModel.onShareRequestHandled()
        assertNull(viewModel.shareRequest.value)
    }

    @Test
    fun `shareFiles erfasst den Dialekt einmalig trotz Aenderung waehrend des Schreibens`() = runTest(dispatcher) {
        recordsFlow.value = listOf(record())
        lateinit var viewModel: TableExportViewModel
        // Simuliert einen Nutzer, der das Trennzeichen wechselt, waehrend der Export noch laeuft:
        // der Hook feuert erst NACH dem erfassten Dialekt-Snapshot in shareFiles(), aber VOR dem
        // Aufbau von ShareFilesRequest. Ohne den Fix (einmalige Erfassung) wuerde die spaeter
        // gebaute ShareFilesRequest den neuen Dialekt tragen, obwohl der Dateiinhalt/-name den
        // alten verwendet - ein Mismatch fuer den Empfaenger.
        shareFileStore = FakeShareFileStore(tmpFolder.root, duringWrite = { viewModel.selectDelimiter(CsvDelimiter.TAB) })
        viewModel = buildViewModel(FakeExtractor(pages = listOf(tablePage(0))))
        advanceUntilIdle()
        viewModel.selectDelimiter(CsvDelimiter.COMMA)

        viewModel.shareFiles()
        advanceUntilIdle()

        val request = viewModel.shareRequest.value
        assertNotNull(request)
        assertEquals(
            "Dateiinhalt und ShareFilesRequest muessen denselben, beim Aufruf aktiven Dialekt verwenden",
            CsvDelimiter.COMMA,
            request!!.dialect.delimiter
        )
    }

    @Test
    fun `Doppel-Tap auf Retry startet nur eine Extraktion`() = runTest(dispatcher) {
        recordsFlow.value = listOf(record())
        val extractor = FakeExtractor(pages = listOf(tablePage(0)))
        val viewModel = buildViewModel(extractor)
        advanceUntilIdle()
        val callsAfterInitialLoad = extractor.callCount

        // Beide Aufrufe erfolgen VOR advanceUntilIdle(): unter StandardTestDispatcher wird keiner
        // der beiden launch()-Blöcke ausgefuehrt, bevor beide reExtract()-Aufrufe zurueckgekehrt
        // sind - genau das Fenster eines schnellen Doppel-Tap auf "Erneut versuchen".
        viewModel.reExtract("auto")
        viewModel.reExtract("auto")
        advanceUntilIdle()

        assertEquals(
            "Ein Doppel-Tap darf nur eine Erkennung auslösen, nicht zwei parallele OCR-Läufe",
            callsAfterInitialLoad + 1,
            extractor.callCount
        )
    }

    private fun buildViewModel(extractor: OcrPositionedTextExtractor, encrypted: Boolean = false): TableExportViewModel {
        val extractTableUseCase = ExtractTableUseCase(extractor)
        val guard = DocumentWorkflowGuard(FakePdfSecurityOps(encrypted))
        val workflow = ExtractTableWorkflow(extractTableUseCase, guard)
        val exportUseCase = ExportTableCsvUseCase(downloadsStorage, shareFileStore, TestDispatcherProvider(dispatcher))
        val errorMapper = WorkflowErrorMapper(resourceProvider)
        return TableExportViewModel(
            repository = repository,
            extractTableWorkflow = workflow,
            exportTableCsvUseCase = exportUseCase,
            tableDraftStore = draftStore,
            errorMapper = errorMapper,
            resourceProvider = resourceProvider,
            dispatcherProvider = TestDispatcherProvider(dispatcher),
            savedStateHandle = SavedStateHandle(mapOf("scanId" to 7L))
        )
    }

    private fun record() = Document(
        id = 7L,
        filename = "scan",
        filepath = tmpFolder.newFile("scan.pdf").apply { writeText("pdf") }.absolutePath,
        timestamp = 0L,
        pageCount = 1,
        fileSize = 0L
    )

    /** Fingerabdruck (Groesse, Aenderungszeit, Seitenzahl), der zum echten [record]-File passt -
     * damit ein Test-Draft von [TableExportViewModel.isDraftFresh] als aktuell erkannt wird. */
    private fun matchingFingerprint(document: Document): Triple<Long, Long, Int> {
        val file = File(document.filepath)
        return Triple(file.length(), file.lastModified(), document.pageCount)
    }
}

private fun rect(left: Float, top: Float, width: Float, height: Float = 20f) =
    OcrRect(left, top, left + width, top + height)

private fun line(text: String, left: Float, top: Float, width: Float): OcrLine {
    val bounds = rect(left, top, width)
    val element = OcrElement(text, bounds, confidence = 0.9f, angleDeg = 0f)
    return OcrLine(text, bounds, listOf(element), element.confidence, element.angleDeg)
}

private fun tablePage(pageIndex: Int): OcrPositionedPage {
    val lines = (0 until 3).flatMap { row ->
        val top = row * 30f
        listOf(line("R${row}C0", 0f, top, 100f), line("R${row}C1", 150f, top, 100f))
    }
    return OcrPositionedPage(pageIndex = pageIndex, widthPx = 600, heightPx = 800, lines = lines)
}

private fun textOnlyPage(pageIndex: Int): OcrPositionedPage {
    val lines = (0 until 3).map { idx ->
        line("Zeile $idx normaler Fließtext ohne jede Tabellenstruktur.", 0f, idx * 30f, 400f)
    }
    return OcrPositionedPage(pageIndex = pageIndex, widthPx = 600, heightPx = 800, lines = lines)
}

private class FakeExtractor(
    private val pages: List<OcrPositionedPage> = emptyList(),
    private val duringExtraction: ((onStatus: (OcrPipelineStatus) -> Unit, onProgress: (Int, Int) -> Unit) -> Unit)? = null
) : OcrPositionedTextExtractor {
    var callCount = 0
        private set

    override suspend fun extract(
        document: Document,
        languageCode: String,
        onStatus: (OcrPipelineStatus) -> Unit,
        onProgress: (current: Int, total: Int) -> Unit
    ): List<OcrPositionedPage> {
        callCount++
        duringExtraction?.invoke(onStatus, onProgress)
        return pages
    }
}

private class FakePdfSecurityOps(private val encrypted: Boolean) : PdfSecurityOps {
    override fun protectPdf(input: File, outputDir: File, password: String): File = error("not used")
    override fun unlockPdf(input: File, outputDir: File, password: String): File = error("not used")
    override fun removePassword(input: File, outputDir: File): File = error("not used")
    override fun restrictUsage(
        input: File,
        outputDir: File,
        ownerPassword: String,
        canPrint: Boolean,
        canCopy: Boolean,
        canEdit: Boolean
    ): File = error("not used")

    override fun isPdfEncrypted(input: File): Boolean = encrypted
}

private class FakeTableDraftStore : TableDraftStore {
    val drafts = mutableMapOf<Long, TableDraft>()
    var failSave = false
    var failLoad = false
    var failDelete = false
    var failCleanup = false

    override suspend fun saveDraft(draft: TableDraft) {
        if (failSave) throw IOException("simulierter Speicherfehler (z. B. Speicherplatzmangel)")
        drafts[draft.scanId] = draft
    }

    override suspend fun loadDraft(scanId: Long): TableDraft? {
        if (failLoad) throw IOException("simulierter Lesefehler")
        return drafts[scanId]
    }

    override suspend fun deleteDraft(scanId: Long) {
        if (failDelete) throw IOException("simulierter Loeschfehler")
        drafts.remove(scanId)
    }

    override suspend fun deleteStaleDrafts() {
        if (failCleanup) throw IOException("simulierter Cleanup-Fehler")
    }
}

private class FakeDownloadsStorage : DownloadsStorage {
    var shouldFail = false
    val written = mutableListOf<String>()

    override fun writeDownload(displayName: String, mimeType: String, writer: (OutputStream) -> Unit): DownloadEntry {
        if (shouldFail) error("simulierter Fehler")
        val output = ByteArrayOutputStream()
        writer(output)
        written += displayName
        return object : DownloadEntry {
            override val displayName: String = displayName
            override fun delete() = Unit
        }
    }
}

private class FakeShareFileStore(
    private val baseDir: File,
    private val duringWrite: (() -> Unit)? = null
) : CsvShareFileStore {
    override fun writeShareFile(displayName: String, writer: (OutputStream) -> Unit): File {
        duringWrite?.invoke()
        val file = File(baseDir, displayName)
        file.outputStream().use { writer(it) }
        return file
    }
}
