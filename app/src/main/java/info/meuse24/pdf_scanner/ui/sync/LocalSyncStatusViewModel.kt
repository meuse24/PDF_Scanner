package info.meuse24.pdf_scanner.ui.sync

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import info.meuse24.pdf_scanner.domain.gateway.LocalSyncServer
import info.meuse24.pdf_scanner.domain.model.LocalSyncState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Thin read-only wrapper so the drawer can show a "connection active" badge. */
@HiltViewModel
class LocalSyncStatusViewModel @Inject constructor(
    localSyncServer: LocalSyncServer
) : ViewModel() {
    val state: StateFlow<LocalSyncState> = localSyncServer.state
}
