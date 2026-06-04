package info.meuse24.pdf_scanner.ui.print

import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.usecase.CheckPrintPageSizeWarningUseCase
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PrintRequestCoordinator(
    private val checkPrintPageSizeWarningUseCase: CheckPrintPageSizeWarningUseCase
) {
    private val _pendingPrintDocument = MutableStateFlow<Document?>(null)
    val pendingPrintDocument: StateFlow<Document?> = _pendingPrintDocument.asStateFlow()

    private val _printRequests = MutableSharedFlow<Document>(extraBufferCapacity = 1)
    val printRequests: SharedFlow<Document> = _printRequests.asSharedFlow()

    private val printCheckInProgress = AtomicBoolean(false)

    fun requestPrint(scope: CoroutineScope, record: Document) {
        if (!printCheckInProgress.compareAndSet(false, true)) return
        scope.launch {
            try {
                if (checkPrintPageSizeWarningUseCase(record)) {
                    _pendingPrintDocument.value = record
                } else {
                    _printRequests.emit(record)
                }
            } finally {
                printCheckInProgress.set(false)
            }
        }
    }

    fun confirmPrintWarning(scope: CoroutineScope) {
        val record = _pendingPrintDocument.value ?: return
        _pendingPrintDocument.value = null
        scope.launch {
            _printRequests.emit(record)
        }
    }

    fun dismissPrintWarning() {
        _pendingPrintDocument.value = null
    }
}
