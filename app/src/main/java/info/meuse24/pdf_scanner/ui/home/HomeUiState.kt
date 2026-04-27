package info.meuse24.pdf_scanner.ui.home

import android.net.Uri
import androidx.compose.runtime.Immutable
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.model.Folder
import info.meuse24.pdf_scanner.util.AppSettings

@Immutable
data class HomeArchiveUiState(
    val settings: AppSettings = AppSettings(),
    val scans: List<Document> = emptyList(),
    val filteredScans: List<Document> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val currentFolder: Folder? = null,
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
data class HomeOperationUiState(
    val ocrText: String? = null,
    val ocrReviewRequestId: Long? = null,
    val playReviewRequestId: Long = 0L,
    val ocrLoading: Boolean = false,
    val ocrProgress: HomeOcrProgress? = null,
    val ocrStatusText: String? = null,
    val editLoading: Boolean = false
)

@Immutable
data class HomeMessageUiState(
    val error: String? = null,
    val success: String? = null,
    val trashMessage: String? = null,
    val lastTrashed: List<Long> = emptyList()
)
