package info.meuse24.pdf_scanner.ui.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.common.DetectedEntities
import info.meuse24.pdf_scanner.domain.common.detectDocumentEntities
import info.meuse24.pdf_scanner.domain.common.PdfSearchMatch
import info.meuse24.pdf_scanner.domain.common.findMatches
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.AcroFormCapability
import info.meuse24.pdf_scanner.domain.pdf.PdfFormOps
import info.meuse24.pdf_scanner.domain.pdf.PdfTextOps
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.domain.usecase.CheckPrintPageSizeWarningUseCase
import info.meuse24.pdf_scanner.domain.usecase.ExportScanUseCase
import info.meuse24.pdf_scanner.domain.gateway.DispatcherProvider
import info.meuse24.pdf_scanner.ui.print.PrintRequestCoordinator
import info.meuse24.pdf_scanner.util.PdfDocumentBitmapHandle
import info.meuse24.pdf_scanner.util.PdfPageBitmapCache
import info.meuse24.pdf_scanner.util.PdfPageBitmapCacheKey
import info.meuse24.pdf_scanner.util.PdfPageBitmapRenderException
import info.meuse24.pdf_scanner.util.PdfPageBitmapRenderFailure
import info.meuse24.pdf_scanner.util.PdfPageBitmapRenderer
import info.meuse24.pdf_scanner.util.RenderedPdfPage
import info.meuse24.pdf_scanner.domain.gateway.ResourceProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.math.roundToInt

private const val SAVED_CURRENT_PAGE = "pdf_viewer_current_page"
private const val SAVED_ZOOM_SCALE = "pdf_viewer_zoom_scale"
private const val PDF_VIEWER_VISIBLE_ZOOM_RENDER_DEBOUNCE_MS = 120L
private const val PDF_VIEWER_SEARCH_DEBOUNCE_MS = 275L
private val PDF_VIEWER_ZOOM_SCALE_STEPS = floatArrayOf(1f, 1.5f, 2f, 3f, 4f, 5f)

