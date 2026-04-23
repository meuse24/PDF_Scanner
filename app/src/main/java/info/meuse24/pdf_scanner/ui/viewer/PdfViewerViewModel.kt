package info.meuse24.pdf_scanner.ui.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.ExportScanUseCase
import info.meuse24.pdf_scanner.util.DispatcherProvider
import info.meuse24.pdf_scanner.util.PdfDocumentBitmapHandle
import info.meuse24.pdf_scanner.util.PdfPageBitmapCache
import info.meuse24.pdf_scanner.util.PdfPageBitmapCacheKey
import info.meuse24.pdf_scanner.util.PdfPageBitmapRenderException
import info.meuse24.pdf_scanner.util.PdfPageBitmapRenderFailure
import info.meuse24.pdf_scanner.util.PdfPageBitmapRenderer
import info.meuse24.pdf_scanner.util.RenderedPdfPage
import info.meuse24.pdf_scanner.util.ResourceProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.math.roundToInt

private const val SAVED_CURRENT_PAGE = "pdf_viewer_current_page"
private const val SAVED_ZOOM_SCALE = "pdf_viewer_zoom_scale"
private val PDF_VIEWER_ZOOM_SCALE_STEPS = floatArrayOf(1f, 1.5f, 2f, 3f, 4f, 5f)

@HiltViewModel
class PdfViewerViewModel @Inject constructor(
    private val repository: ScanRepository,
    private val pageBitmapRenderer: PdfPageBitmapRenderer,
    private val exportScanUseCase: ExportScanUseCase,
    private val resourceProvider: ResourceProvider,
    private val dispatcherProvider: DispatcherProvider,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val scanId: Long = checkNotNull(savedStateHandle["scanId"])

    private val bitmapCache = PdfPageBitmapCache()
    private val renderJobs = ConcurrentHashMap<Int, RenderJob>()
    private val documentHandleRef = AtomicReference<PdfDocumentBitmapHandle?>(null)
    private val isCleared = AtomicBoolean(false)

    @Volatile
    private var documentKey: String? = null
    private var openDocumentJob: Job? = null
    private var visiblePageIndexes: Set<Int> = setOf(savedStateHandle[SAVED_CURRENT_PAGE] ?: 0)
    private var targetWidthPx: Int = 0

    private val _uiState = MutableStateFlow(
        PdfViewerUiState(
            currentPageIndex = savedStateHandle[SAVED_CURRENT_PAGE] ?: 0,
            zoomScale = savedStateHandle[SAVED_ZOOM_SCALE] ?: 1f
        )
    )
    val uiState: StateFlow<PdfViewerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllScans().collect { records ->
                val record = records.find { it.id == scanId }
                if (record == null) {
                    closeDocument()
                    _uiState.update {
                        it.copy(
                            record = null,
                            loading = false,
                            errorMessage = resourceProvider.getString(R.string.pdf_viewer_file_missing),
                            pageCount = 0,
                            pages = emptyMap()
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
    }

    fun requestZoomRender(pageIndex: Int, viewportWidthPx: Int, zoomScale: Float) {
        setZoomScale(zoomScale)
        val (width, maxSide) = zoomRenderDimensions(viewportWidthPx, zoomScale)
        renderPage(pageIndex, width, maxSide, cacheResult = false)
    }

    fun prefetchZoomRender(pageIndex: Int, viewportWidthPx: Int) {
        val (width, maxSide) = zoomRenderDimensions(viewportWidthPx, 2f)
        renderPage(pageIndex, width, maxSide, cacheResult = false)
    }

    fun setZoomScale(scale: Float) {
        val clamped = scale.coerceIn(1f, 5f)
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

    override fun onCleared() {
        super.onCleared()
        isCleared.set(true)
        cancelRenderJobs()
        openDocumentJob?.cancel()
        closeDocument()
        bitmapCache.clear()
    }

    private fun onRecordChanged(record: ScanRecord) {
        _uiState.update { it.copy(record = record) }
        val nextKey = "${record.filepath}:${record.fileSize}"
        if (nextKey == documentKey && documentHandleRef.get() != null) return

        documentKey = nextKey
        cancelRenderJobs()
        closeDocument()
        bitmapCache.clear()

        if (record.isEncrypted) {
            _uiState.update {
                it.copy(
                    loading = false,
                    errorMessage = resourceProvider.getString(R.string.pdf_viewer_file_encrypted),
                    pageCount = 0,
                    pages = emptyMap()
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
                    pages = emptyMap()
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
                        currentPageIndex = current
                    )
                }
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

        val keepPages = visiblePageIndexes
            .flatMap { pageIndex ->
                (pageIndex - PDF_VIEWER_PREFETCH_DISTANCE)..(pageIndex + PDF_VIEWER_PREFETCH_DISTANCE)
            }
            .filter { it in 0 until pageCount }
            .toSet()

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
