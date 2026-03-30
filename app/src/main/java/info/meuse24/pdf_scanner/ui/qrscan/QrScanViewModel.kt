package info.meuse24.pdf_scanner.ui.qrscan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.data.local.ScanRecord
import info.meuse24.pdf_scanner.data.repository.ScanRepository
import info.meuse24.pdf_scanner.domain.usecase.QrCodeResult
import info.meuse24.pdf_scanner.domain.usecase.ScanQrCodesUseCase
import info.meuse24.pdf_scanner.util.DispatcherProvider
import info.meuse24.pdf_scanner.util.ResourceProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QrScanViewModel @Inject constructor(
    private val repository: ScanRepository,
    private val scanQrCodesUseCase: ScanQrCodesUseCase,
    private val resourceProvider: ResourceProvider,
    private val dispatcherProvider: DispatcherProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val scanId: Long = checkNotNull(savedStateHandle["scanId"])

    private val _record = MutableStateFlow<ScanRecord?>(null)
    val record: StateFlow<ScanRecord?> = _record.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _results = MutableStateFlow<List<QrCodeResult>>(emptyList())
    val results: StateFlow<List<QrCodeResult>> = _results.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _progress = MutableStateFlow(0 to 0)
    val progress: StateFlow<Pair<Int, Int>> = _progress.asStateFlow()

    private var initialScanStarted = false

    init {
        loadRecord()
    }

    private fun loadRecord() {
        viewModelScope.launch {
            repository.getAllScans().collect { scans ->
                val found = scans.find { it.id == scanId }
                _record.value = found
                if (found != null && !initialScanStarted) {
                    initialScanStarted = true
                    if (found.isEncrypted) {
                        _error.value = resourceProvider.getString(R.string.qr_scan_error_encrypted)
                    } else {
                        startScan(found)
                    }
                }
            }
        }
    }

    fun startScan() {
        startScan(_record.value ?: return)
    }

    fun clearError() {
        _error.value = null
    }

    private fun startScan(record: ScanRecord) {
        if (_scanning.value) return
        _error.value = null
        _results.value = emptyList()
        _progress.value = 0 to record.pageCount.coerceAtLeast(0)
        _scanning.value = true

        viewModelScope.launch(dispatcherProvider.io) {
            try {
                _results.value = scanQrCodesUseCase(record) { current, total ->
                    _progress.value = current to total
                }
            } catch (exception: Exception) {
                _error.value = exception.message ?: resourceProvider.getString(R.string.error_source_unavailable)
            } finally {
                _scanning.value = false
            }
        }
    }
}
