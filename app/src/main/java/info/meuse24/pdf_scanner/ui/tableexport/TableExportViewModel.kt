package info.meuse24.pdf_scanner.ui.tableexport

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.gateway.DispatcherProvider
import info.meuse24.pdf_scanner.domain.gateway.ResourceProvider
import info.meuse24.pdf_scanner.domain.gateway.TableDraftStore
import info.meuse24.pdf_scanner.domain.model.CsvDelimiter
import info.meuse24.pdf_scanner.domain.model.DelimitedTextDialect
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.OcrPipelineStatus
import info.meuse24.pdf_scanner.domain.model.TableDraft
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.domain.usecase.ExportTableCsvUseCase
import info.meuse24.pdf_scanner.domain.workflow.ExtractTableWorkflow
import info.meuse24.pdf_scanner.domain.workflow.ScanWorkflowError
import info.meuse24.pdf_scanner.domain.workflow.WorkflowErrorMapper
import info.meuse24.pdf_scanner.domain.workflow.WorkflowResult
import info.meuse24.pdf_scanner.ui.ocr.OCR_LANGUAGE_AUTO
import info.meuse24.pdf_scanner.ui.ocr.defaultOcrLanguage
import info.meuse24.pdf_scanner.util.systemDefaultCsvDelimiter
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val DRAFT_SAVE_DEBOUNCE_MILLIS = 800L

/** Ergebnis eines Teilen-Exports, das der Screen als Android-Share-Intent umsetzt (braucht Context). */
data class ShareFilesRequest(val files: List<File>, val dialect: DelimitedTextDialect)

