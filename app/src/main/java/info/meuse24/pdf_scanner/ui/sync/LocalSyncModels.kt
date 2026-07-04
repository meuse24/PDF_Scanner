package info.meuse24.pdf_scanner.ui.sync

import info.meuse24.pdf_scanner.domain.model.LocalSyncState

data class LocalSyncUiState(
    val state: LocalSyncState = LocalSyncState.Stopped,
    val showFirstUseNotice: Boolean = false,
    val errorMessage: String? = null
)
