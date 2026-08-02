package info.meuse24.pdf_scanner.ui.sync

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.gateway.LocalSyncServer
import info.meuse24.pdf_scanner.domain.gateway.ResourceProvider
import info.meuse24.pdf_scanner.domain.model.LocalSyncError
import info.meuse24.pdf_scanner.domain.model.LocalSyncState
import info.meuse24.pdf_scanner.domain.repository.AppSettingsRepository
import info.meuse24.pdf_scanner.util.sync.LocalSyncFirstUseStore
import info.meuse24.pdf_scanner.util.sync.LocalSyncService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LocalSyncViewModel @Inject constructor(
    private val localSyncServer: LocalSyncServer,
    private val resourceProvider: ResourceProvider,
    private val appSettingsRepository: AppSettingsRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val firstUseStore = LocalSyncFirstUseStore(context)
    private val _showFirstUseNotice = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LocalSyncUiState> = combine(
        localSyncServer.state,
        _showFirstUseNotice,
        _errorMessage,
        appSettingsRepository.settings
    ) { state, showNotice, errorMessage, settings ->
        LocalSyncUiState(
            state = state,
            showFirstUseNotice = showNotice,
            errorMessage = errorMessage ?: (state as? LocalSyncState.Error)?.let { mapErrorMessage(it.reason) },
            idleTimeoutMinutes = settings.localSyncIdleTimeoutMinutes
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalSyncUiState())

    fun onConnectClicked() {
        if (firstUseStore.hasAcknowledgedNotice()) {
            startService()
        } else {
            _showFirstUseNotice.value = true
        }
    }

    fun onFirstUseConfirmed() {
        firstUseStore.setNoticeAcknowledged()
        _showFirstUseNotice.value = false
        startService()
    }

    fun onFirstUseCancelled() {
        _showFirstUseNotice.value = false
    }

    fun onDisconnectClicked() {
        context.startService(LocalSyncService.stopIntent(context))
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun startService() {
        _errorMessage.value = null
        ContextCompat.startForegroundService(context, LocalSyncService.startIntent(context))
    }

    private fun mapErrorMessage(reason: LocalSyncError): String = when (reason) {
        LocalSyncError.NO_LOCAL_NETWORK -> resourceProvider.getString(R.string.local_sync_error_no_network)
        LocalSyncError.PORT_UNAVAILABLE -> resourceProvider.getString(R.string.local_sync_error_port_unavailable)
        LocalSyncError.HARD_STOPPED -> resourceProvider.getString(R.string.local_sync_error_hard_stopped)
        LocalSyncError.UNKNOWN -> resourceProvider.getString(R.string.local_sync_error_unknown)
    }
}