@HiltViewModel
class TableExportViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val extractTableWorkflow: ExtractTableWorkflow,
    private val exportTableCsvUseCase: ExportTableCsvUseCase,
    private val tableDraftStore: TableDraftStore,
    private val errorMapper: WorkflowErrorMapper,
    private val resourceProvider: ResourceProvider,
    private val dispatcherProvider: DispatcherProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val scanId: Long = checkNotNull(savedStateHandle["scanId"])

    private val _record = MutableStateFlow<Document?>(null)
    val record: StateFlow<Document?> = _record.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _ocrStatus = MutableStateFlow<OcrPipelineStatus?>(null)
    val ocrStatus: StateFlow<OcrPipelineStatus?> = _ocrStatus.asStateFlow()

    private val _pageProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val pageProgress: StateFlow<Pair<Int, Int>?> = _pageProgress.asStateFlow()

    private val _pages = MutableStateFlow<List<EditableTablePage>>(emptyList())
    val pages: StateFlow<List<EditableTablePage>> = _pages.asStateFlow()

    private val _selectedPageIndex = MutableStateFlow(0)
    val selectedPageIndex: StateFlow<Int> = _selectedPageIndex.asStateFlow()

    private val _dialect = MutableStateFlow(DelimitedTextDialect(delimiter = systemDefaultCsvDelimiter()))
    val dialect: StateFlow<DelimitedTextDialect> = _dialect.asStateFlow()

    private val _noTableMessage = MutableStateFlow<String?>(null)
    val noTableMessage: StateFlow<String?> = _noTableMessage.asStateFlow()

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    private val _success = MutableStateFlow<String?>(null)
    val success: StateFlow<String?> = _success.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _pendingDraft = MutableStateFlow<TableDraft?>(null)
    val pendingDraft: StateFlow<TableDraft?> = _pendingDraft.asStateFlow()

    private val _shareRequest = MutableStateFlow<ShareFilesRequest?>(null)
    val shareRequest: StateFlow<ShareFilesRequest?> = _shareRequest.asStateFlow()

    private var originalPages: Map<Int, EditableTablePage> = emptyMap()
    private var draftSaveJob: Job? = null
    private var initialLoadRequested = false

    init {
        viewModelScope.launch(dispatcherProvider.io) {
            runCatchingCancellable { tableDraftStore.deleteStaleDrafts() }
        }
        viewModelScope.launch {
            repository.getAllScans().collect { scans ->
                val found = scans.find { it.id == scanId }
                if (found != null && !initialLoadRequested) {
                    initialLoadRequested = true
                    _record.value = found
                    checkForDraftOrExtract(found)
                }
            }
        }
    }

    private fun checkForDraftOrExtract(record: Document) {
        viewModelScope.launch(dispatcherProvider.io) {
            val draft = runCatchingCancellable { tableDraftStore.loadDraft(scanId) }.getOrNull()
            val freshDraft = draft?.takeIf { isDraftFresh(it, record) }
            if (draft != null && freshDraft == null) {
                // Quelle hat sich seit der Extraktion veraendert (Seiten gedreht/geloescht/neu
                // sortiert unter derselben scanId) - der Draft wuerde Zeilen der alten PDF-Version
                // anbieten. Verwerfen statt anzubieten, fail-soft wie jede andere Draft-Op.
                runCatchingCancellable { tableDraftStore.deleteDraft(scanId) }
            }
            when (freshDraft) {
                null -> extract(record, defaultOcrLanguage())
                else -> {
                    _pendingDraft.value = freshDraft
                    _loading.value = false
                }
            }
        }
    }

    /** Vergleicht den beim Speichern erfassten Quell-Fingerabdruck mit dem aktuellen Dateistand. */
    private fun isDraftFresh(draft: TableDraft, record: Document): Boolean {
        val file = File(record.filepath)
        return draft.sourceFileSize == file.length() &&
            draft.sourceLastModified == file.lastModified() &&
            draft.sourcePageCount == record.pageCount
    }

    /** Übernimmt den zwischengespeicherten Entwurf statt neu zu erkennen. */
    fun continueDraft() {
        val draft = _pendingDraft.value ?: return
        _dialect.value = DelimitedTextDialect(draft.delimiter, draft.protectFormulas)
        _pages.value = draft.pages.map { it.toEditablePage() }
        originalPages = draft.pages.associate { page ->
            page.pageIndex to page.copy(rows = page.originalRows).toEditablePage()
        }
        _selectedPageIndex.value = 0
        _pendingDraft.value = null
        _loading.value = false
    }

    /** Verwirft den zwischengespeicherten Entwurf und erkennt die Tabelle neu. */
    fun discardDraftAndReextract(languageCode: String = OCR_LANGUAGE_AUTO) {
        val record = _record.value ?: return
        if (_loading.value) return
        // Synchron gesetzt, bevor die Coroutine startet: ein zweiter, ganz schnell
        // aufeinanderfolgender Aufruf (Doppel-Tap) sieht _loading bereits true und bricht ab,
        // statt eine zweite parallele Erkennung fuer dieselbe PDF zu starten.
        _loading.value = true
        val draftScanId = _pendingDraft.value?.scanId
        _pendingDraft.value = null
        viewModelScope.launch(dispatcherProvider.io) {
            draftScanId?.let { clearDraft(it) }
            extract(record, languageCode)
        }
    }

    fun reExtract(languageCode: String) {
        val record = _record.value ?: return
        if (_loading.value) return
        // Siehe discardDraftAndReextract(): synchrones Setzen schliesst das Doppel-Tap-Fenster,
        // das entstuende, wuerde _loading erst innerhalb von extract() (nach clearDraft()) gesetzt.
        _loading.value = true
        viewModelScope.launch(dispatcherProvider.io) {
            clearDraft()
            extract(record, languageCode)
        }
    }

    private suspend fun extract(record: Document, languageCode: String) {
        cancelPendingDraftSave()
        _loading.value = true
        _error.value = null
        _noTableMessage.value = null
        _ocrStatus.value = null
        _pageProgress.value = null

        when (
            val result = extractTableWorkflow(
                record = record,
                languageCode = languageCode,
                onStatus = { _ocrStatus.value = it },
                onProgress = { current, total ->
                    // Sobald Seitenfortschritt eintrifft, ist das Modell bereit - ein zuvor
                    // gesetzter Modellstatus (z. B. "wird installiert") darf nicht stehen bleiben.
                    _ocrStatus.value = null
                    _pageProgress.value = current to total
                }
            )
        ) {
            is WorkflowResult.Success -> {
                val tables = result.value.tablesByPage.mapNotNull { (pageIndex, table) ->
                    table?.let { pageIndex to it }
                }.sortedBy { it.first }
                val editablePages = tables.map { (_, table) -> table.toEditablePage() }
                originalPages = editablePages.associateBy { it.pageIndex }
                _pages.value = editablePages
                _selectedPageIndex.value = 0
                scheduleDraftSave()
            }
            is WorkflowResult.Failure -> {
                if (result.error is ScanWorkflowError.NoTableFound) {
                    _noTableMessage.value = errorMapper.map(result.error)
                } else {
                    _error.value = errorMapper.map(result.error)
                }
                originalPages = emptyMap()
                _pages.value = emptyList()
            }
        }

        _ocrStatus.value = null
        _pageProgress.value = null
        _loading.value = false
    }

    fun selectPage(index: Int) {
        if (index in _pages.value.indices) _selectedPageIndex.value = index
    }

    fun editCell(pageIndex: Int, rowIndex: Int, columnIndex: Int, newText: String) {
        updatePage(pageIndex) { it.withCellText(rowIndex, columnIndex, newText) }
    }

    fun toggleRowIncluded(pageIndex: Int, rowIndex: Int, included: Boolean) {
        updatePage(pageIndex) { it.withRowIncluded(rowIndex, included) }
    }

    fun togglePageSelectedForExport(pageIndex: Int, selected: Boolean) {
        updatePage(pageIndex) { it.withSelectedForExport(selected) }
    }

    fun resetPage(pageIndex: Int) {
        val original = originalPages[pageIndex] ?: return
        updatePage(pageIndex) { it.resetTo(original) }
    }

    private fun updatePage(pageIndex: Int, transform: (EditableTablePage) -> EditableTablePage) {
        _pages.value = _pages.value.map { page -> if (page.pageIndex == pageIndex) transform(page) else page }
        scheduleDraftSave()
    }

    fun selectDelimiter(delimiter: CsvDelimiter) {
        _dialect.value = _dialect.value.copy(delimiter = delimiter)
        scheduleDraftSave()
    }

    fun setProtectFormulas(enabled: Boolean) {
        _dialect.value = _dialect.value.copy(protectFormulas = enabled)
        scheduleDraftSave()
    }

    private fun scheduleDraftSave() {
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch(dispatcherProvider.io) {
            delay(DRAFT_SAVE_DEBOUNCE_MILLIS)
            // Komfort-Cache: ein fehlgeschlagenes Schreiben (z. B. Speicherplatzmangel oder ein
            // fehlgeschlagener Atomic-Move) darf die App nicht abstuerzen lassen.
            runCatchingCancellable { saveDraftNow() }
        }
    }

    /** Wartet, bis ein laufender/anstehender Debounce-Save wirklich beendet ist. Verhindert, dass
     * er nach einem expliziten [clearDraft] noch verspaetet einen neuen Draft schreibt. */
    private suspend fun cancelPendingDraftSave() {
        draftSaveJob?.cancelAndJoin()
        draftSaveJob = null
    }

    /** Storniert einen anstehenden Debounce-Save und loescht den Draft danach fail-soft. */
    private suspend fun clearDraft(id: Long = scanId) {
        cancelPendingDraftSave()
        runCatchingCancellable { tableDraftStore.deleteDraft(id) }
    }

    /**
     * Fuehrt [block] aus und wandelt jeden Fehler ausser [CancellationException] in ein
     * [Result.failure] um - der Entwurfs-Cache ist ein Komfort-Feature, ein I/O-Fehler (volle
     * Ablage, fehlgeschlagener Atomic-Move, ...) darf die App nie zum Absturz bringen. Eine
     * Cancellation wird bewusst nicht abgefangen, damit Struktur-Concurrency intakt bleibt.
     */
    private suspend fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun saveDraftNow() {
        val currentPages = _pages.value
        val record = _record.value ?: return
        if (currentPages.isEmpty()) return
        val file = File(record.filepath)
        tableDraftStore.saveDraft(
            TableDraft(
                scanId = scanId,
                savedAtEpochMillis = System.currentTimeMillis(),
                delimiter = _dialect.value.delimiter,
                protectFormulas = _dialect.value.protectFormulas,
                pages = currentPages.map { page -> page.toDraftPage(originalPages[page.pageIndex] ?: page) },
                sourceFileSize = file.length(),
                sourceLastModified = file.lastModified(),
                sourcePageCount = record.pageCount
            )
        )
    }

    private fun selectedCsvPages(): List<info.meuse24.pdf_scanner.domain.usecase.TableCsvPage> =
        _pages.value.filter { it.selectedForExport }.map { it.toCsvPage() }

    fun saveToDownloads() {
        val record = _record.value ?: return
        val csvPages = selectedCsvPages()
        if (csvPages.isEmpty() || _exporting.value) return
        // Einmalig erfasst: Trennzeichen bleibt waehrend des Exports bedienbar, ein Wechsel
        // waehrend eines langsamen Exports darf nicht dazu fuehren, dass Dateiname/-inhalt und
        // ein spaeter gelesener Wert auseinanderlaufen.
        val dialect = _dialect.value

        _exporting.value = true
        _error.value = null
        _success.value = null
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val outcome = exportTableCsvUseCase.saveToDownloads(record.filename, csvPages, dialect)
                _success.value = resourceProvider.getString(R.string.table_export_save_success, outcome.entries.size)
                clearDraft()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                _error.value = resourceProvider.getString(R.string.table_export_save_error)
            } finally {
                _exporting.value = false
            }
        }
    }

    fun shareFiles() {
        val record = _record.value ?: return
        val csvPages = selectedCsvPages()
        if (csvPages.isEmpty() || _exporting.value) return
        // Siehe saveToDownloads(): ein einziger erfasster Dialekt fuer Dateiinhalt UND die
        // spaeter gebaute ShareFilesRequest (MIME-Typ) - sonst koennte ein Delimiter-Wechsel
        // waehrend des Exports zu einer Datei mit dem alten, aber einem Share-Intent mit dem
        // neuen MIME-Typ fuehren (Empfaenger bekaeme falsch interpretierte Daten).
        val dialect = _dialect.value

        _exporting.value = true
        _error.value = null
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val outcome = exportTableCsvUseCase.shareFiles(record.filename, csvPages, dialect)
                _shareRequest.value = ShareFilesRequest(outcome.files, dialect)
                clearDraft()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                _error.value = resourceProvider.getString(R.string.table_export_share_error)
            } finally {
                _exporting.value = false
            }
        }
    }

    fun onShareRequestHandled() {
        _shareRequest.value = null
    }

    fun clearError() {
        _error.value = null
    }

    fun clearSuccess() {
        _success.value = null
    }

    override fun onCleared() {
        super.onCleared()
        draftSaveJob?.cancel()
    }
}
