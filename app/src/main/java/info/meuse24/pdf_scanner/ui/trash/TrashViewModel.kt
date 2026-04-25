package info.meuse24.pdf_scanner.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.repository.TrashDocumentRepository
import info.meuse24.pdf_scanner.domain.usecase.PurgeTrashUseCase
import info.meuse24.pdf_scanner.domain.usecase.RestoreMissingFileException
import info.meuse24.pdf_scanner.domain.usecase.RestoreScansUseCase
import info.meuse24.pdf_scanner.util.DispatcherProvider
import info.meuse24.pdf_scanner.util.ResourceProvider
import info.meuse24.pdf_scanner.util.TrashConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    trashRepository: TrashDocumentRepository,
    private val restoreScansUseCase: RestoreScansUseCase,
    private val purgeTrashUseCase: PurgeTrashUseCase,
    private val resourceProvider: ResourceProvider,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    val trashedScans: StateFlow<List<Document>> = trashRepository.getTrashedScans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _success = MutableStateFlow<String?>(null)
    val success: StateFlow<String?> = _success.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    init {
        viewModelScope.launch(dispatcherProvider.io) {
            purgeTrashUseCase.purgeExpired(TrashConstants.RETENTION_MILLIS)
        }
    }

    fun setSelectedIds(ids: Set<Long>) {
        _selectedIds.value = ids
    }

    fun toggleSelectedId(id: Long) {
        val current = _selectedIds.value
        _selectedIds.value = if (id in current) current - id else current + id
    }

    fun clearSelectedIds() {
        _selectedIds.value = emptySet()
    }

    fun restore(records: List<Document>) {
        val ids = records.map { it.id }
        if (ids.isEmpty() || _loading.value) return
        _loading.value = true
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                restoreScansUseCase(ids)
                _success.value = resourceProvider.getQuantityString(
                    R.plurals.trash_restored,
                    ids.size,
                    ids.size
                )
            } catch (_: RestoreMissingFileException) {
                _error.value = resourceProvider.getString(R.string.error_restore_missing_file)
            } catch (_: Exception) {
                _error.value = resourceProvider.getString(R.string.error_restore_failed)
            } finally {
                _loading.value = false
            }
        }
    }

    fun purge(records: List<Document>) {
        if (records.isEmpty() || _loading.value) return
        _loading.value = true
        viewModelScope.launch(dispatcherProvider.io) {
            try {
                if (purgeTrashUseCase.purgeSelected(records)) {
                    _success.value = resourceProvider.getQuantityString(
                        R.plurals.trash_purged,
                        records.size,
                        records.size
                    )
                } else {
                    _error.value = resourceProvider.getString(R.string.error_delete_failed)
                }
            } catch (_: Exception) {
                _error.value = resourceProvider.getString(R.string.error_delete_failed)
            } finally {
                _loading.value = false
            }
        }
    }

    fun emptyTrash() {
        purge(trashedScans.value)
    }

    fun clearError() {
        _error.value = null
    }

    fun clearSuccess() {
        _success.value = null
    }
}