@HiltViewModel
class PdfViewerViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val pageBitmapRenderer: PdfPageBitmapRenderer,
    private val exportScanUseCase: ExportScanUseCase,
    checkPrintPageSizeWarningUseCase: CheckPrintPageSizeWarningUseCase,
    private val resourceProvider: ResourceProvider,
    private val dispatcherProvider: DispatcherProvider,
    private val pdfFormOps: PdfFormOps,
    private val pdfTextOps: PdfTextOps,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val scanId: Long = checkNotNull(savedStateHandle["scanId"])

    private val bitmapCache = PdfPageBitmapCache(onEvict = { key ->
        _uiState.update { state ->
            val pageState = state.pages[key.pageIndex] ?: return@update state
            if (pageState.renderedWidthPx == key.targetWidthPx) {
                state.copy(pages = state.pages + (key.pageIndex to pageState.copy(bitmap = null)))
            } else state
        }
    })
    private val renderJobs = ConcurrentHashMap<Int, RenderJob>()
    private val documentHandleRef = AtomicReference<PdfDocumentBitmapHandle?>(null)
    private val isCleared = AtomicBoolean(false)
    private val searchExtractionGeneration = AtomicLong(0)

    @Volatile
    private var documentKey: String? = null
    private var openDocumentJob: Job? = null
    private var visibleZoomRenderJob: Job? = null
    @Volatile
    private var searchJob: Job? = null
    private var searchTextExtractionJob: Job? = null
    private var highlightJob: Job? = null
    private var entityDetectionJob: Job? = null
    private var entitySourceKey: EntitySourceKey? = null
    private var visiblePageIndexes: Set<Int> = setOf(savedStateHandle[SAVED_CURRENT_PAGE] ?: 0)
    private var targetWidthPx: Int = 0
    private val searchCacheLock = Any()
    private var searchPageTexts: MutableList<PageSearchText> = mutableListOf()
    private val searchBoxCache = LinkedHashMap<Int, List<info.meuse24.pdf_scanner.domain.pdf.NormalizedBox?>>(3, 0.75f, true)
    private var searchTextExtractionStarted = false

    private val _uiState = MutableStateFlow(
        PdfViewerUiState(
            currentPageIndex = savedStateHandle[SAVED_CURRENT_PAGE] ?: 0,
            zoomScale = savedStateHandle[SAVED_ZOOM_SCALE] ?: 1f
        )
    )
    val uiState: StateFlow<PdfViewerUiState> = _uiState.asStateFlow()
    private val printRequestCoordinator = PrintRequestCoordinator(checkPrintPageSizeWarningUseCase)
    val pendingPrintDocument: StateFlow<Document?> = printRequestCoordinator.pendingPrintDocument
    val printRequests: SharedFlow<Document> = printRequestCoordinator.printRequests
    private val _scrollToPageRequests = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val scrollToPageRequests: SharedFlow<Int> = _scrollToPageRequests.asSharedFlow()

    fun requestPrint(record: Document) {
        printRequestCoordinator.requestPrint(viewModelScope, record)
    }

    fun confirmPrintWarning() {
        printRequestCoordinator.confirmPrintWarning(viewModelScope)
    }

    fun dismissPrintWarning() {
        printRequestCoordinator.dismissPrintWarning()
    }

    init {
        viewModelScope.launch {
            repository.getAllScans().collect { records ->
                val record = records.find { it.id == scanId }
                if (record == null) {
                    entitySourceKey = null
                    entityDetectionJob?.cancel()
                    closeDocument()
                    _uiState.update {
                        it.copy(
                            record = null,
                            loading = false,
                            errorMessage = resourceProvider.getString(R.string.pdf_viewer_file_missing),
                            pageCount = 0,
                            pages = emptyMap(),
                            detectedEntities = DetectedEntities(),
                            pageSearchAvailable = false,
                            searchExtractionRunning = false,
                            searchActive = false,
                            searchQuery = "",
                            searchMatches = emptyList(),
                            searchCurrentIndex = -1,
                            searching = false,
                            searchHighlights = null,
                            formCapability = AcroFormCapability.NONE
                        )
                    }
                } else {
                    onRecordChanged(record)
                }
            }
        }
    }

    fun onVisiblePagesChanged(pageIndexes: List<Int>, targetWidthPx: Int) {
        val pageCount = _uiState.value.pageCount
        if (pageCount <= 0) return

        val visible = pageIndexes
            .filter { it in 0 until pageCount }
            .ifEmpty { listOf(_uiState.value.currentPageIndex.coerceIn(0, pageCount - 1)) }
            .toSet()
        visiblePageIndexes = visible
        this.targetWidthPx = targetWidthPx

        val currentPage = visible.minOrNull() ?: 0
        savedStateHandle[SAVED_CURRENT_PAGE] = currentPage
        _uiState.update { it.copy(currentPageIndex = currentPage) }

        renderVisiblePages()
        val currentZoom = _uiState.value.zoomScale
        if (currentZoom > 1f) scheduleZoomRender(targetWidthPx, currentZoom)
    }

    fun requestVisibleZoomRender(viewportWidthPx: Int, zoomScale: Float) {
        setZoomScale(zoomScale)
        scheduleZoomRender(viewportWidthPx, zoomScale)
    }

    private fun scheduleZoomRender(viewportWidthPx: Int, zoomScale: Float) {
        val pageCount = _uiState.value.pageCount
        if (documentHandleRef.get() == null || pageCount <= 0 || viewportWidthPx <= 0) return
        val (width, maxSide) = zoomRenderDimensions(viewportWidthPx, zoomScale)
        val pageIndexes = retainedPageIndexes(pageCount)
        visibleZoomRenderJob?.cancel()
        visibleZoomRenderJob = viewModelScope.launch {
            delay(PDF_VIEWER_VISIBLE_ZOOM_RENDER_DEBOUNCE_MS)
            pageIndexes.forEach { pageIndex ->
                renderPage(pageIndex, width, maxSide, cacheResult = false)
            }
        }
    }

    fun setZoomScale(scale: Float) {
        val clamped = scale.coerceIn(1f, PDF_VIEWER_MAX_ZOOM_SCALE)
        savedStateHandle[SAVED_ZOOM_SCALE] = clamped
        _uiState.update { it.copy(zoomScale = clamped) }
    }

    fun retry() {
        val record = _uiState.value.record ?: return
        documentKey = null
        onRecordChanged(record)
    }

    fun exportCurrentPdf() {
        val record = _uiState.value.record ?: return
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                val displayName = exportScanUseCase(record)
                _uiState.update {
                    it.copy(transientMessage = resourceProvider.getString(R.string.export_success, displayName))
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(transientMessage = resourceProvider.getString(R.string.error_export_failed))
                }
            }
        }
    }

    fun clearTransientMessage() {
        _uiState.update { it.copy(transientMessage = null) }
    }

    fun openSearch() {
        val record = _uiState.value.record ?: return
        if (!searchTextExtractionStarted) {
            searchTextExtractionStarted = startSearchTextExtraction(
                file = File(record.filepath),
                pageCount = _uiState.value.pageCount,
                expectedDocumentKey = documentKey ?: return
            )
        }
        if (!_uiState.value.pageSearchAvailable && !_uiState.value.searchExtractionRunning) {
            _uiState.update {
                it.copy(transientMessage = resourceProvider.getString(R.string.pdf_viewer_search_needs_ocr))
            }
            return
        }
        _uiState.update { it.copy(searchActive = true) }
    }

    fun updateSearchQuery(query: String) {
        if (!_uiState.value.searchActive) return
        searchJob?.cancel()
        val trimmed = query.trim()
        _uiState.update {
            it.copy(
                searchQuery = query,
                searchMatches = emptyList(),
                searchCurrentIndex = -1,
                searching = trimmed.isNotEmpty(),
                searchHighlights = null
            )
        }
        if (trimmed.isEmpty()) return
        searchJob = viewModelScope.launch {
            delay(PDF_VIEWER_SEARCH_DEBOUNCE_MS)
            refreshSearchResults(trimmed)
        }
    }

    fun goToNextMatch() {
        moveToMatch(step = 1)
    }

    fun goToPreviousMatch() {
        moveToMatch(step = -1)
    }

    fun closeSearch() {
        searchJob?.cancel()
        searchJob = null
        highlightJob?.cancel()
        highlightJob = null
        _uiState.update {
            it.copy(
                searchActive = false,
                searchQuery = "",
                searchMatches = emptyList(),
                searchCurrentIndex = -1,
                searching = false,
                searchHighlights = null
            )
        }
    }

    fun notifyIbanCopied() {
        _uiState.update {
            it.copy(transientMessage = resourceProvider.getString(R.string.pdf_viewer_copy_iban_success))
        }
    }

    fun notifyAmountCopied() {
        _uiState.update {
            it.copy(transientMessage = resourceProvider.getString(R.string.pdf_viewer_copy_amount_success))
        }
    }

    fun notifyCalendarAppMissing() {
        _uiState.update {
            it.copy(transientMessage = resourceProvider.getString(R.string.pdf_viewer_no_calendar_app))
        }
    }

    override fun onCleared() {
        super.onCleared()
        isCleared.set(true)
        cancelRenderJobs()
        searchJob?.cancel()
        searchTextExtractionJob?.cancel()
        searchExtractionGeneration.incrementAndGet()
        highlightJob?.cancel()
        entityDetectionJob?.cancel()
        openDocumentJob?.cancel()
        closeDocument()
        bitmapCache.clear()
    }

    private fun onRecordChanged(record: Document) {
        val previousRecord = _uiState.value.record
        val shouldRestartSearchText = searchTextExtractionStarted
        val searchContentChanged = previousRecord == null ||
            previousRecord.id != record.id ||
            previousRecord.filepath != record.filepath ||
            previousRecord.fileSize != record.fileSize ||
            previousRecord.pageTexts != record.pageTexts
        _uiState.update { state ->
            state.copy(
                record = record,
                pageSearchAvailable = if (searchContentChanged) false else state.pageSearchAvailable,
                searchExtractionRunning = if (searchContentChanged) false else state.searchExtractionRunning,
                searchActive = if (searchContentChanged) false else state.searchActive,
                searchQuery = if (searchContentChanged) "" else state.searchQuery,
                searchMatches = if (searchContentChanged) emptyList() else state.searchMatches,
                searchCurrentIndex = if (searchContentChanged) -1 else state.searchCurrentIndex,
                searching = if (searchContentChanged) false else state.searching,
                searchHighlights = if (searchContentChanged) null else state.searchHighlights
            )
        }
        if (searchContentChanged) {
            searchJob?.cancel()
            searchTextExtractionJob?.cancel()
            searchExtractionGeneration.incrementAndGet()
            highlightJob?.cancel()
            synchronized(searchCacheLock) {
                searchPageTexts.clear()
                searchBoxCache.clear()
            }
            searchTextExtractionStarted = false
        }
        detectEntities(record)
        val nextKey = "${record.filepath}:${record.fileSize}"
        if (nextKey == documentKey && documentHandleRef.get() != null) {
            if (searchContentChanged) {
                val pageCount = _uiState.value.pageCount
                prepareSearchText(record, pageCount)
                if (shouldRestartSearchText) {
                    searchTextExtractionStarted = startSearchTextExtraction(File(record.filepath), pageCount, nextKey)
                }
            }
            return
        }

        documentKey = nextKey
        cancelRenderJobs()
        closeDocument()
        bitmapCache.clear()
        visiblePageIndexes = setOf(0)

        if (record.isEncrypted) {
            _uiState.update {
                it.copy(
                    loading = false,
                    errorMessage = resourceProvider.getString(R.string.pdf_viewer_file_encrypted),
                    pageCount = 0,
                    pages = emptyMap(),
                    pageSearchAvailable = false,
                    searchExtractionRunning = false
                )
            }
            return
        }

        val file = File(record.filepath)
        if (!file.exists()) {
            _uiState.update {
                it.copy(
                    loading = false,
                    errorMessage = resourceProvider.getString(R.string.pdf_viewer_file_missing),
                    pageCount = 0,
                    pages = emptyMap(),
                    pageSearchAvailable = false,
                    searchExtractionRunning = false
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                loading = true,
                errorMessage = null,
                pageCount = 0,
                pages = emptyMap()
            )
        }

        openDocumentJob?.cancel()
        openDocumentJob = viewModelScope.launch(dispatcherProvider.io) {
            try {
                val formCapability = runCatching {
                    pdfFormOps.detectFormCapability(file)
                }.getOrDefault(AcroFormCapability.NONE)
                val handle = pageBitmapRenderer.openDocument(file)
                if (documentKey != nextKey) {
                    handle.close()
                    return@launch
                }
                if (handle.pageCount <= 0) {
                    handle.close()
                    _uiState.update {
                        it.copy(
                            loading = false,
                            errorMessage = resourceProvider.getString(R.string.pdf_viewer_file_corrupted),
                            pageCount = 0,
                            pages = emptyMap()
                        )
                    }
                    return@launch
                }
                val actualPageCount = handle.pageCount
                if (!setDocumentHandle(handle, nextKey)) return@launch
                val current = _uiState.value.currentPageIndex.coerceIn(0, actualPageCount - 1)
                _uiState.update {
                    it.copy(
                        loading = false,
                        errorMessage = null,
                        pageCount = actualPageCount,
                        currentPageIndex = current,
                        pageSearchAvailable = false,
                        searchExtractionRunning = false,
                        searchActive = false,
                        searchQuery = "",
                        searchMatches = emptyList(),
                        searchCurrentIndex = -1,
                        searching = false,
                        searchHighlights = null,
                        formCapability = formCapability
                    )
                }
                prepareSearchText(record, actualPageCount)
                renderVisiblePages()
            } catch (exception: PdfPageBitmapRenderException) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        errorMessage = mapFailure(exception.failure),
                        pageCount = 0,
                        pages = emptyMap()
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        errorMessage = resourceProvider.getString(R.string.pdf_viewer_renderer_failed),
                        pageCount = 0,
                        pages = emptyMap()
                    )
                }
            }
        }
    }

    private fun renderVisiblePages() {
        val pageCount = _uiState.value.pageCount
        if (documentHandleRef.get() == null || pageCount <= 0 || targetWidthPx <= 0) return

        val keepPages = retainedPageIndexes(pageCount)

        bitmapCache.retainPages(keepPages)
        renderJobs.keys.filter { it !in keepPages }.forEach { pageIndex ->
            renderJobs.remove(pageIndex)?.job?.cancel()
        }
        _uiState.update { state ->
            state.copy(
                pages = state.pages.mapValues { (pageIndex, pageState) ->
                    if (pageIndex in keepPages) pageState
                    else pageState.copy(bitmap = null, loading = false)
                }
            )
        }

        keepPages.forEach { pageIndex ->
            renderPage(pageIndex, targetWidthPx, PDF_VIEWER_FIT_MAX_BITMAP_SIDE_PX)
        }
    }

    private fun prepareSearchText(record: Document, pageCount: Int) {
        val ocrPages = record.pageTexts.takeIf { it.size == pageCount }.orEmpty()
        synchronized(searchCacheLock) {
            searchPageTexts = MutableList(pageCount) { pageIndex ->
                ocrPages.getOrNull(pageIndex)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { PageSearchText(it, SearchTextSource.OCR_FALLBACK) }
                    ?: PageSearchText("", SearchTextSource.NONE)
            }
        }
        _uiState.update {
            it.copy(pageSearchAvailable = synchronized(searchCacheLock) { searchPageTexts.any { page -> page.text.isNotBlank() } })
        }
    }

    private fun startSearchTextExtraction(
        file: File,
        pageCount: Int,
        expectedDocumentKey: String
    ): Boolean {
        if (pageCount <= 0 || expectedDocumentKey != documentKey) return false
        val generation = searchExtractionGeneration.incrementAndGet()
        _uiState.update { it.copy(searchExtractionRunning = true) }
        searchTextExtractionJob?.cancel()
        searchTextExtractionJob = viewModelScope.launch(dispatcherProvider.io) {
            try {
                pdfTextOps.extractSearchText(file).collect { page ->
                    if (documentKey != expectedDocumentKey) return@collect
                    if (page.text.isNotBlank()) {
                        synchronized(searchCacheLock) {
                            if (page.pageIndex !in searchPageTexts.indices) return@collect
                            searchPageTexts[page.pageIndex] = PageSearchText(page.text, SearchTextSource.NATIVE)
                        }
                        _uiState.update { it.copy(pageSearchAvailable = true) }
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                // OCR fallback, if present, remains usable. The PDF viewer itself stays unaffected.
            } finally {
                if (documentKey == expectedDocumentKey && searchExtractionGeneration.get() == generation) {
                    viewModelScope.launch(dispatcherProvider.main) {
                        if (documentKey == expectedDocumentKey && searchExtractionGeneration.get() == generation) {
                            _uiState.update { it.copy(searchExtractionRunning = false) }
                            val state = _uiState.value
                            if (!state.pageSearchAvailable && state.searchActive) {
                                _uiState.update {
                                    it.copy(
                                        searchActive = false,
                                        searchMatches = emptyList(),
                                        searchCurrentIndex = -1,
                                        searching = false,
                                        transientMessage = resourceProvider.getString(R.string.pdf_viewer_search_needs_ocr)
                                    )
                                }
                            } else {
                                state.searchQuery.trim().takeIf { it.isNotEmpty() }?.let { query ->
                                    searchJob?.cancel()
                                    searchJob = viewModelScope.launch { refreshSearchResults(query) }
                                }
                            }
                        }
                    }
                }
            }
        }
        return true
    }

    private suspend fun refreshSearchResults(query: String) {
        val expectedDocumentKey = documentKey ?: return
        val pageTexts = synchronized(searchCacheLock) { searchPageTexts.map { it.text } }
        val matches = withContext(dispatcherProvider.default) { findMatches(pageTexts, query) }
        if (documentKey != expectedDocumentKey || _uiState.value.searchQuery.trim() != query) return
        _uiState.update {
            it.copy(
                searchMatches = matches,
                searchCurrentIndex = if (matches.isEmpty()) -1 else 0,
                searching = it.searchExtractionRunning,
                searchHighlights = null
            )
        }
        matches.firstOrNull()?.let { match ->
            _scrollToPageRequests.tryEmit(match.pageIndex)
            updateHighlights(match.pageIndex, matches, 0, expectedDocumentKey)
        }
    }

    private fun updateHighlights(
        pageIndex: Int,
        matches: List<PdfSearchMatch>,
        currentIndex: Int,
        expectedDocumentKey: String
    ) {
        val page = synchronized(searchCacheLock) { searchPageTexts.getOrNull(pageIndex) } ?: return
        if (page.source != SearchTextSource.NATIVE) {
            _uiState.update { it.copy(searchHighlights = null) }
            return
        }
        highlightJob?.cancel()
        highlightJob = viewModelScope.launch(dispatcherProvider.io) {
            try {
                val file = _uiState.value.record?.filepath?.let(::File) ?: return@launch
                val boxes = synchronized(searchCacheLock) { searchBoxCache[pageIndex] }
                    ?: pdfTextOps.extractPageGlyphBoxes(file, pageIndex).also { loaded ->
                        synchronized(searchCacheLock) {
                            searchBoxCache[pageIndex] = loaded
                            while (searchBoxCache.size > 3) searchBoxCache.remove(searchBoxCache.entries.first().key)
                        }
                    }
                val pageMatches = matches.withIndex().filter { it.value.pageIndex == pageIndex }
                val highlights = withContext(dispatcherProvider.default) {
                    PdfSearchHighlights(
                        pageIndex = pageIndex,
                        active = boxesForRange(boxes, matches[currentIndex].rawRange),
                        others = pageMatches
                            .filter { it.index != currentIndex }
                            .flatMap { boxesForRange(boxes, it.value.rawRange) }
                    )
                }
                if (documentKey == expectedDocumentKey && _uiState.value.searchCurrentIndex == currentIndex) {
                    _uiState.update { it.copy(searchHighlights = highlights) }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                if (documentKey == expectedDocumentKey) _uiState.update { it.copy(searchHighlights = null) }
            }
        }
    }

    private fun moveToMatch(step: Int) {
        val state = _uiState.value
        if (state.searchMatches.isEmpty()) return
        val current = state.searchCurrentIndex.coerceAtLeast(0)
        val nextIndex = (current + step + state.searchMatches.size) % state.searchMatches.size
        _uiState.update { it.copy(searchCurrentIndex = nextIndex) }
        val previous = state.searchMatches[current]
        val next = state.searchMatches[nextIndex]
        if (previous.pageIndex != next.pageIndex) _scrollToPageRequests.tryEmit(next.pageIndex)
        documentKey?.let { updateHighlights(next.pageIndex, state.searchMatches, nextIndex, it) }
    }

    private fun detectEntities(record: Document) {
        val extractedText = record.extractedText?.takeIf { it.isNotBlank() }
        val fallbackPageTexts = if (extractedText == null) record.pageTexts else emptyList()
        val nextSourceKey = EntitySourceKey(record.id, extractedText, fallbackPageTexts)
        if (nextSourceKey == entitySourceKey) return
        entitySourceKey = nextSourceKey
        entityDetectionJob?.cancel()
        _uiState.update { it.copy(detectedEntities = DetectedEntities()) }
        if (extractedText == null && fallbackPageTexts.none { it.isNotBlank() }) return

        entityDetectionJob = viewModelScope.launch(dispatcherProvider.default) {
            val sourceText = extractedText
                ?: fallbackPageTexts.joinToString("\n\n")
            val entities = detectDocumentEntities(sourceText)
            if (entitySourceKey == nextSourceKey) {
                _uiState.update { it.copy(detectedEntities = entities) }
            }
        }
    }

    private fun retainedPageIndexes(pageCount: Int): Set<Int> =
        visiblePageIndexes
            .flatMap { pageIndex ->
                (pageIndex - PDF_VIEWER_PREFETCH_DISTANCE)..(pageIndex + PDF_VIEWER_PREFETCH_DISTANCE)
            }
            .filter { it in 0 until pageCount }
            .toSet()

    private fun renderPage(
        pageIndex: Int,
        targetWidthPx: Int,
        maxBitmapSidePx: Int,
        cacheResult: Boolean = true
    ) {
        val handle = documentHandleRef.get() ?: return
        val key = PdfPageBitmapCacheKey(pageIndex, targetWidthPx)
        if (cacheResult) {
            bitmapCache.get(key)?.let { cached ->
                applyRenderedPage(cached, loading = false)
                return
            }
        }

        val existing = _uiState.value.pages[pageIndex]
        if (existing?.bitmap != null && existing.renderedWidthPx >= targetWidthPx * 0.95f) return

        renderJobs[pageIndex]?.let { running ->
            if (running.targetWidthPx >= targetWidthPx && running.job.isActive) return
            running.job.cancel()
        }

        _uiState.update { state ->
            state.copy(
                pages = state.pages + (
                    pageIndex to (state.pages[pageIndex] ?: PdfViewerPageState(pageIndex)).copy(
                        loading = true,
                        errorMessage = null
                    )
                )
            )
        }

        val expectedDocumentKey = documentKey
        val job = viewModelScope.launch(dispatcherProvider.io) {
            try {
                val rendered = handle.renderPage(pageIndex, targetWidthPx, maxBitmapSidePx)
                if (documentKey != expectedDocumentKey) return@launch
                if (cacheResult) {
                    bitmapCache.put(key, rendered)
                }
                applyRenderedPage(rendered, loading = false)
            } catch (exception: PdfPageBitmapRenderException) {
                applyPageError(pageIndex, mapFailure(exception.failure))
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                applyPageError(pageIndex, resourceProvider.getString(R.string.pdf_viewer_renderer_failed))
            } finally {
                renderJobs.remove(pageIndex)
            }
        }
        renderJobs[pageIndex] = RenderJob(targetWidthPx, job)
    }

    private fun applyRenderedPage(rendered: RenderedPdfPage, loading: Boolean) {
        _uiState.update { state ->
            state.copy(
                pages = state.pages + (
                    rendered.pageIndex to PdfViewerPageState(
                        pageIndex = rendered.pageIndex,
                        widthPt = rendered.widthPt,
                        heightPt = rendered.heightPt,
                        bitmap = rendered.bitmap,
                        renderedWidthPx = rendered.renderedWidthPx,
                        loading = loading,
                        errorMessage = null
                    )
                )
            )
        }
    }

    private fun applyPageError(pageIndex: Int, message: String) {
        _uiState.update { state ->
            state.copy(
                pages = state.pages + (
                    pageIndex to (state.pages[pageIndex] ?: PdfViewerPageState(pageIndex)).copy(
                        loading = false,
                        errorMessage = message
                    )
                )
            )
        }
    }

    private fun cancelRenderJobs() {
        visibleZoomRenderJob?.cancel()
        visibleZoomRenderJob = null
        renderJobs.values.forEach { it.job.cancel() }
        renderJobs.clear()
    }

    private fun closeDocument() {
        documentHandleRef.getAndSet(null)?.close()
    }

    private fun setDocumentHandle(handle: PdfDocumentBitmapHandle, expectedKey: String): Boolean {
        if (
            isCleared.get() ||
            documentKey != expectedKey ||
            !documentHandleRef.compareAndSet(null, handle)
        ) {
            handle.close()
            return false
        }
        if (isCleared.get() || documentKey != expectedKey) {
            documentHandleRef.compareAndSet(handle, null)
            handle.close()
            return false
        }
        return true
    }

    private fun zoomRenderDimensions(viewportWidthPx: Int, zoomScale: Float): Pair<Int, Int> {
        val safeViewportWidth = viewportWidthPx.coerceAtLeast(64)
        val scaleStep = PDF_VIEWER_ZOOM_SCALE_STEPS.firstOrNull { zoomScale <= it }
            ?: PDF_VIEWER_ZOOM_SCALE_STEPS.last()
        val maxSide = minOf(
            PDF_VIEWER_ZOOM_MAX_BITMAP_SIDE_PX,
            (safeViewportWidth * 2).coerceAtLeast(PDF_VIEWER_FIT_MAX_BITMAP_SIDE_PX)
        )
        val width = (safeViewportWidth * scaleStep)
            .roundToInt()
            .coerceAtLeast(safeViewportWidth)
            .coerceAtMost(maxSide)
        return width to maxSide
    }

    private fun mapFailure(failure: PdfPageBitmapRenderFailure): String = when (failure) {
        PdfPageBitmapRenderFailure.FileMissing ->
            resourceProvider.getString(R.string.pdf_viewer_file_missing)
        PdfPageBitmapRenderFailure.FileEncrypted ->
            resourceProvider.getString(R.string.pdf_viewer_file_encrypted)
        PdfPageBitmapRenderFailure.FileCorrupted ->
            resourceProvider.getString(R.string.pdf_viewer_file_corrupted)
        PdfPageBitmapRenderFailure.InvalidPage ->
            resourceProvider.getString(R.string.pdf_viewer_invalid_page)
        PdfPageBitmapRenderFailure.OutOfMemory ->
            resourceProvider.getString(R.string.pdf_viewer_out_of_memory)
        PdfPageBitmapRenderFailure.RendererFailure ->
            resourceProvider.getString(R.string.pdf_viewer_renderer_failed)
    }
}

private data class RenderJob(
    val targetWidthPx: Int,
    val job: Job
)

private data class EntitySourceKey(
    val documentId: Long,
    val extractedText: String?,
    val fallbackPageTexts: List<String>
)

private enum class SearchTextSource { NATIVE, OCR_FALLBACK, NONE }

private data class PageSearchText(
    val text: String,
    val source: SearchTextSource
)

private fun boxesForRange(
    boxes: List<info.meuse24.pdf_scanner.domain.pdf.NormalizedBox?>,
    range: IntRange
): List<info.meuse24.pdf_scanner.domain.pdf.NormalizedBox> {
    if (boxes.isEmpty()) return emptyList()
    return boxes.subList(
        range.first.coerceIn(0, boxes.lastIndex),
        (range.last + 1).coerceIn(0, boxes.size)
    ).filterNotNull().distinct()
}
