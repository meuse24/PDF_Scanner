package info.meuse24.pdf_scanner.ui.home

import android.net.Uri
import androidx.compose.runtime.Immutable
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.Folder
import info.meuse24.pdf_scanner.domain.model.AppSettings

@Immutable
data class HomeArchiveUiState(
    val settings: AppSettings = AppSettings(),
    val scans: List<Document> = emptyList(),
    val filteredScans: List<Document> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val currentFolder: Folder? = null,
    val currentFolderId: Long? = null,
    val favoritesFilter: Boolean = false,
    val currentTagKey: String? = null,
    val availableTagKeys: List<String> = emptyList(),
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.ByDate,
    val pendingImageUris: List<Uri> = emptyList()
)

@Immutable
data class HomeOcrProgress(
    val currentPage: Int,
    val totalPages: Int
)

@Immutable
data class HomeDocxOcrPrompt(
    val documentIds: List<Long>
)

@Immutable
data class HomeOperationUiState(
    val ocrText: String? = null,
    val ocrReviewRequestId: Long? = null,
    val playReviewRequestId: Long = 0L,
    val ocrLoading: Boolean = false,
    val ocrProgress: HomeOcrProgress? = null,
    val ocrStatusText: String? = null,
    val aiPromptLoading: Boolean = false,
    val editLoading: Boolean = false,
    val docxOcrPrompt: HomeDocxOcrPrompt? = null
)

@Immutable
data class HomeMessageUiState(
    val error: String? = null,
    val success: String? = null,
    val trashMessage: String? = null,
    val lastTrashed: List<Long> = emptyList()
)

@Immutable
data class AddedDocumentScrollRequest(
    val documentId: Long,
    val folderId: Long?
)

internal fun AddedDocumentScrollRequest.matchesArchiveContext(
    state: HomeArchiveUiState
): Boolean =
    state.searchQuery.isBlank() &&
        !state.favoritesFilter &&
        state.currentTagKey == null &&
        folderId == state.currentFolderId

@Immutable
sealed interface HomeHashUiState {
    data object Idle : HomeHashUiState
    data class Calculating(val filename: String) : HomeHashUiState
    data class Success(val filename: String, val sha256: String) : HomeHashUiState
}
