package info.meuse24.pdf_scanner.ui.businesscard

import android.content.Context
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import info.meuse24.pdf_scanner.BuildConfig
import info.meuse24.pdf_scanner.R
import info.meuse24.pdf_scanner.domain.model.BusinessCard
import info.meuse24.pdf_scanner.domain.model.Document
import info.meuse24.pdf_scanner.domain.repository.DocumentRepository
import info.meuse24.pdf_scanner.domain.usecase.ExportVCardUseCase
import info.meuse24.pdf_scanner.domain.usecase.ScanBusinessCardUseCase
import info.meuse24.pdf_scanner.domain.gateway.DispatcherProvider
import info.meuse24.pdf_scanner.domain.gateway.ResourceProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BusinessCardExternalAction {
    data class AddToContacts(val uri: android.net.Uri) : BusinessCardExternalAction
    data class ShareVCard(val uri: android.net.Uri) : BusinessCardExternalAction
}

data class BusinessCardUiState(
    val record: Document? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val fullName: String = "",
    val organization: String = "",
    val jobTitle: String = "",
    val emails: String = "",
    val phones: String = "",
    val urls: String = "",
    val address: String = "",
    val externalAction: BusinessCardExternalAction? = null
)

@HiltViewModel
class BusinessCardViewModel @Inject constructor(
    repository: DocumentRepository,
    private val scanBusinessCardUseCase: ScanBusinessCardUseCase,
    private val exportVCardUseCase: ExportVCardUseCase,
    private val resourceProvider: ResourceProvider,
    private val dispatcherProvider: DispatcherProvider,
    @param:ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val scanId: Long = checkNotNull(savedStateHandle["scanId"])

    private val recordFlow = repository.getAllScans()
        .map { scans -> scans.find { it.id == scanId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _loading = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)
    private val _card = MutableStateFlow(BusinessCard())
    private val _externalAction = MutableStateFlow<BusinessCardExternalAction?>(null)

    val uiState: StateFlow<BusinessCardUiState> = combine(
        recordFlow,
        _loading,
        _error,
        _card,
        _externalAction
    ) { record, loading, error, card, externalAction ->
        BusinessCardUiState(
            record = record,
            loading = loading,
            error = error,
            fullName = card.fullName.orEmpty(),
            organization = card.organization.orEmpty(),
            jobTitle = card.jobTitle.orEmpty(),
            emails = card.emails.joinToString("\n"),
            phones = card.phones.joinToString("\n"),
            urls = card.urls.joinToString("\n"),
            address = card.address.orEmpty(),
            externalAction = externalAction
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BusinessCardUiState())

    init {
        viewModelScope.launch(dispatcherProvider.io) {
            val record = recordFlow.value
            if (record == null) {
                _error.value = resourceProvider.getString(R.string.error_document_not_found)
                _loading.value = false
                return@launch
            }

            runCatching { scanBusinessCardUseCase(record) }
                .onSuccess { _card.value = it }
                .onFailure {
                    _error.value = resourceProvider.getString(R.string.business_card_scan_failed)
                }
            _loading.value = false
        }
    }

    fun updateFullName(value: String) = updateCard { copy(fullName = value.trim().ifBlank { null }) }
    fun updateOrganization(value: String) = updateCard { copy(organization = value.trim().ifBlank { null }) }
    fun updateJobTitle(value: String) = updateCard { copy(jobTitle = value.trim().ifBlank { null }) }
    fun updateEmails(value: String) = updateCard { copy(emails = splitLines(value)) }
    fun updatePhones(value: String) = updateCard { copy(phones = splitLines(value)) }
    fun updateUrls(value: String) = updateCard { copy(urls = splitLines(value)) }
    fun updateAddress(value: String) = updateCard { copy(address = value.trim().ifBlank { null }) }

    fun addToContacts() {
        exportCard { uri -> BusinessCardExternalAction.AddToContacts(uri) }
    }

    fun shareVCard() {
        exportCard { uri -> BusinessCardExternalAction.ShareVCard(uri) }
    }

    fun consumeExternalAction() {
        _externalAction.value = null
    }

    fun reportError(message: String) {
        _error.value = message
    }

    fun clearError() {
        _error.value = null
    }

    private fun exportCard(actionBuilder: (android.net.Uri) -> BusinessCardExternalAction) {
        if (_loading.value) return

        _loading.value = true
        viewModelScope.launch(dispatcherProvider.io) {
            runCatching {
                val file = exportVCardUseCase(_card.value)
                FileProvider.getUriForFile(
                    context,
                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                    file
                )
            }.onSuccess { uri ->
                _externalAction.value = actionBuilder(uri)
            }.onFailure {
                _error.value = resourceProvider.getString(R.string.business_card_export_failed)
            }
            _loading.value = false
        }
    }

    private fun updateCard(transform: BusinessCard.() -> BusinessCard) {
        _card.value = _card.value.transform()
    }

    private fun splitLines(value: String): List<String> = value.lines()
        .map(String::trim)
        .filter(String::isNotBlank)
}
