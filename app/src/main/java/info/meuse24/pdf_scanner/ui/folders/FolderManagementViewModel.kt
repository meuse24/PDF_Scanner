package info.meuse24.pdf_scanner.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.repository.FolderRepository
import info.meuse24.pdf_scanner.domain.usecase.CreateFolderUseCase
import info.meuse24.pdf_scanner.domain.usecase.DeleteFolderUseCase
import info.meuse24.pdf_scanner.domain.usecase.RenameFolderUseCase
import info.meuse24.pdf_scanner.ui.home.ArchiveFilter
import info.meuse24.pdf_scanner.ui.home.ArchiveFilterStore
import info.meuse24.pdf_scanner.util.DispatcherProvider
import info.meuse24.pdf_scanner.util.ResourceProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FolderManagementUiState(
    val folders: List<info.meuse24.pdf_scanner.domain.model.Folder> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class FolderManagementViewModel @Inject constructor(
    folderRepository: FolderRepository,
    private val createFolderUseCase: CreateFolderUseCase,
    private val renameFolderUseCase: RenameFolderUseCase,
    private val deleteFolderUseCase: DeleteFolderUseCase,
    private val archiveFilterStore: ArchiveFilterStore,
    private val resourceProvider: ResourceProvider,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {
    private val _loading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<FolderManagementUiState> = combine(
        folderRepository.observeFolders(),
        _loading,
        _error
    ) { folders, loading, error ->
        FolderManagementUiState(
            folders = folders,
            loading = loading,
            error = error
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FolderManagementUiState())

    fun createFolder(name: String) {
        runMutation(
            onFailureMessage = R.string.folder_create_failed
        ) {
            createFolderUseCase(name)
        }
    }

    fun renameFolder(id: Long, name: String) {
        runMutation(
            onFailureMessage = R.string.folder_rename_failed
        ) {
            renameFolderUseCase(id, name)
        }
    }

    fun deleteFolder(id: Long) {
        runMutation(
            onFailureMessage = R.string.folder_delete_failed
        ) {
            if (archiveFilterStore.filter.value == ArchiveFilter.Folder(id)) {
                archiveFilterStore.showAllDocuments()
            }
            deleteFolderUseCase(id)
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun runMutation(
        onFailureMessage: Int,
        block: suspend () -> Unit
    ) {
        if (_loading.value) return
        _loading.value = true
        viewModelScope.launch(dispatcherProvider.io) {
            runCatching { block() }
                .onFailure {
                    _error.value = resourceProvider.getString(onFailureMessage)
                }
            _loading.value = false
        }
    }
}
